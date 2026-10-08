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

import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bounded session review state. Client input never supplies the source, patient, proposal kind or evidence. */
public final class ChartUpdateReview implements Serializable {
    public static final String SESSION_KEY = ChartUpdateReview.class.getName();
    private final String token = UUID.randomUUID().toString();
    private final String provider;
    private final int document;
    private final int patient;
    private final String sourceHash;
    private final String agentName;
    private final ChartUpdateCoverage coverage;
    private final long expiresAt = Instant.now().plusSeconds(900).getEpochSecond();
    private final Map<String, ChartUpdateProposals.Proposal> proposals = new LinkedHashMap<>();
    private final Map<String, String> outcomes = new LinkedHashMap<>();
    private final Map<String, ChartUpdateSuggestions.Suggestion> suggestions = new LinkedHashMap<>();
    private final Map<String, Draft> drafts = new LinkedHashMap<>();
    private String fingerprint;

    /** Clinician edits only; approval is deliberately never carried into a fresh response. */
    public record Draft(String text, String dueDate, String assignee, String destination) implements Serializable {
        public Draft {
            if (text == null || text.length() > 2000 || dueDate == null || dueDate.length() > 10
                    || assignee == null || assignee.length() > 20 || destination == null || destination.length() > 20) {
                throw new IllegalArgumentException("Invalid review draft.");
            }
        }
    }

    public ChartUpdateReview(String provider, ChartUpdateContext.Snapshot snapshot, List<ChartUpdateProposals.Proposal> candidates) {
        this(provider, snapshot, candidates, "");
    }

    public ChartUpdateReview(String provider, ChartUpdateContext.Snapshot snapshot,
            List<ChartUpdateProposals.Proposal> candidates, String agentName) {
        this(provider, snapshot, candidates, agentName, "");
    }

    public ChartUpdateReview(String provider, ChartUpdateContext.Snapshot snapshot,
            List<ChartUpdateProposals.Proposal> candidates, String agentName, String suggestedAssignee) {
        this(provider, snapshot, new ChartUpdateProposals.Report(candidates, null), agentName, suggestedAssignee);
    }

    public ChartUpdateReview(String provider, ChartUpdateContext.Snapshot snapshot,
            ChartUpdateProposals.Report report, String agentName, String suggestedAssignee) {
        this.provider = provider;
        this.agentName = agentName;
        coverage = report.coverage();
        document = snapshot.documentId();
        patient = snapshot.patientId();
        sourceHash = snapshot.sourceHash();
        fingerprint = snapshot.fingerprint();
        report.proposals().forEach(candidate -> {
            proposals.put(candidate.key(), candidate);
            suggestions.put(candidate.key(), ChartUpdateSuggestions.suggest(candidate, snapshot.date(), suggestedAssignee));
        });
    }

    public void authorize(String actor, String suppliedToken) {
        if (!provider.equals(actor) || !token.equals(suppliedToken)) {
            throw new SecurityException("Chart-update review authorization failed");
        }
        if (Instant.now().getEpochSecond() >= expiresAt) {
            throw new IllegalStateException("This review expired or was replaced. Generate proposals again.");
        }
    }
    public ChartUpdateProposals.Proposal proposal(String key) {
        if (!proposals.containsKey(key)) throw new IllegalArgumentException("Unknown proposal");
        return proposals.get(key);
    }
    public String receiptKey(String key) { return receiptKey(patient, sourceHash, proposal(key)); }
    static String receiptKey(int patient, String sourceHash, ChartUpdateProposals.Proposal proposal) {
        return ChartUpdateProposals.hash(patient + "\n" + sourceHash + "\n" + proposal.key());
    }
    public String getToken() { return token; }
    public int getDocument() { return document; }
    public int getPatient() { return patient; }
    public String getSourceHash() { return sourceHash; }
    public ChartUpdateCoverage getCoverage() { return coverage; }
    public String getAgentName() { return agentName; }
    public String getFingerprint() { return fingerprint; }
    public Map<String, ChartUpdateProposals.Proposal> getProposals() {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(proposals));
    }
    public Map<String, String> getOutcomes() { return Map.copyOf(outcomes); }
    public ChartUpdateSuggestions.Suggestion suggestion(String key) { proposal(key); return suggestions.get(key); }
    public Draft draft(String key) { return drafts.getOrDefault(key, suggestion(key).draft()); }
    public void remember(String key, Draft draft) {
        proposal(key);
        if (!outcomes.containsKey(key)) drafts.put(key, draft);
    }
    // The last draft stays (remember() ignores a finished proposal), so the review shows what was saved or
    // dismissed, and in which section, rather than the original suggestion.
    public void record(String key, String outcome) { proposal(key); outcomes.put(key, outcome); }
    public void refresh(String value) { fingerprint = value; }
}
