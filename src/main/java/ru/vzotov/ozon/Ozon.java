package ru.vzotov.ozon;

import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Flux;
import ru.vzotov.ozon.model.ClientOperations;
import ru.vzotov.ozon.model.ClientOperationsRequest;
import ru.vzotov.ozon.model.EChecks;
import ru.vzotov.ozon.model.OrderList;
import ru.vzotov.ozon.model.OrderListFilter;

import java.net.URI;

public interface Ozon {
    Flux<ClientOperations> clientOperations(ClientOperationsRequest request);

    Flux<OrderList> orders(OrderListFilter filter);

    Flux<EChecks> eChecks();

    Flux<DataBuffer> download(URI uri);
}
