package ru.vzotov.ozon;

import io.netty.buffer.ByteBuf;
import reactor.core.publisher.Flux;
import ru.vzotov.ozon.model.OzonApi;

import java.net.URI;

public interface Ozon {
    Flux<OzonApi.ClientOperations> clientOperations(OzonApi.ClientOperationsRequest request);

    Flux<OzonApi.OrderList> orders(OzonApi.OrderListFilter filter);

    Flux<OzonApi.EChecks> eChecks();

    Flux<ByteBuf> download(URI uri);
}
