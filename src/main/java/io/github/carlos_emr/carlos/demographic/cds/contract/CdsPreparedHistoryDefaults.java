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

/** Transient optional history defaults, applied only after the original payload seal is verified. */
public final class CdsPreparedHistoryDefaults {
    private CdsPreparedHistoryDefaults() {}

    public static ObjectNode apply(JsonNode value) {
        if (value == null
                || !value.isObject()
                || value.has("archived") && !value.get("archived").isBoolean())
            throw new IllegalArgumentException("CDS_PREPARED_HISTORY_DEFAULTS_INVALID");
        ObjectNode result = ((ObjectNode) value).deepCopy();
        if (!result.has("archived")) result.put("archived", false);
        return result;
    }
}
