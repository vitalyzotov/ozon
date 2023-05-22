package ru.vzotov.ozon;

import reactor.core.publisher.Mono;
import ru.vzotov.ozon.model.ClientOperations;
import ru.vzotov.ozon.model.ClientOperationsRequest;

public interface Ozon {
    Mono<ClientOperations> clientOperations(Mono<ClientOperationsRequest> request);
}
