/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.db;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.assertThat;

/** Executes the forward correction against both complete province diagnosis catalogs. */
@Tag("unit")
class DiagnosticDescriptionMigrationUnitTest {
    @ParameterizedTest
    @ValueSource(strings = {"on", "bc"})
    void shouldCorrectOnlyEncodedApostrophes_whenMigratingProvinceCatalog(String province) throws Exception {
        String seed = resource("db/migration/" + province + "/V1.0.2__" + province + "_data.sql");
        int start = seed.indexOf("INSERT INTO `diagnosticcode` VALUES");
        assertThat(start).isNotNegative();
        int end = seed.indexOf(";\n", start);
        assertThat(end).isGreaterThan(start);
        String migration = resource("db/migration/common/V1.0.53__decode_diagnostic_description_apostrophes.sql");
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:dx_" + province + ";MODE=MySQL");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE diagnosticcode (id INT PRIMARY KEY, diagnostic_code VARCHAR(20), description VARCHAR(2000), status VARCHAR(5), region VARCHAR(10))");
            // H2 does not accept MariaDB's backslash-escaped apostrophes in the frozen dump.
            statement.execute(seed.substring(start, end + 1).replace("\\'", "''"));
            var expected = new LinkedHashMap<Integer, String>();
            int encoded = 0;
            try (var rows = statement.executeQuery("SELECT id,description FROM diagnosticcode ORDER BY id")) {
                while (rows.next()) {
                    String description = rows.getString(2);
                    if (description.contains("&#146;")) encoded++;
                    expected.put(rows.getInt(1), description.replace("&#146;", "'"));
                }
            }
            assertThat(encoded).isEqualTo("on".equals(province) ? 13 : 0);
            assertThat(statement.executeUpdate(migration)).isEqualTo(encoded);
            assertThat(statement.executeUpdate(migration)).isZero();
            var actual = new LinkedHashMap<Integer, String>();
            try (var rows = statement.executeQuery("SELECT id,description FROM diagnosticcode ORDER BY id")) {
                while (rows.next()) actual.put(rows.getInt(1), rows.getString(2));
            }
            assertThat(actual).isEqualTo(expected);
        }
    }

    private static String resource(String path) throws Exception {
        try (var stream = new ClassPathResource(path).getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
