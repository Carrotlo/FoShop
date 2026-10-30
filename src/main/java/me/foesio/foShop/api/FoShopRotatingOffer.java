package me.foesio.foShop.api;

/** One active rotating-shop entry. Section id __global__ identifies a global-price entry. */
public record FoShopRotatingOffer(String sectionId, String productId, double sellMultiplier) { }
