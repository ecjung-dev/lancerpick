package com.example.lancerpick.product.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.web.PageResponse;
import com.example.lancerpick.product.dto.OwnedProductSummaryResponse;
import com.example.lancerpick.product.repository.MyProductQueries;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!demo")
public class MyProductService {
    private final MyProductQueries queries;
    public MyProductService(MyProductQueries queries) { this.queries = queries; }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<OwnedProductSummaryResponse> list(long memberId, int page, int size) {
        if (!PageResponse.isValidRequest(page, size)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "페이지는 0 이상, 조회 개수는 1~100이어야 합니다.");
        }
        long total = queries.count(memberId);
        long offset = (long) page * size;
        List<OwnedProductSummaryResponse> items = offset >= total ? List.of() : queries.find(memberId, size, offset);
        int pages = (int) Math.min(Integer.MAX_VALUE, total / size + (total % size == 0 ? 0 : 1));
        return new PageResponse<>(items, page, size, total, pages);
    }
}
