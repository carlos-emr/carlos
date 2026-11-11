/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors */
package io.github.carlos_emr.carlos.config;

import java.nio.file.Path;

/** Unprivileged fixture; this class is never included in the installed helper. */
public final class ConfigurationPreflightFixture {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("reconcile")) {
            PackagedConfigurationPreflight.reconcile(Path.of(args[1]), false);
        } else if (args[0].equals("status")) {
            java.util.Properties properties = new java.util.Properties();
            try (var input = java.nio.file.Files.newInputStream(Path.of(args[1]))) { properties.load(input); }
            System.out.println(Boolean.parseBoolean(properties.getProperty("AbandonOldChart")) ? "ready" : "absent");
        } else if (args[0].equals("verify")) {
            PackagedConfigurationPreflight.verify(Path.of(args[1]), Path.of(args[2]));
        } else {
            throw new IllegalArgumentException("unknown fixture mode");
        }
    }
}
