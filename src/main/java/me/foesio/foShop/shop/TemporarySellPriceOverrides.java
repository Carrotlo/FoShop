package me.foesio.foShop.shop;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.LongSupplier;

/** Runtime-only owner-scoped price replacements. All access stays on the calling server context. */
public final class TemporarySellPriceOverrides implements Listener {
    private final Map<Key, Override> overrides = new LinkedHashMap<>();
    private final LongSupplier clock;
    private long revision;

    public TemporarySellPriceOverrides() {
        this(System::currentTimeMillis);
    }

    TemporarySellPriceOverrides(LongSupplier clock) {
        this.clock = clock;
    }

    public synchronized boolean set(Plugin owner, String id, ItemStack template, double price, long seconds) {
        if (owner == null || !owner.isEnabled() || id == null || id.isBlank()
                || template == null || template.getType() == Material.AIR
                || !Double.isFinite(price) || price < 0D || seconds <= 0L) {
            return false;
        }
        long duration;
        try {
            duration = Math.multiplyExact(seconds, 1000L);
        } catch (ArithmeticException exception) {
            return false;
        }
        long now = clock.getAsLong();
        if (Long.MAX_VALUE - now < duration) {
            return false;
        }
        ItemStack copy = template.clone();
        copy.setAmount(1);
        Key key = key(owner, id);
        overrides.remove(key);
        overrides.put(key, new Override(owner, copy, price, now + duration));
        revision++;
        return true;
    }

    public synchronized boolean remove(Plugin owner, String id) {
        if (owner == null || id == null) return false;
        boolean removed = overrides.remove(key(owner, id)) != null;
        if (removed) revision++;
        return removed;
    }

    public synchronized int removeAll(Plugin owner) {
        if (owner == null) return 0;
        int before = overrides.size();
        overrides.keySet().removeIf(key -> key.owner().equals(owner.getName().toLowerCase(Locale.ROOT)));
        int removed = before - overrides.size();
        if (removed > 0) revision++;
        return removed;
    }

    public synchronized OptionalDouble price(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return OptionalDouble.empty();
        long now = clock.getAsLong();
        if (overrides.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now
                || !entry.getValue().owner().isEnabled())) revision++;
        ItemStack unit = item.clone();
        unit.setAmount(1);
        for (Override override : new ArrayList<>(overrides.values()).reversed()) {
            if (unit.isSimilar(override.template())) return OptionalDouble.of(override.price());
        }
        return OptionalDouble.empty();
    }

    public synchronized void clear() {
        if (!overrides.isEmpty()) revision++;
        overrides.clear();
    }

    public synchronized long revision() {
        return revision;
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        removeAll(event.getPlugin());
    }

    private Key key(Plugin owner, String id) {
        return new Key(owner.getName().toLowerCase(Locale.ROOT), id.trim().toLowerCase(Locale.ROOT));
    }

    private record Key(String owner, String id) { }
    private record Override(Plugin owner, ItemStack template, double price, long expiresAt) { }
}
