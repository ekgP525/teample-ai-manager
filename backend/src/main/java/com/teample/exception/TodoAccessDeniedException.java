package com.teample.exception;

public class TodoAccessDeniedException extends RuntimeException {
    public TodoAccessDeniedException(String message) {
        super(message);
    }
}