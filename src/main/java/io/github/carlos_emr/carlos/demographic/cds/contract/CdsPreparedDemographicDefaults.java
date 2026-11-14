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
 * Transient demographic defaults, applied only after the original payload seal is verified.
 *
 * <p>Preparations sealed before a CARLOS sender's native demographic record was read back omit
 * {@code carried} and {@code properties}; they mean "nothing restored" (an empty object and an
 * empty list) there. The stored bytes and seal are never rewritten; only the decoded tree is
 * translated. A present value of another shape is refused rather than guessed.
 */
public final class CdsPreparedDemographicDefaults {
    private CdsPreparedDemographicDefaults() {}

    public static ObjectNode apply(JsonNode value) {
        if (value == null || !value.isObject())
            throw new IllegalArgumentException("CDS_PREPARED_DEMOGRAPHIC_DEFAULTS_INVALID");
        ObjectNode result = ((ObjectNode) value).deepCopy();
        if (!result.has("carried")) result.putObject("carried");
        else if (!result.get("carried").isObject())
            throw new IllegalArgumentException("CDS_PREPARED_DEMOGRAPHIC_DEFAULTS_INVALID");
        if (!result.has("properties")) result.putArray("properties");
        else if (!result.get("properties").isArray())
            throw new IllegalArgumentException("CDS_PREPARED_DEMOGRAPHIC_DEFAULTS_INVALID");
        return result;
    }
}
