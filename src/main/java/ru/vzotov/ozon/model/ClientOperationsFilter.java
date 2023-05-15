package ru.vzotov.ozon.model;

import java.util.List;

public record ClientOperationsFilter(
        List<Object> categories,
        DateRange date,
        String effect
) {
    /**
     * Outgoing operations
     */
    public static final String EFFECT_CREDIT = "EFFECT_CREDIT";

    /**
     * All operations
     */
    public static final String EFFECT_UNKNOWN = "EFFECT_UNKNOWN";

    /**
     * Incoming operations
     */
    public static final String EFFECT_DEBIT = "EFFECT_DEBIT";
}
