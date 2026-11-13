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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;

/**
 * Transient medication defaults, applied only after the original payload seal is verified.
 *
 * <p>Preparations sealed before duration units were preserved (release 2026.08 before #3958)
 * hold the initial duration as {@code source.durationDays}: a whole number of days, which the
 * persister wrote with {@code durunit='D'}. That meaning is exactly {@code duration} with
 * {@code durationUnit "D"}. The stored bytes and seal are never rewritten; only the decoded tree
 * is translated. A payload naming both shapes, a unit without an amount (or the reverse), or a
 * unit outside D/W/M/Y is refused rather than guessed.
 */
public final class CdsPreparedMedicationDefaults {
    private static final Set<String> UNITS = Set.of("D", "W", "M", "Y");

    private CdsPreparedMedicationDefaults() {}

    public static ObjectNode apply(JsonNode value) {
        ObjectNode result;
        try {
            result = CdsPreparedHistoryDefaults.apply(value);
        } catch (IllegalArgumentException invalid) {
            throw invalid();
        }
        JsonNode source = result.get("source");
        if (source == null || !source.isObject()) throw invalid();
        ObjectNode prescription = (ObjectNode) source;
        if (prescription.has("durationDays")) {
            if (prescription.has("duration") || prescription.has("durationUnit")) throw invalid();
            JsonNode days = prescription.remove("durationDays");
            if (days.isNull()) {
                prescription.putNull("duration");
                prescription.putNull("durationUnit");
            } else if (days.isTextual()) {
                prescription.put("duration", days.textValue());
                prescription.put("durationUnit", "D");
            } else throw invalid();
        }
        JsonNode amount = prescription.get("duration"), unit = prescription.get("durationUnit");
        if (amount == null || unit == null) throw invalid();
        if (amount.isNull() != unit.isNull()) throw invalid();
        if (!amount.isNull()
                && (!amount.isTextual()
                        || !amount.textValue().matches("[0-9]+")
                        || !unit.isTextual()
                        || !UNITS.contains(unit.textValue()))) throw invalid();
        return result;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("CDS_PREPARED_MEDICATION_DEFAULTS_INVALID");
    }
}
