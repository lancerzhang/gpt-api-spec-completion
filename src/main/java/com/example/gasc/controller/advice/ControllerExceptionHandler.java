package com.example.gasc.controller.advice;

import com.example.gasc.exception.ExistingJobException;
import com.example.gasc.exception.InsufficientBudgetException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ControllerExceptionHandler {

    @ExceptionHandler(InsufficientBudgetException.class)
    public ResponseEntity<String> handleInsufficientBudgetException(InsufficientBudgetException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.FORBIDDEN);  // You can use another suitable status code.
    }

    @ExceptionHandler(ExistingJobException.class)
    public ResponseEntity<String> handleExistingJobException(ExistingJobException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.CONFLICT); // HttpStatus.CONFLICT (409) is suitable for this scenario.
    }

    // ... handle other exceptions ...
}
