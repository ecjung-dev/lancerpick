package com.example.lancerpick.settlement.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.web.PageResponse;
import com.example.lancerpick.settlement.dto.SettlementResponse;
import com.example.lancerpick.settlement.dto.SettlementSummaryResponse;
import com.example.lancerpick.settlement.repository.SettlementQueryRepository;
import java.math.BigDecimal;
import java.time.ZoneOffset;
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
  private final SettlementQueryRepository queries;

  public SettlementQueryService(SettlementQueryRepository queries) {
    this.queries = queries;
  }

  public PageResponse<SettlementResponse> list(long member, int page, int size) {
    if (page < 0 || size < 1 || size > 100) {
      throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "페이지를 확인해 주세요.");
    }
    long total = queries.count(member);
    long offset = (long) page * size;
    List<SettlementResponse> items =
        offset >= total
            ? List.of()
            : queries.list(member, size, offset).stream()
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
    var totals = queries.totals(member);
    return new SettlementSummaryResponse(
        totals.count(), safe(totals.gross()), safe(totals.fee()), safe(totals.seller()));
  }

  private static long safe(BigDecimal value) {
    long amount = value.longValueExact();
    if (amount > 9_007_199_254_740_991L) {
      throw new ArithmeticException("Settlement summary exceeds JSON safe integer range");
    }
    return amount;
  }
}
