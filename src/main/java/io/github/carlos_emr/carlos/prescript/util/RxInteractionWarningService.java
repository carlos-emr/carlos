/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prescript.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Vector;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Works out which drug-drug interaction warnings each staged prescription card shows.
 *
 * <p>The Rx page stages new prescriptions as cards, each keyed by its stash random id and
 * carrying the drug's ATC code. A card warns about an interaction between its drug and any other
 * drug in the check: another staged card, or one of the patient's current medications. The
 * interactions come from DrugRef's Holbrook data ({@code fetch("interactions_byATC", ...)}),
 * which reports each pair as an affecting drug, an effect and an affected drug with a significance
 * of 1 (minor), 2 (moderate) or 3 (major). Interactions among the patient's current medications
 * alone have no card and are not reported here. A record whose effect is {@code N} ("has no
 * effect on") documents that the pair does <em>not</em> interact; it is not a warning. In the
 * shipped data those are exactly the records with no significance, which would otherwise surface
 * as hundreds of "unknown significance" warnings.</p>
 *
 * <p>A check that could not run is reported as {@link Status#UNAVAILABLE}, never as a check with
 * no warnings: the page must not tell a prescriber that a drug has no interactions when DrugRef
 * never answered (issue #4506).</p>
 *
 * @since 2026-10-15
 */
public class RxInteractionWarningService {

    private static final Logger logger = MiscUtils.getLogger();

    /**
     * Looks up the interactions among a set of ATC codes.
     */
    @FunctionalInterface
    public interface InteractionLookup {
        /**
         * @param atcCodes distinct ATC codes, at least two
         * @return one map per interaction with DrugRef's Holbrook keys ({@code affectingatc},
         *         {@code affectedatc}, {@code affectingdrug}, {@code affecteddrug}, {@code significance},
         *         {@code effect}, {@code evidence}, {@code comment})
         * @throws Exception when DrugRef cannot answer
         */
        List<Map<?, ?>> interactionsAmong(List<String> atcCodes) throws Exception;
    }

    /** The outcome of a check. */
    public enum Status {
        /** The check ran (or there was nothing to compare); {@link Result#warnings()} is complete. */
        CHECKED,
        /** The warning level is {@link RxInteractionWarningLevelResolver#NONE}; nothing was checked. */
        OFF,
        /** DrugRef could not answer; nothing is known about interactions. */
        UNAVAILABLE
    }

    /** How serious DrugRef grades an interaction, with the marker id prefix the Rx card uses. */
    public enum Severity {
        MAJOR(3, "major"),
        MODERATE(2, "moderate"),
        MINOR(1, "minor"),
        UNKNOWN(0, "unknown");

        private final int significance;
        private final String code;

        Severity(int significance, String code) {
            this.significance = significance;
            this.code = code;
        }

        /** @return DrugRef's significance (0 when ungraded) */
        public int significance() {
            return significance;
        }

        /** @return the marker id prefix on the card ({@code major_<randomId>} ...) */
        public String code() {
            return code;
        }

        static Severity of(Object significance) {
            String value = significance == null ? "" : significance.toString().trim();
            for (Severity severity : values()) {
                if (severity != UNKNOWN && value.equals(Integer.toString(severity.significance))) {
                    return severity;
                }
            }
            return UNKNOWN;
        }
    }

    /**
     * One staged prescription card.
     *
     * @param randomId the card's stash random id (the {@code <rand>} of {@code set_<rand>})
     * @param atcCode  the drug's ATC code, or {@code null}/blank when it has none
     */
    public record StagedDrug(long randomId, String atcCode) {
    }

    /**
     * One warning shown on one card.
     *
     * @param randomId the card it belongs to
     * @param severity how serious the interaction is
     * @param partner  the other drug's name as DrugRef records it
     * @param summary  the interaction as one sentence, e.g. "CIPROFLOXACIN augments THEOPHYLLINE"
     * @param evidence the evidence grade in words ("good", "fair", "poor" or "unknown")
     * @param comment  DrugRef's clinical comment, or an empty string
     */
    public record Warning(long randomId, Severity severity, String partner, String summary,
                          String evidence, String comment) {
    }

    /**
     * @param status    whether the check ran
     * @param level     the warning level applied
     * @param warnings  the warnings to show, most serious first; empty unless {@link Status#CHECKED}
     * @param unchecked the staged cards whose drug has no ATC code, so DrugRef could not compare them
     *                  with anything; empty unless {@link Status#CHECKED}. A blank card must not read
     *                  as "no interactions".
     */
    public record Result(Status status, int level, List<Warning> warnings, List<Long> unchecked) {

        public Result {
            warnings = List.copyOf(warnings);
            unchecked = List.copyOf(unchecked);
        }

        /** A result with no unchecked cards. */
        public Result(Status status, int level, List<Warning> warnings) {
            this(status, level, warnings, List.of());
        }
    }

    private static final Comparator<Warning> MOST_SERIOUS_FIRST = Comparator
            .comparingInt((Warning warning) -> -warning.severity().significance())
            .thenComparing(Warning::partner)
            .thenComparing(Warning::summary);

    private final InteractionLookup lookup;

    /**
     * Checks against the configured DrugRef service.
     */
    public RxInteractionWarningService() {
        this(RxInteractionWarningService::drugRefInteractions);
    }

    /**
     * @param lookup the interaction source (tests supply their own)
     */
    public RxInteractionWarningService(InteractionLookup lookup) {
        this.lookup = lookup;
    }

    /**
     * Checks the staged cards against each other and against the patient's current medications.
     *
     * @param level            the warning level ({@link RxInteractionWarningLevelResolver#resolve})
     * @param staged           the staged cards
     * @param currentAtcCodes  the ATC codes of the patient's current medications
     * @return the warnings per card, or why there are none
     */
    public Result check(int level, List<StagedDrug> staged, Collection<String> currentAtcCodes) {
        if (level == RxInteractionWarningLevelResolver.NONE) {
            return new Result(Status.OFF, level, List.of());
        }
        List<Long> unchecked = new ArrayList<>();
        Set<String> stagedCodes = new LinkedHashSet<>();
        for (StagedDrug drug : staged) {
            String code = atc(drug.atcCode());
            if (code == null) {
                unchecked.add(drug.randomId());
            } else {
                stagedCodes.add(code);
            }
        }
        Set<String> codes = new LinkedHashSet<>(stagedCodes);
        currentAtcCodes.stream().map(RxInteractionWarningService::atc).filter(Objects::nonNull).forEach(codes::add);
        // A card can only interact with a second, different drug.
        if (stagedCodes.isEmpty() || codes.size() < 2) {
            return new Result(Status.CHECKED, level, List.of(), unchecked);
        }

        List<Map<?, ?>> interactions = interactionsAmong(codes);
        if (interactions == null) {
            return new Result(Status.UNAVAILABLE, level, List.of());
        }
        Set<Warning> warnings = new LinkedHashSet<>();
        for (Map<?, ?> interaction : interactions) {
            warnings.addAll(warningsFor(level, interaction, staged));
        }
        List<Warning> sorted = new ArrayList<>(warnings);
        sorted.sort(MOST_SERIOUS_FIRST);
        return new Result(Status.CHECKED, level, sorted, unchecked);
    }

    /** DrugRef's interactions among the codes, or {@code null} when it could not answer. */
    private List<Map<?, ?>> interactionsAmong(Set<String> codes) {
        try {
            List<Map<?, ?>> interactions = lookup.interactionsAmong(new ArrayList<>(codes));
            if (interactions == null) {
                logger.error("Drug interaction check unavailable (no answer from DrugRef)");
            }
            return interactions;
        } catch (Exception e) {
            // The class name only: the ATC codes and DrugRef's text describe this patient's medications.
            logger.error("Drug interaction check unavailable ({})", e.getClass().getSimpleName());
            return null;
        }
    }

    /** The warnings one interaction record puts on the staged cards it involves. */
    private static List<Warning> warningsFor(int level, Map<?, ?> interaction, List<StagedDrug> staged) {
        String effectCode = text(interaction.get("effect")).trim();
        Severity severity = Severity.of(interaction.get("significance"));
        if ("N".equals(effectCode) || "n".equals(effectCode)
                || !RxInteractionWarningLevelResolver.isShown(level, severity.significance())) {
            return List.of();
        }
        String affectingAtc = atc(text(interaction.get("affectingatc")));
        String affectedAtc = atc(text(interaction.get("affectedatc")));
        String affectingDrug = name(interaction.get("affectingdrug"), affectingAtc);
        String affectedDrug = name(interaction.get("affecteddrug"), affectedAtc);
        String summary = affectingDrug + " " + effect(effectCode) + " " + affectedDrug;
        String evidence = evidence(text(interaction.get("evidence")).trim());
        String comment = text(interaction.get("comment")).trim();
        List<Warning> warnings = new ArrayList<>();
        for (StagedDrug drug : staged) {
            String code = atc(drug.atcCode());
            // A card warns when it is exactly one side of the interaction: no drug interacts with itself.
            if (code != null && code.equals(affectingAtc) != code.equals(affectedAtc)) {
                String partner = code.equals(affectingAtc) ? affectedDrug : affectingDrug;
                warnings.add(new Warning(drug.randomId(), severity, partner, summary, evidence, comment));
            }
        }
        return warnings;
    }

    /**
     * The default lookup: DrugRef's {@code interactions_byATC}. Every element of the answer must be
     * an interaction record; anything else means the answer cannot be trusted.
     */
    private static List<Map<?, ?>> drugRefInteractions(List<String> atcCodes) throws Exception {
        Vector<?> answer = new RxDrugRef().getInteractions(new Vector<>(atcCodes));
        List<Map<?, ?>> interactions = new ArrayList<>(answer.size());
        for (Object item : answer) {
            if (!(item instanceof Map<?, ?> interaction)) {
                throw new IllegalStateException("DrugRef returned an interaction that is not a record");
            }
            interactions.add(interaction);
        }
        return interactions;
    }

    private static String atc(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim();
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String name(Object drug, String atcCode) {
        String name = text(drug).trim();
        if (!name.isEmpty()) {
            return name;
        }
        return atcCode == null ? "another drug" : atcCode;
    }

    /** Holbrook effect codes, as DrugRef's own legend words them ({@code N}, no effect, is never a warning). */
    static String effect(String code) {
        return switch (code) {
            case "A" -> "augments";
            case "a" -> "augments (no clinical effect)";
            case "I" -> "inhibits";
            case "i" -> "inhibits (no clinical effect)";
            default -> "has an unknown effect on";
        };
    }

    /** Holbrook evidence grades. */
    static String evidence(String code) {
        return switch (code) {
            case "G" -> "good";
            case "F" -> "fair";
            case "P" -> "poor";
            default -> "unknown";
        };
    }
}
