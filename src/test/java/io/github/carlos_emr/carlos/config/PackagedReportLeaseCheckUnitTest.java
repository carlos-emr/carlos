/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.config;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
@Tag("read")
class PackagedReportLeaseCheckUnitTest {
    @TempDir
    Path directory;

    @Test
    void shouldResolveTimeoutFromInstalledSources_inStartupOrder() throws Exception {
        Path managed = directory.resolve("managed.properties");
        Path home = directory.resolve("home.properties");
        Files.writeString(directory.resolve("jdbc-defaults.properties"), "site.lease=17\n");
        Files.writeString(directory.resolve("carlos.properties"), "db_remove_abandoned_timeout=360\nsite.lease=420\n");
        Files.writeString(managed, "");
        assertEquals(360, PackagedReportLeaseCheck.configuredTimeout(directory, managed, home));
        Files.writeString(managed, "db_remove_abandoned_timeout=${site.lease}\n");
        assertEquals(420, PackagedReportLeaseCheck.configuredTimeout(directory, managed, home));
        Files.writeString(home, "site.lease=+015\n");
        assertEquals(15, PackagedReportLeaseCheck.configuredTimeout(directory, managed, home));
        Files.writeString(home, "site.lease=${db_remove_abandoned_timeout}\n");
        assertThrows(IllegalArgumentException.class, () -> PackagedReportLeaseCheck.configuredTimeout(directory, managed, home));
    }
}
