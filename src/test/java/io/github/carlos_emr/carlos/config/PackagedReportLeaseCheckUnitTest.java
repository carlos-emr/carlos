/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.config;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("read")
class PackagedReportLeaseCheckUnitTest extends CarlosUnitTestBase {
    @TempDir
    Path directory;

    @Test
    void shouldResolveTimeoutFromInstalledSources_inStartupOrder() throws Exception {
        Path managed = directory.resolve("managed.properties");
        Path home = directory.resolve("home.properties");
        String[] args = {directory.toString(), managed.toString()};
        Files.writeString(directory.resolve("jdbc-defaults.properties"), "site.lease=420\n");
        Files.writeString(directory.resolve("carlos.properties"), "db_remove_abandoned_timeout=360\nsite.lease=15\n");
        Files.writeString(managed, "");
        assertEquals(0, PackagedReportLeaseCheck.status(args, home));
        Files.writeString(managed, "db_remove_abandoned_timeout=${site.lease}\n");
        assertEquals(15, PackagedReportLeaseCheck.status(args, home));
        Files.writeString(home, "site.lease=420\n");
        assertEquals(0, PackagedReportLeaseCheck.status(args, home));
        Files.writeString(home, "site.lease=+015\n");
        assertEquals(15, PackagedReportLeaseCheck.status(args, home));
        Files.writeString(home, "site.lease=${db_remove_abandoned_timeout}\n");
        assertEquals(1, PackagedReportLeaseCheck.status(args, home));
    }

    @Test
    void shouldReportAnInspectionFailure_whenArgumentsOrFilesAreMissing() {
        Path home = directory.resolve("home.properties");
        assertEquals(1, PackagedReportLeaseCheck.status(new String[0], home));
        String[] args = {directory.toString(), directory.resolve("missing.properties").toString()};
        assertEquals(1, PackagedReportLeaseCheck.status(args, home));
    }
}
