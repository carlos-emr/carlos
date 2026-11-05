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
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;
import static io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryAgentProtocol.JSON;

/** Reusable host boundary for a draft summary of one caller-authorized text extract. */
public final class DocumentSummaryService {
    public static final String ENABLED_PROPERTY = "clinical.ai_document_summary.enabled";
    private static final Semaphore CAPACITY = new Semaphore(1);
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
    /**
     * Words that assert an action status. A point may use one only when one of its own excerpts uses a
     * word of the same family; the overview only when the document does. Families rather than stems:
     * "prescrit" supports "prescrite", "prescription" supports "prescribed". A fixed list in English and
     * French: the rule is a provenance check on wording the model added, not a clinical judgement.
     * Deliberately absent: "instruction" (an overview rightly calls an imperative line an instruction),
     * French "prévu" (planned, the dated-plan paraphrase, not a booking), and the everyday families
     * given/donné and taken/pris ("étant donné", "given the history", "prise de sang", "prise en charge",
     * "taking into account", "take care"), which say nothing about a medication's status.
     */
    static final List<Set<String>> STATUS_FAMILIES = List.of(
            Set.of("prescribed", "prescribe", "prescribes", "prescribing", "prescription",
                    "prescrit", "prescrite", "prescrits", "prescrites", "prescrire"),
            Set.of("administered", "administer", "administers", "administering", "administration",
                    "administré", "administrée", "administrés", "administrées", "administrer"),
            Set.of("started", "start", "starts", "starting", "commenced", "initiated",
                    "commencé", "commencée", "commencés", "commencées", "commencer", "débuté", "débutée", "instauré", "instaurée"),
            Set.of("scheduled", "schedule", "schedules", "scheduling",
                    "programmé", "programmée", "programmés", "programmées",
                    "planifié", "planifiée", "planifiés", "planifiées"),
            Set.of("booked", "book", "books", "booking",
                    "réservé", "réservée", "réservés", "réservées"),
            Set.of("arranged", "arrange", "arranges", "arranging",
                    "organisé", "organisée", "organisés", "organisées", "convenu", "convenue", "convenus", "convenues"));
    private final ClinicalSummaryAgent agent;

    public DocumentSummaryService() { this(ClinicalSummaryAgents.configuredDocument()); }

    public DocumentSummaryService(ClinicalSummaryAgent agent) { this.agent = agent; }

