package com.example.demo.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Forma estable de paginacion para el Front, independiente del objeto Page
 * interno de Spring Data (que no queremos exponer directamente).
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
