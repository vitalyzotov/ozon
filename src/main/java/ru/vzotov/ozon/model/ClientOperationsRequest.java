package ru.vzotov.ozon.model;

public record ClientOperationsRequest(Cursors cursors, ClientOperationsFilter filter, int page, int perPage) {
}
