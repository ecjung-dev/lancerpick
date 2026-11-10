package com.example.lancerpick.order.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.web.PageResponse;
import com.example.lancerpick.member.service.MemberSanctionQuery;
import com.example.lancerpick.order.dto.OrderDetailResponse;
import com.example.lancerpick.order.dto.OrderSide;
import com.example.lancerpick.order.dto.OrderSummaryResponse;
import com.example.lancerpick.order.entity.OrderStatus;
import com.example.lancerpick.order.repository.OrderDetailQueries;
import com.example.lancerpick.order.repository.OrderListQueries;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!demo")
// 전체 건수/목록과 여러 테이블의 상세를 같은 읽기 스냅샷으로 조립한다. 쓰기 잠금은 사용하지 않는다.
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class OrderQueryService {
  static final int MAX_KEYWORD_LENGTH = 100;
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final Pattern ASCII_DIGITS = Pattern.compile("[0-9]+");
  private static final Pattern DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
  private final OrderDetailQueries details;
  private final OrderListQueries lists;
  private final MemberSanctionQuery sanctions;

  public OrderQueryService(
      OrderDetailQueries details, OrderListQueries lists, MemberSanctionQuery sanctions) {
    this.details = details;
    this.lists = lists;
    this.sanctions = sanctions;
  }

  /**
   * q는 앞뒤 공백을 지운 뒤 비면 무시하고, 숫자만이면 주문번호 정확 일치, 아니면 상품명·상대방 표시 이름 부분 일치다.
   * from/to는 한국 날짜(yyyy-MM-dd)이며 [from 00:00 KST, to+1 00:00 KST)로 해석한다. sort는 latest(기본)·oldest.
   */
  public PageResponse<OrderSummaryResponse> list(
      long memberId, OrderSide side, String status, String q, String from, String to, String sort,
      int page, int size) {
    if (side == null || page < 0 || size < 1 || size > 100) throw invalidInput();
    OrderStatus filter = null;
    if (status != null) {
      try {
        filter = OrderStatus.valueOf(status);
      } catch (IllegalArgumentException exception) {
        throw invalidInput();
      }
    }
    String keyword = q == null ? null : q.strip();
    if (keyword != null && keyword.isEmpty()) keyword = null;
    if (keyword != null && keyword.codePointCount(0, keyword.length()) > MAX_KEYWORD_LENGTH) throw invalidInput();
    boolean orderNumberOnly = keyword != null && ASCII_DIGITS.matcher(keyword).matches();
    var parsedId = orderNumberOnly ? OrderIds.positive(keyword) : java.util.OptionalLong.empty();
    Long orderId = parsedId.isPresent() ? parsedId.getAsLong() : null;
    LocalDate fromDate = date(from);
    LocalDate toDate = date(to);
    if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) throw invalidInput();
    boolean oldest;
    if (sort == null || sort.equals("latest")) oldest = false;
    else if (sort.equals("oldest")) oldest = true;
    else throw invalidInput();
    var condition = new OrderListQueries.Condition(
        memberId, side, filter, orderNumberOnly ? null : keyword, orderNumberOnly, orderId,
        fromDate == null ? null : kstStartInUtc(fromDate),
        toDate == null ? null : kstStartInUtc(toDate.plusDays(1)),
        oldest);
    long total = lists.count(condition);
    long offset = (long) page * size;
    var items =
        offset >= total
            ? List.<OrderSummaryResponse>of()
            : lists.find(condition, size, offset);
    return new PageResponse<>(
        items, page, size, total, Math.toIntExact(total / size + (total % size == 0 ? 0 : 1)));
  }

  public OrderDetailResponse detail(long memberId, String id) {
    long orderId = OrderIds.positive(id).orElseThrow(OrderQueryService::invalidInput);
    var detail = details.forParticipant(orderId, memberId);
    var actions =
        OrderAllowedActions.actions(detail, detail.buyer().id().equals(Long.toString(memberId)));
    // 표시만 보완한다. 제재 사유는 응답에 노출하지 않고 명령의 회원 잠금·권한 검사는 유지한다.
    if (actions.contains("ACCEPT") && sanctions.restriction(memberId).blocksNewActivity()) {
      actions = actions.stream().filter(action -> !"ACCEPT".equals(action)).toList();
    }
    return detail.withAllowedActions(actions);
  }

  // 네 자리 연도만 받아 날짜 경계 계산이 범위를 벗어나지 않게 한다. 존재하지 않는 날짜는 STRICT 파싱이 거부한다.
  private static LocalDate date(String value) {
    if (value == null) return null;
    if (!DATE.matcher(value).matches()) throw invalidInput();
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException exception) {
      throw invalidInput();
    }
  }

  private static LocalDateTime kstStartInUtc(LocalDate date) {
    return date.atStartOfDay(KST).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
  }

  private static BusinessException invalidInput() {
    return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "주문 조회 조건을 확인해 주세요.");
  }
}