    /**
     * Summarizes text already loaded and authorized by the caller. This method has no record access
     * and deliberately accepts no filename or patient identifier, so it can be embedded safely in
     * different CARLOS workflows without giving the model adapter access to managers or sessions.
     */
    public DocumentSummary summarize(String text) throws ClinicalSummaryGenerationException {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Readable document text is required");
        }
        if (!CAPACITY.tryAcquire()) {
            throw new ClinicalSummaryGenerationException("A document summary is already being generated. Try again when it finishes.");
        }
        try {
            ObjectNode source = JSON.createObjectNode().put("id", "document")
                    .put("title", "Document").put("text", text);
            ObjectNode request = JSON.createObjectNode().put("contract_version", ClinicalSummaryAgentProtocol.VERSION)
                    .put("request_id", UUID.randomUUID().toString()).put("workflow", "single-document-summary")
                    .put("data_classification", "clinical-document")
                    .put("instructions", ClinicalSummaryAgentProtocol.resource("document-summary-prompt.txt"));
            request.putArray("sources").add(source);
            request.set("output_schema", JSON.readTree(ClinicalSummaryAgentProtocol.resource("document-summary-schema.json")));
            if (JSON.writeValueAsBytes(request).length > agent.requestBytes()) {
                throw new ClinicalSummaryGenerationException("This document is too large for the configured model. No partial summary was generated.");
            }
            JsonNode output = agent.generate(request);
            validate(output, text);
            return new DocumentSummary(output);
        } catch (ClinicalSummaryGenerationException expected) {
            throw expected;
        } catch (ClinicalSummaryOutputLimitException truncated) {
            throw new ClinicalSummaryGenerationException("The model could not finish this document within its output limit. No partial summary was generated.");
        } catch (SocketTimeoutException timeout) {
            throw new ClinicalSummaryGenerationException("Document summarization timed out. The original document is unchanged.");
        } catch (IOException unavailable) {
            throw new ClinicalSummaryGenerationException("The configured agent is unavailable or returned an unreadable response. The original document is unchanged.");
        } catch (RuntimeException invalid) {
            throw new ClinicalSummaryGenerationException("The agent response failed validation. The original document is unchanged.");
        } finally {
            CAPACITY.release();
        }
    }

    // Exact excerpts and lexical overlap are provenance checks, not proof of clinical correctness.
    static void validate(JsonNode output, String source) {
        ClinicalSummaryAgentProtocol.exactFields(output, Set.of("overview", "points"));
        String overview = text(output, "overview", 4000);
        JsonNode points = output.path("points");
        if (!points.isArray() || points.isEmpty() || points.size() > 50) {
            throw new IllegalArgumentException("Invalid document summary points");
        }
        Set<String> unique = new HashSet<>();
        for (JsonNode point : points) {
            ClinicalSummaryAgentProtocol.exactFields(point, Set.of("text", "evidence"));
            String statement = text(point, "text", 2000);
            if (!unique.add(normalize(statement))) throw new IllegalArgumentException("Duplicate summary point");
            JsonNode evidence = point.path("evidence");
            if (!evidence.isArray() || evidence.isEmpty() || evidence.size() > 5) {
                throw new IllegalArgumentException("Invalid document evidence");
            }
            StringBuilder excerpts = new StringBuilder();
            Set<String> cited = new HashSet<>();
            for (JsonNode item : evidence) {
                if (!item.isTextual() || item.asText().isBlank() || item.asText().length() > 800
                        || !source.contains(item.asText()) || !cited.add(item.asText())) {
                    throw new IllegalArgumentException("Evidence is not a unique verbatim document excerpt");
                }
                excerpts.append(' ').append(item.asText());
            }
            if (!sharesWord(statement, excerpts.toString())) {
                throw new IllegalArgumentException("Summary point lacks lexical support");
            }
            String status = statusWordWithoutSupport(statement, excerpts.toString());
            if (status != null) throw new IllegalArgumentException("Summary point adds a status its evidence does not state: " + status);
            String possessor = DocumentSummaryFidelityValidator.addedPossessor(statement, excerpts.toString());
            if (possessor != null) throw new IllegalArgumentException("Summary point resolves a possessor its evidence does not state: " + possessor);
            List<String> quoted = new ArrayList<>();
            evidence.forEach(item -> quoted.add(item.asText()));
            if (DocumentSummaryFidelityValidator.evidenceInInjectedLine(quoted, source)) {
                throw new IllegalArgumentException("Summary point cites text addressed to an AI");
            }
            String command = DocumentSummaryFidelityValidator.injectedCommandWord(statement, source);
            if (command != null) throw new IllegalArgumentException("Summary point describes text addressed to an AI: " + command);
        }
        if (!sharesWord(overview, source)) throw new IllegalArgumentException("Overview lacks lexical support");
        String status = statusWordWithoutSupport(overview, source);
        if (status != null) throw new IllegalArgumentException("Overview adds a status the document does not state: " + status);
        String possessor = DocumentSummaryFidelityValidator.addedPossessor(overview, source);
        if (possessor != null) throw new IllegalArgumentException("Overview resolves a possessor the document does not state: " + possessor);
        String command = DocumentSummaryFidelityValidator.injectedCommandWord(overview, source);
        if (command != null) throw new IllegalArgumentException("Overview describes text addressed to an AI: " + command);
    }

    /** The first status word in {@code statement} whose family is absent from {@code reference}, else null. */
    static String statusWordWithoutSupport(String statement, String reference) {
        Set<String> stated = allWords(statement);
        Set<String> supporting = allWords(reference);
        for (Set<String> family : STATUS_FAMILIES) {
            if (supporting.stream().anyMatch(family::contains)) continue;
            for (String word : stated) if (family.contains(word)) return word;
        }
        return null;
    }

    private static Set<String> allWords(String value) {
        var result = new HashSet<String>();
        var matcher = WORD.matcher(Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT));
        while (matcher.find()) result.add(matcher.group());
        return result;
    }

    private static String text(JsonNode object, String field, int max) {
        JsonNode value = object.path(field);
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > max
                || value.asText().indexOf('\r') >= 0) throw new IllegalArgumentException("Invalid summary text");
        return value.asText().strip();
    }

    private static boolean sharesWord(String left, String right) {
        Set<String> words = words(right);
        return words(left).stream().anyMatch(words::contains);
    }

    private static Set<String> words(String value) {
        var result = new HashSet<String>();
        var matcher = WORD.matcher(Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String word = matcher.group();
            if (word.length() >= 4 || word.chars().allMatch(Character::isDigit)) result.add(word);
        }
        return result;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }
}
