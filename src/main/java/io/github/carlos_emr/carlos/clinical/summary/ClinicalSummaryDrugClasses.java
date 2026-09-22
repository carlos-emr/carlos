/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import io.github.carlos_emr.CarlosProperties;
import static io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryAgentProtocol.JSON;

/**
 * The site's drug-name to ATC-code table for the same-class conflict check.
 *
 * <p>Read once from the file named by {@code clinical.ai_summary_generation.drugClasses}, a JSON
 * object of lower-case drug name to ATC code. The table is derived from the site's drug reference
 * database and is not shipped, because the ATC classification belongs to the WHO Collaborating
 * Centre for Drug Statistics Methodology. With no property the check is skipped.
 *
 * @since 2026-09-22
 */
final class ClinicalSummaryDrugClasses {
    static final String PROPERTY = "clinical.ai_summary_generation.drugClasses";
    private static volatile Map<String, String> loaded;
    private static volatile String loadedFrom;

    private ClinicalSummaryDrugClasses() { }

    static Map<String, String> configured() {
        return configured(CarlosProperties.getInstance().getProperty(PROPERTY, ""));
    }

    static synchronized Map<String, String> configured(String path) {
        if (path == null || path.isBlank()) return null;
        if (loaded != null && path.equals(loadedFrom)) return loaded;
        try {
            JsonNode table = JSON.readTree(Files.readAllBytes(Path.of(path)));
            if (!table.isObject() || table.isEmpty()) throw new IllegalArgumentException("Invalid drug class table");
            Map<String, String> classes = new LinkedHashMap<>();
            table.fields().forEachRemaining(entry -> {
                if (!entry.getValue().isTextual() || entry.getValue().asText().length() < 5) {
                    throw new IllegalArgumentException("Invalid drug class table");
                }
                classes.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue().asText());
            });
            loaded = Map.copyOf(classes);
            loadedFrom = path;
            return loaded;
        } catch (IOException unreadable) {
            throw new IllegalArgumentException("Unreadable drug class table", unreadable);
        }
    }
}
