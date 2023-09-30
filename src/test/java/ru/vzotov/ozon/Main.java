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
import java.util.stream.Stream;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private void communicate(OzonAuthentication auth, PinCode pinCode) {
        final Ozon ozon = buildOzon(auth, pinCode);

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
                    try (final OutputStream out = Files.newOutputStream(Path.of("output.pdf"), StandardOpenOption.CREATE)) {
                        out.write(bytes);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        /* */

    }

    private static Ozon buildOzon(OzonAuthentication auth, PinCode pinCode) {
        return new OzonBuilder()
                .httpClient(http -> http
                        .wiretap("reactor.netty.http.client.HttpClient",
                                LogLevel.DEBUG, AdvancedByteBufFormat.TEXTUAL))
                .authorize(Mono.just(auth), Mono.just(pinCode));
    }


    public static void main(String... args) throws IOException {
        final String harFile = args[0];
        final String pinValue = args[1];
        final String command = args[2];

        LogManager.getLogManager().readConfiguration(Main.class.getResourceAsStream("/jul.properties"));

        final OzonAuthentication ozonAuth = OzonAuthentication.fromHar(new File(harFile));

        final Main instance = new Main();
        if ("demo".equalsIgnoreCase(command)) {
            instance.doDemo(ozonAuth, pinValue);
        } else if ("details".equalsIgnoreCase(command)) {
            final String orderId = args[3];
            instance.doDetails(ozonAuth, pinValue, orderId);
        } else if ("dl".equalsIgnoreCase(command)) {
            instance.doDownload(ozonAuth, pinValue);
        }
    }

    private void doDemo(OzonAuthentication ozonAuth, String pinValue) {
        Optional.ofNullable(ozonAuth)
                .ifPresent(auth -> {
                    final PinCode pin = new PinCode(pinValue);
                    communicate(auth, pin);
                });
    }

    private void doDetails(OzonAuthentication ozonAuth, String pinValue, String orderId) {
        Optional.ofNullable(ozonAuth)
                .ifPresent(auth -> {
                    final PinCode pin = new PinCode(pinValue);
                    final Ozon ozon = buildOzon(auth, pin);
                    final List<OzonApi.OrderDetailsPage> pages = ozon.orderDetails(orderId).buffer(1).blockFirst();
                    for (OzonApi.OrderDetailsPage page : pages) {
                        log.info("Order {} details: {}", orderId, page);
                        page.findPostings().stream()
                                .flatMap(p -> p.data().postings().stream())
                                .forEach(p -> {
                                    String postingLink = p.action().link();
                                    if (postingLink.contains("/orderDetailsPosting/")) {
                                        final OzonApi.OrderDetailsPosting posting = ozon.orderDetailsPosting(postingLink).blockFirst();
                                        log.info("Order {} posting: {}", orderId, posting);
                                    }
                                });
                    }
                });

    }

    private void doDownload(OzonAuthentication ozonAuth, String pinValue) {
        Optional.ofNullable(ozonAuth)
                .ifPresent(auth -> {
                    final PinCode pin = new PinCode(pinValue);
                    final Ozon ozon = buildOzon(auth, pin);
                    final EChecks checks = ozon.eChecks().blockFirst();
                    Stream.of(Objects.requireNonNull(checks))
                            .flatMap(item -> {
                                List<OzonApi.Check> items = new ArrayList<>(item.items());
                                Collections.shuffle(items);
                                return items.stream();
                            })
                            .findFirst()
                            .map(check -> check.button().action().link())
                            .map(URI::create)
                            .ifPresent(uri -> {
                                log.info("Downloading PDF from {}", uri);
                                final byte[] bytes = Objects.requireNonNull(
                                        ByteBufFlux.fromInbound(ozon.download(uri)).aggregate().asByteArray().block()
                                );
                                try (final OutputStream out = Files.newOutputStream(Path.of("output.pdf"), StandardOpenOption.CREATE)) {
                                    out.write(bytes);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                });
    }

}
