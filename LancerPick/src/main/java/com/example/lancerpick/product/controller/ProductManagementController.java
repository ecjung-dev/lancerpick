package com.example.lancerpick.product.controller;

import com.example.lancerpick.common.security.CurrentMemberId;
import com.example.lancerpick.product.dto.OwnedProductResponse;
import com.example.lancerpick.product.service.ProductManagementService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@Profile("!demo")
@RequestMapping("/api")
public class ProductManagementController {
    private final ProductManagementService products;
    private final CurrentMemberId current;
    public ProductManagementController(ProductManagementService products, CurrentMemberId current) { this.products = products; this.current = current; }
    @GetMapping("/me/products/{id}")
    public ResponseEntity<OwnedProductResponse> mine(Authentication auth, @PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(products.getMine(current.require(auth), id));
    }
    @PostMapping("/products")
    public ResponseEntity<OwnedProductResponse> create(Authentication auth, @RequestBody JsonNode body) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(products.create(current.require(auth), body));
    }
    @PatchMapping("/products/{id}")
    public ResponseEntity<OwnedProductResponse> patch(Authentication auth, @PathVariable String id, @RequestBody JsonNode body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(products.patch(current.require(auth), id, body));
    }

    @PatchMapping("/products/{id}/sale-status")
    public ResponseEntity<OwnedProductResponse> changeSaleStatus(
            Authentication auth, @PathVariable String id, @RequestBody JsonNode body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(products.changeSaleStatus(current.require(auth), id, body));
    }
}
