/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.test.base;

import io.github.carlos_emr.carlos.utility.MariaDbCompatibilityDriver;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/** Opens opt-in test-owned schemas through the application's production JDBC adapter. */
public final class MariaDbTestConnections {
    private MariaDbTestConnections() { }

    public static Connection open(String url, String user, String password) throws SQLException {
        Properties properties = new Properties();
        if (user != null) properties.setProperty("user", user);
        if (password != null) properties.setProperty("password", password);
        Connection connection = new MariaDbCompatibilityDriver().connect(url, properties);
        if (connection == null) throw new SQLException("Unsupported native test database URL");
        return connection;
    }
}
