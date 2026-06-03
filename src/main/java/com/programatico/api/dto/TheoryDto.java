package com.programatico.api.dto;

import com.programatico.api.domain.enums.LayoutType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public final class TheoryDto {

    private TheoryDto() {}

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long moduleId;
        private String moduleTitle;
        private List<Page> pages;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Page {
        private Long id;
        private String title;
        private String description;
        private int order;
        private List<Block> blocks;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Block {
        private Long id;
        private LayoutType layoutType;
        private String textContent;
        private String imageUrl;
        private int order;
    }
}
