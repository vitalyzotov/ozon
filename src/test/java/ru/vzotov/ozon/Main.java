package ru.vzotov.ozon;

import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.logging.LogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.transport.logging.AdvancedByteBufFormat;
import ru.vzotov.ozon.model.OzonApi;
import ru.vzotov.ozon.model.OzonApi.ClientOperations;
import ru.vzotov.ozon.model.OzonApi.ClientOperationsFilter;
import ru.vzotov.ozon.model.OzonApi.ClientOperationsRequest;
import ru.vzotov.ozon.model.OzonApi.Cursors;
import ru.vzotov.ozon.model.OzonApi.DateRange;
import ru.vzotov.ozon.model.OzonApi.EChecks;
import ru.vzotov.ozon.model.OzonApi.OrderList;
import ru.vzotov.ozon.model.OzonApi.OrderListFilter;
import ru.vzotov.ozon.security.SecurityApi.OzonAuthentication;
import ru.vzotov.ozon.security.SecurityApi.PinCode;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.LogManager;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private void communicate(OzonAuthentication auth, PinCode pinCode) {
        final Ozon ozon = new OzonBuilder()
                .httpClient(http -> http
                        .wiretap("reactor.netty.http.client.HttpClient",
                                LogLevel.DEBUG, AdvancedByteBufFormat.TEXTUAL))
                .authorize(Mono.just(auth), Mono.just(pinCode));

        final List<ClientOperations> operations = ozon.clientOperations(
                new ClientOperationsRequest(
                        new Cursors(),
                        new ClientOperationsFilter(
                                Collections.emptyList(),
                                new DateRange(YearMonth.now()),
                                ClientOperationsFilter.EFFECT_UNKNOWN
                        ),
                        1,
                        30
                )
        ).buffer(2).blockFirst();

        log.info("Operations: {}", operations);

        final OrderList orders = ozon.orders(OrderListFilter.ALL).blockFirst();
        log.info("Orders: {}", orders);

        final List<EChecks> checks = ozon.eChecks().buffer(2).blockFirst();
        log.info("EChecks: {}", checks);

        Objects.requireNonNull(checks)
                .stream().flatMap(item -> item.items().stream())
                .map(check -> check.button().action().link())
                .filter(s -> s.startsWith("ozon://pdf"))
                .map(QueryStringDecoder::new)
                .flatMap(u -> u.parameters().computeIfAbsent("url", k -> Collections.emptyList()).stream())
                .forEach(System.out::println);

        /* */
        Objects.requireNonNull(checks)
                .stream().flatMap(item -> {
                    List<OzonApi.Check> items = new ArrayList<>(item.items());
                    Collections.shuffle(items);
                    return items.stream();
                })
                .findFirst().map(check -> check.button().action().link())
                .map(URI::create)
                .ifPresent(uri -> {
                    log.info("Downloading PDF from {}", uri);
                    final byte[] bytes = Objects.requireNonNull(
                            ByteBufFlux.fromInbound(ozon.download(uri)).aggregate().asByteArray().block()
                    );
                    try(final OutputStream out = Files.newOutputStream(Path.of("output.pdf"), StandardOpenOption.CREATE)) {
                        out.write(bytes);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

         /* */

    }


    public static void main(String... args) throws IOException {
        LogManager.getLogManager().readConfiguration(Main.class.getResourceAsStream("/jul.properties"));

        Main instance = new Main();
        Optional.ofNullable(OzonAuthentication.fromHar(new File(args[0])))
                .ifPresent(auth -> {
                    final PinCode pin = new PinCode(args[1]);
                    instance.communicate(auth, pin);
                });
    }


}
