package com.personalfinance.expense.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsDomainExceptionsToTheirStatus() {
        assertThat(handler.notFound(new NotFoundException("gone")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(handler.conflict(new ConflictException("clash")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.unprocessable(new UnprocessableException("nope")).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void keepsTheDomainMessageInTheBody() {
        ResponseEntity<Map<String, String>> response = handler.notFound(new NotFoundException("Expense not found"));

        assertThat(response.getBody()).containsEntry("error", "Expense not found");
    }

    @Test
    void honoursTheStatusOfSpringsOwnErrorResponses() {
        NoResourceFoundException missingRoute = new NoResourceFoundException(HttpMethod.GET, "/api/v1/nope");

        ResponseEntity<Map<String, String>> response = handler.unexpected(missingRoute);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void reportsTrulyUnexpectedFailuresWithoutLeakingInternals() {
        ResponseEntity<Map<String, String>> response =
                handler.unexpected(new IllegalStateException("connection string user=admin password=hunter2"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("error", "Unexpected error");
        assertThat(response.getBody().toString()).doesNotContain("hunter2");
    }

    @Test
    void fallsBackToTheReasonPhraseWhenAnExceptionCarriesNoMessage() {
        ResponseEntity<Map<String, String>> response =
                handler.responseStatus(new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("error", "Unauthorized");
    }
}
