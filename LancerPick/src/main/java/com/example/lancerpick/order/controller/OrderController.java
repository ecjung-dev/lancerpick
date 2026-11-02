package com.example.lancerpick.order.controller;

import com.example.lancerpick.common.security.CurrentMemberId;
import com.example.lancerpick.order.dto.CreateOrderRequest;
import com.example.lancerpick.order.dto.OrderDetailResponse;
import com.example.lancerpick.order.dto.OrderSide;
import com.example.lancerpick.order.dto.OrderSummaryResponse;
import com.example.lancerpick.common.web.PageResponse;
import com.example.lancerpick.order.service.OrderQueryService;
import com.example.lancerpick.order.service.OrderDecisionService;
import com.example.lancerpick.order.dto.RejectOrderRequest;
import com.example.lancerpick.order.dto.CreateDeliveryRequest;
import com.example.lancerpick.order.service.OrderDeliveryService;
import com.example.lancerpick.order.service.OrderRevisionService;
import com.example.lancerpick.order.dto.CreateRevisionRequest;
import com.example.lancerpick.order.service.OrderCreationApiService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!demo")
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderCreationApiService orders;
    private final CurrentMemberId currentMember;
    private final OrderQueryService queries;
    private final OrderDecisionService decisions;
    private final OrderDeliveryService deliveries;
    private final OrderRevisionService revisions;

    public OrderController(OrderCreationApiService orders, CurrentMemberId currentMember, OrderQueryService queries,
            OrderDecisionService decisions, OrderDeliveryService deliveries, OrderRevisionService revisions) {
        this.orders = orders;
        this.currentMember = currentMember;
        this.queries = queries;
        this.decisions = decisions;
        this.deliveries = deliveries;
        this.revisions = revisions;
    }

    @PostMapping("/{id}/revision-request")
    public ResponseEntity<OrderDetailResponse> revision(Authentication authentication, @PathVariable String id,
            @Valid @RequestBody CreateRevisionRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(revisions.request(currentMember.require(authentication), id, request));
    }

    @PostMapping("/{id}/deliveries")
    public ResponseEntity<OrderDetailResponse> deliver(Authentication authentication, @PathVariable String id,
            @Valid @RequestBody CreateDeliveryRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(deliveries.submit(currentMember.require(authentication), id, request));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<OrderDetailResponse> accept(Authentication authentication, @PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(decisions.accept(currentMember.require(authentication), id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<OrderDetailResponse> reject(Authentication authentication, @PathVariable String id,
            @Valid @RequestBody RejectOrderRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(decisions.reject(currentMember.require(authentication), id, request.reason()));
    }

    @GetMapping
    public ResponseEntity<PageResponse<OrderSummaryResponse>> list(Authentication authentication,
            @RequestParam OrderSide side, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(queries.list(currentMember.require(authentication), side, status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDetailResponse> detail(Authentication authentication, @PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(queries.detail(currentMember.require(authentication), id));
    }

    @PostMapping
    public ResponseEntity<OrderDetailResponse> create(Authentication authentication,
            @Valid @RequestBody CreateOrderRequest request) {
        var result = orders.create(currentMember.require(authentication), request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .cacheControl(CacheControl.noStore()).body(result.order());
    }
}
