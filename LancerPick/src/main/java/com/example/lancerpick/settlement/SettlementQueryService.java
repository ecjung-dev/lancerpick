package com.example.lancerpick.settlement;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.web.PageResponse;
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

    public record Settlement(
            String id,
            String orderId,
            long grossAmount,
            int feeRateBps,
            long feeAmount,
            long sellerAmount,
            String createdAt) {}

    public record Summary(
            long completedCount,
            long grossAmount,
            long feeAmount,
            long sellerAmount) {}

    public PageResponse<Settlement> list(long member, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "페이지를 확인해 주세요.");
        }
        long total = queries.count(member);
        long offset = (long) page * size;
        List<Settlement> items = offset >= total ? List.of() : queries.list(member, size, offset).stream()
                .map(row -> new Settlement(
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

    public Summary summary(long member) {
        var totals = queries.totals(member);
        return new Summary(
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
