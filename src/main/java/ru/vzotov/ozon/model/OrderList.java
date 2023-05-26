package ru.vzotov.ozon.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OrderList(
        @JsonProperty("orderListApp")
        List<Order> items
) implements OzonCollection<Order> {
}
