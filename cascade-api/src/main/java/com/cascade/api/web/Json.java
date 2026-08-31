package com.cascade.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Jackson wiring plus the small request/response helpers the servlets share. */
public final class Json {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private Json() {
    }

    public static void write(HttpServletResponse response, int status, Object body)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json; charset=utf-8");
        MAPPER.writeValue(response.getOutputStream(), body);
    }

    public static void error(HttpServletResponse response, int status, String message)
            throws IOException {
        write(response, status, Map.of("error", message));
    }

    public static <T> T read(HttpServletRequest request, Class<T> type) throws IOException {
        return MAPPER.readValue(request.getInputStream(), type);
    }

    public static Map<String, Object> readMap(HttpServletRequest request) throws IOException {
        byte[] body = request.getInputStream().readAllBytes();
        if (body.length == 0) {
            return new LinkedHashMap<>();
        }
        return MAPPER.readValue(new String(body, StandardCharsets.UTF_8), Map.class);
    }

    public static String stringify(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("could not serialise " + value.getClass(), e);
        }
    }

    public static Map<String, Object> map() {
        return new LinkedHashMap<>();
    }
}
