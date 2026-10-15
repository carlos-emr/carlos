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
package io.github.carlos_emr.carlos.prescript.data;

import java.util.ArrayList;
import java.util.List;

/** Converts DrugRef interactions to plain text card warnings, honoring the configured severity threshold. */
public final class RxInteractionWarnings {
    private RxInteractionWarnings() { }

    public record Warning(String cardId, String severity, String text) { }

    /** ATC codes are the only drug identities accepted by the interaction service. */
    public static boolean usableCode(String code) {
        return AllergyAtcClass.isDrugCode(code) && code.equals(code.trim());
    }

    /** Unknown significance stays visible unless warnings are explicitly disabled (level 4). */
    public static List<Warning> forStash(RxPrescriptionData.Prescription[] stash,
                                       RxDrugData.Interaction[] interactions, int minimum) {
        if (interactions == null) throw new IllegalStateException("Interaction check did not complete");
        List<Warning> warnings = new ArrayList<>();
        if (minimum == 4) return warnings;
        for (var interaction : interactions) {
            if (interaction == null || !usableCode(interaction.affectingatc) || !usableCode(interaction.affectedatc)) {
                throw new IllegalStateException("DrugRef returned an interaction without usable drug codes");
            }
            int significance = switch (String.valueOf(interaction.significance)) {
                case "1" -> 1;
                case "2" -> 2;
                case "3" -> 3;
                default -> 0;
            };
            if (significance > 0 && significance < minimum) continue;
            String severity = switch (significance) {
                case 1 -> "minor";
                case 2 -> "moderate";
                case 3 -> "major";
                default -> "unknown";
            };
            String text = drugName(interaction.affectingdrug, interaction.affectingatc) + " / "
                    + drugName(interaction.affecteddrug, interaction.affectedatc)
                    + " (" + severity + ")" + (interaction.comment == null ? "" : ": " + interaction.comment);
            for (var prescription : stash) {
                String code = prescription.getAtcCode();
                if (matches(code, interaction.affectingatc) || matches(code, interaction.affectedatc)) {
                    warnings.add(new Warning(String.valueOf(prescription.getRandomId()), severity, text));
                }
            }
        }
        return warnings;
    }

    private static boolean matches(String code, String interactionCode) {
        return usableCode(code) && usableCode(interactionCode) && code.startsWith(interactionCode);
    }

    private static String drugName(String name, String atc) {
        return name == null || name.isBlank() ? atc : name;
    }
}
