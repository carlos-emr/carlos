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
     * Returns 10 if publishing the candidate safely restores the default, 15 for a
     * retained 15-second setting, zero for another timeout, or one on inspection failure.
     * Property values must never reach package logs.
     *
     * @param args installed WEB-INF/classes directory, managed properties file, and optional candidate file
     */
    public static void main(String[] args) {
        System.exit(status(args, Path.of(System.getProperty("user.home"), "carlos.properties")));
    }

    static int status(String[] args, Path home) {
        if (args.length != 2 && args.length != 3) {
            return 1;
        }
        try {
            Path classes = Path.of(args[0]);
            Path managed = Path.of(args[1]);
            Properties defaults = PackagedConfigurationPreflight.readFile(classes.resolve("jdbc-defaults.properties"));
            defaults.putAll(PackagedConfigurationPreflight.readFile(classes.resolve("carlos.properties")));
            Properties installed = PackagedConfigurationPreflight.readFile(managed);
            Properties overrides = new Properties();
            if (!Files.notExists(home) && !Files.isSameFile(managed, home)) {
                overrides = PackagedConfigurationPreflight.readFile(home);
            }
            if (configuredTimeout(defaults, installed, overrides) != 15) {
                return 0;
            }
            if (args.length == 3 && !overrides.containsKey(KEY)) {
                // Removing the physical line must remove exactly this logical property.
                // Reject duplicate/escaped keys and lines belonging to a continued value.
                Properties candidate = PackagedConfigurationPreflight.readFile(Path.of(args[2]));
                if (installed.remove(KEY) != null && installed.equals(candidate)
                        && configuredTimeout(defaults, candidate, overrides) == 360) {
                    return 10;
                }
            }
            return 15;
        } catch (IOException | RuntimeException failure) {
            // Spring diagnostics may contain the referenced property's value.
            return 1;
        }
    }

    private static int configuredTimeout(Properties defaults, Properties managed, Properties home) {
        // Same order as applicationContext.xml, CarlosProperties and the Startup listener.
        Properties values = new Properties();
        values.putAll(defaults);
        values.putAll(managed);
        values.putAll(home);
        var sources = new MutablePropertySources();
        sources.addLast(new PropertiesPropertySource("carlos", values));
        return new PropertySourcesPropertyResolver(sources).getRequiredProperty(KEY, Integer.class);
    }
}
