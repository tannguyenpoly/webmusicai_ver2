package com.fpoly.webmusicai.exception;

public class TierRestrictedException extends RuntimeException {
    public TierRestrictedException(String message) {
        super(message);
    }
}
