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

/** Optional extension defaults applied only to a transient tree after the original preparation seal is verified. */
public final class CdsPreparedAllergyDefaults {
    private CdsPreparedAllergyDefaults() {}

    public static ObjectNode apply(JsonNode value) {
        if (value == null
                || !value.isObject()
                || value.has("archived") && !value.get("archived").isBoolean()) invalid();
        ObjectNode result = ((ObjectNode) value).deepCopy();
        if (!result.has("archived")) result.put("archived", false);
        if (!result.has("atcResidual")) result.put("atcResidual", false);
        else if (!result.get("atcResidual").isBoolean()) invalid();
        for (String optional : java.util.List.of("ageOfOnset", "onsetOfReaction")) {
            if (!result.has(optional)) result.putNull(optional);
            else if (!result.get(optional).isNull() && !result.get(optional).isTextual()) invalid();
        }
        return result;
    }

    private static void invalid() {
        throw new IllegalArgumentException("CDS_PREPARED_ALLERGY_DEFAULTS_INVALID");
    }
}
