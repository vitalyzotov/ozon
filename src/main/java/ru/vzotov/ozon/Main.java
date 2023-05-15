package ru.vzotov.ozon;

import io.netty.handler.logging.LogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.logging.AdvancedByteBufFormat;
import ru.vzotov.ozon.model.ClientOperations;
import ru.vzotov.ozon.model.ClientOperationsFilter;
import ru.vzotov.ozon.model.ClientOperationsRequest;
import ru.vzotov.ozon.model.Cursors;
import ru.vzotov.ozon.model.DateRange;
import ru.vzotov.ozon.security.OzonAuthentication;
import ru.vzotov.ozon.security.OzonAuthorization;
import ru.vzotov.ozon.security.PinCode;

import java.io.File;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@SpringBootApplication
public class Main implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private void communicate(OzonAuthentication auth, PinCode pinCode) {
        Ozon ozon = new Ozon();
        final Mono<OzonAuthorization> authorize = ozon.authorize(Mono.just(auth), Mono.just(pinCode));

        final ClientOperations operations = ozon.clientOperations(authorize, Mono.just(
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
        )).block();

        log.info("Operations: {}", operations);

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

    private void play() {
        HttpClient httpClient = HttpClient
                .create()
                .wiretap("reactor.netty.http.client.HttpClient",
                        LogLevel.DEBUG, AdvancedByteBufFormat.TEXTUAL);

        WebClient webClient = WebClient.builder()
                .codecs(codecs -> {
                    codecs.defaultCodecs()
                            .configureDefaultCodec(conf -> {
                                log.info("codec {}", conf);
                            });
                })
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl("https://cat-fact.herokuapp.com")
                .defaultHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .build();

        Mono<String> error = webClient.post().uri(b -> b.pathSegment("facts", "random").build())
                .bodyValue(new ClientOperationsRequest(
                        new Cursors(),
                        new ClientOperationsFilter(
                                Collections.emptyList(),
                                new DateRange(YearMonth.now()),
                                ClientOperationsFilter.EFFECT_UNKNOWN
                        ),
                        1,
                        30
                ))
                .retrieve().bodyToMono(String.class);
        error.block();

        Mono<String> response = webClient.get().uri(b -> b.pathSegment("facts", "random").build())
                .retrieve()
                .bodyToMono(String.class);
        String fact = response.block();
        log.info("Got {}", fact);
    }
}
