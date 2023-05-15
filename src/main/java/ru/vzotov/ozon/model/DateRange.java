package ru.vzotov.ozon.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.YearMonth;

import static com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING;

public record DateRange(
        @JsonFormat(shape = STRING) LocalDate from,
        @JsonFormat(shape = STRING) LocalDate to) {
    public DateRange(YearMonth month) {
        this(month.atDay(1), month.atEndOfMonth());
    }
}
