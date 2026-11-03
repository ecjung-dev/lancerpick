package com.example.lancerpick.seller.controller;

import com.example.lancerpick.common.security.CurrentMemberId;
import com.example.lancerpick.seller.dto.SellerProfileRequest;
import com.example.lancerpick.seller.dto.SellerProfileResponse;
import com.example.lancerpick.seller.service.SellerProfileService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!demo")
@RequestMapping("/api")
public class SellerProfileController {

    private final SellerProfileService profiles;
    private final CurrentMemberId currentMemberId;

    public SellerProfileController(
            SellerProfileService profiles,
            CurrentMemberId currentMemberId) {

        this.profiles = profiles;
        this.currentMemberId = currentMemberId;
    }

    @GetMapping("/sellers/{id}")
    public ResponseEntity<SellerProfileResponse> getPublic(
            @PathVariable String id) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(profiles.getPublic(id));
    }

    @GetMapping("/me/seller-profile")
    public ResponseEntity<SellerProfileResponse> getMine(
            Authentication authentication) {

        long memberId = currentMemberId.require(authentication);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(profiles.getMine(memberId));
    }

    @PutMapping("/me/seller-profile")
    public ResponseEntity<SellerProfileResponse> save(
            Authentication authentication,
            @RequestBody SellerProfileRequest request) {

        long memberId = currentMemberId.require(authentication);
        var result = profiles.save(memberId, request);

        HttpStatus status = result.created()
                ? HttpStatus.CREATED
                : HttpStatus.OK;

        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(result.profile());
    }
}