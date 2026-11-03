package com.example.lancerpick.seller.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "seller_profiles")
public class SellerProfile {

    @Id
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "introduction", nullable = false, columnDefinition = "TEXT")
    private String introduction;

    @Column(name = "specialty", nullable = false, length = 100)
    private String specialty;

    @Column(name = "portfolio_url", length = 2048)
    private String portfolioUrl;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SellerProfile() {
    }

    public static SellerProfile create(
            long memberId,
            String introduction,
            String specialty,
            String portfolioUrl,
            LocalDateTime now) {

        var profile = new SellerProfile();
        profile.memberId = memberId;
        profile.introduction = introduction;
        profile.specialty = specialty;
        profile.portfolioUrl = portfolioUrl;

        LocalDateTime timestamp = now.truncatedTo(ChronoUnit.MICROS);
        profile.createdAt = timestamp;
        profile.updatedAt = timestamp;

        return profile;
    }

    public void updateProfile(
            String introduction,
            String specialty,
            String portfolioUrl,
            LocalDateTime now) {

        this.introduction = introduction;
        this.specialty = specialty;
        this.portfolioUrl = portfolioUrl;
        this.updatedAt = now.truncatedTo(ChronoUnit.MICROS);
    }
}