package me.foesio.foShop.economy;

import me.foesio.foShop.FoShop;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class PermissionService {

    private static final String VAULT_PLUGIN = "Vault";
    private static final String PERMISSION_CLASS = "net.milkbowl.vault.permission.Permission";

    private final FoShop plugin;
    private Object permission;
    private Method playerHasPlayerMethod;
    private Method playerHasWorldMethod;
    private Method playerAddPlayerMethod;
    private Method playerAddWorldMethod;

    public PermissionService(FoShop plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        permission = null;
        playerHasPlayerMethod = null;
        playerHasWorldMethod = null;
        playerAddPlayerMethod = null;
        playerAddWorldMethod = null;

        Plugin vault = plugin.getServer().getPluginManager().getPlugin(VAULT_PLUGIN);
        if (vault == null) {
            return false;
        }

        try {
            Class<?> permissionClass = Class.forName(PERMISSION_CLASS, false, vault.getClass().getClassLoader());
            RegisteredServiceProvider<?> provider = plugin.getServer().getServicesManager().getRegistration(permissionClass);
            if (provider == null || provider.getProvider() == null) {
                return false;
            }

            playerHasPlayerMethod = findMethod(permissionClass, "playerHas", Player.class, String.class);
            playerHasWorldMethod = findMethod(permissionClass, "playerHas", String.class, String.class, String.class);
            playerAddPlayerMethod = findMethod(permissionClass, "playerAdd", Player.class, String.class);
            playerAddWorldMethod = findMethod(permissionClass, "playerAdd", String.class, String.class, String.class);
            if (playerHasPlayerMethod == null && playerHasWorldMethod == null) {
                return false;
            }

            permission = provider.getProvider();
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("Vault permission API was not usable: " + exception.getMessage());
            if (plugin.getFileLogger() != null) {
                plugin.getFileLogger().warn("Vault permission API was not usable: " + exception.getMessage());
            }
            return false;
        }
    }

    public boolean isEnabled() {
        return permission != null;
    }

    public boolean has(Player player, String node) {
        if (permission == null) {
            return player.hasPermission(node);
        }
        try {
            Object result = playerHasPlayerMethod != null
                    ? playerHasPlayerMethod.invoke(permission, player, node)
                    : playerHasWorldMethod.invoke(permission, player.getWorld().getName(), player.getName(), node);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            logVaultFailure("playerHas", exception);
            permission = null;
            return player.hasPermission(node);
        }
    }

    public boolean grant(Player player, String node) {
        if (permission == null) {
            return false;
        }
        try {
            Object result = playerAddPlayerMethod != null
                    ? playerAddPlayerMethod.invoke(permission, player, node)
                    : playerAddWorldMethod == null ? false : playerAddWorldMethod.invoke(permission, player.getWorld().getName(), player.getName(), node);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            logVaultFailure("playerAdd", exception);
            permission = null;
            return false;
        }
    }

    private Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    private void logVaultFailure(String action, Throwable throwable) {
        Throwable cause = throwable instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause()
                : throwable;
        plugin.getLogger().warning("Vault permission " + action + " failed: " + cause.getMessage());
        if (plugin.getFileLogger() != null) {
            plugin.getFileLogger().warn("Vault permission " + action + " failed: " + cause.getMessage());
        }
    }
}
