package com.example.lancerpick.order.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.text.TextNormalizer;
import com.example.lancerpick.common.time.UtcTimes;
import com.example.lancerpick.member.entity.MemberRole;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.notification.repository.OutboxEventRepository;
import com.example.lancerpick.notification.service.OrderEventPublisher;
import com.example.lancerpick.order.dto.ReviewTimerResponse;
import com.example.lancerpick.order.entity.CancellationRequestStatus;
import com.example.lancerpick.order.entity.Delivery;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.entity.OrderHistory;
import com.example.lancerpick.order.entity.OrderResponseTimer;
import com.example.lancerpick.order.entity.OrderStatus;
import com.example.lancerpick.order.repository.CancellationRequestRepository;
import com.example.lancerpick.order.repository.DeliveryRepository;
import com.example.lancerpick.order.repository.OrderClosureRepository;
import com.example.lancerpick.order.repository.OrderHistoryRepository;
import com.example.lancerpick.order.repository.OrderRepository;
import com.example.lancerpick.order.repository.OrderResponseTimerRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * V3 검수 무응답 자동 구매 확정(E7). 판매자가 기산점+48시간 이후 7일 타이머를 시작하고, 만료되면 시스템이 정상 확정과 같은
 * 수수료로 구매를 확정한다. 모든 판단은 주문 배타 잠금 아래 서버 시각으로 한다. 기산점은 마지막 납품 시각과 거절·철회된 취소
 * 요청의 처리 시각 중 늦은 값이다.
 */
@Service
@Profile("!demo")
public class ReviewTimerService {
  private static final Logger log = LoggerFactory.getLogger(ReviewTimerService.class);
  // DATETIME 범위의 최소값. 첫 조회의 커서로 쓴다.
  private static final LocalDateTime CURSOR_START = LocalDateTime.of(1000, 1, 1, 0, 0);

  private final OrderRepository orders;
  private final OrderResponseTimerRepository timers;
  private final DeliveryRepository deliveries;
  private final CancellationRequestRepository cancellations;
  private final OrderClosureRepository closures;
  private final OrderHistoryRepository histories;
  private final OrderEventPublisher events;
  private final OrderFundingGuard funding;
  private final FullFundingService fullFunding;
  private final MemberRepository members;
  private final OutboxEventRepository outbox;
  private final TransactionTemplate expiryTransaction;
  private final Clock clock;
  private final OrderCommandLocks locks;

