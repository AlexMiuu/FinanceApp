package com.personalfinance.expense.web;

public final class ApiExceptions {

    private ApiExceptions() {
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }

    public static class UnprocessableException extends RuntimeException {
        public UnprocessableException(String message) {
            super(message);
        }
    }
}
