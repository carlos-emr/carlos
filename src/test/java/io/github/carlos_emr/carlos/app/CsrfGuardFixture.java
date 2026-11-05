/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.owasp.csrfguard.CsrfGuard;

/**
 * Loads a CsrfGuard configuration for one test and puts the JVM-wide singleton back afterwards.
 *
 * <p>{@code CsrfGuard} is a process-wide singleton and Surefire reuses forks, so a test that calls
 * {@code CsrfGuard.load} without restoring leaves every later test in the fork with an enabled,
 * production-configured guard. Restoring is not a matter of re-loading with {@code Enabled=false}:
 * {@code PropertiesConfigurationProviderFactory} pins its first provider in a static field, so a
 * second load through it is ignored. The way back to the disabled guard other tests start from is
 * {@code NullConfigurationProviderFactory}, whose provider reports disabled; {@code CsrfGuard.load}
 * clears its own expirable cache, so the switch takes effect at once.</p>
 *
 * @since 2026-11-05
 */
public final class CsrfGuardFixture implements AutoCloseable {

    static final Path SHIPPED_PROPERTIES = Path.of("src/main/webapp/WEB-INF/Owasp.CsrfGuard.properties");
    private static final String FACTORY = "org.owasp.csrfguard.configuration.provider.factory";

    private final Properties loaded;
    private final boolean enabledBefore;

    private CsrfGuardFixture(Properties loaded, boolean enabledBefore) {
        this.loaded = loaded;
        this.enabledBefore = enabledBefore;
    }

    /**
     * Loads the shipped {@code Owasp.CsrfGuard.properties} through the properties provider factory.
     *
     * @return CsrfGuardFixture the fixture that restores the previous guard state on close
     * @throws IOException if the shipped properties file cannot be read
     */
    public static CsrfGuardFixture loadShippedConfiguration() throws IOException {
        Properties properties = new Properties();
        try (var in = Files.newInputStream(SHIPPED_PROPERTIES)) {
            properties.load(in);
        }
        properties.setProperty(FACTORY, "org.owasp.csrfguard.config.PropertiesConfigurationProviderFactory");
        return load(properties);
    }

    /**
     * Loads the given configuration, remembering whether the guard was enabled before.
     *
     * <p>Private on purpose: the properties factory pins the first provider it builds for the
     * rest of the fork, so a test that loaded a non-shipped configuration through it would hand
     * that configuration to every later {@link #loadShippedConfiguration()} without an error.</p>
     *
     * @param properties Properties the CsrfGuard configuration to load
     * @return CsrfGuardFixture the fixture that restores the previous guard state on close
     */
    private static CsrfGuardFixture load(Properties properties) {
        boolean enabledBefore = CsrfGuard.getInstance().isEnabled();
        CsrfGuard.load(properties);
        return new CsrfGuardFixture(properties, enabledBefore);
    }

    /** The singleton as configured by this fixture. */
    public CsrfGuard guard() {
        return CsrfGuard.getInstance();
    }

    /** Puts the singleton back into the enabled or disabled state it had before {@link #load}. */
    @Override
    public void close() {
        Properties restore = new Properties();
        restore.putAll(loaded);
        if (!enabledBefore) {
            restore.setProperty(FACTORY, "org.owasp.csrfguard.config.NullConfigurationProviderFactory");
        }
        CsrfGuard.load(restore);
    }
}
