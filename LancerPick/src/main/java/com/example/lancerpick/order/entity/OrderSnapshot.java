package com.example.lancerpick.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;
import com.example.lancerpick.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import com.example.lancerpick.common.text.TextNormalizer;

/** 상품 변경과 관계없이 주문 당시 상품·거래 조건을 보존한다. */
@Entity
@Table(name = "order_snapshots")
public class OrderSnapshot implements Persistable<Long> {

    // 공유 PK는 생성 시 이미 존재한다. 신규 스냅샷을 merge하여 기존 원문을 덮어쓰지 않는다.
    @Transient
    private boolean newEntity = true;

    // 주문 ID를 기본 키로 함께 사용하며 별도 ID를 생성하지 않는다.
    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "product_version_snapshot",
            nullable = false, updatable = false)
    private Long productVersionSnapshot;

    @Column(name = "product_title_snapshot",
            nullable = false, updatable = false, length = 100)
    private String productTitleSnapshot;

    @Column(name = "description_snapshot",
            nullable = false, updatable = false, columnDefinition = "TEXT")
    private String descriptionSnapshot;

    @Column(name = "scope_snapshot",
            nullable = false, updatable = false, columnDefinition = "TEXT")
    private String scopeSnapshot;

    @Column(name = "preparation_snapshot",
            nullable = false, updatable = false, columnDefinition = "TEXT")
    private String preparationSnapshot;

    @Column(name = "terms_snapshot",
            nullable = false, updatable = false, columnDefinition = "TEXT")
    private String termsSnapshot;

    @Column(name = "requirements",
            nullable = false, updatable = false, columnDefinition = "TEXT")
    private String requirements;

    protected OrderSnapshot() {
    }

    @Override
    public Long getId() {
        return orderId;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    private void markExisting() {
        newEntity = false;
    }

    public static OrderSnapshot create(
            Long orderId,
            Long productVersionSnapshot,
            String productTitleSnapshot,
            String descriptionSnapshot,
            String scopeSnapshot,
            String preparationSnapshot,
            String termsSnapshot,
            String requirements
    ) {
        if (orderId == null || orderId <= 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED",
                    "주문 ID는 양수여야 합니다."
            );
        }

        if (productVersionSnapshot == null || productVersionSnapshot < 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED",
                    "상품 버전은 0 이상이어야 합니다."
            );
        }
        OrderSnapshot snapshot = new OrderSnapshot();

        snapshot.orderId = orderId;
        snapshot.productVersionSnapshot = productVersionSnapshot;
        snapshot.productTitleSnapshot =
                normalizeRequired(productTitleSnapshot, 100, "상품명");

        snapshot.descriptionSnapshot =
                normalizeRequired(descriptionSnapshot, 10000, "상품 설명");

        snapshot.scopeSnapshot =
                normalizeRequired(scopeSnapshot, 3000, "작업 범위");

        snapshot.preparationSnapshot =
                normalizeRequired(preparationSnapshot, 3000, "구매자 준비사항");

        snapshot.termsSnapshot =
                normalizeRequired(termsSnapshot, 10000, "거래 규칙");

        snapshot.requirements =
                normalizeRequired(requirements, 5000, "구매자 요구사항");

        return snapshot;
    }

    private static String normalizeRequired(
            String value,
            int maxLength,
            String fieldName
    ) {
        if (value == null) {
            throw invalidText(fieldName, maxLength);
        }

        String normalized = TextNormalizer.normalize(value);
        int length = normalized.codePointCount(0, normalized.length());

        if (length < 1 || length > maxLength) {
            throw invalidText(fieldName, maxLength);
        }

        return normalized;
    }

    private static BusinessException invalidText(
            String fieldName,
            int maxLength
    ) {
        return new BusinessException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                fieldName + "은(는) 공백 정리 후 1~" + maxLength + "자여야 합니다."
        );
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getProductVersionSnapshot() {
        return productVersionSnapshot;
    }

    public String getProductTitleSnapshot() {
        return productTitleSnapshot;
    }

    public String getDescriptionSnapshot() {
        return descriptionSnapshot;
    }

    public String getScopeSnapshot() {
        return scopeSnapshot;
    }

    public String getPreparationSnapshot() {
        return preparationSnapshot;
    }

    public String getTermsSnapshot() {
        return termsSnapshot;
    }

    public String getRequirements() {
        return requirements;
    }
}
