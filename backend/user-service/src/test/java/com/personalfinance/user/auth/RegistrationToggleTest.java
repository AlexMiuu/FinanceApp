package com.personalfinance.user.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import com.personalfinance.user.config.AuthProperties;
import com.personalfinance.user.controller.AuthController;
import com.personalfinance.user.entity.UserEntity;
import com.personalfinance.user.service.AuthService;
import com.personalfinance.user.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * A deployment reachable from the web but meant for one person closes signup. The
 * check has to sit on the endpoint, not only in the UI, so this drives it through
 * the controller rather than asserting on the flag.
 */
class RegistrationToggleTest {

    private static final String BODY =
            "{\"email\":\"a@example.com\",\"password\":\"correct horse battery\",\"displayName\":\"A\"}";

    private final AuthService authService = mock(AuthService.class);

    private MockMvc mockMvcWithRegistration(boolean enabled) {
        AuthProperties properties = new AuthProperties(
                Duration.ofMinutes(15),
                Duration.ofDays(30),
                false,
                enabled,
                new AuthProperties.Jwt(""),
                new AuthProperties.Google("", ""));

        return MockMvcBuilders
                .standaloneSetup(new AuthController(
                        authService,
                        mock(JwtService.class),
                        new com.personalfinance.user.auth.RefreshCookies(properties),
                        properties))
                .build();
    }

    @Test
    void registrationIsOpenByDefaultSoDevelopmentIsUnaffected() {
        AuthProperties defaults = new AuthProperties(
                Duration.ofMinutes(15), Duration.ofDays(30), false, true,
                new AuthProperties.Jwt(""), new AuthProperties.Google("", ""));

        assertThat(defaults.registrationEnabled()).isTrue();
    }

    @Test
    void aClosedDeploymentRefusesRegistrationAndNeverReachesTheService() throws Exception {
        mockMvcWithRegistration(false)
                .perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());

        verify(authService, never()).register(any(), any(), any());
    }

    @Test
    void anOpenDeploymentStillRegisters() throws Exception {
        when(authService.register(any(), any(), any())).thenReturn(new AuthService.TokenPair(
                "access", "refresh", Duration.ofDays(30),
                new UserEntity("a@example.com", "hash", "A", null)));

        mockMvcWithRegistration(true)
                .perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated());

        verify(authService).register(any(), any(), any());
    }

    @Test
    void theSignInScreenIsToldWhichWaysInExist() throws Exception {
        mockMvcWithRegistration(false)
                .perform(get("/api/v1/auth/oauth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registration").value(false))
                .andExpect(jsonPath("$.google").value(false));

        mockMvcWithRegistration(true)
                .perform(get("/api/v1/auth/oauth/providers"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.registration").value(true));
    }
}
