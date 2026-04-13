package com.atheryon.mortgages.exception;

import java.util.List;

public class BusinessRuleException extends RuntimeException {

    private final String code;
    private final List<String> details;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
        this.details = List.of();
    }

    public BusinessRuleException(String code, String message, List<String> details) {
        super(message);
        this.code = code;
        this.details = details != null ? details : List.of();
    }

    public String getCode() {
        return code;
    }

    public List<String> getDetails() {
        return details;
    }
}
