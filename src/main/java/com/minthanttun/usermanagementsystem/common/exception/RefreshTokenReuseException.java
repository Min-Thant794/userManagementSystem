package com.minthanttun.usermanagementsystem.common.exception;

public class RefreshTokenReuseException extends RuntimeException {
    public RefreshTokenReuseException(String message) {
        super (message);
    }
}
