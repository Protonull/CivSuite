package com.devotedmc.ExilePearl.storage;

import com.devotedmc.ExilePearl.ExilePearl;
import com.devotedmc.ExilePearl.PearlFactory;
import com.devotedmc.ExilePearl.PearlLogger;
import com.devotedmc.ExilePearl.config.Document;
import com.devotedmc.ExilePearl.config.MySqlConfig;
import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.util.Index;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import vg.civcraft.mc.civmodcore.dao.ConnectionPool;

import java.sql.*;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.UUID;
import java.util.logging.Level;

class MySqlStorage implements PluginStorage {

    private static final Integer DATABASE_VERSION = 3;

    private final PearlFactory pearlFactory;
    private final PearlLogger logger;
    private final MySqlConfig config;

    private ConnectionPool db;
    private boolean isConnected = false;

    private static final String createPluginTable = "create table if not exists exilepearlplugin( " +
        "setting varchar(255) NOT NULL," +
        "value varchar(255) NOT NULL," +
        "PRIMARY KEY (setting));";

    private static final String createPearlTable = "create table if not exists exilepearls( " +
        "uid varchar(36) not null," +
        "killer_id varchar(36) not null," +
        "pearl_id int not null," +
        "ptype int not null," +
        "world varchar(36) not null," +
        "x int not null," +
        "y int not null," +
        "z int not null," +
        "health int not null," +
        "pearled_on long not null," +
        "freed_offline bool," +
        "PRIMARY KEY (uid));";

    private static final String createSummonTable = "create table if not exists returnlocations( " +
        "uid varchar(36) not null," +
        "world varchar(36) not null," +
        "x int not null," +
        "y int not null," +
        "z int not null," +
        "PRIMARY KEY (uid));";

    private static final String migration0001PearlTable = "alter table exilepearls " +
        "add column last_seen long not null;";
    private static final String migration0001PearlTable2 = "update exilepearls " +
        "set last_seen = unix_timestamp() * 1000;";
    private static final String migration0002PearlTable = "alter table exilepearls " +
        "add column summoned bool default 0;";

    /**
     * Creates a new MySqlStorage instance
     *
     * @param pearlFactory The pearl factory
     */
    public MySqlStorage(final PearlFactory pearlFactory, final PearlLogger logger, final MySqlConfig config) {
        Preconditions.checkNotNull(pearlFactory, "pearlFactory");
        Preconditions.checkNotNull(logger, "logger");
        Preconditions.checkNotNull(config, "config");

        this.pearlFactory = pearlFactory;
        this.logger = logger;
        this.config = config;
    }

    @Override
    public boolean connect() {
        db = new ConnectionPool(
            config.getMySqlUsername(),
            config.getMySqlPassword(),
            config.getMySqlHost(),
            config.getMySqlPort(),
            "mysql",
            config.getMySqlDatabaseName(),
            config.getMySqlPoolSize(),
            config.getMySqlConnectionTimeout(),
            config.getMySqlIdleTimeout(),
            config.getMySqlMaxLifetime());

        isConnected = true;
        setupDatabase();
        return true;
    }

    @Override
    public void disconnect() {
        isConnected = false;
        db.close();
    }

    @Override
    public boolean isConnected() {
        return isConnected;
    }

    /**
     * Sets up the database
     */
    private void setupDatabase() {

        try (Connection connection = db.getConnection();) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(createPluginTable)) {
                preparedStatement.execute();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Failed to create the plugin table.");
            }

            try (PreparedStatement preparedStatement = connection.prepareStatement(createPearlTable)) {
                preparedStatement.execute();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Failed to create the pearl table.");
            }

            try (PreparedStatement preparedStatement = connection.prepareStatement(createSummonTable)) {
                preparedStatement.execute();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Failed to create summon table");
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to setup the database");
        }

