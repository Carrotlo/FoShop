package me.foesio.foShop.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class FoTeamsHook {
    private static final long UNAVAILABLE_RETRY_MILLIS = 5_000L;

    private final JavaPlugin plugin;
    private Object teamService;
    private Method teamOfMethod;
    private Method byNameMethod;
    private Method byIdMethod;
    private Method teamNamesMethod;
    private Class<?> teamClass;
    private Method getTeamIdMethod;
    private Method getTeamNameMethod;
    private long nextLoadAttemptMillis;
    private boolean warned;

    public FoTeamsHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean available() {
        return load(false);
    }

    public Optional<TeamInfo> teamOf(Player player) {
        if (player == null) {
            return Optional.empty();
        }
        return teamOf(player.getUniqueId());
    }

    public Optional<TeamInfo> teamOf(UUID playerId) {
        if (playerId == null || !load(true)) {
            return Optional.empty();
        }
        try {
            Object result = teamOfMethod.invoke(teamService, playerId);
            return optionalTeam(result).map(this::teamInfo);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warn("FoTeams team lookup failed: " + failureMessage(exception));
            return Optional.empty();
        }
    }

    public Optional<TeamInfo> teamByInput(String input) {
        if (input == null || input.isBlank() || !load(true)) {
            return Optional.empty();
        }

        String query = input.trim();
        try {
            if (byNameMethod != null) {
                Optional<TeamInfo> byName = optionalTeam(byNameMethod.invoke(teamService, query)).map(this::teamInfo);
                if (byName.isPresent()) {
                    return byName;
                }
            }
            if (byIdMethod != null) {
                try {
                    int id = Integer.parseInt(query);
                    return optionalTeam(byIdMethod.invoke(teamService, id)).map(this::teamInfo);
                } catch (NumberFormatException ignored) {
                    return Optional.empty();
                }
            }
            return Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warn("FoTeams team resolve failed: " + failureMessage(exception));
            return Optional.empty();
        }
    }

    public List<String> teamNames() {
        if (!load(false) || teamNamesMethod == null) {
            return List.of();
        }
        try {
            Object result = teamNamesMethod.invoke(teamService);
            if (result instanceof List<?> list) {
                return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warn("FoTeams team name lookup failed: " + failureMessage(exception));
        }
        return List.of();
    }

    private Optional<Object> optionalTeam(Object result) {
        if (!(result instanceof Optional<?> optional) || optional.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(optional.get());
    }

    private TeamInfo teamInfo(Object team) {
        try {
            cacheTeamAccessors(team.getClass());
            String id = String.valueOf(getTeamIdMethod.invoke(team));
            Object nameValue = getTeamNameMethod.invoke(team);
            String name = nameValue == null ? "" : String.valueOf(nameValue);
            return new TeamInfo(id, name.isBlank() ? id : name);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void cacheTeamAccessors(Class<?> currentTeamClass) throws NoSuchMethodException {
        if (teamClass == currentTeamClass && getTeamIdMethod != null && getTeamNameMethod != null) {
            return;
        }
        teamClass = currentTeamClass;
        getTeamIdMethod = currentTeamClass.getMethod("getId");
        getTeamNameMethod = currentTeamClass.getMethod("getName");
    }

    private boolean load(boolean warnOnFailure) {
        if (teamService != null && teamOfMethod != null) {
            Plugin foTeams = Bukkit.getPluginManager().getPlugin("FoTeams");
            if (foTeams != null && foTeams.isEnabled()) {
                return true;
            }
            clearLoadedState();
        }
        long now = System.currentTimeMillis();
        if (now < nextLoadAttemptMillis) {
            return false;
        }
        Plugin foTeams = Bukkit.getPluginManager().getPlugin("FoTeams");
        if (foTeams == null || !foTeams.isEnabled()) {
            nextLoadAttemptMillis = now + UNAVAILABLE_RETRY_MILLIS;
            return false;
        }
        try {
            teamService = invokeTeamService(foTeams);
            teamOfMethod = teamService.getClass().getMethod("teamOf", UUID.class);
            byNameMethod = methodOrNull("byName", String.class);
            byIdMethod = methodOrNull("byId", int.class);
            teamNamesMethod = methodOrNull("teamNames");
            nextLoadAttemptMillis = 0L;
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            clearLoadedState();
            nextLoadAttemptMillis = now + UNAVAILABLE_RETRY_MILLIS;
            if (warnOnFailure) {
                warn("FoTeams integration is unavailable: " + failureMessage(exception));
            }
            return false;
        }
    }

    private Method methodOrNull(String name, Class<?>... parameterTypes) {
        try {
            return teamService.getClass().getMethod(name, parameterTypes);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    private Object invokeTeamService(Plugin foTeams) throws ReflectiveOperationException {
        ClassLoader classLoader = foTeams.getClass().getClassLoader();
        Class<?> teamServiceClass = Class.forName("me.foesio.foTeams.service.TeamService", false, classLoader);
        MethodHandle getTeamService = MethodHandles.publicLookup().findVirtual(
                foTeams.getClass(),
                "getTeamService",
                MethodType.methodType(teamServiceClass)
        );
        try {
            return getTeamService.invoke(foTeams);
        } catch (RuntimeException | Error exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new IllegalStateException("FoTeams getTeamService failed", throwable);
        }
    }

    private void clearLoadedState() {
        teamService = null;
        teamOfMethod = null;
        byNameMethod = null;
        byIdMethod = null;
        teamNamesMethod = null;
        teamClass = null;
        getTeamIdMethod = null;
        getTeamNameMethod = null;
    }

    private void warn(String message) {
        if (warned) {
            return;
        }
        warned = true;
        plugin.getLogger().warning(message);
    }

    private static String failureMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        Throwable cause = throwable.getCause();
        return cause == null ? throwable.getClass().getSimpleName() : cause.toString();
    }

    public record TeamInfo(String id, String name) {
    }
}
