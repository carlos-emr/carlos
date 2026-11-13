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
package io.github.carlos_emr.carlos.demographic.cds.contract;

/** Vendor residual fields with explicit semantics shared by the two transfer directions. */
public final class CdsNativeExtensionContract {
    private CdsNativeExtensionContract() {}

    /** The pinned allergy category has no native record-active/archive field. */
    public static final String ALLERGY_ARCHIVED = "CARLOS.Allergy.Archived.v1";

    /** Exact native age text; this extension does not invent a unit or a birth date. */
    public static final String ALLERGY_AGE_OF_ONSET = "CARLOS.Allergy.AgeOfOnset.v1";

    public static final String ALLERGY_ONSET_OF_REACTION = "CARLOS.Allergy.OnsetOfReaction.v1";

    /** An ATC class is not a DIN and cannot be written in the standard allergy Code. */
    public static final String ALLERGY_ATC = "CARLOS.Allergy.Atc.v1";

    /**
     * A native severity with no CDS 5.2 value. CDS has only NO/MI/MO/LT; CARLOS's "Severe" (3) is
     * not evidence of a life-threatening reaction, so the standard Severity is left absent and
     * the native label travels here instead of refusing the patient.
     */
    public static final String ALLERGY_SEVERITY = "CARLOS.Allergy.Severity.v1";

    /** Native severity codes carried only by {@link #ALLERGY_SEVERITY}. */
    public static final java.util.Map<String, String> ALLERGY_SEVERITY_VALUES =
            java.util.Map.of("3", "Severe");

    /** CPP categories have no standard record-archive field; this is independent of problem resolution. */
    public static final String HISTORY_ARCHIVED = "CARLOS.History.Archived.v1";

    /**
     * PersonalHistory's earlier raw narrative carrier, still read for files that carry it. Its
     * raw content is {@code xs:token}, so line breaks and repeated spaces did not survive a
     * schema-aware reader; the exporter now writes {@link #HISTORY_NARRATIVE} instead.
     */
    public static final String HISTORY_PERSONAL_NARRATIVE = "CARLOS.History.PersonalNarrative.v1";

    /**
     * The complete native narrative of a PersonalHistory item (which permits only residual
     * fields), or of a FamilyHistory, PastHealth, ProblemList or RiskFactors item too long for its
     * description element (250 characters, 120 for RiskFactor). For the latter the exporter writes
     * no description and the same text in the standard Notes element for any receiver; this
     * carrier lets a CARLOS importer restore the item's text exactly rather than a placeholder. A
     * description element, when present, always wins. The content is {@link #encodeNarrative}:
     * residual Content is {@code xs:token}, and a schema-aware reader (the CARLOS importer
     * included) collapses the line breaks and repeated spaces of raw text.
     */
    public static final String HISTORY_NARRATIVE = "CARLOS.History.Narrative.v1";

    private static final com.fasterxml.jackson.databind.ObjectMapper NARRATIVE_JSON =
            new com.fasterxml.jackson.databind.ObjectMapper()
                    .enable(
                            com.fasterxml.jackson.databind.DeserializationFeature
                                    .FAIL_ON_TRAILING_TOKENS);

    /**
     * One JSON string literal in which every XML whitespace character (space, tab, line feed,
     * carriage return) is a JSON escape, so token normalization cannot alter it.
     */
    public static String encodeNarrative(String text) {
        if (text == null) throw new IllegalArgumentException("CDS_HISTORY_NARRATIVE_ENCODING_INVALID");
        try {
            return NARRATIVE_JSON.writeValueAsString(text).replace(" ", "\\u0020");
        } catch (com.fasterxml.jackson.core.JsonProcessingException impossible) {
            throw new IllegalStateException("CDS_HISTORY_NARRATIVE_ENCODING_INVALID", impossible);
        }
    }

    /** Exact inverse of {@link #encodeNarrative}; any other shape is refused, never guessed. */
    public static String decodeNarrative(String encoded) {
        if (encoded == null
                || encoded.isEmpty()
                || encoded.chars().anyMatch(c -> c == ' ' || c == '\t' || c == '\r' || c == '\n'))
            throw new IllegalArgumentException("CDS_HISTORY_NARRATIVE_ENCODING_INVALID");
        try {
            var node = NARRATIVE_JSON.readTree(encoded);
            if (node == null || !node.isTextual())
                throw new IllegalArgumentException("CDS_HISTORY_NARRATIVE_ENCODING_INVALID");
            return node.textValue();
        } catch (com.fasterxml.jackson.core.JsonProcessingException malformed) {
            throw new IllegalArgumentException("CDS_HISTORY_NARRATIVE_ENCODING_INVALID");
        }
    }

    /** Complete native prescription context; unqualified codes remain explicitly unqualified. */
    public static final String MEDICATION_SOURCE_CONTEXT = "CARLOS.Medication.Source.v1";

    /**
     * Exact native archive state. The exported PrescriptionStatus of an archived row is derived
     * from its archive reason (Obsolete, Nullified or Aborted), never its stale copied status.
     */
    public static final String MEDICATION_ARCHIVED = "CARLOS.Medication.Archived.v1";

    /**
     * Every indication (reason, {@code drugReason}) the chart shows for a prescription, in id
     * order: each row's coding system, code, comments, primary and archived flags, archive
     * reason, recording provider number and coded date, with SQL NULL and empty text distinct
     * and no literal XML whitespace. The standard ProblemCode, which names no coding system and
     * holds ten characters, carries at most one ICD-9 code; this carrier keeps them all. An
     * importer treats it as opaque source; it does not establish a native reason.
     */
    public static final String MEDICATION_REASONS = "CARLOS.Medication.Reasons.v1";

    /**
     * Complete native prevention rows (the parent, every extension and every date-precision row)
     * behind a PastHealth entry exported from a CARLOS screening, or behind an Immunizations entry
     * exported from a vaccine recorded in CARLOS. SQL NULL and empty text stay distinct, and the
     * JSON carries no literal XML whitespace so token normalization cannot alter it.
     */
    public static final String PREVENTION_SOURCE_CONTEXT = "CARLOS.Prevention.Source.v1";

    public static final String BOOLEAN_TYPE = "boolean";
    public static final String STRING_TYPE = "string";

    /** Labels match the native allergy vocabulary; no other code means Unknown. */
    public static final java.util.Map<String, String> ALLERGY_ONSET_VALUES =
            java.util.Map.of("0", "Unknown", "1", "Immediate", "2", "Gradual", "3", "Slow");

    /** WHO ATC hierarchy: anatomical group through chemical substance; one exact code. */
    public static boolean validAllergyAtc(String value) {
        return value != null
                && value.matches("[ABCDGHJLMNPRSV](?:[0-9]{2}(?:[A-Z](?:[A-Z](?:[0-9]{2})?)?)?)?");
    }
}
