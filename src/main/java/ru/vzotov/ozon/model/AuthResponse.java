package ru.vzotov.ozon.model;

public record AuthResponse(OzonClient client, String authToken, String refreshToken, Long exp) {
}
