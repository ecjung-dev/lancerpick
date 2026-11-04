package com.example.lancerpick.product.controller;

import com.example.lancerpick.common.web.PageResponse;
import com.example.lancerpick.product.dto.ProductSummaryResponse;
import com.example.lancerpick.product.dto.ProductDetailResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import com.example.lancerpick.product.service.ProductSearchService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@Profile("!demo")
public class ProductSearchController {

    private final ProductSearchService productSearchService;

    public ProductSearchController(
            ProductSearchService productSearchService) {
        this.productSearchService = productSearchService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDetailResponse> detail(@PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(productSearchService.getPublicDetail(id));
    }

    @GetMapping
    public PageResponse<ProductSummaryResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return productSearchService.search(
                keyword, categoryId, page, size
        );
    }
}
