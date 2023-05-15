package ru.vzotov.ozon.model;

import java.time.LocalDate;
import java.util.List;

public record OzonClient(
        String id,
        Boolean isLoggedIn,
        String identificationLevel,
        String identificationType,
        String name,
        String fullName,
        LocalDate dateOfBirth,
        String passportInfo,
        List<Object> companies,
        String status
) {
}
