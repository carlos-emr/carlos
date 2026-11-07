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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsHealthCardAuthority;

/** Exact guide-constrained preferred pharmacy values. Source disposition and historical reconciliation remain separate evidence. */
public record CdsExportPreferredPharmacyDto(String name, Address address, CdsExportDemographicMappingDto.Phone phone,
        CdsExportDemographicMappingDto.Phone fax, String email) {
    public record Address(String line1, String city, String subdivision, String postalCode) {
        public Address {
            if (line1 == null && city == null && subdivision == null && postalCode == null
                    || line1 != null && (!token(line1, 50) || line1.isEmpty()) || city != null && !token(city, 80)
                    || subdivision != null && !(subdivision.startsWith("CA-") && CdsHealthCardAuthority.CANADIAN.contains(subdivision.substring(3))
                            || CdsHealthCardAuthority.PINNED_US.contains(subdivision))
                    || postalCode != null && (subdivision == null || !token(postalCode, 10) || !postalCode.matches(subdivision != null && subdivision.startsWith("US-") ? "[0-9-]*" : "[a-zA-Z0-9-]*")))
                throw new IllegalArgumentException("CDS_EXPORT_PHARMACY_ADDRESS_INVALID");
        }
        @Override public String toString() { return "PharmacyAddress[protected]"; }
    }
    public CdsExportPreferredPharmacyDto {
        if (name == null || name.isEmpty() || !text(name, 50)
                || phone != null && !"W".equals(phone.type()) || fax != null && !"W".equals(fax.type())
                || email != null && (!token(email, 50) || !email.matches("([.a-zA-Z0-9_-])+@([a-zA-Z0-9_-])+(([a-zA-Z0-9_-])*\\.([a-zA-Z0-9_-])+)+")))
            throw new IllegalArgumentException("CDS_EXPORT_PHARMACY_VALUES_INVALID");
    }
    private static boolean text(String value, int maximum) {
        return value.codePointCount(0, value.length()) <= maximum && value.codePoints().noneMatch(code -> code < 0x20 && code != '\t' && code != '\r' && code != '\n'
                || code >= 0xd800 && code <= 0xdfff || code == 0xfffe || code == 0xffff);
    }
    private static boolean token(String value, int maximum) {
        return text(value, maximum) && !value.startsWith(" ") && !value.endsWith(" ") && !value.contains("  ")
                && value.codePoints().noneMatch(code -> code == '\t' || code == '\r' || code == '\n');
    }
    @Override public String toString() { return "CdsExportPreferredPharmacyDto[protected]"; }
}
