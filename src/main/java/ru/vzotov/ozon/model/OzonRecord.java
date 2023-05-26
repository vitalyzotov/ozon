package ru.vzotov.ozon.model;

import java.time.LocalDate;

public sealed interface OzonRecord permits Check, ClientOperation, Order {
    LocalDate date();
}
