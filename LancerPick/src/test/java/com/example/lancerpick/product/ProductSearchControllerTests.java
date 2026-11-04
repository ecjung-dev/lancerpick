package com.example.lancerpick.product;

import com.example.lancerpick.common.exception.ApiExceptionHandler;
import com.example.lancerpick.common.security.SecurityConfig;
import com.example.lancerpick.common.security.SecurityErrorWriter;
import com.example.lancerpick.common.web.TraceIdFilter;
import com.example.lancerpick.product.controller.ProductSearchController;
import com.example.lancerpick.product.repository.ProductSearchQueries;
import com.example.lancerpick.product.service.ProductSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 Service 입력 검증과 공개 HTTP 경계를 확인한다. SQL은 MySQL 통합 검사에서 실행한다. */
@WebMvcTest(controllers = ProductSearchController.class,
        properties = "spring.config.location=optional:classpath:/default-catalog-test.properties")
@ActiveProfiles("test")
@Import({ProductSearchService.class, SecurityConfig.class, SecurityErrorWriter.class,
        TraceIdFilter.class, ApiExceptionHandler.class})
class ProductSearchControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean ProductSearchQueries queries;

    @Test
    void anonymousGetsDefaultPageAndEmptyResult() throws Exception {
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.totalPages").value(0));
        verify(queries).count(null, null);
        verifyNoMoreInteractions(queries);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"0", "-1", "abc", "1.5", "9223372036854775808"})
    void invalidDetailIdDoesNotQuery(String id) throws Exception {
        mvc.perform(get("/api/products/" + id)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(queries);
    }

    @Test
    void absentDetailIsPublic404() throws Exception {
        when(queries.findPublicDetail(12L)).thenReturn(java.util.Optional.empty());
        mvc.perform(get("/api/products/12")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @ParameterizedTest
    @CsvSource({"page,-1", "page,abc", "page,2147483648", "size,0", "size,101",
            "size,abc", "categoryId,0", "categoryId,-1", "categoryId,abc",
            "categoryId,9223372036854775808", "categoryId,1.5"})
    void badInputNeverQueriesDatabase(String field, String value) throws Exception {
        mvc.perform(get("/api/products").param(field, value))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(queries);
    }

    @Test
    void emptyCategoryIsInvalid() throws Exception {
        mvc.perform(get("/api/products").param("categoryId", ""))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(queries);
    }

    @Test
    void normalizesUnicodeWhitespaceAndCountsEmojiAsOneCodePoint() throws Exception {
        String keyword = "😀".repeat(100);
        mvc.perform(get("/api/products").param("keyword", "\u00a0" + keyword + "\u3000"))
                .andExpect(status().isOk());
        verify(queries).count(keyword, null);
        mvc.perform(get("/api/products").param("keyword", keyword + "😀"))
                .andExpect(status().isBadRequest());
        verifyNoMoreInteractions(queries);
    }

    @Test
    void whitespaceOnlyMeansNoKeywordAndOffsetDoesNotOverflow() throws Exception {
        when(queries.count(null, 12L)).thenReturn(201L);
        mvc.perform(get("/api/products").param("keyword", "\u0085 \u3000")
                        .param("categoryId", "12").param("page", "2147483647").param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(201)).andExpect(jsonPath("$.totalPages").value(3));
        verify(queries).count(null, 12L);
        verifyNoMoreInteractions(queries);
    }
}
