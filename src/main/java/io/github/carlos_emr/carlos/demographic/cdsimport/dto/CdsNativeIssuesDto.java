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

import java.util.Map;

/** Prepared restoration of note-free system markers; catalogue identities belong to the receiver. */
public record CdsNativeIssuesDto(Marker marker) {

    public record Marker(
            String code, Map<String, String> catalogue, long issueId,
            boolean acute, boolean certain, boolean major, boolean resolved,
            String type, Integer programId, String updated) {
        public Marker {
            catalogue = java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(catalogue));
            if (code == null || code.isEmpty() || issueId < 0 || type == null || updated == null)
                throw new IllegalArgumentException("CDS_NATIVE_ISSUE_MARKER_INVALID");
        }

        public Marker target(long id, Integer program, String time) {
            return new Marker(code, catalogue, id, acute, certain, major, resolved, type, program, time);
        }

        @Override public String toString() { return "CdsNativeIssuesDto.Marker[protected]"; }
    }

    @Override public String toString() { return "CdsNativeIssuesDto[protected]"; }
}
