package com.starsoft.voint.common.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * One shape for every paginated list the panels consume, so a table component can be written once
 * instead of once per endpoint.
 *
 * <p>Echoes back the page, size, sort and direction that were actually applied rather than what
 * was asked for. A request can be corrected on the way in - an unknown sort column falls back to a
 * safe default - and the table has to render the state the server really used, not the one the URL
 * happened to contain.
 */
public record PageResponse<T>(
        List<T> content,
        /** Zero-based, matching Spring Data. */
        int page,
        int size,
        long totalElements,
        int totalPages,
        String sort,
        String direction
) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                describeSort(page),
                describeDirection(page));
    }

    /** For lists paginated in memory, where there is no {@link Page} to read the state from. */
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long total,
                                         String sort, String direction) {
        int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
        return new PageResponse<>(content, page, size, total, totalPages, sort, direction);
    }

    private static String describeSort(Page<?> page) {
        return page.getSort().stream().findFirst()
                .map(order -> order.getProperty())
                .orElse(null);
    }

    private static String describeDirection(Page<?> page) {
        return page.getSort().stream().findFirst()
                .map(order -> order.getDirection().name().toLowerCase())
                .orElse(null);
    }
}
