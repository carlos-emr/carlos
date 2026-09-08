/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

/** Generation is limited to checksum-verified NHS fixtures, never a client-supplied synthetic flag. */
public final class SyntheticSummaryScope {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonNode FIXTURES = fixtures();

    private SyntheticSummaryScope() { }

    public static String fixtureId(Demographic demographic) {
        for (JsonNode fixture : FIXTURES) {
            if (fixture.path("chart_no").asText().equals(demographic.getChartNo())
                    && fixture.path("alias").asText().equals(demographic.getAlias())
                    && fixture.path("label").asText().equals(demographic.getDisplayName())) {
                return fixture.path("chart_no").asText();
            }
        }
        return "";
    }

    public static boolean isEligible(ClinicalSummaryArtifact artifact) {
        return isEligible(artifact, FIXTURES);
    }

    static boolean isEligible(ClinicalSummaryArtifact artifact, JsonNode fixtures) {
        JsonNode chart = MAPPER.valueToTree(artifact.getView());
        String fixtureId = chart.path("patient_context").path("generation_fixture").asText();
        for (JsonNode fixture : fixtures) {
            if (!fixture.path("chart_no").asText().equals(fixtureId)
                    || !fixture.path("label").asText().equals(chart.path("patient_context").path("label").asText())) {
                continue;
            }
            Set<String> allowed = new HashSet<>();
            for (JsonNode note : fixture.path("notes")) {
                allowed.add(note.path("sha256").asText() + "|" + note.path("date").asText());
            }
            Set<String> found = new HashSet<>();
            for (JsonNode source : chart.path("sources")) {
                if ("identity".equals(source.path("id").asText())) {
                    String patientId = chart.path("patient_context").path("id").asText();
                    if (!patientId.matches("demographic-[1-9][0-9]*") || !source.path("text").asText().equals(
                            "Demographic number: " + patientId.substring("demographic-".length())
                                    + "\nName: " + fixture.path("label").asText())) {
                        return false;
                    }
                    continue;
                }
                String fingerprint = sha256(source.path("text").asText()) + "|" + source.path("date").asText();
                if (!source.path("id").asText().matches("note-[1-9][0-9]*")
                        || !allowed.contains(fingerprint) || !found.add(fingerprint)) {
                    return false;
                }
            }
            // A partial or inaccessible chart must not masquerade as the complete research fixture.
            return found.equals(allowed);
        }
        return false;
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static JsonNode fixtures() {
        try (InputStream input = SyntheticSummaryScope.class.getResourceAsStream("/clinical/summary/nhs-generation-fixtures.json")) {
            if (input == null) {
                throw new IOException("Missing synthetic fixture manifest");
            }
            return MAPPER.readTree(input);
        } catch (IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
}
