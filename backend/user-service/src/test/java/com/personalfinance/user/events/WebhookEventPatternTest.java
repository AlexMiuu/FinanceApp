package com.personalfinance.user.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class WebhookEventPatternTest {

    @ParameterizedTest
    @CsvSource({
            "expense.*, expense.created, true",
            "expense.*, expense.created.extra, false",
            "expense.*, expense, false",
            "expense.#, expense, true",
            "expense.#, expense.created, true",
            "expense.#, expense.created.extra, true",
            "income.updated, income.updated, true",
            "income.updated, income.created, false",
            "income.updated, income.updated.extra, false",
    })
    void matches(String pattern, String routingKey, boolean expected) {
        assertThat(WebhookEventPattern.matches(pattern, routingKey)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void matchesReturnsFalseForANullOrEmptyPattern(String pattern) {
        assertThat(WebhookEventPattern.matches(pattern, "expense.created")).isFalse();
    }

    @org.junit.jupiter.api.Test
    void matchesReturnsFalseForANullRoutingKey() {
        assertThat(WebhookEventPattern.matches("expense.*", null)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "expense.$$", "expense!created", "expense. created"})
    void isValidRejectsBlankNullOrInvalidCharacters(String pattern) {
        assertThat(WebhookEventPattern.isValid(pattern)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"expense.created", "expense.*", "expense.#", "quest.*", "a", "a1_2.b3_4"})
    void isValidAcceptsValidPatterns(String pattern) {
        assertThat(WebhookEventPattern.isValid(pattern)).isTrue();
    }
}
