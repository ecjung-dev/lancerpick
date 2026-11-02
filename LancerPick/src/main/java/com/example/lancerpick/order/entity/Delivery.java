package com.example.lancerpick.order.entity;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.text.TextNormalizer;
import jakarta.persistence.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.http.HttpStatus;

/** 제출 원문만 보존한다. 결과물 URL에 접속하거나 파일을 가져오지 않는다. */
@Entity
@Table(name = "deliveries")
public class Delivery {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;
    @Column(name = "sequence_no", nullable = false, updatable = false)
    private int sequenceNo;
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String description;
    @Column(name = "result_url", nullable = false, updatable = false, length = 2048)
    private String resultUrl;
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "submitted_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime submittedAt;

    protected Delivery() {}

    public static Delivery create(long orderId, int sequence, String description, String resultUrl, LocalDateTime at) {
        if (orderId <= 0 || sequence < 1 || sequence > 2 || at == null || at.getYear() < 1000 || at.getYear() > 9999) {
            throw invalidInput();
        }
        String text = description == null ? "" : TextNormalizer.normalize(description);
        if (text.isEmpty() || text.codePointCount(0, text.length()) > 5000) throw invalidInput();
        String url = resultUrl == null ? "" : TextNormalizer.normalize(resultUrl);
        if (url.isEmpty() || url.codePointCount(0, url.length()) > 2048) throw invalidUrl();
        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) throw invalidUrl();
        } catch (URISyntaxException e) { throw invalidUrl(); }
        Delivery delivery = new Delivery();
        delivery.orderId = orderId;
        delivery.sequenceNo = sequence;
        delivery.description = text;
        delivery.resultUrl = url;
        delivery.submittedAt = at.truncatedTo(ChronoUnit.MICROS);
        return delivery;
    }

    private static BusinessException invalidInput() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "납품 설명은 1~5000자, 회차는 1 또는 2여야 합니다.");
    }
    private static BusinessException invalidUrl() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_URL", "결과물은 사용자 정보가 없는 유효한 HTTPS URL이어야 합니다.");
    }
}
