package com.starsoft.voint.common.dto;

import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Turns raw query parameters into a {@link Pageable}.
 *
 * <p>The sort column is checked against a whitelist rather than passed through. A sort parameter
 * goes straight into a JPA property path, so accepting anything lets a caller sort by fields the
 * endpoint never meant to expose - and, for properties that do not exist, turns a typo in a URL
 * into a 500.
 *
 * <p>Page size is capped for the same reason: {@code ?size=1000000} should not be a way to ask the
 * server to build a million rows.
 */
public final class PageRequests {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 200;

    private PageRequests() {
    }

    public static Pageable of(Integer page, Integer size, String sort, String direction,
                              Set<String> allowedSorts, String defaultSort) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return PageRequest.of(safePage, safeSize, sortOf(sort, direction, allowedSorts, defaultSort));
    }

    public static Sort sortOf(String sort, String direction, Set<String> allowed, String fallback) {
        String property = sort != null && allowed.contains(sort) ? sort : fallback;
        Sort.Direction dir = "desc".equalsIgnoreCase(direction)
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return Sort.by(dir, property);
    }

    /** The column a caller actually gets, after the whitelist has had its say. */
    public static String resolveSort(String sort, Set<String> allowed, String fallback) {
        return sort != null && allowed.contains(sort) ? sort : fallback;
    }

    public static String resolveDirection(String direction) {
        return "desc".equalsIgnoreCase(direction) ? "desc" : "asc";
    }

    public static int resolvePage(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    public static int resolveSize(Integer size) {
        return size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }
}
