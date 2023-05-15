package ru.vzotov.ozon.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OzonImage(
        @JsonProperty("default")
        String defaultUrl
) {
}
