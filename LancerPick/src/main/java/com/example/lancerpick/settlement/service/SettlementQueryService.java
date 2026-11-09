package com.example.lancerpick.settlement.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.web.PageResponse;
import com.example.lancerpick.settlement.dto.SettlementResponse;
import com.example.lancerpick.settlement.dto.SettlementSummaryResponse;
import com.example.lancerpick.settlement.repository.SettlementQueryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!demo")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class SettlementQueryService {
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private final SettlementQueryRepository queries;

  public SettlementQueryService(SettlementQueryRepository queries) {
    this.queries = queries;
  }

  public PageResponse<SettlementResponse> list(long member, int page, int size) {
    return list(member, page, size, null, null, null);
  }

  public PageResponse<SettlementResponse> list(
      long member, int page, int size, String from, String to, String sort) {
    if (page < 0 || size < 1 || size > 100) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "페이지를 확인해 주세요.");
    }
    var condition = condition(member, from, to, sort);
    long total = queries.count(condition);
    long offset = (long) page * size;
    List<SettlementResponse> items =
        offset >= total
            ? List.of()
            : queries.list(condition, size, offset).stream()
                .map(row ->
                    new SettlementResponse(
                        row.getId().toString(),
                        Long.toString(row.getOrderId()),
                        row.getGrossAmount(),
                        row.getFeeRateBps(),
                        row.getFeeAmount(),
                        row.getSellerAmount(),
                        row.getCreatedAt().atOffset(ZoneOffset.UTC).toString()))
                .toList();
    int totalPages = Math.toIntExact(total / size + (total % size == 0 ? 0 : 1));
    return new PageResponse<>(items, page, size, total, totalPages);
  }

  public SettlementSummaryResponse summary(long member) {
    return summary(member, null, null);
  }

  public SettlementSummaryResponse summary(long member, String from, String to) {
    var totals = queries.totals(condition(member, from, to, null));
    return new SettlementSummaryResponse(
        totals.count(), safe(totals.gross()), safe(totals.fee()), safe(totals.seller()));
  }

  private static SettlementQueryRepository.Condition condition(
      long member, String from, String to, String sort) {
    var start = date(from);
    var end = date(to);
    if ((start != null && end != null && start.isAfter(end))
        || (sort != null && !sort.equals("latest") && !sort.equals("oldest"))) {
      throw invalidCondition();
    }
    var lower = start == null ? null : kstStart(start);
    // MySQL DATETIME은 1000~9999년이다. 범위를 넘어서는 바인딩 없이 끝 날짜도 포함한다.
    var minimum = LocalDateTime.of(1000, 1, 1, 0, 0);
    if (lower != null && lower.isBefore(minimum)) {
      lower = minimum;
    }
    var upper = end == null ? null : kstStart(end.plusDays(1));
    return new SettlementQueryRepository.Condition(member, lower, upper, "oldest".equals(sort));
  }

  private static LocalDate date(String value) {
    if (value == null) {
      return null;
    }
    if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
      throw invalidCondition();
    }
    try {
      var parsed = LocalDate.parse(value);
      if (parsed.getYear() < 1000) {
        throw invalidCondition();
      }
      return parsed;
    } catch (DateTimeParseException exception) {
      throw invalidCondition();
    }
  }

  private static LocalDateTime kstStart(LocalDate date) {
    return date.atStartOfDay(KST).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
  }

  private static BusinessException invalidCondition() {
    return new BusinessException(
        HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "판매 수익 조회 조건을 확인해 주세요.");
  }

  private static long safe(BigDecimal value) {
    long amount = value.longValueExact();
    if (amount > 9_007_199_254_740_991L) {
      throw new ArithmeticException("Settlement summary exceeds JSON safe integer range");
    }
    return amount;
  }
}
