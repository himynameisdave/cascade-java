package com.cascade.api.web;

import java.util.Map;

/** An error with an intended HTTP status; the filter renders it as JSON. */
public class ApiException extends RuntimeException {

    private final int status;
    private final Map<String, String> details;

    public ApiException(int status, String message) {
        this(status, message, null);
    }

    public ApiException(int status, String message, Map<String, String> details) {
        super(message);
        this.status = status;
        this.details = details;
    }

    public int status() {
        return status;
    }

    public Map<String, String> details() {
        return details;
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(401, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(403, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(409, message);
    }

    /** 422: the request was well-formed but the transition is not allowed. */
    public static ApiException unprocessable(String message) {
        return new ApiException(422, message);
    }
}
