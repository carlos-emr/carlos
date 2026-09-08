/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/** Fixed classpath fixture; no chart, upload, database, or inference dependency. */
public final class SyntheticClinicalSummaryProvider implements ClinicalSummaryArtifactProvider, ClinicalSummarySourceProvider {
    public static final String FIXTURE = "/clinical/summary/synthetic-overview.json";

    @Override
    public ClinicalSummaryArtifact load(LoggedInInfo user, ClinicalSummaryRequest request) throws IOException {
        if (!ClinicalSummaryRequest.synthetic().equals(request)) {
            throw new IllegalArgumentException("Only the fixed synthetic scope is supported");
        }
        try (InputStream input = SyntheticClinicalSummaryProvider.class.getResourceAsStream(FIXTURE)) {
            if (input == null) {
                throw new IOException("Synthetic clinical summary fixture is missing");
            }
            return new ClinicalSummaryArtifact(new ObjectMapper().readTree(input));
        }
    }

    @Override
    public Optional<Map<String, Object>> resolve(LoggedInInfo user, ClinicalSummaryRequest request, String sourceId)
            throws IOException {
        Object sources = load(user, request).getView().get("sources");
        for (Object item : (Iterable<?>) sources) {
            @SuppressWarnings("unchecked")
            Map<String, Object> source = (Map<String, Object>) item;
            if (source.get("id").equals(sourceId)) {
                return Optional.of(source);
            }
        }
        return Optional.empty();
    }
}
