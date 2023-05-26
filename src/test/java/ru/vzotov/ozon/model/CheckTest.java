package ru.vzotov.ozon.model;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.time.LocalDateTime;
import java.time.Month;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(JUnit4.class)
public class CheckTest {

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
}
