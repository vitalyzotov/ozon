package ru.vzotov.ozon.model;

public record Cursors(String next, String prev) {
    public Cursors() {
        this(null, null);
    }
}
