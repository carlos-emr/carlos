/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;

/** Read-only Debian configure check, run as the service user with the installed Spring libraries. */
public final class PackagedReportLeaseCheck {
    private static final String KEY = "db_remove_abandoned_timeout";

    private PackagedReportLeaseCheck() {
    }

    /**
     * Returns exit status 15 for the former default, zero for another timeout, or one
     * if the value cannot be checked. Property values must never reach package logs.
     *
     * @param args installed WEB-INF/classes directory and managed properties file
     */
    public static void main(String[] args) {
        System.exit(status(args, Path.of(System.getProperty("user.home"), "carlos.properties")));
    }

    static int status(String[] args, Path home) {
        if (args.length != 2) {
            return 1;
        }
        try {
            return configuredTimeout(Path.of(args[0]), Path.of(args[1]), home) == 15 ? 15 : 0;
        } catch (IOException | RuntimeException failure) {
            // Spring diagnostics may contain the referenced property's value.
            return 1;
        }
    }

    private static int configuredTimeout(Path classes, Path managed, Path home) throws IOException {
        // Same order as applicationContext.xml, CarlosProperties and the Startup listener.
        Properties values = PackagedConfigurationPreflight.readFile(classes.resolve("jdbc-defaults.properties"));
        values.putAll(PackagedConfigurationPreflight.readFile(classes.resolve("carlos.properties")));
        values.putAll(PackagedConfigurationPreflight.readFile(managed));
        if (!Files.notExists(home) && !Files.isSameFile(managed, home)) {
            values.putAll(PackagedConfigurationPreflight.readFile(home));
        }
        var sources = new MutablePropertySources();
        sources.addLast(new PropertiesPropertySource("carlos", values));
        return new PropertySourcesPropertyResolver(sources).getRequiredProperty(KEY, Integer.class);
    }
}
