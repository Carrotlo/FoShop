package me.foesio.foShop.api;

/** An enabled global sell listing. Kind is MATERIAL, POTION, or ENCHANTMENT;
 * id matches the corresponding global rotating-offer product id. */
public record FoShopGlobalPriceSnapshot(String kind, String id, double sellPrice,
                                        boolean rotatingEligible) { }
