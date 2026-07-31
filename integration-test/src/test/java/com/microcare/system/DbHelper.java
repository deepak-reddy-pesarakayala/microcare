package com.microcare.system;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plain-JDBC helper for the system test. Connects to the MySQL container as
 * {@code root/test} and lets the test seed data (doctor slots) and assert
 * against each service's database (invoices, notification logs, audit logs).
 */
public final class DbHelper {

    private static final String URL_TEMPLATE =
            "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    private final String host;
    private final int port;
    private final String user;
    private final String password;

    public DbHelper(String host, int port, String user, String password) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
    }

    public void execute(String database, String sql, Object... params) {
        try (Connection conn = connect(database);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            ps.execute();
        } catch (SQLException e) {
            throw new IllegalStateException("DB execute failed on db=" + database + ": " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> query(String database, String sql, Object... params) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = connect(database);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= cols; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("DB query failed on db=" + database + ": " + e.getMessage(), e);
        }
        return rows;
    }

    public long count(String database, String sql, Object... params) {
        List<Map<String, Object>> rows = query(database, sql, params);
        return rows.isEmpty() ? 0 : ((Number) rows.get(0).values().iterator().next()).longValue();
    }

    /**
     * Executes every init script from the classpath (init-scripts/*.sql) against
     * the MySQL container so the test mirrors the docker-compose first-boot flow.
     */
    public void runInitScripts() {
        // GRANT statements in the scripts reference this user — create it first.
        execute("microcare_patients",
                "CREATE USER IF NOT EXISTS 'microcare_user'@'%' IDENTIFIED BY 'microcare_pass'");

        List<String> scripts = List.of(
                "init-scripts/01-create-auth-db.sql",
                "init-scripts/02-create-appointments-db.sql",
                "init-scripts/03-create-billing-db.sql",
                "init-scripts/04-create-notifications-db.sql");

        for (String script : scripts) {
            String content = readResource(script);
            // Scripts are simple DDL/DML — splitting on ';' is safe.
            for (String statement : content.split(";")) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    execute("microcare_patients", trimmed);
                }
            }
        }
    }

    private Connection connect(String database) throws SQLException {
        return DriverManager.getConnection(URL_TEMPLATE.formatted(host, port, database), user, password);
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    private static String readResource(String path) {
        try (InputStream in = DbHelper.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Resource not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read resource: " + path, e);
        }
    }
}
