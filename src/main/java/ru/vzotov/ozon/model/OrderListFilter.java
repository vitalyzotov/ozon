package ru.vzotov.ozon.model;

public record OrderListFilter(
        int sort
) {
    public static final OrderListFilter ALL = new OrderListFilter(0);
    public static final OrderListFilter WAIT_FOR_PAYMENT = new OrderListFilter(1);
    public static final OrderListFilter IN_PROGRESS = new OrderListFilter(2);
    public static final OrderListFilter COMPLETED = new OrderListFilter(3);
    public static final OrderListFilter CANCELLED = new OrderListFilter(4);
}
