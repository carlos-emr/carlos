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

/**
 * Transient immunization defaults, applied only after the original payload seal is verified.
 *
 * <p>Preparations sealed before the CARLOS {@code Prevention.Source.v1} carrier was restored
 * (#6198) omit {@code providerName} and {@code reminderDate}; both mean "not restored" (null)
 * there. The stored bytes and seal are never rewritten; only the decoded tree is translated. A
 * present value that is not text or null is refused rather than guessed.
 */
public final class CdsPreparedImmunizationDefaults {
    private CdsPreparedImmunizationDefaults() {}

    public static ObjectNode apply(JsonNode value) {
        if (value == null || !value.isObject())
            throw new IllegalArgumentException("CDS_PREPARED_IMMUNIZATION_DEFAULTS_INVALID");
        ObjectNode result = ((ObjectNode) value).deepCopy();
        for (String optional : java.util.List.of("providerName", "reminderDate")) {
            if (!result.has(optional)) result.putNull(optional);
            else if (!result.get(optional).isNull() && !result.get(optional).isTextual())
                throw new IllegalArgumentException("CDS_PREPARED_IMMUNIZATION_DEFAULTS_INVALID");
        }
        return result;
    }
}
