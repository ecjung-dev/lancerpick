package com.example.lancerpick.review.controller;

import com.example.lancerpick.common.security.CurrentMemberId;
import com.example.lancerpick.review.dto.*;
import com.example.lancerpick.review.service.ReviewService;
import tools.jackson.databind.JsonNode;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!demo")
@RequestMapping("/api")
public class ReviewController {
    private final ReviewService reviews;
    private final CurrentMemberId current;
    public ReviewController(ReviewService reviews, CurrentMemberId current) {
        this.reviews = reviews;
        this.current = current;
    }
    @PostMapping("/orders/{id}/review")
    public ResponseEntity<ReviewResponse> write(Authentication auth, @PathVariable String id,
            @RequestBody JsonNode request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(reviews.write(current.require(auth), id, ReviewRequest.parse(request)));
    }
    @GetMapping("/products/{id}/reviews")
    public ResponseEntity<ProductReviewsResponse> list(@PathVariable String id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String rating) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reviews.list(id, page, size, rating));
    }
}
