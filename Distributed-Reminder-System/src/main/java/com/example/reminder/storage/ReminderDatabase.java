package com.example.reminder.storage;

import com.example.reminder.model.Reminder;
import com.example.reminder.model.ReminderStatus;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Production-grade SQL Database storage layer using JDBC, HikariCP connection pooling,
 * and B-tree compound indexing on (time_bucket, trigger_time, status).
 */
public class ReminderDatabase implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final AtomicLong queryCount = new AtomicLong(0);
    private final AtomicLong writeCount = new AtomicLong(0);

    public ReminderDatabase(String jdbcUrl) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setPoolName("ReminderDB-Pool");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        this.dataSource = new HikariDataSource(config);
        initializeSchema();
    }

    public static ReminderDatabase createInMemory(String dbName) {
        return new ReminderDatabase("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
    }

    private void initializeSchema() {
        String createTableSql = """
                CREATE TABLE IF NOT EXISTS reminders (
                    id VARCHAR(64) PRIMARY KEY,
                    user_id VARCHAR(64) NOT NULL,
                    title VARCHAR(255) NOT NULL,
                    message TEXT,
                    trigger_time BIGINT NOT NULL,
                    time_bucket VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL DEFAULT 'PENDING'
                );
                """;

        String createIndexSql = """
                CREATE INDEX IF NOT EXISTS idx_reminders_prefetch
                ON reminders (time_bucket, trigger_time, status);
                """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSql);
            stmt.execute(createIndexSql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database schema", e);
        }
    }

    public void save(Reminder reminder) {
        String sql = """
                MERGE INTO reminders (id, user_id, title, message, trigger_time, time_bucket, status)
                KEY (id)
                VALUES (?, ?, ?, ?, ?, ?, ?);
                """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            writeCount.incrementAndGet();
            ps.setString(1, reminder.getId());
            ps.setString(2, reminder.getUserId());
            ps.setString(3, reminder.getTitle());
            ps.setString(4, reminder.getMessage());
            ps.setLong(5, reminder.getTriggerTimeMs());
            ps.setString(6, reminder.getTimeBucket());
            ps.setString(7, reminder.getStatus().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error saving reminder " + reminder.getId(), e);
        }
    }

    public void saveBatch(Collection<Reminder> reminders) {
        if (reminders == null || reminders.isEmpty()) return;

        String sql = """
                MERGE INTO reminders (id, user_id, title, message, trigger_time, time_bucket, status)
                KEY (id)
                VALUES (?, ?, ?, ?, ?, ?, ?);
                """;

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Reminder reminder : reminders) {
                    writeCount.incrementAndGet();
                    ps.setString(1, reminder.getId());
                    ps.setString(2, reminder.getUserId());
                    ps.setString(3, reminder.getTitle());
                    ps.setString(4, reminder.getMessage());
                    ps.setLong(5, reminder.getTriggerTimeMs());
                    ps.setString(6, reminder.getTimeBucket());
                    ps.setString(7, reminder.getStatus().name());
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to batch save reminders", e);
        }
    }

    public Optional<Reminder> findById(String id) {
        String sql = "SELECT id, user_id, title, message, trigger_time, time_bucket, status FROM reminders WHERE id = ?";
        queryCount.incrementAndGet();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding reminder by id: " + id, e);
        }
        return Optional.empty();
    }

    /**
     * Efficient lookahead window batch query using the compound B-tree index.
     */
    public List<Reminder> findRemindersForWindow(long startMs, long endMs, ReminderStatus status) {
        java.util.Set<String> buckets = Reminder.calculateTimeBuckets(startMs, endMs);
        if (buckets.isEmpty()) return new ArrayList<>();

        String inClause = String.join(",", java.util.Collections.nCopies(buckets.size(), "?"));
        String sql = "SELECT id, user_id, title, message, trigger_time, time_bucket, status " +
                "FROM reminders WHERE time_bucket IN (" + inClause + ") " +
                "AND trigger_time BETWEEN ? AND ? AND status = ? ORDER BY trigger_time ASC";

        queryCount.incrementAndGet();
        List<Reminder> results = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (String bucket : buckets) {
                ps.setString(idx++, bucket);
            }
            ps.setLong(idx++, startMs);
            ps.setLong(idx++, endMs);
            ps.setString(idx, status.name());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error querying lookahead reminder window", e);
        }
        return results;
    }

    public void updateStatus(String id, ReminderStatus newStatus) {
        String sql = "UPDATE reminders SET status = ? WHERE id = ?";
        writeCount.incrementAndGet();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus.name());
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating status for reminder " + id, e);
        }
    }

    public void batchUpdateStatus(Collection<String> ids, ReminderStatus newStatus) {
        if (ids == null || ids.isEmpty()) return;

        // Sort IDs to guarantee global lock ordering across concurrent threads (deadlock prevention)
        List<String> sortedIds = new ArrayList<>(new TreeSet<>(ids));

        int batchChunk = 200;
        for (int i = 0; i < sortedIds.size(); i += batchChunk) {
            List<String> chunk = sortedIds.subList(i, Math.min(sortedIds.size(), i + batchChunk));
            String inClause = String.join(",", java.util.Collections.nCopies(chunk.size(), "?"));
            String sql = "UPDATE reminders SET status = ? WHERE id IN (" + inClause + ")";

            boolean updated = false;
            int retries = 3;
            while (!updated && retries > 0) {
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    writeCount.addAndGet(chunk.size());
                    ps.setString(1, newStatus.name());
                    for (int k = 0; k < chunk.size(); k++) {
                        ps.setString(k + 2, chunk.get(k));
                    }
                    ps.executeUpdate();
                    updated = true;
                } catch (SQLException e) {
                    retries--;
                    if (retries == 0) {
                        throw new RuntimeException("Failed to batch update reminder statuses after retries", e);
                    }
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ignored) {}
                }
            }
        }
    }

    public int count() {
        queryCount.incrementAndGet();
        String sql = "SELECT COUNT(*) FROM reminders";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error counting reminders", e);
        }
        return 0;
    }

    private Reminder mapRow(ResultSet rs) throws SQLException {
        return new Reminder(
                rs.getString("id"),
                rs.getString("user_id"),
                rs.getString("title"),
                rs.getString("message"),
                rs.getLong("trigger_time"),
                ReminderStatus.valueOf(rs.getString("status"))
        );
    }

    public long getQueryCount() {
        return queryCount.get();
    }

    public long getWriteCount() {
        return writeCount.get();
    }

    public void resetMetrics() {
        queryCount.set(0);
        writeCount.set(0);
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
