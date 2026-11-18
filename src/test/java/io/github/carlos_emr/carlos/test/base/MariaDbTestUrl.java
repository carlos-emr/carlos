/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.test.base;

import java.util.regex.Pattern;

/** Server-only JDBC URLs for tests that own their MariaDB schema. */
public final class MariaDbTestUrl {
    private static final Pattern SERVER_URL = Pattern.compile(
            "jdbc:(?:mariadb|mysql)://(?:[A-Za-z0-9._-]+|\\[[0-9A-Fa-f:.]+\\])(?::[0-9]{1,5})?/");

    private MariaDbTestUrl() { }

    /** Validates the server address before test code selects its owned catalog. */
    public static String requireServerUrl(String url) {
        if (url == null || !SERVER_URL.matcher(url).matches()) {
            throw new IllegalArgumentException("MariaDB tests require a jdbc:mariadb or jdbc:mysql server URL without credentials, a database or query options");
        }
        return url;
    }

    /** Identifies the legacy scheme on a validated server URL. */
    public static boolean usesLegacyScheme(String url) {
        return url.startsWith("jdbc:mysql:");
    }
}
