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
package io.github.carlos_emr.carlos.demographic.cdsexport.dto;

import java.util.List;
import java.util.Set;

/** Exact external personal-contact wire values; source association/history and retained reconciliation are separate evidence. */
public record CdsExportPersonalContactDto(String sourceRecordKey, String firstName, String lastName, List<Purpose> purposes,
        List<CdsExportDemographicMappingDto.Phone> phones, String email, String note) {
    public static final Set<String> PURPOSE_CODES = Set.of("EC", "NK", "AS", "CG", "PA", "IN", "GT", "SDM");
    public record Purpose(boolean coded, String value) {
        public Purpose {
            if (value == null || value.isEmpty() || !text(value, 50) || coded && !PURPOSE_CODES.contains(value))
                throw new IllegalArgumentException("CDS_EXPORT_CONTACT_PURPOSE_INVALID");
        }
        @Override public String toString() { return "ContactPurpose[protected]"; }
    }
    public CdsExportPersonalContactDto {
        purposes = List.copyOf(purposes); phones = List.copyOf(phones);
        if (sourceRecordKey == null || !sourceRecordKey.matches("[0-9]{20}") || firstName == null && lastName == null
                || firstName != null && !text(firstName, 50) || lastName != null && !text(lastName, 50)
                || purposes.size() > 8192 || phones.size() > 3 || phones.stream().map(CdsExportDemographicMappingDto.Phone::type).distinct().count() != phones.size()
                || email != null && (email.codePointCount(0, email.length()) > 50 || !email.matches("([.a-zA-Z0-9_-])+@([a-zA-Z0-9_-])+(([a-zA-Z0-9_-])*\\.([a-zA-Z0-9_-])+)+"))
                || note != null && !text(note, 200)) throw new IllegalArgumentException("CDS_EXPORT_CONTACT_VALUES_INVALID");
    }
    private static boolean text(String value, int maximum) {
        return value.codePointCount(0, value.length()) <= maximum && value.codePoints().noneMatch(code -> code < 0x20 && code != '\t' && code != '\r' && code != '\n'
                || code >= 0xd800 && code <= 0xdfff || code == 0xfffe || code == 0xffff);
    }
    @Override public String toString() { return "CdsExportPersonalContactDto[protected]"; }
}
