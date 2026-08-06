package com.personalfinance.user.privacy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.personalfinance.user.controller.AccountController;
import com.personalfinance.user.service.AccountDeletionService;
import com.personalfinance.user.service.DataExportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The confirmation gate lives on the request DTO, so it has to be exercised
 * through the @Valid boundary rather than by calling the service directly.
 */
class AccountControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private AccountDeletionService accountDeletionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        accountDeletionService = mock(AccountDeletionService.class);
        DataExportService dataExportService = mock(DataExportService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AccountController(accountDeletionService, dataExportService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deleteWithConfirmationErasesTheAccount() throws Exception {
        mockMvc.perform(delete("/api/v1/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm\":true}"))
                .andExpect(status().isNoContent());

        verify(accountDeletionService).initiateErasure(USER_ID);
    }

    @Test
    void deleteWithoutConfirmationIsRejectedBeforeAnythingIsDeleted() throws Exception {
        mockMvc.perform(delete("/api/v1/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm\":false}"))
                .andExpect(status().isBadRequest());

        verify(accountDeletionService, never()).initiateErasure(any());
    }

    @Test
    void deleteWithAMissingConfirmFlagIsRejectedBeforeAnythingIsDeleted() throws Exception {
        mockMvc.perform(delete("/api/v1/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(accountDeletionService, never()).initiateErasure(any());
    }
}
