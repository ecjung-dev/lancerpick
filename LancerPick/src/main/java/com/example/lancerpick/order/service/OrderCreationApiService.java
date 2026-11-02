package com.example.lancerpick.order.service;

import com.example.lancerpick.order.dto.CreateOrderRequest;
import com.example.lancerpick.order.dto.OrderDetailResponse;
import com.example.lancerpick.order.repository.OrderDetailQueries;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!demo")
public class OrderCreationApiService {
    private final OrderCreationService creation;
    private final OrderDetailQueries details;

    public OrderCreationApiService(OrderCreationService creation, OrderDetailQueries details) {
        this.creation = creation;
        this.details = details;
    }

    /** 생성 잠금을 응답 조립까지 유지한다. 조립 실패도 새 주문과 함께 롤백한다. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result create(long buyerId, CreateOrderRequest request) {
        var result = creation.create(buyerId, request);
        var detail = details.forParticipant(result.orderId(), buyerId);
        var actions = OrderAllowedActions.actions(detail.status(), detail.buyer().id().equals(Long.toString(buyerId)),
                detail.revisionRequest(), detail.cancellationRequests(), detail.review());
        return new Result(detail.withAllowedActions(actions), result.created());
    }

    public record Result(OrderDetailResponse order, boolean created) {}
}
