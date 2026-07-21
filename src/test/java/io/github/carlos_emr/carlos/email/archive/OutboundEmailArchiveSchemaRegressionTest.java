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

package io.github.carlos_emr.carlos.email.archive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Outbound email archive schema")
@Tag("unit")
class OutboundEmailArchiveSchemaRegressionTest {

    private static final Path ARCHIVE_MIGRATION = Path.of(
            "database", "mysql", "updates", "update-2026-07-07-outbound-email-archive.sql");
    private static final Path FRESH_SCHEMA = Path.of("database", "mysql", "oscarinit.sql");

    @Test
    @DisplayName("should define delete prevention triggers in archive migration")
    void shouldDefineDeletePreventionTriggers_inArchiveMigration() throws IOException {
        assertDeletePreventionTriggers(Files.readString(ARCHIVE_MIGRATION, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("should define delete prevention triggers in fresh schema")
    void shouldDefineDeletePreventionTriggers_inFreshSchema() throws IOException {
        assertDeletePreventionTriggers(Files.readString(FRESH_SCHEMA, StandardCharsets.UTF_8));
    }

    private static void assertDeletePreventionTriggers(String sql) {
        assertThat(sql)
                .contains("DROP TRIGGER IF EXISTS `trg_outboundEmailArchive_prevent_delete`")
                .contains("CREATE TRIGGER `trg_outboundEmailArchive_prevent_delete`")
                .contains("BEFORE DELETE ON `outboundEmailArchive`")
                .contains("Outbound email archives must use controlled tombstone workflow")
                .contains("DROP TRIGGER IF EXISTS `trg_outboundEmailArchiveDeletion_prevent_delete`")
                .contains("CREATE TRIGGER `trg_outboundEmailArchiveDeletion_prevent_delete`")
                .contains("BEFORE DELETE ON `outboundEmailArchiveDeletion`")
                .contains("Outbound email archive tombstones are immutable");
    }
}
