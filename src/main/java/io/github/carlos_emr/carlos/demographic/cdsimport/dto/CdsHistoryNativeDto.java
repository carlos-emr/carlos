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
package io.github.carlos_emr.carlos.demographic.cdsimport.dto;

import java.util.List;

/** Bounded native clinical note projection; oversized source text is never clipped into a false statement. */
public record CdsHistoryNativeDto(
        String content,
        String issueCode,
        Long observationMillis,
        Long createdMillis,
        String providerNo,
        CdsTemporalDto observationDate,
        String observationField,
        List<CdsClinicalDiagnosticDto> diagnostics) {
    public CdsHistoryNativeDto {
        diagnostics = List.copyOf(diagnostics);
        if (content == null
                || content.codePointCount(0, content.length()) > 393216
                || providerNo != null && providerNo.length() > 6)
            throw new IllegalArgumentException("HISTORY_PREPARED_TEXT_INVALID");
    }

    @Override
    public String toString() {
        return "CdsHistoryNativeDto[protected]";
    }
}
