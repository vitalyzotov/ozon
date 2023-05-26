package ru.vzotov.ozon.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public record ClientOperation(
        String id,
        String operationId,
        String purpose,
        OffsetDateTime time,
        String merchantCategoryCode,
        Merchant merchant,
        String merchantName,
        OzonImage image,
        String type,
        String status,
        String sbpMessage,
        String ozonOrderNumber,
        String categoryGroupName,
        Long accountAmount,
        Direction direction,
        List<Object> bonus,
        Object meta
) implements OzonRecord {
    @Override
    public LocalDate date() {
        return time.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
