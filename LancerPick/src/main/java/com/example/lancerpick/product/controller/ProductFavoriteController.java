package com.example.lancerpick.product.controller;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.security.CurrentMemberId;
import com.example.lancerpick.common.web.PageResponse;
import com.example.lancerpick.product.dto.ProductSummaryResponse;
import com.example.lancerpick.product.service.ProductFavoriteService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!demo")
@RequestMapping("/api/me/favorites")
public class ProductFavoriteController {
    private final ProductFavoriteService favorites;
    private final CurrentMemberId current;

    public ProductFavoriteController(ProductFavoriteService favorites, CurrentMemberId current) {
        this.favorites = favorites;
        this.current = current;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ProductSummaryResponse>> list(Authentication auth,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(favorites.list(current.require(auth), page, size));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<Status> status(Authentication auth, @PathVariable long productId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new Status(favorites.contains(current.require(auth), positive(productId))));
    }

    @PutMapping("/{productId}")
    public ResponseEntity<Void> add(Authentication auth, @PathVariable long productId) {
        favorites.add(current.require(auth), positive(productId));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> remove(Authentication auth, @PathVariable long productId) {
        favorites.remove(current.require(auth), positive(productId));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    public record Status(boolean favorited) {}

    private static long positive(long id) {
        if (id <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "올바른 상품 번호가 아닙니다.");
        }
        return id;
    }
}
