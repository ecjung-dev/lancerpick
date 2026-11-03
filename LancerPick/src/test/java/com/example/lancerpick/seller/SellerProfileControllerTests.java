package com.example.lancerpick.seller;

import com.example.lancerpick.common.exception.ApiExceptionHandler;
import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.common.security.CurrentMemberId;
import com.example.lancerpick.common.security.SecurityConfig;
import com.example.lancerpick.common.security.SecurityErrorWriter;
import com.example.lancerpick.common.web.TraceIdFilter;
import com.example.lancerpick.seller.controller.SellerProfileController;
import com.example.lancerpick.seller.dto.SellerProfileRequest;
import com.example.lancerpick.seller.dto.SellerProfileResponse;
import com.example.lancerpick.seller.service.SellerProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SellerProfileController.class,
        properties = "spring.config.location=optional:classpath:/default-catalog-test.properties")
@ActiveProfiles("test")
@Import({SecurityConfig.class, SecurityErrorWriter.class, CurrentMemberId.class, TraceIdFilter.class, ApiExceptionHandler.class})
class SellerProfileControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean SellerProfileService profiles;
    private static final SellerProfileResponse RESPONSE = new SellerProfileResponse("37", "표시 이름", "소개", "디자인", null);
    private static final String BODY = "{\"introduction\":\"소개\",\"specialty\":\"디자인\"}";

    record TestMember(Long memberId) implements AuthenticatedMember {}

    private static UsernamePasswordAuthenticationToken member() {
        return UsernamePasswordAuthenticationToken.authenticated(new TestMember(37L), null,
                AuthorityUtils.createAuthorityList("ROLE_USER"));
    }

    @Test
    void publicProfileIsAnonymousAndContainsOnlyPublicFields() throws Exception {
        when(profiles.getPublic("37")).thenReturn(RESPONSE);
        mvc.perform(get("/api/sellers/37"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("37"))
                .andExpect(jsonPath("$.displayName").value("표시 이름"))
                .andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.balance").doesNotExist());
    }

    @Test
    void anonymousCannotReadOrWriteOwnProfile() throws Exception {
        mvc.perform(get("/api/me/seller-profile")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/me/seller-profile").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(profiles);
    }

    @Test
    void authenticatedWriteStillNeedsCsrf() throws Exception {
        mvc.perform(put("/api/me/seller-profile").with(authentication(member()))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        verifyNoInteractions(profiles);
    }

    @Test
    void ownReadUsesPrincipalRatherThanClientIdentity() throws Exception {
        when(profiles.getMine(37L)).thenReturn(RESPONSE);
        mvc.perform(get("/api/me/seller-profile").with(authentication(member())).param("memberId", "999"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        verify(profiles).getMine(37L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void createAndUpdateUseAuthenticatedIdAndDifferentStatus(boolean created) throws Exception {
        var input = new SellerProfileRequest("소개", "디자인", null);
        when(profiles.save(37L, input)).thenReturn(new SellerProfileService.SaveResult(RESPONSE, created));
        mvc.perform(put("/api/me/seller-profile").with(authentication(member())).with(csrf())
                        .param("memberId", "999").header("X-Member-Id", "999")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().is(created ? 201 : 200)).andExpect(jsonPath("$.id").value("37"))
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(profiles).save(37L, input);
    }
}
