package com.example.lancerpick.seller;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.seller.dto.SellerProfileRequest;
import com.example.lancerpick.seller.service.SellerProfileInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SellerProfileInputTests {
    @Test
    void normalizesWhitespaceAndNewlinesWithoutChangingInternalText() {
        var input = SellerProfileInput.from(new SellerProfileRequest("\u0085 소개\r\n 두 줄\r셋째 \u3000", "\u00a0 디자인 😀 ", " https://example.com/work "));
        assertThat(input.introduction()).isEqualTo("소개\n 두 줄\n셋째");
        assertThat(input.specialty()).isEqualTo("디자인 😀");
        assertThat(input.portfolioUrl()).isEqualTo("https://example.com/work");
    }

    @Test
    void codePointLimitsAcceptEmojiWithoutSilentTruncation() {
        assertThat(SellerProfileInput.from(new SellerProfileRequest("😀".repeat(2000), "😀".repeat(100), null)).specialty())
                .isEqualTo("😀".repeat(100));
        assertInvalid(new SellerProfileRequest("😀".repeat(2001), "디자인", null));
        assertInvalid(new SellerProfileRequest("소개", "😀".repeat(101), null));
        assertThatThrownBy(() -> SellerProfileInput.from(null)).isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\u0085\u00a0\u3000"})
    void requiredTextCannotBeMissingOrWhitespace(String text) {
        assertInvalid(new SellerProfileRequest(text, "디자인", null));
        assertInvalid(new SellerProfileRequest("소개", text, null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\u00a0\u3000"})
    void missingOrBlankPortfolioMeansRemoval(String url) {
        assertThat(SellerProfileInput.from(new SellerProfileRequest("소개", "디자인", url)).portfolioUrl()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com", "javascript:alert(1)", "//example.com", "https:///work",
            "https://user:password@example.com", "https://example.com/a b", "not-a-url"})
    void rejectsInvalidOrCredentialBearingUrls(String url) {
        assertThatThrownBy(() -> SellerProfileInput.from(new SellerProfileRequest("소개", "디자인", url)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("INVALID_URL"));
    }

    @Test
    void urlLengthIsValidatedBeforeSaving() {
        String prefix = "https://example.com/";
        String boundary = prefix + "a".repeat(2048 - prefix.length());
        assertThat(SellerProfileInput.from(new SellerProfileRequest("소개", "디자인", boundary)).portfolioUrl()).hasSize(2048);
        assertInvalid(new SellerProfileRequest("소개", "디자인", boundary + "a"));
    }

    private static void assertInvalid(SellerProfileRequest request) {
        assertThatThrownBy(() -> SellerProfileInput.from(request))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("VALIDATION_FAILED"));
    }
}
