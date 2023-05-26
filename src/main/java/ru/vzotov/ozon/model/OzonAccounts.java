package ru.vzotov.ozon.model;

import java.util.List;

public record OzonAccounts(
        List<OzonAccount> accounts,
        OzonAccount mainAccount
) {
}
