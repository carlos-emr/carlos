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
import java.util.Map;

/** One external source contact with bounded native slots and separately preserved source purposes. */
public record CdsContactDto(String firstName, String lastName, String email, String note,
        Map<String, Phone> phones, List<String> roles, Boolean emergencyContact,
        Boolean substituteDecisionMaker, List<CdsClinicalDiagnosticDto> diagnostics) {
    public record Phone(String number, String extension) { }
    public CdsContactDto {
        if (phones.size() > 3 || roles.isEmpty() || roles.size() > 1 || diagnostics.size() > 16) {
            throw new IllegalArgumentException("Invalid bounded contact representation");
        }
        // Deterministic iteration is essential when a prepared DTO is retried in another JVM.
        phones = java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(phones));
        roles = List.copyOf(roles); diagnostics = List.copyOf(diagnostics);
    }
}
