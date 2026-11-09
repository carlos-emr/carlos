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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Independent export admission/delegation policy; import enablement never supplies export authority. */
@Service
public class CdsExportConfigurationService {
    private final Properties properties;

    public CdsExportConfigurationService(@Qualifier("cdsExportProperties") Properties supplied) {
        properties = new Properties();
        properties.putAll(supplied);
    }

    /** Same trusted server facility policy used by the active case-management note reader. */
    public boolean filterNotesOnFacility() {
        String value = properties.getProperty("FILTER_ON_FACILITY", "true").strip();
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        throw new IllegalStateException("CDS_EXPORT_CONFIGURATION_INVALID");
    }

    public boolean enabled() {
        String value = properties.getProperty("cds.export.enabled", "false").strip();
        if (!Set.of("true", "false").contains(value))
            throw new IllegalStateException("CDS_EXPORT_CONFIGURATION_INVALID");
        return value.equals("true");
    }

    /** Status/control remain available when new disclosure admission is disabled. */
    public void requireEnabled() {
        if (!enabled()) throw new IllegalStateException("CDS_EXPORT_ADMISSION_DISABLED");
    }

    /** Dedicated foreground contexts force this false; durable browser acceptance still survives a paused dispatcher. */
    public boolean workerEnabled() {
        String value = properties.getProperty("cds.export.worker.enabled", "true").strip();
        if (!Set.of("true", "false").contains(value)) {
            invalid();
        }
        return value.equals("true");
    }

    /** A finite attempt wall budget is independent of lease liveness; exhaustion never truncates a package. */
    public long maxDurationMillis() {
        String value = properties.getProperty("cds.export.maxDurationMillis", "86400000").strip();
        try {
            if (!value.matches("[0-9]{1,10}")) invalid();
            long selected = Long.parseLong(value);
            if (selected < 1 || selected > 31L * 24 * 60 * 60 * 1000) invalid();
            return selected;
        } catch (NumberFormatException malformed) {
            throw new IllegalStateException("CDS_EXPORT_CONFIGURATION_INVALID");
        }
    }

    /** Protected server input root; this is separate from clinical source/document and artifact stores. */
    public java.nio.file.Path selectionRoot() {
        return configuredRoot("cds.export.selectionRoot");
    }

    /** Existing retained import storage is readable independently of new import admission. */
    public java.nio.file.Path retainedImportRoot() {
        return configuredRoot("cds.import.stagingRoot");
    }

    private java.nio.file.Path configuredRoot(String key) {
        String value = properties.getProperty(key, "").strip();
        try {
            java.nio.file.Path root = java.nio.file.Path.of(value);
            if (value.isEmpty() || !root.isAbsolute() || !root.equals(root.normalize())) invalid();
            return root;
        } catch (RuntimeException malformed) {
            throw new IllegalStateException("CDS_EXPORT_CONFIGURATION_INVALID");
        }
    }

    /** Additional to independently verified operator identity and every live provider/patient/domain permission. */
    public void requireDelegation(String operator, String provider, Integer facility) {
        if (operator == null
                || !operator.matches("[A-Za-z_][A-Za-z0-9_.-]{0,63}\\$?")
                || provider == null
                || !provider.matches("[A-Za-z0-9_-]{1,16}")
                || facility != null && facility < 1) deny();
        String prefix = "cds.export.operator." + operator + ".";
        Set<String> providers = tokens(prefix + "providers");
        if (providers.stream().anyMatch(p -> !p.matches("[A-Za-z0-9_-]{1,16}"))) invalid();
        Set<String> facilities = tokens(prefix + "facilities");
        for (String configured : facilities) {
            try {
                if (!configured.matches("[1-9][0-9]{0,9}") || Integer.parseInt(configured) < 1)
                    invalid();
            } catch (NumberFormatException malformed) {
                invalid();
            }
        }
        if (!providers.contains(provider)
                || !facilities.isEmpty()
                        && (facility == null || !facilities.contains(facility.toString()))) deny();
    }

    /** Unscoped metadata delegation does not inherit a deleted historical facility; restricted grants still require their exact scope. */
    public Integer metadataFacility(String verifiedOperator, Integer historicalHint) {
        if (verifiedOperator == null
                || !verifiedOperator.matches("[A-Za-z_][A-Za-z0-9_.-]{0,63}\\$?")) deny();
        return tokens("cds.export.operator." + verifiedOperator + ".facilities").isEmpty()
                ? null
                : historicalHint;
    }

    private Set<String> tokens(String key) {
        String text = properties.getProperty(key, "").strip();
        if (text.isEmpty()) return Set.of();
        String[] values = text.split("[,\\s]+", -1);
        if (Arrays.stream(values).anyMatch(String::isEmpty)) invalid();
        return Arrays.stream(values).collect(Collectors.toUnmodifiableSet());
    }

    private static void invalid() {
        throw new IllegalStateException("CDS_EXPORT_CONFIGURATION_INVALID");
    }

    private static void deny() {
        throw new SecurityException("CDS_EXPORT_AUTHORIZATION_DENIED");
    }
}
