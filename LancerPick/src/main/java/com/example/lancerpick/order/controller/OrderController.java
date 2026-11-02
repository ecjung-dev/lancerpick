package com.example.lancerpick.order.controller;

import com.example.lancerpick.common.security.CurrentMemberId;
import com.example.lancerpick.order.dto.CreateOrderRequest;
import com.example.lancerpick.order.dto.OrderDetailResponse;
import com.example.lancerpick.order.service.OrderCreationApiService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!demo")
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderCreationApiService orders;
    private final CurrentMemberId currentMember;

    public OrderController(OrderCreationApiService orders, CurrentMemberId currentMember) {
        this.orders = orders;
        this.currentMember = currentMember;
    }

    @PostMapping
    public ResponseEntity<OrderDetailResponse> create(Authentication authentication,
            @Valid @RequestBody CreateOrderRequest request) {
        var result = orders.create(currentMember.require(authentication), request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .cacheControl(CacheControl.noStore()).body(result.order());
    }
}
