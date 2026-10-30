package me.foesio.foShop.api;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.List;

/** Effective offers changed after a timed, admin, API, or reload reset. State may not be durable if disk writing fails. */
public final class FoShopRotationChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final List<FoShopRotatingOffer> previousOffers;
    private final List<FoShopRotatingOffer> currentOffers;
    private final long nextResetAtMillis;
    private final Cause cause;

    public FoShopRotationChangedEvent(List<FoShopRotatingOffer> previousOffers,
                                      List<FoShopRotatingOffer> currentOffers,
                                      long nextResetAtMillis, Cause cause) {
        super(!Bukkit.isPrimaryThread());
        this.previousOffers = List.copyOf(previousOffers);
        this.currentOffers = List.copyOf(currentOffers);
        this.nextResetAtMillis = nextResetAtMillis;
        this.cause = cause;
    }

    public List<FoShopRotatingOffer> getPreviousOffers() { return previousOffers; }
    public List<FoShopRotatingOffer> getCurrentOffers() { return currentOffers; }
    public long getNextResetAtMillis() { return nextResetAtMillis; }
    public Cause getCause() { return cause; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }

    public enum Cause { TIMED, ADMIN, API, RELOAD }
}
