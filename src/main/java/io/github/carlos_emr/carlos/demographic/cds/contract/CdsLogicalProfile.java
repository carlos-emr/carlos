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

/** Logical profile identity is explicit even when profiles use identical wire schema bytes. */
public enum CdsLogicalProfile {
    ONTARIO_MD_5_2("omd-5.2"),
    PCON_2024_02("pcon-2024-02");

    private final String identifier;

    CdsLogicalProfile(String identifier) { this.identifier = identifier; }

    public String identifier() { return identifier; }

    /** Recognition establishes identity only, not implemented export support or conformance. */
    public static CdsLogicalProfile fromIdentifier(String identifier) {
        for (CdsLogicalProfile profile : values()) {
            if (profile.identifier.equals(identifier)) return profile;
        }
        throw new IllegalArgumentException("CDS_LOGICAL_PROFILE_INVALID");
    }
}
