/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
package io.github.carlos_emr.carlos.commn.dao;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("Repeatable routing coordination migrations")
class LabRoutingLockMigrationUnitTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("should preserve coordination keys and audit defaults when migrations are repeated")
    void shouldApplyRepeatedly_withFreshOrPreexistingTable(boolean preexisting) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:routing-migration-"
                + java.util.UUID.randomUUID() + ";MODE=MySQL");
             var statement = connection.createStatement()) {
            if (preexisting) {
                statement.execute("CREATE TABLE providerLabRoutingLock(lab_no INT PRIMARY KEY)");
                statement.execute("INSERT INTO providerLabRoutingLock(lab_no) VALUES(170)");
            }
            for (int attempt = 0; attempt < 2; attempt++) {
                for (String migration : new String[] {
                        "V1.0.21__serialize_missing_lab_routing_creation.sql",
                        "V1.0.22__add_lab_routing_lock_audit_columns.sql"}) {
                    try (var reader = Files.newBufferedReader(Path.of("database/mysql/migration/common", migration))) {
                        RunScript.execute(connection, reader);
                    }
                }
            }
            statement.execute("INSERT INTO providerLabRoutingLock(lab_no) VALUES(170) ON DUPLICATE KEY UPDATE lab_no=VALUES(lab_no)");
            try (var rows = statement.executeQuery("SELECT lab_no,lastUpdateUser,lastUpdateDate FROM providerLabRoutingLock")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(170);
                assertThat(rows.getString(2)).isEqualTo("system");
                assertThat(rows.getTimestamp(3)).isNotNull();
                assertThat(rows.next()).isFalse();
            }
        }
    }
}
