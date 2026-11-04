package com.example.lancerpick.product.controller;

import com.example.lancerpick.common.security.CurrentMemberId;
import com.example.lancerpick.common.web.PageResponse;
import com.example.lancerpick.product.dto.OwnedProductSummaryResponse;
import com.example.lancerpick.product.service.MyProductService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!demo")
@RequestMapping("/api/me/products")
public class MyProductController {
    private final MyProductService products;
    private final CurrentMemberId current;
    public MyProductController(MyProductService products, CurrentMemberId current) { this.products = products; this.current = current; }
    @GetMapping
    public ResponseEntity<PageResponse<OwnedProductSummaryResponse>> list(Authentication auth,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(products.list(current.require(auth), page, size));
    }
}
