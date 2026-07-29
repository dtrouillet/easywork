package fr.easywork.document.dto;

import java.util.List;

public record PageResponse<T>(List<T> content, PageMetadata page) {
    public record PageMetadata(int number, int size, long totalElements, int totalPages) {}
}
