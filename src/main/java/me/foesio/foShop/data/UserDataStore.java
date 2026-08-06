package me.foesio.foShop.data;

import me.foesio.foShop.FoShop;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

public final class UserDataStore {

    private final FoShop plugin;
    private Connection connection;

    public UserDataStore(FoShop plugin) {
        this.plugin = plugin;
    }

    public void open() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            plugin.getLogger().warning("Failed creating FoShop data folder for userdata.db.");
            return;
        }

        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + new File(dataFolder, "userdata.db").getAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA busy_timeout=5000");
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA synchronous=NORMAL");
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS purchase_limits (
                            player_uuid TEXT NOT NULL,
                            item_key TEXT NOT NULL,
                            amount INTEGER NOT NULL,
                            started_at INTEGER NOT NULL,
                            PRIMARY KEY (player_uuid, item_key)
                        )
                        """);
            }
        } catch (ClassNotFoundException exception) {
            plugin.getLogger().warning("SQLite JDBC driver missing. FoShop userdata.db persistence is disabled.");
            close();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed opening FoShop userdata.db: " + exception.getMessage());
            close();
        }
    }

    public synchronized Optional<PurchaseLimitRecord> getPurchaseLimit(UUID playerId, String itemKey) {
        if (connection == null) {
            return Optional.empty();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT amount, started_at FROM purchase_limits WHERE player_uuid = ? AND item_key = ?")) {
            statement.setString(1, playerId.toString());
            statement.setString(2, itemKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(new PurchaseLimitRecord(resultSet.getInt("amount"), resultSet.getLong("started_at")));
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed reading purchase limit from userdata.db: " + exception.getMessage());
        }
        return Optional.empty();
    }

    public synchronized void savePurchaseLimit(UUID playerId, String itemKey, int amount, long startedAt) {
        if (connection == null) {
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO purchase_limits (player_uuid, item_key, amount, started_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(player_uuid, item_key) DO UPDATE SET
                    amount = excluded.amount,
                    started_at = excluded.started_at
                """)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, itemKey);
            statement.setInt(3, amount);
            statement.setLong(4, startedAt);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed saving purchase limit to userdata.db: " + exception.getMessage());
        }
    }

    public synchronized void deletePurchaseLimit(UUID playerId, String itemKey) {
        if (connection == null) {
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM purchase_limits WHERE player_uuid = ? AND item_key = ?")) {
            statement.setString(1, playerId.toString());
            statement.setString(2, itemKey);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed deleting purchase limit from userdata.db: " + exception.getMessage());
        }
    }

    public synchronized void close() {
        if (connection == null) {
            return;
        }

        try {
            connection.close();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed closing FoShop userdata.db: " + exception.getMessage());
        } finally {
            connection = null;
        }
    }

    public record PurchaseLimitRecord(int amount, long startedAt) {
    }
}