        if (isFirstRun()) {
            logger.log(Level.WARNING, "ExilePearl is running for the first time.");

            applyMigration0001();
            applyMigration0002();
            applyMigration0003();

            if (config.getMigratePrisonPearl()) {
                migratePrisonPearl();
            }

            // add new migrations here.

            setHasRun();

            updateDatabaseVersion();
        } else if (getDatabaseVersion() < DATABASE_VERSION) {
            boolean success = true;
            if (getDatabaseVersion() < 2) {
                // run migration 1.
                success &= applyMigration0001();
            }
            if (getDatabaseVersion() < 3) {
                success &= applyMigration0002();
            }
            if (getDatabaseVersion() < 4) {
                success &= applyMigration0003();
            }
            // and new migrations here.
            // if (getDatabaseVersion() < 5) { // next migration

            if (success) {
                updateDatabaseVersion();
            } else {
                logger.log(Level.SEVERE, "Not all database migrations applied cleanly, something might be horribly broken!");
            }
        }
    }

    private boolean applyMigration0001() {
        try (Connection connection = db.getConnection();) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(migration0001PearlTable)) {
                preparedStatement.execute();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Failed to add last_seen");
                return false;
            }

            try (PreparedStatement preparedStatement = connection.prepareStatement(migration0001PearlTable2)) {
                preparedStatement.execute();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Failed to update last_seen to valid values for all pearls.");
            }
            return true;
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to apply migration 0001");
        }
        return false;
    }

    private boolean applyMigration0002() {
        try (Connection connection = db.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(migration0002PearlTable);) {
            preparedStatement.execute();
            return true;
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to apply migration 0002");
        }
        return false;
    }

    private boolean applyMigration0003() {
        try (final Connection conn = this.db.getConnection()) {
            try (final PreparedStatement stmt = conn.prepareStatement("ALTER TABLE exilepearls ADD CONSTRAINT unique_pearl_id UNIQUE (pearl_id);")) {
                stmt.execute();
            }
            try (final PreparedStatement stmt = conn.prepareStatement(
                """
                create table if not exists ep_capturelocations(
                    pearl_id int not null,
                    world varchar(36) not null,
                    x int not null,
                    y int not null,
                    z int not null,
                    FOREIGN KEY (pearl_id) REFERENCES exilepearls(pearl_id) ON UPDATE CASCADE ON DELETE CASCADE
                );
                """
            )) {
                stmt.execute();
            }
            return true;
        }
        catch (final SQLException ex) {
            this.logger.log(Level.SEVERE, "Failed to apply migration 0003", ex);
        }
        return false;
    }

    @Override
    public Collection<ExilePearl> loadAllPearls() {
        final Set<ExilePearl> pearls = new HashSet<>();
        this.logger.log("Loading ExilePearl pearls.");
        try (final Connection conn = this.db.getConnection()) {
            final Int2ObjectMap<Document> pearlsById = new Int2ObjectArrayMap<>();
            // Load all pearls
            try (final PreparedStatement stmt = conn.prepareStatement("SELECT pearl_id, ptype as pearl_type, uid as victim_uuid, killer_id as killer_uuid, world, x, y, z, health, pearled_on, freed_offline, last_seen, summoned FROM exilepearls;")) {
                final ResultSet results = stmt.executeQuery();
                while (results.next()) {
                    final var doc = new Document();

                    final int pearlId;
                    doc.append(StorageKeys.PEARL_ID, pearlId = results.getInt("pearl_id"));
                    doc.append(StorageKeys.PEARL_TYPE, results.getInt("pearl_type"));
                    doc.append(StorageKeys.VICTIM_UUID, results.getString("victim_uuid"));
                    doc.append(StorageKeys.KILLER_UUID, results.getString("killer_uuid"));
                    doc.append(StorageKeys.PEARL_LOCATION, new Document()
                        .append(StorageKeys.LOCATION_WORLD, results.getString("world"))
                        .append(StorageKeys.LOCATION_X, results.getInt("x"))
                        .append(StorageKeys.LOCATION_Y, results.getInt("y"))
                        .append(StorageKeys.LOCATION_Z, results.getInt("z"))
                    );
                    doc.append(StorageKeys.PEARL_HEALTH, results.getInt("health"));
                    doc.append(StorageKeys.PEARL_CAPTURE_DATE, new Date(results.getLong("pearled_on")));
                    doc.append(StorageKeys.PEARL_FREED_WHILE_OFFLINE, results.getBoolean("freed_offline"));
                    doc.append(StorageKeys.VICTIM_LAST_SEEN, new Date(results.getLong("last_seen")));
                    doc.append(StorageKeys.VICTIM_SUMMONED, new Date(results.getLong("summoned")));

                    pearlsById.put(pearlId, doc);
                }
            }
            // This is necessary because return locations are relationally linked via the victim's uuid
            final Index<UUID, Document> pearlsByVictimUuid = Index.create(
                (doc) -> doc.getUUID(StorageKeys.VICTIM_UUID),
                List.copyOf(pearlsById.values())
            );
            // Load all return locations
            try (final PreparedStatement stmt = conn.prepareStatement("SELECT uid as victim_uuid, world, x, y, z FROM returnlocations;")) {
                final ResultSet results = stmt.executeQuery();
                while (results.next()) {
                    final UUID victimUuid = UUID.fromString(results.getString("victim_uuid"));
                    final Document doc = pearlsByVictimUuid.value(victimUuid);
                    if (doc == null) {
                        this.logger.log(Level.WARNING, "Cannot find pearl for [" + victimUuid + "] to set a return location for!");
                        continue;
                    }
                    doc.append(StorageKeys.VICTIM_RETURN_LOCATION, new Document()
                        .append(StorageKeys.LOCATION_WORLD, results.getString("world"))
                        .append(StorageKeys.LOCATION_X, results.getInt("x"))
                        .append(StorageKeys.LOCATION_Y, results.getInt("y"))
                        .append(StorageKeys.LOCATION_Z, results.getInt("z"))
                    );
                }
            }
            // Load all capture locations
            try (final PreparedStatement stmt = conn.prepareStatement("SELECT pearl_id, world, x, y, z FROM ep_capturelocations;")) {
                final ResultSet results = stmt.executeQuery();
                while (results.next()) {
                    final int pearlId = results.getInt("pearl_id");
                    final Document doc = pearlsById.get(pearlId);
                    if (doc == null) {
                        this.logger.log(Level.WARNING, "Cannot find pearl for [" + pearlId + "] to set a capture location for!");
                        continue;
                    }
                    doc.append(StorageKeys.PEARL_CAPTURE_LOCATION, new Document()
                        .append(StorageKeys.LOCATION_WORLD, results.getString("world"))
                        .append(StorageKeys.LOCATION_X, results.getInt("x"))
                        .append(StorageKeys.LOCATION_Y, results.getInt("y"))
                        .append(StorageKeys.LOCATION_Z, results.getInt("z"))
                    );
                }
            }
            // Convert all pearl documents into pearls
            for (final Document doc : pearlsById.values()) {
                final UUID victimUuid = doc.getUUID(StorageKeys.VICTIM_UUID);
                pearls.add(this.pearlFactory.createExilePearl(victimUuid, doc));
            }
        }
        catch (final SQLException ex) {
            this.logger.log(Level.SEVERE, "An error occurred when loading pearls.", ex);
        }
        return pearls;
    }

    @Override
    public void pearlInsert(ExilePearl pearl) {
        Preconditions.checkNotNull(pearl, "pearl");
        try (final Connection conn = this.db.getConnection()) {
            try (final PreparedStatement stmt = conn.prepareStatement("INSERT INTO exilepearls VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);")) {
                final Location pearlLocation = pearl.getLocation();
                stmt.setString(1, pearl.getPlayerId().toString());
                stmt.setString(2, pearl.getKillerId().toString());
                stmt.setInt(3, pearl.getPearlId());
                stmt.setInt(4, pearl.getPearlType().toInt());
                stmt.setString(5, pearlLocation.getWorld().getName());
                stmt.setInt(6, pearlLocation.getBlockX());
                stmt.setInt(7, pearlLocation.getBlockY());
                stmt.setInt(8, pearlLocation.getBlockZ());
                stmt.setInt(9, pearl.getHealth());
                stmt.setLong(10, pearl.getPearledOn().getTime());
                stmt.setBoolean(11, pearl.getFreedOffline());
                stmt.setLong(12, pearl.getLastOnline().getTime());
                stmt.setBoolean(13, pearl.isSummoned());
                stmt.executeUpdate();
            }
            final Location captureLocation = pearl.getCaptureLocation();
            if (captureLocation != null) {
                try (final PreparedStatement stmt = conn.prepareStatement("INSERT INTO ep_capturelocations VALUES (?, ?, ?, ?, ?);")) {
                    stmt.setInt(1, pearl.getPearlId());
                    stmt.setString(2, captureLocation.getWorld().getName());
                    stmt.setInt(3, captureLocation.getBlockX());
                    stmt.setInt(4, captureLocation.getBlockY());
                    stmt.setInt(5, captureLocation.getBlockZ());
                    stmt.executeUpdate();
                }
            }
        }
        catch (final SQLException ex) {
            ex.printStackTrace();
            logFailedPearlOperation(ex, pearl, "insert record");
        }
    }

    @Override
    public void pearlRemove(ExilePearl pearl) {
        Preconditions.checkNotNull(pearl, "pearl");
        try (final Connection conn = this.db.getConnection()) {
            try (final PreparedStatement stmt = conn.prepareStatement("DELETE FROM exilepearls WHERE uid = ?;")) {
                stmt.setString(1, pearl.getPlayerId().toString());
                stmt.executeUpdate();
            }
            try (final PreparedStatement stmt = conn.prepareStatement("DELETE FROM ep_capturelocations where pearl_id = ?;")) {
                stmt.setInt(1, pearl.getPearlId());
                stmt.executeUpdate();
            }
        }
        catch (final SQLException ex) {
            ex.printStackTrace();
            logFailedPearlOperation(ex, pearl, "delete record");
        }
    }

    @Override
    public void updatePearlLocation(ExilePearl pearl) {
        Preconditions.checkNotNull(pearl, "pearl");

        try (Connection connection = db.getConnection();
             PreparedStatement ps = connection.prepareStatement("UPDATE exilepearls SET world = ?, x = ?, y = ?, z = ? WHERE uid = ?");) {

            Location l = pearl.getLocation();
            ps.setString(1, l.getWorld().getName());
            ps.setInt(2, l.getBlockX());
            ps.setInt(3, l.getBlockY());
            ps.setInt(4, l.getBlockZ());
            ps.setString(5, pearl.getPlayerId().toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            logFailedPearlOperation(ex, pearl, "update 'location'");
        }
    }

    @Override
    public void updatePearlHealth(ExilePearl pearl) {
        Preconditions.checkNotNull(pearl, "pearl");

        try (Connection connection = db.getConnection();
             PreparedStatement ps = connection.prepareStatement("UPDATE exilepearls SET health = ? WHERE uid = ?");) {

            ps.setInt(1, pearl.getHealth());
            ps.setString(2, pearl.getPlayerId().toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            logFailedPearlOperation(ex, pearl, "update 'health'");
        }
    }

    @Override
    public void updatePearlFreedOffline(ExilePearl pearl) {
        Preconditions.checkNotNull(pearl, "pearl");

        try (Connection connection = db.getConnection();
             PreparedStatement ps = connection.prepareStatement("UPDATE exilepearls SET freed_offline = ? WHERE uid = ?");) {

            ps.setBoolean(1, pearl.getFreedOffline());
            ps.setString(2, pearl.getPlayerId().toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            logFailedPearlOperation(ex, pearl, "update 'freed offline'");
        }
    }

    @Override
    public void updatePearlType(ExilePearl pearl) {
        Preconditions.checkNotNull(pearl, "pearl");

        try (Connection connection = db.getConnection();
             PreparedStatement ps = connection.prepareStatement("UPDATE exilepearls SET ptype = ? WHERE uid = ?");) {

            ps.setInt(1, pearl.getPearlType().toInt());
            ps.setString(2, pearl.getPlayerId().toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            logFailedPearlOperation(ex, pearl, "update 'freed offline'");
        }
    }

    @Override
    public void updatePearlKiller(ExilePearl pearl) {
        Preconditions.checkNotNull(pearl, "pearl");

        try (Connection connection = db.getConnection();
             PreparedStatement ps = connection.prepareStatement("UPDATE exilepearls SET killer_id = ? WHERE uid = ?");) {

            ps.setString(1, pearl.getKillerId().toString());
            ps.setString(2, pearl.getPlayerId().toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            logFailedPearlOperation(ex, pearl, "update 'killer_id'");
        }
    }

    @Override
    public void updatePearlLastOnline(ExilePearl pearl) {
        Preconditions.checkNotNull(pearl, "pearl");

        try (Connection connection = db.getConnection();
             PreparedStatement ps = connection.prepareStatement("UPDATE exilepearls SET last_seen = ? WHERE uid = ?");) {

            ps.setLong(1, pearl.getLastOnline().getTime());
            ps.setString(2, pearl.getPlayerId().toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            logFailedPearlOperation(ex, pearl, "update 'last_seen'");
        }
    }

    @Override
    public void updatePearlSummoned(ExilePearl pearl) {
        Preconditions.checkNotNull(pearl, "pearl");

        try (Connection connection = db.getConnection();
             PreparedStatement ps = connection.prepareStatement("UPDATE exilepearls SET summoned = ? WHERE uid = ?");) {
            ps.setBoolean(1, pearl.isSummoned());
            ps.setString(2, pearl.getPlayerId().toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            logFailedPearlOperation(ex, pearl, "update 'summoned'");
        }
    }

    @Override
    public void updateReturnLocation(ExilePearl pearl) {
        Preconditions.checkNotNull(pearl, "pearl");

        try (Connection connection = db.getConnection();) {
            if (pearl.getReturnLocation() != null) {
                try (PreparedStatement ps = connection.prepareStatement("INSERT INTO returnlocations (uid, world, x, y, z) VALUES (?, ?, ?, ?, ?);")) {
                    ps.setString(1, pearl.getPlayerId().toString());
                    Location loc = pearl.getReturnLocation();
                    ps.setString(2, loc.getWorld().getName());
                    ps.setInt(3, loc.getBlockX());
                    ps.setInt(4, loc.getBlockY());
                    ps.setInt(5, loc.getBlockZ());
                    ps.executeUpdate();
                } catch (SQLException ex) {
                    logFailedPearlOperation(ex, pearl, "insert return location");
                }
            } else {
                try (PreparedStatement ps = connection.prepareStatement("DELETE FROM returnlocations WHERE uid = ?;")) {
                    ps.setString(1, pearl.getPlayerId().toString());
                    ps.executeUpdate();
                } catch (SQLException ex) {
                    logFailedPearlOperation(ex, pearl, "delete return location");
                }
            }
        } catch (SQLException ex) {
            logFailedPearlOperation(ex, pearl, "update return location");
        }
    }

    @Override
    public void updateCaptureLocation(
        final @NotNull ExilePearl pearl
    ) {
        Preconditions.checkNotNull(pearl, "pearl");
        try (final Connection conn = this.db.getConnection()) {
            final Location captureLocation = pearl.getCaptureLocation();
            if (captureLocation == null) {
                try (final PreparedStatement stmt = conn.prepareStatement("DELETE FROM ep_capturelocations WHERE pearl_id = ?;")) {
                    stmt.setInt(1, pearl.getPearlId());
                    stmt.executeUpdate();
                }
                catch (final SQLException ex) {
                    logFailedPearlOperation(ex, pearl, "delete capture location");
                }
            }
            else {
                try (final PreparedStatement stmt = conn.prepareStatement("INSERT INTO ep_capturelocations (pearl_id, world, x, y, z) VALUES (?, ?, ?, ?, ?);")) {
                    stmt.setInt(1, pearl.getPearlId());
                    stmt.setString(2, captureLocation.getWorld().getName());
                    stmt.setInt(3, captureLocation.getBlockX());
                    stmt.setInt(4, captureLocation.getBlockY());
                    stmt.setInt(5, captureLocation.getBlockZ());
                    stmt.executeUpdate();
                }
                catch (final SQLException ex) {
                    logFailedPearlOperation(ex, pearl, "insert capture location");
                }
            }
        }
        catch (final SQLException ex) {
            logFailedPearlOperation(ex, pearl, "update capture location");
        }
    }

    @Override
    public void updatePearledOnDate(ExilePearl pearl) {
        Preconditions.checkNotNull(pearl, "pearl");

        try (Connection connection = db.getConnection();
             PreparedStatement ps = connection.prepareStatement("UPDATE exilepearls SET pearled_on = ? WHERE uid = ?");) {

            ps.setLong(1, pearl.getPearledOn().getTime());
            ps.setString(2, pearl.getPlayerId().toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            logFailedPearlOperation(ex, pearl, "update 'pearled_on'");
        }
    }

    /**
     * Migrate prison pearl data
     */
    private void migratePrisonPearl() {
        logger.log(Level.WARNING, "Attempting to perform PrisonPearl data migration.");

        ConnectionPool migrateDb = new ConnectionPool(
            config.getMySqlUsername(),
            config.getMySqlPassword(),
            config.getMySqlHost(),
            config.getMySqlPort(),
            "mysql",
            config.getMySqlMigrateDatabaseName(),
            config.getMySqlPoolSize(),
            config.getMySqlConnectionTimeout(),
            config.getMySqlIdleTimeout(),
            config.getMySqlMaxLifetime());

        int migrated = 0;
        int failed = 0;

        try {
            // Check if the table exists
            try (Connection connection = migrateDb.getConnection();) {
                DatabaseMetaData dbm = migrateDb.getConnection().getMetaData();

                ResultSet tables = dbm.getTables(null, null, "PrisonPearls", null);
                if (!tables.next()) {
                    logger.log(Level.WARNING, "No PrisonPearl data was found.");
                    return;
                }
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Failed to retrieve PrisonPearl table.");
                return;
            }

            try (Connection connection = migrateDb.getConnection();
                 PreparedStatement getAllPearls = connection.prepareStatement("SELECT * FROM PrisonPearls;");) {
                ResultSet set = getAllPearls.executeQuery();
                while (set.next()) {
                    try {
                        UUID playerId = UUID.fromString(set.getString("uuid"));

                        String w = set.getString("world");
                        World world = Bukkit.getWorld(w);
                        int x = set.getInt("x");
                        int y = set.getInt("y");
                        int z = set.getInt("z");
                        int pearlId = set.getInt("uq");
                        UUID killerId = null;
                        String killerUUIDAsString = set.getString("killer");
                        if (killerUUIDAsString == null) {
                            killerId = UUID.randomUUID();
                        } else {
                            killerId = UUID.fromString(killerUUIDAsString);
                        }
                        long imprisonTime = set.getLong("pearlTime");
                        if (imprisonTime == 0) {
                            imprisonTime = new Date().getTime();
                        }
                        Date pearledOn = new Date(imprisonTime);
                        Location loc = new Location(world, x, y, z);

                        Document doc = new Document("killer_id", killerId)
                            .append("pearl_id", pearlId)
                            .append("location", loc)
                            .append("pearled_on", pearledOn)
                            .append("last_seen", pearledOn); // TODO: overloading for now.

                        ExilePearl pearl = pearlFactory.createdMigratedPearl(playerId, doc);
                        if (pearl != null) {
                            pearlInsert(pearl);
                            migrated++;
                        } else {
                            failed++;
                        }

                    } catch (SQLException ex) {
                        failed++;
                        logger.log(Level.SEVERE, "Failed to migrate PisonPearl pearl.");
                    }
                }
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "An error occurred when migrating PrisonPearl data.");
                return;
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "An error occurred when migrating PrisonPearl data.");
            return;
        }

        logger.log(Level.INFO, "PrisonPearl data migration complete. Migrated %d and %d failed.", migrated, failed);
    }

    /**
     * Gets the database version
     *
     * @return The database version
     */
    private int getDatabaseVersion() {
        int version = DATABASE_VERSION;
        String versionStr = getPluginSetting("db_version");

        if (versionStr != null) {
            version = Integer.parseInt(versionStr);
        }

        return version;
    }

    /**
     * Updates the database version
     */
    private void updateDatabaseVersion() {
        setPluginSetting("db_version", DATABASE_VERSION.toString());
    }

    private boolean isFirstRun() {
        return getPluginSetting("first_run") == null;
    }

    private void setHasRun() {
        setPluginSetting("first_run", "1");
    }

    private void setPluginSetting(final String setting, final String value) {
        try (Connection connection = db.getConnection();
             PreparedStatement stmt = connection.prepareStatement("INSERT INTO exilepearlplugin(setting, value) VALUES(?, ?) " +
                 "ON DUPLICATE KEY UPDATE value = ?;");) {
            stmt.setString(1, setting);
            stmt.setString(2, value);
            stmt.setString(3, value);
            stmt.execute();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to update the plugin setting %s.", setting);
            ex.printStackTrace();
        }
    }

    private String getPluginSetting(final String setting) {
        String value = null;

        try (Connection connection = db.getConnection();
             PreparedStatement stmt = connection.prepareStatement("SELECT * FROM exilepearlplugin where setting = ?;");) {
            stmt.setString(1, setting);

            ResultSet resultSet = stmt.executeQuery();
            if (resultSet.next()) {
                value = resultSet.getString("value");
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to update the plugin setting %s.", setting);
            ex.printStackTrace();
        }
        return value;
    }

    private void logFailedPearlOperation(Exception ex, ExilePearl pearl, String action) {
        logger.log(Level.SEVERE, "Failed to %s pearl for player %s.", action, pearl.getPlayerName());
    }
}
