package com.example.demo.controller;

import com.example.demo.exception.InvalidRequestException;

final class EtagPreconditions {
    private EtagPreconditions() {
    }

    static Long parse(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.startsWith("W/")) normalized = normalized.substring(2);
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        try {
            return Long.valueOf(normalized);
        } catch (NumberFormatException ex) {
            throw new InvalidRequestException("If-Match debe contener el ETag numerico del recurso");
        }
    }

    static String value(long version) {
        return "\"" + version + "\"";
    }
}
