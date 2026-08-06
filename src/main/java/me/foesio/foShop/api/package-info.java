/**
 * Public FoShop developer API.
 *
 * <p>Other plugins can softdepend on FoShop and fetch {@link me.foesio.foShop.api.FoShopSellBoostApi}
 * from Bukkit's services manager:</p>
 *
 * <pre>{@code
 * RegisteredServiceProvider<FoShopSellBoostApi> provider =
 *         Bukkit.getServicesManager().getRegistration(FoShopSellBoostApi.class);
 * FoShopSellBoostApi api = provider == null ? null : provider.getProvider();
 *
 * if (api != null) {
 *     api.setGlobalMultiplierBoost(this, "event_boost", 2.0, 3600L); // 2x for 1 hour
 *     api.setPlayerMultiplierBoost(this, "personal_boost", player.getUniqueId(), 1.5, 600L);
 *     api.removeBoost(this, "event_boost");
 * }
 * }</pre>
 */
package me.foesio.foShop.api;
