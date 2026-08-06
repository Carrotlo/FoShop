package me.foesio.foShop.economy;

import me.foesio.foShop.FoShop;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class EconomyService {

    private static final String VAULT_PLUGIN = "Vault";
    private static final String ECONOMY_CLASS = "net.milkbowl.vault.economy.Economy";

    private final FoShop plugin;
    private Object economy;
    private Method getBalanceMethod;
    private Method withdrawMethod;
    private Method depositMethod;
    private Method formatMethod;

    public EconomyService(FoShop plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        economy = null;
        getBalanceMethod = null;
        withdrawMethod = null;
        depositMethod = null;
        formatMethod = null;

        Plugin vault = plugin.getServer().getPluginManager().getPlugin(VAULT_PLUGIN);
        if (vault == null) {
            return false;
        }

        try {
            Class<?> economyClass = Class.forName(ECONOMY_CLASS, false, vault.getClass().getClassLoader());
            RegisteredServiceProvider<?> provider = plugin.getServer().getServicesManager().getRegistration(economyClass);
            if (provider == null || provider.getProvider() == null) {
                return false;
            }

            getBalanceMethod = economyClass.getMethod("getBalance", OfflinePlayer.class);
            withdrawMethod = economyClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            depositMethod = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            formatMethod = economyClass.getMethod("format", double.class);
            economy = provider.getProvider();
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("Vault economy API was not usable: " + exception.getMessage());
            if (plugin.getFileLogger() != null) {
                plugin.getFileLogger().warn("Vault economy API was not usable: " + exception.getMessage());
            }
            return false;
        }
    }

    public boolean isEnabled() {
        return economy != null;
    }

    public double getBalance(Player player) {
        Object balance = invoke("getBalance", getBalanceMethod, player);
        return balance instanceof Number number ? number.doubleValue() : 0D;
    }

    public boolean withdraw(Player player, double amount) {
        return transactionSuccess(invoke("withdrawPlayer", withdrawMethod, player, amount));
    }

    public boolean deposit(Player player, double amount) {
        return transactionSuccess(invoke("depositPlayer", depositMethod, player, amount));
    }

    public String format(double amount) {
        if (economy == null) {
            return plugin.getFoConfig().formatMoney(amount);
        }
        Object formatted = invoke("format", formatMethod, amount);
        return formatted instanceof String text ? text : plugin.getFoConfig().formatMoney(amount);
    }

    private Object invoke(String action, Method method, Object... args) {
        if (economy == null || method == null) {
            return null;
        }
        try {
            return method.invoke(economy, args);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            logVaultFailure(action, exception);
            economy = null;
            return null;
        }
    }

    private boolean transactionSuccess(Object response) {
        if (response == null) {
            return false;
        }
        try {
            Object result = response.getClass().getMethod("transactionSuccess").invoke(response);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            logVaultFailure("transactionSuccess", exception);
            return false;
        }
    }

    private void logVaultFailure(String action, Throwable throwable) {
        Throwable cause = throwable instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause()
                : throwable;
        plugin.getLogger().warning("Vault economy " + action + " failed: " + cause.getMessage());
        if (plugin.getFileLogger() != null) {
            plugin.getFileLogger().warn("Vault economy " + action + " failed: " + cause.getMessage());
        }
    }
}
