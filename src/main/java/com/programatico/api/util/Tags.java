package com.programatico.api.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;

/**
 * Parser ÚNICO das tags de exercício. O campo {@code exercises.tags} convive
 * com dois formatos históricos: JSON array (gravado pelo admin) e CSV (gravado
 * pelos seeds). Antes cada service tinha seu parser e o de CSV puro corrompia
 * tags JSON em assuntos como {@code ["laços"} — use sempre este.
 */
public final class Tags {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Tags() {}

    public static List<String> parse(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(tags, new TypeReference<List<String>>() {});
        } catch (Exception ignored) {
            return Arrays.stream(tags.split(","))
                    .map(String::trim)
                    .filter(tag -> !tag.isEmpty())
                    .toList();
        }
    }
}
