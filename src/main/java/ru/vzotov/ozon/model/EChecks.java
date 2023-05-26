package ru.vzotov.ozon.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record EChecks(
        String title,
        @JsonProperty("cheques")
        List<Check> items
) implements OzonCollection<Check> {
}
