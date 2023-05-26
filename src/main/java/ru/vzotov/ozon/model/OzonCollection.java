package ru.vzotov.ozon.model;

import java.util.List;

public sealed interface OzonCollection<T extends OzonRecord> permits ClientOperations, EChecks, OrderList {
    List<T> items();
}
