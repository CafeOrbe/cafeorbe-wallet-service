package com.cafeorbe.wallet.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ManejadorDeErrores {

    public record ApiError(int status, String mensaje, Map<String, String> campos) {
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> sinIdentidad(MissingRequestHeaderException e) {
        return ResponseEntity.status(401).body(new ApiError(401, "Sesión requerida", Map.of()));
    }
}
