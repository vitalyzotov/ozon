package ru.vzotov.ozon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import reactor.core.publisher.Mono;
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

}
