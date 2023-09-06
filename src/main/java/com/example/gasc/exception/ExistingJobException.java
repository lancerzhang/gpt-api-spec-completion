package com.example.gasc.exception;

public class ExistingJobException extends RuntimeException {
    public ExistingJobException(String message) {
        super(message);
    }
}
