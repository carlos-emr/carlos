/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.demographic.cds.jdbc;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@Tag("integration")
@EnabledIfSystemProperty(named = "cds.test.properties", matches = ".+")
class CdsBinaryReadsMariaDbIntegrationTest {
    private Connection open() throws Exception {
        var properties = new Properties();
        try (var input = Files.newInputStream(Path.of(System.getProperty("cds.test.properties")))) {
            properties.load(input);
        }
        String url = properties.getProperty("jdbcUrl", "");
        if (!"true".equals(properties.getProperty("ownedDisposableDatabase"))
                || !url.matches("jdbc:mariadb://[^/]+/carlos_cds_test(?:\\?.*)?"))
            throw new IllegalArgumentException("CDS_BINARY_OWNED_DATABASE_REQUIRED");
        return DriverManager.getConnection(
                url, properties.getProperty("jdbcUser"), properties.getProperty("jdbcPassword"));
    }

    @Test
    void shouldRetainExactFloatBits_andKeepStatementsAndResultsGuarded() throws Exception {
        try (var connection = open();
                var query =
                        CdsBinaryReads.prepareDriver(
                                connection,
                                "SELECT CAST(0.1 AS FLOAT) AS f32, CAST(0.1 AS DOUBLE) AS f64, ? AS"
                                    + " bound_value")) {
            query.setString(1, "controlled");
            try (var rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(Double.doubleToRawLongBits(rows.getDouble("f32")))
                        .isEqualTo(Double.doubleToRawLongBits((double) 0.1f));
                assertThat(Double.doubleToRawLongBits(rows.getDouble("f64")))
                        .isEqualTo(Double.doubleToRawLongBits(0.1d));
                assertThat(rows.getString("bound_value")).isEqualTo("controlled");
                assertThat(rows.getStatement()).isSameAs(query);
                assertThatThrownBy(() -> rows.unwrap(org.mariadb.jdbc.client.result.Result.class))
                        .isInstanceOf(SQLException.class);
                assertThat(rows.next()).isFalse();
            }
        }
    }

    @Test
    void shouldRefuseAnActualTextProtocolResult_beforeReadingItsValues() throws Exception {
        try (var connection = open();
                var query = connection.createStatement();
                var text = query.executeQuery("SELECT CAST(0.1 AS FLOAT) AS f32")) {
            assertThatThrownBy(() -> CdsMariaDbBinaryRead.requireBinaryResult(text))
                    .isInstanceOf(SQLException.class)
                    .hasMessage("CDS_BINARY_RESULT_PROTOCOL_REQUIRED");
            assertThat(text.getRow()).isZero();
        }
    }
}
