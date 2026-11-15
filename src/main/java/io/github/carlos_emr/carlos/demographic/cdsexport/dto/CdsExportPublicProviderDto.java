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

/** Public clinical provider values from a qualified captured lookup, never a provider entity or a permission grant. */
public record CdsExportPublicProviderDto(String firstName, String lastName, String ohipNumber, String cpsoNumber) {
    public CdsExportPublicProviderDto(String firstName, String lastName, String ohipNumber) { this(firstName, lastName, ohipNumber, null); }
    public CdsExportPublicProviderDto {
        if (firstName == null || lastName == null || firstName.codePointCount(0, firstName.length()) > 50
                || lastName.codePointCount(0, lastName.length()) > 50 || ohipNumber != null && (ohipNumber.isEmpty()
                || ohipNumber.codePointCount(0, ohipNumber.length()) > 6 || ohipNumber.startsWith(" ") || ohipNumber.endsWith(" ")
                || ohipNumber.contains("  ") || ohipNumber.codePoints().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("CDS_EXPORT_PROVIDER_VALUES_REQUIRES_REVIEW");
        }
        if (cpsoNumber != null && (cpsoNumber.isEmpty() || cpsoNumber.codePointCount(0, cpsoNumber.length()) > 6
                || cpsoNumber.startsWith(" ") || cpsoNumber.endsWith(" ") || cpsoNumber.contains("  ")
                || cpsoNumber.codePoints().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("CDS_EXPORT_PROVIDER_VALUES_REQUIRES_REVIEW");
        }
    }
    @Override public String toString() { return "CdsExportPublicProviderDto[protected]"; }
}
