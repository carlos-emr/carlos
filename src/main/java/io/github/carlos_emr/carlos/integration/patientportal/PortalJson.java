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
package io.github.carlos_emr.carlos.integration.patientportal;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * Field readers shared by the portal response types.
 *
 * <p>Every reader distinguishes "absent" and "JSON null" from a usable value and yields {@code null}
 * for both. Jackson's own convenience accessors coerce a missing field to {@code ""} or {@code 0},
 * which would turn a portal contract change into silently wrong data rather than an obvious one.
 *
 * @since 2026-08-19
 */
final class PortalJson {

    private PortalJson() {}

    static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    static Long optionalLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }

    static Integer optionalInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    static boolean bool(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() && value.asBoolean();
    }

    /**
     * Parses a portal timestamp.
     *
     * <p>The portal emits ISO-8601 with an explicit offset — {@code Z} in practice, though pydantic
     * renders a configured offset as {@code +00:00}. {@link OffsetDateTime} accepts both forms;
     * {@link Instant#parse} would reject the second.
     */
    static Instant timestamp(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : OffsetDateTime.parse(value).toInstant();
    }
}
