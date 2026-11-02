package com.example.lancerpick.order.entity;

import org.junit.jupiter.api.Test;
import com.example.lancerpick.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;

class OrderSnapshotTests {

    @Test
    void 주문ID와_상품버전을_보관하고_문자열을_정리한다() {
        OrderSnapshot snapshot = OrderSnapshot.create(
                15L,
                0L,
                "\u3000로고 제작\u00A0",
                " 상세 설명\r\n둘째 줄 ",
                " 작업 범위 ",
                " 구매자 준비사항 ",
                " 거래 규칙 ",
                " 파란색으로 제작해 주세요. "
        );

        assertThat(snapshot.getOrderId()).isEqualTo(15L);
        assertThat(snapshot.getProductVersionSnapshot()).isEqualTo(0L);
        assertThat(snapshot.getProductTitleSnapshot()).isEqualTo("로고 제작");
        assertThat(snapshot.getDescriptionSnapshot())
                .isEqualTo("상세 설명\n둘째 줄");
        assertThat(snapshot.getScopeSnapshot()).isEqualTo("작업 범위");
        assertThat(snapshot.getPreparationSnapshot()).isEqualTo("구매자 준비사항");
        assertThat(snapshot.getTermsSnapshot()).isEqualTo("거래 규칙");
        assertThat(snapshot.getRequirements())
                .isEqualTo("파란색으로 제작해 주세요.");
    }

    @Test
    void 주문ID가_없거나_양수가_아니면_거절한다() {
        Long[] invalidIds = {null, 0L, -1L};

        for (Long orderId : invalidIds) {
            assertInvalidSnapshot(orderId, 0L);
        }
    }

    @Test
    void 상품버전이_없거나_음수이면_거절한다() {
        Long[] invalidVersions = {null, -1L};

        for (Long version : invalidVersions) {
            assertInvalidSnapshot(15L, version);
        }
    }

    private void assertInvalidSnapshot(Long orderId, Long version) {
        assertThatThrownBy(() -> OrderSnapshot.create(
                orderId,
                version,
                "상품명",
                "상품 설명",
                "작업 범위",
                "구매자 준비사항",
                "거래 규칙",
                "구매자 요구사항"
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status())
                            .isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.code())
                            .isEqualTo("VALIDATION_FAILED");
                });
    }

    @Test
    void 각_문자열이_없거나_공백뿐이면_거절한다() {
        String[] invalidTexts = {null, "", " ", "\u00A0\u3000", "\r\n"};

        for (int index = 0; index < 6; index++) {
            for (String invalidText : invalidTexts) {
                String[] texts = {
                        "상품명", "상품 설명", "작업 범위",
                        "구매자 준비사항", "거래 규칙", "구매자 요구사항"
                };
                texts[index] = invalidText;

                assertThatThrownBy(() -> OrderSnapshot.create(
                        15L, 0L,
                        texts[0], texts[1], texts[2],
                        texts[3], texts[4], texts[5]
                ))
                        .as("필드 위치: %s, 입력값: %s", index, invalidText)
                        .isInstanceOfSatisfying(BusinessException.class, exception -> {
                            assertThat(exception.status())
                                    .isEqualTo(HttpStatus.BAD_REQUEST);
                            assertThat(exception.code())
                                    .isEqualTo("VALIDATION_FAILED");
                        });
            }
        }
    }

    @Test
    void 각_문자열은_최대길이까지_허용하고_초과하면_거절한다() {
        int[] maxLengths = {100, 10000, 3000, 3000, 10000, 5000};

        for (int index = 0; index < maxLengths.length; index++) {
            String[] texts = {
                    "상품명", "상품 설명", "작업 범위",
                    "구매자 준비사항", "거래 규칙", "구매자 요구사항"
            };

            texts[index] = "😀".repeat(maxLengths[index]);

            assertThatCode(() -> OrderSnapshot.create(
                    15L, 0L,
                    texts[0], texts[1], texts[2],
                    texts[3], texts[4], texts[5]
            ))
                    .as("최대길이를 허용할 필드: %s", index)
                    .doesNotThrowAnyException();

            texts[index] = "😀".repeat(maxLengths[index] + 1);

            assertThatThrownBy(() -> OrderSnapshot.create(
                    15L, 0L,
                    texts[0], texts[1], texts[2],
                    texts[3], texts[4], texts[5]
            ))
                    .as("길이 초과를 거절할 필드: %s", index)
                    .isInstanceOfSatisfying(BusinessException.class, exception -> {
                        assertThat(exception.status())
                                .isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(exception.code())
                                .isEqualTo("VALIDATION_FAILED");
                    });
        }
    }
}