package com.personalfinance.quest.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void aMissingGoalBecomesA404CarryingOnlyItsMessage() {
        ResponseEntity<Map<String, String>> response =
                handler.notFound(new NotFoundException("Goal not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo(Map.of("error", "Goal not found"));
    }

    @Test
    void aValidationFailureReportsTheFirstOffendingField() {
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("goalRequestDto", "targetAmount", "must be greater than 0")));
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<Map<String, String>> response = handler.validation(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .containsEntry("error", "targetAmount: must be greater than 0");
    }

    @Test
    void aValidationFailureWithNoFieldErrorsStillProducesAMessage() {
        // Given a binding result that reports no field-level errors
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of());
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<Map<String, String>> response = handler.validation(exception);

        // Then the fallback message is used rather than a null body value
        assertThat(response.getBody()).containsEntry("error", "Validation failed");
    }

    @Test
    void aMalformedCalendarMonthBecomesA400NotA500() {
        // Given the value the calendar endpoint would fail to parse
        DateTimeParseException thrown = null;
        try {
            YearMonth.parse("not-a-month");
        } catch (DateTimeParseException e) {
            thrown = e;
        }
        assertThat(thrown).isNotNull();

        ResponseEntity<Map<String, String>> response = handler.badDate(thrown);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Invalid date value: not-a-month");
    }

    @Test
    void noHandlerEverPutsAStackTraceInTheResponseBody() {
        NotFoundException notFound = new NotFoundException("Quest not found");

        Map<String, String> body = handler.notFound(notFound).getBody();

        assertThat(body).isNotNull();
        assertThat(body.keySet()).containsExactly("error");
        assertThat(body.values()).noneSatisfy(value ->
                assertThat(value).contains("com.personalfinance.quest"));
    }
}
