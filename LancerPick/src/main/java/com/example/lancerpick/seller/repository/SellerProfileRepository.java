package com.example.lancerpick.seller.repository;

import com.example.lancerpick.seller.entity.SellerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerProfileRepository
        extends JpaRepository<SellerProfile, Long> {
}