  public ReviewTimerService(
      OrderRepository orders,
      OrderResponseTimerRepository timers,
      DeliveryRepository deliveries,
      CancellationRequestRepository cancellations,
      OrderClosureRepository closures,
      OrderHistoryRepository histories,
      OrderEventPublisher events,
      OrderFundingGuard funding,
      FullFundingService fullFunding,
      MemberRepository members,
      OutboxEventRepository outbox,
      PlatformTransactionManager transactionManager,
      Clock clock,
      OrderCommandLocks locks) {
    this.orders = orders;
    this.timers = timers;
    this.deliveries = deliveries;
    this.cancellations = cancellations;
    this.closures = closures;
    this.histories = histories;
    this.events = events;
    this.funding = funding;
    this.fullFunding = fullFunding;
    this.members = members;
    this.outbox = outbox;
    this.expiryTransaction = new TransactionTemplate(transactionManager);
    this.expiryTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    this.clock = clock;
    this.locks = locks;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ReviewTimerResponse status(long memberId, long orderId) {
    var order =
        orders
            .findById(orderId)
            .filter(candidate ->
                (candidate.getBuyerId() == memberId || candidate.getSellerId() == memberId)
                    && candidate.isFullFunding())
            .orElseThrow(ReviewTimerService::notFound);
    return view(order, memberId, now());
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public ReviewTimerResponse start(long memberId, long orderId) {
    var order = locks.lockParticipantOrder(memberId, orderId);
    if (!order.isFullFunding())
      throw conflict("ORDER_POLICY_NOT_READY", "결제 금액 보관 정책 주문이 아닙니다.");
    if (order.getSellerId() != memberId)
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "판매자만 타이머를 시작할 수 있습니다.");
    var at = now();
    // 재전송·동시 요청: 주문 잠금 아래 이미 활성 타이머가 있으면 새로 만들지 않고 현재 상태를 돌려준다.
    if (timers.existsByOrderIdAndStatus(orderId, OrderResponseTimer.ACTIVE))
      return view(order, memberId, at);
    if (order.getStatus() != OrderStatus.AWAITING_CONFIRMATION)
      throw conflict("ORDER_STATE_CONFLICT", "검수 대기 주문에서만 자동 구매 확정 타이머를 시작할 수 있습니다.");
    if (!cancellations.findByOrderIdAndStatus(orderId, CancellationRequestStatus.PENDING).isEmpty())
      throw conflict("CANCELLATION_PENDING", "대기 중인 취소 요청을 먼저 처리해 주세요.");
    funding.requireHeldForWork(order);
    var delivery = currentDelivery(order);
    var basis = basisAt(order, delivery);
    if (at.isBefore(OrderResponseTimer.eligibleAt(basis)))
      throw conflict("TIMER_NOT_ELIGIBLE", "납품 또는 취소 요청 처리 후 48시간이 지나야 타이머를 시작할 수 있습니다.");
    if (adminStoppedSince(orderId, basis))
      throw conflict(
          "TIMER_ADMIN_STOPPED", "관리자가 해제한 타이머는 새 납품이나 취소 요청 처리 이후에 다시 시작할 수 있습니다.");
    int cycle =
        Math.toIntExact(
                timers.countByOrderIdAndTimerTypeAndDeliveryId(
                    orderId, OrderResponseTimer.AUTO_CONFIRM, delivery.getId()))
            + 1;
    var timer =
        timers.saveAndFlush(
            OrderResponseTimer.startAutoConfirm(
                orderId, delivery.getId(), cycle, basis, memberId, at));
    events.publish(
        order,
        histories.saveAndFlush(OrderHistory.timerStarted(orderId, memberId, at, timer.getId())));
    return view(order, memberId, at);
  }

  /**
   * 관리자 타이머 해제(K5 화면·API가 호출할 내부 명령). 자동 확정과 같은 주문 잠금으로 직렬화하며, 만료 시각이 지난 타이머는
   * 자동 확정이 우선이라 해제하지 않는다. 이미 완료된 정산을 되돌리지 않는다.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public ReviewTimerResponse adminStop(long adminId, long orderId, String rawReason) {
    var admin = orders.findActorForShare(adminId).orElseThrow(ReviewTimerService::unauthorized);
    if (admin.getRole() != MemberRole.ADMIN)
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "관리자만 타이머를 해제할 수 있습니다.");
    String reason = rawReason == null ? "" : TextNormalizer.normalize(rawReason);
    if (reason.isEmpty() || reason.codePointCount(0, reason.length()) > 1000)
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "해제 사유는 공백 정리 후 1~1000자여야 합니다.");
    var order =
        orders
            .findForSystemUpdate(orderId)
            .filter(Order::isFullFunding)
            .orElseThrow(ReviewTimerService::notFound);
    var timer =
        timers
            .findByOrderIdAndStatus(orderId, OrderResponseTimer.ACTIVE)
            .orElseThrow(() -> conflict("TIMER_NOT_ACTIVE", "진행 중인 자동 구매 확정 타이머가 없습니다."));
    var at = now();
    if (timer.isDue(at)) throw ReviewTimers.autoConfirmDue();
    timer.adminStop(adminId, reason, at);
    timers.flush();
    events.publish(
        order,
        histories.saveAndFlush(
            OrderHistory.timerAdminStopped(orderId, adminId, at, timer.getId(), reason)));
    return view(order, adminId, at);
  }

  /**
   * 실행 시작 시각까지 만료된 활성 타이머를 batchSize씩 (만료 시각, ID) 순서로 끝까지 순회하며, 한 건마다 별도 트랜잭션으로
   * 처리한다. 한 주문의 실패가 다른 주문 처리를 막지 않는다. 실패한 타이머는 ACTIVE로 남아 다음 실행에서 다시 시도되며, 커서가
   * 지나가므로 같은 실행에서 뒤의 정상 대상을 가로막지 않는다.
   */
  public int expireDue(int batchSize) {
    var now = now();
    var afterAt = CURSOR_START;
    long afterId = 0;
    int completed = 0;
    while (true) {
      var batch = timers.dueTimersAfter(now, afterAt, afterId, PageRequest.of(0, batchSize));
      for (var due : batch) {
        long timerId = due.id();
        try {
          if (Boolean.TRUE.equals(expiryTransaction.execute(status -> expire(timerId))))
            completed++;
        } catch (BusinessException e) {
          log.warn("Auto confirmation skipped: timerId={} code={}", timerId, e.code());
        } catch (RuntimeException e) {
          log.warn(
              "Auto confirmation failed: timerId={} type={}", timerId, e.getClass().getSimpleName());
        }
      }
      if (batch.size() < batchSize) return completed;
      var last = batch.getLast();
      afterAt = last.expiresAt();
      afterId = last.id();
    }
  }

  /**
   * 만료 24시간 전 구간에 들어온 활성 타이머의 구매자에게 사전 안내를 한 번 보낸다(E9). 타이머마다 별도 트랜잭션에서 주문 잠금 후
   * 활성·구간·안내 여부를 다시 확인한다. 이미 안내한 타이머는 잠금 없이 먼저 건너뛴다. 실패는 다음 실행에서 다시 시도한다.
   */
  public int noticeDue(int batchSize) {
    var now = now();
    var noticeUntil = now.plusHours(OrderResponseTimer.NOTICE_BEFORE_HOURS);
    var afterAt = CURSOR_START;
    long afterId = 0;
    int published = 0;
    while (true) {
      var batch =
          timers.noticeTimersAfter(now, noticeUntil, afterAt, afterId, PageRequest.of(0, batchSize));
      for (var due : batch) {
        long timerId = due.id();
        if (outbox.existsByEventId(OrderEventPublisher.autoConfirmNoticeEventId(timerId))) continue;
        try {
          if (Boolean.TRUE.equals(expiryTransaction.execute(status -> notice(timerId))))
            published++;
        } catch (RuntimeException e) {
          log.warn(
              "Auto confirmation notice failed: timerId={} type={}",
              timerId,
              e.getClass().getSimpleName());
        }
      }
      if (batch.size() < batchSize) return published;
      var last = batch.getLast();
      afterAt = last.expiresAt();
      afterId = last.id();
    }
  }

  private boolean notice(long timerId) {
    var orderId = timers.orderIdOf(timerId);
    if (orderId.isEmpty()) return false;
    var order =
        orders.findForSystemUpdate(orderId.get()).orElseThrow(ReviewTimerService::integrity);
    var timer = timers.findById(timerId).orElseThrow(ReviewTimerService::integrity);
    var at = now();
    if (!timer.isInNoticeWindow(at) || order.getStatus() != OrderStatus.AWAITING_CONFIRMATION)
      return false;
    return events.publishAutoConfirmNotice(order, timer, at);
  }

  private boolean expire(long timerId) {
    // 타이머 엔티티를 잠금 전에 읽지 않는다. 주문 잠금 후 최신 상태로 다시 확인한다.
    var orderId = timers.orderIdOf(timerId);
    if (orderId.isEmpty()) return false;
    var order =
        orders.findForSystemUpdate(orderId.get()).orElseThrow(ReviewTimerService::integrity);
    var timer = timers.findById(timerId).orElseThrow(ReviewTimerService::integrity);
    var at = now();
    if (!timer.isDue(at)) return false;
    fullFunding.autoConfirm(order, timer, at);
    return true;
  }

  private ReviewTimerResponse view(Order order, long memberId, LocalDateTime now) {
    long orderId = order.getId();
    var active = timers.findByOrderIdAndStatus(orderId, OrderResponseTimer.ACTIVE);
    if (active.isPresent()) return response("ACTIVE", active.get(), false);
    if (order.getStatus() == OrderStatus.COMPLETED) {
      var closure = closures.findByOrderId(orderId).orElse(null);
      if (closure != null
          && "AUTO_CONFIRMED".equals(closure.getReason())
          && closure.getSourceTimerId() != null) {
        var timer =
            timers.findById(closure.getSourceTimerId()).orElseThrow(ReviewTimerService::integrity);
        return response("AUTO_CONFIRMED", timer, false);
      }
      return empty("NOT_IN_REVIEW");
    }
    if (order.getStatus() != OrderStatus.AWAITING_CONFIRMATION) return empty("NOT_IN_REVIEW");
    if (!cancellations.findByOrderIdAndStatus(orderId, CancellationRequestStatus.PENDING).isEmpty())
      return empty("CANCELLATION_PENDING");
    var basis = basisAt(order, currentDelivery(order));
    var eligible = OrderResponseTimer.eligibleAt(basis);
    if (adminStoppedSince(orderId, basis))
      return new ReviewTimerResponse("ADMIN_STOPPED", utc(basis), utc(eligible), null, null, false);
    if (now.isBefore(eligible))
      return new ReviewTimerResponse("WAITING", utc(basis), utc(eligible), null, null, false);
    return new ReviewTimerResponse(
        "STARTABLE", utc(basis), utc(eligible), null, null, order.getSellerId() == memberId);
  }

  private Delivery currentDelivery(Order order) {
    return deliveries
        .findFirstByOrderIdOrderBySequenceNoDesc(order.getId())
        .orElseThrow(ReviewTimerService::integrity);
  }

  private LocalDateTime basisAt(Order order, Delivery delivery) {
    var basis = delivery.getSubmittedAt();
    var reopened = cancellations.latestReopenedAt(order.getId()).orElse(null);
    return reopened != null && reopened.isAfter(basis) ? reopened : basis;
  }

  private boolean adminStoppedSince(long orderId, LocalDateTime basis) {
    return timers.latestAdminStopAt(orderId).map(at -> !at.isBefore(basis)).orElse(false);
  }

  private LocalDateTime now() {
    return UtcTimes.nowMicros(clock);
  }

  private static ReviewTimerResponse response(String status, OrderResponseTimer timer, boolean canStart) {
    return new ReviewTimerResponse(
        status,
        utc(timer.getBasisAt()),
        utc(timer.getEligibleAt()),
        utc(timer.getStartedAt()),
        utc(timer.getExpiresAt()),
        canStart);
  }

  private static ReviewTimerResponse empty(String status) {
    return new ReviewTimerResponse(status, null, null, null, null, false);
  }

  private static OffsetDateTime utc(LocalDateTime value) {
    return value == null ? null : value.atOffset(ZoneOffset.UTC);
  }

  private static BusinessException unauthorized() {
    return new BusinessException(
        HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
  }

  private static BusinessException notFound() {
    return new BusinessException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "주문을 찾을 수 없습니다.");
  }

  private static BusinessException conflict(String code, String message) {
    return new BusinessException(HttpStatus.CONFLICT, code, message);
  }

  private static BusinessException integrity() {
    return new BusinessException(
        HttpStatus.INTERNAL_SERVER_ERROR, "DATA_INTEGRITY_ERROR", "검수 타이머 기록을 확인해야 합니다.");
  }
}
