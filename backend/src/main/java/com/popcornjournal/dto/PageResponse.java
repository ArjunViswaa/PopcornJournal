package com.popcornjournal.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(content, page, size, totalElements, totalPages,
                page == 0, page >= totalPages - 1);
    }

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return of(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
