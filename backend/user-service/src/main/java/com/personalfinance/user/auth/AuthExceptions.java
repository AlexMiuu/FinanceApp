package com.personalfinance.user.auth;

class EmailAlreadyUsedException extends RuntimeException {
    EmailAlreadyUsedException() {
        super("An account with this email already exists");
    }
}

class InvalidCredentialsException extends RuntimeException {
    InvalidCredentialsException() {
        super("Invalid email or password");
    }
}

class InvalidRefreshTokenException extends RuntimeException {
    InvalidRefreshTokenException() {
        super("Refresh token is invalid or expired");
    }
}
