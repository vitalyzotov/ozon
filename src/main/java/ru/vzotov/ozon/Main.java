package ru.vzotov.ozon;

import io.netty.handler.logging.LogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.transport.logging.AdvancedByteBufFormat;
import ru.vzotov.ozon.model.ClientOperations;
import ru.vzotov.ozon.model.ClientOperationsFilter;
import ru.vzotov.ozon.model.ClientOperationsRequest;
import ru.vzotov.ozon.model.Cursors;
import ru.vzotov.ozon.model.DateRange;
import ru.vzotov.ozon.model.EChecks;
import ru.vzotov.ozon.model.OrderList;
import ru.vzotov.ozon.model.OrderListFilter;
import ru.vzotov.ozon.security.OzonAuthentication;
import ru.vzotov.ozon.security.PinCode;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@SpringBootApplication
public class Main implements CommandLineRunner {

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
                .map(s -> UriComponentsBuilder.fromUriString(s).build())
                .flatMap(u -> u.getQueryParams().computeIfAbsent("url", k -> Collections.emptyList()).stream())
                .forEach(System.out::println);

        Objects.requireNonNull(checks)
                .stream().flatMap(item -> item.items().stream())
                .findFirst().map(check -> check.button().action().link())
                .map(URI::create)
                .ifPresent(uri -> {
                    DataBufferUtils.write(ozon.download(uri), Path.of("output.pdf"), StandardOpenOption.CREATE)
                            .share().block();
                });
    }


    public static void main(String... args) {
        final String[] newArgs = Arrays.copyOf(args, args.length + 1);
        newArgs[newArgs.length - 1] = "--spring.profiles.active=ozon";
        SpringApplication.run(Main.class, newArgs);
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Started");
        Optional.ofNullable(OzonAuthentication.fromHar(new File(args[0])))
                .ifPresent(auth -> {
                    final PinCode pin = new PinCode(args[1]);
                    communicate(auth, pin);
                });
    }

}
