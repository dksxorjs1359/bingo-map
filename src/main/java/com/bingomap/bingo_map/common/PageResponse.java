package com.bingomap.bingo_map.common;

import org.springframework.data.domain.Page;

import java.util.List;

/** 페이지 나눔 응답 공통 형식: { items, page(0부터), size, totalPages, totalElements } */
public record PageResponse<T>(List<T> items, int page, int size, int totalPages, long totalElements) {

    public static <T> PageResponse<T> of(Page<?> source, List<T> items) {
        return new PageResponse<>(items, source.getNumber(), source.getSize(),
                source.getTotalPages(), source.getTotalElements());
    }
}
