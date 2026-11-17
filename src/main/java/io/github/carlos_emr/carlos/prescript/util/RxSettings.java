/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prescript.util;

import io.github.carlos_emr.CarlosProperties;

/**
 * Rx settings in the CARLOS properties file, read under their {@code prescript.} name and, when that
 * is not set, under the {@code rx.} name installations carried over from OSCAR use (issue #4797).
 *
 * <p>The namespace cleanup a08586ca65 renamed {@code rx.policy} and {@code rx.default_instruction} to
 * {@code prescript.*} along with the packages. Reading only the new names silently ignored every
 * existing setting, so both are read, the new one first. A blank new name counts as unset, so a template
 * line such as {@code prescript.policy=} cannot switch off an installation's {@code rx.policy}.</p>
 *
 * @since 2026-10-16
 */
final class RxSettings {

    static final String PREFIX = "prescript.";
    static final String LEGACY_PREFIX = "rx.";

    private RxSettings() {
    }

    /**
     * @param name the setting without its prefix, for example {@code policy}
     * @return the {@code prescript.<name>} value unless it is blank, else the {@code rx.<name>} value,
     *         else {@code null}
     */
    static String get(String name) {
        CarlosProperties properties = CarlosProperties.getInstance();
        String value = properties.getProperty(PREFIX + name);
        return value != null && !value.isBlank() ? value : properties.getProperty(LEGACY_PREFIX + name);
    }
}
