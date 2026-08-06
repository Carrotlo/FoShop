package me.foesio.foShop.booster;

public record ActiveSellBooster(
        String id,
        SellBoosterScope scope,
        String ownerId,
        String ownerName,
        double multiplier,
        long startedAtMillis,
        long expiresAtMillis
) {
    public boolean expired(long nowMillis) {
        return expiresAtMillis > 0L && expiresAtMillis <= nowMillis;
    }

    public long remainingSeconds(long nowMillis) {
        if (expiresAtMillis <= 0L) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, (expiresAtMillis - nowMillis + 999L) / 1000L);
    }

    public String displayOwner() {
        if (scope == SellBoosterScope.GLOBAL) {
            return "Global";
        }
        if (ownerName != null && !ownerName.isBlank()) {
            return ownerName;
        }
        return ownerId == null || ownerId.isBlank() ? scope.display() : ownerId;
    }
}
