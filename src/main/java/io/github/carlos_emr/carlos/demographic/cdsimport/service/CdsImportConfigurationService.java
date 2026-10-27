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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportKeys;

/** Server-owned admission, provenance and capacity policy; no caller can mint a source namespace. */
@Service
public class CdsImportConfigurationService {
    public static final String RULESET = "carlos-cds-import-v1";
    private static final Set<String> CORE_PROFILES = Set.of("pcon-2024-02", "omd-5.2");
    private final Properties properties;
    private final java.util.Map<String, String> policyDigests = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<>(16, 0.75f, true) {
                @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, String> eldest) {
                    return size() > 64;
                }
            });

    public CdsImportConfigurationService(@Qualifier("cdsImportProperties") Properties properties) {
        this.properties = new Properties();
        this.properties.putAll(properties);
    }

    public boolean enabled() { return Boolean.parseBoolean(value("cds.import.enabled", "false")); }

    public void requireEnabled() {
        if (!enabled()) throw new CdsImportException("IMPORT_ADMISSION_DISABLED");
    }

    public Path allowedRoot() { return configuredPath("cds.import.allowedRoot"); }
    public Path stagingRoot() { return configuredPath("cds.import.stagingRoot"); }
    public Path uploadRoot() { return configuredPath("cds.import.uploadRoot"); }

    public Path configuredPath(String key) {
        String configured = value(key, "");
        if (configured.isBlank()) throw new CdsImportException("IMPORT_STORAGE_NOT_CONFIGURED");
        Path path;
        try { path = Path.of(configured); }
        catch (RuntimeException failure) { throw new CdsImportException("IMPORT_STORAGE_CONFIGURATION_INVALID"); }
        if (!path.isAbsolute()) throw new CdsImportException("IMPORT_STORAGE_MUST_BE_ABSOLUTE");
        return path.normalize();
    }

    public void requireSource(String namespace, String profile) {
        requireToken(namespace);
        if (namespace.length() > 64) throw new CdsImportException("IMPORT_SOURCE_NAMESPACE_TOO_LONG");
        if (!CORE_PROFILES.contains(profile)
                || !tokens("cds.import.source." + namespace + ".profiles").contains(profile)) {
            throw new CdsImportException("SOURCE_PROFILE_NOT_REGISTERED");
        }
    }

    /** Explicit operator delegation is additional to active-provider/domain privileges. */
    public void requireDelegation(String operator, String providerNo, Integer facility, Integer program) {
        if (operator == null || !operator.matches("[A-Za-z_][A-Za-z0-9_.-]{0,63}\\$?")) {
            throw new SecurityException("Invalid import operator");
        }
        if (providerNo == null || !providerNo.matches("[A-Za-z0-9_-]{1,16}")) {
            throw new SecurityException("Invalid import provider");
        }
        String prefix = "cds.import.operator." + operator + ".";
        if (!tokens(prefix + "providers").contains(providerNo)) {
            throw new SecurityException("Import operator delegation denied");
        }
        requireOptionalScope(prefix + "facilities", facility);
        requireOptionalScope(prefix + "programs", program);
    }

    private void requireOptionalScope(String key, Integer selected) {
        Set<String> allowed = tokens(key);
        if (!allowed.isEmpty() && (selected == null || !allowed.contains(selected.toString()))) {
            throw new SecurityException("Import operator scope denied");
        }
    }

    public CdsInputLimitsDto limits() {
        CdsInputLimitsDto defaults = CdsInputLimitsDto.defaults();
        return new CdsInputLimitsDto(number("cds.import.maxSourceBytes", defaults.maxSourceBytes()),
                number("cds.import.maxExpandedBytes", defaults.maxExpandedBytes()),
                number("cds.import.maxEntries", defaults.maxEntries()),
                Math.toIntExact(number("cds.import.maxXmlDepth", defaults.maxXmlDepth())),
                Math.toIntExact(number("cds.import.maxScalarCharacters", defaults.maxBoundedScalarCharacters())),
                number("cds.import.maxBinaryBytes", defaults.maxBinaryBytes()),
                number("cds.import.minimumFreeDiskBytes", defaults.minimumFreeDiskBytes()),
                number("cds.import.maxDurationMillis", defaults.maxDurationMillis()),
                Math.toIntExact(number("cds.import.maxPathCharacters", defaults.maxPathCharacters())),
                ratio("cds.import.maxCompressionRatio", defaults.maxCompressionRatio()),
                Math.toIntExact(number("cds.import.maxNamespaceAliases", defaults.maxNamespaceAliases())),
                Math.toIntExact(number("cds.import.maxUniqueXmlNames", defaults.maxUniqueXmlNames())),
                Math.toIntExact(number("cds.import.maxXmlNameCharacters", defaults.maxXmlNameCharacters())),
                Math.toIntExact(number("cds.import.maxNamespaceUriCharacters", defaults.maxNamespaceUriCharacters())));
    }

    public long number(String key, long fallback) {
        String configured = value(key, Long.toString(fallback));
        try {
            long result = Long.parseLong(configured);
            if (result < 1) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException failure) {
            throw new CdsImportException("IMPORT_RESOURCE_CONFIGURATION_INVALID");
        }
    }

    public String value(String key, String fallback) { return properties.getProperty(key, fallback).trim(); }

    /** Credentials retain their exact configured value; callers must never include it in diagnostics. */
    public String rawValue(String key) { return properties.getProperty(key); }

    /** Effective normalization choices are part of provenance; resource limits and live grants are not clinical facts. */
    public String normalizationPolicyDigest(CdsImportCommand command) {
        String calendarZone = java.time.ZoneId.systemDefault().getId();
        String cacheKey = command.sourceNamespace() + "\u0000" + calendarZone;
        String cached = policyDigests.get(cacheKey);
        if (cached != null) return cached;
        java.util.SortedMap<String, String> policy = new java.util.TreeMap<>();
        policy.put("cds.import.clinicalZone", value("cds.import.clinicalZone", ""));
        policy.put("cds.import.native.maxNoteBytes", Long.toString(number("cds.import.native.maxNoteBytes", 262144)));
        policy.put("runtime.calendarZone", calendarZone);
        policy.put("hibernate.jdbc.time_zone", value("hibernate.jdbc.time_zone", ""));
        String source = "cds.import.source." + command.sourceNamespace() + ".";
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("cds.import.native.") || key.startsWith("cds.import.mapping.")
                    || key.startsWith("cds.import.normalization.")
                    || key.startsWith(source) && !key.equals(source + "profiles")) {
                policy.put(key, value(key, ""));
            }
        }
        var digest = CdsImportKeys.digest();
        CdsImportKeys.add(digest, "carlos-cds-normalization-policy-v1");
        for (var entry : policy.entrySet()) {
            CdsImportKeys.add(digest, entry.getKey());
            CdsImportKeys.add(digest, java.util.HexFormat.of().formatHex(
                    CdsImportKeys.digest().digest(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        }
        String value = java.util.HexFormat.of().formatHex(digest.digest());
        policyDigests.put(cacheKey, value);
        return value;
    }

    private double ratio(String key, double fallback) {
        try {
            double value = Double.parseDouble(value(key, Double.toString(fallback)));
            if (!Double.isFinite(value) || value < 1) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException invalid) { throw new CdsImportException("IMPORT_RESOURCE_CONFIGURATION_INVALID"); }
    }

    private Set<String> tokens(String key) {
        String configured = value(key, "");
        if (configured.length() > 8192) throw new CdsImportException("IMPORT_REGISTRY_CONFIGURATION_INVALID");
        return Arrays.stream(configured.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public static void requireToken(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw new CdsImportException("IMPORT_IDENTITY_TOKEN_INVALID");
        }
    }
}
