package com.atheryon.mortgages.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(
    LocalDateTime timestamp,
    int status,
    String error,
    String code,
    String message,
    List<String> details,
    String correlationId
) {
    public static ErrorResponse of(int status, String error, String code, String message) {
        return new ErrorResponse(LocalDateTime.now(), status, error, code, message, List.of(), null);
    }

    public static ErrorResponse of(int status, String error, String code, String message, List<String> details) {
        return new ErrorResponse(LocalDateTime.now(), status, error, code, message, details, null);
    }
}
