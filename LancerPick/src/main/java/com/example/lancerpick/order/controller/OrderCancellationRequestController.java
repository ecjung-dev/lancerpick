package com.example.lancerpick.order.controller;

import com.example.lancerpick.common.security.CurrentMemberId;
import com.example.lancerpick.order.dto.*;
import com.example.lancerpick.order.service.OrderCancellationRequestService;
import com.example.lancerpick.order.service.OrderCancellationRequestService.Resolution;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!demo")
@RequestMapping("/api/orders/{id}/cancellation-requests")
public class OrderCancellationRequestController {
    private final OrderCancellationRequestService requests;
    private final CurrentMemberId members;
    public OrderCancellationRequestController(OrderCancellationRequestService requests, CurrentMemberId members) { this.requests = requests; this.members = members; }

    @PostMapping
    public ResponseEntity<CancellationResultResponse> create(Authentication authentication, @PathVariable String id, @Valid @RequestBody CreateCancellationRequest input) {
        var result = requests.create(members.require(authentication), id, input);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).cacheControl(CacheControl.noStore()).body(result.result());
    }
    @PostMapping("/{requestId}/approve")
    public ResponseEntity<CancellationResultResponse> approve(Authentication authentication, @PathVariable String id, @PathVariable String requestId) {
        return response(requests.resolve(members.require(authentication), id, requestId, Resolution.APPROVED, null));
    }
    @PostMapping("/{requestId}/reject")
    public ResponseEntity<CancellationResultResponse> reject(Authentication authentication, @PathVariable String id, @PathVariable String requestId,
            @Valid @RequestBody(required = false) RejectCancellationRequest input) {
        return response(requests.resolve(members.require(authentication), id, requestId, Resolution.REJECTED, input == null ? null : input.responseReason()));
    }
    @PostMapping("/{requestId}/withdraw")
    public ResponseEntity<CancellationResultResponse> withdraw(Authentication authentication, @PathVariable String id, @PathVariable String requestId) {
        return response(requests.resolve(members.require(authentication), id, requestId, Resolution.WITHDRAWN, null));
    }
    private static ResponseEntity<CancellationResultResponse> response(CancellationResultResponse result) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}
