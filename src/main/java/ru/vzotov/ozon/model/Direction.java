package ru.vzotov.ozon.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum Direction {
    @JsonProperty("outgoing")
    OUTGOING,
    @JsonProperty("incoming")
    INCOMING;
}
