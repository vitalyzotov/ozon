package ru.vzotov.ozon.model;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class CheckTest {

    private static final Logger log = LoggerFactory.getLogger(CheckTest.class);

    @Test
    public void testDate() {
        final Check check = new Check(
                "Заказ №27975760-0121",
                "2 апреля 2023 в 11:53",
                "498 ₽",
                "/my/orderdetails/?order=27975760-0121",
                "ozon://my/orderDetails/?order=27975760-0121",
                null,
                null
        );
        assertThat(check.dateTime())
                .isEqualTo(LocalDateTime.of(2023, Month.APRIL, 2, 11, 53));
    }

    @Test
    public void testReactor() {
        AtomicInteger counter = new AtomicInteger(0);
        Mono<String> constant = Mono.just("constant");
        Mono<String> value = Mono.create(sink -> {
            final int val = counter.incrementAndGet();
            if (val <= 2) {
                log.info("Create empty mono");
                sink.success();
            } else {
                log.info("Create value mono");
                sink.success("val" + val);
            }
        });

        final Mono<String> result = constant.zipWith(value)
                .flatMap(tuple -> Mono.just(tuple.getT1() + "_" + tuple.getT2()))
                .cache(s -> Duration.ofMillis(Long.MAX_VALUE), e -> Duration.ZERO, () -> Duration.ZERO);

        final String string1 = result.block();
        final String string2 = result.block();
        final String string3 = result.block();
        final String string4 = result.block();
        assertThat(string1).isNull();
        assertThat(string2).isNull();
        assertThat(string3).isEqualTo("constant_val3");
        assertThat(string4).isEqualTo("constant_val3");
        assertThat(counter.get()).isEqualTo(3);// mono is called 3 times
    }
}
