package ru.vzotov.ozon.model;

import java.util.List;

public record ClientOperations(
        Cursors cursors,
        Boolean hasNextPage,
        List<ClientOperation> items
) implements OzonCollection<ClientOperation> {
}
