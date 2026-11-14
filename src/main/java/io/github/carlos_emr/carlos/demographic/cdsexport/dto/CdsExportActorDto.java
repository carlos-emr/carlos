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

/** Identity is durable; permission is reconstructed from live account/delegation policy at each boundary. */
public record CdsExportActorDto(String operator, String providerNo, Integer securityNo, Integer facilityId) {
    public CdsExportActorDto {
        if (!isOperatorIdentifier(operator)
                || providerNo == null || !providerNo.matches("[A-Za-z0-9_-]{1,16}")
                || securityNo != null && (securityNo < 1 || !operator.equals("web-" + securityNo))
                || facilityId != null && facilityId < 1) {
            throw new IllegalArgumentException("CDS_EXPORT_ACTOR_INVALID");
        }
    }
    /** Durable keys distinguish Unix accounts from browser accounts even if their display identifiers match. */
    public String operatorKey() { return securityNo == null ? "unix:" + operator : "web:" + securityNo; }
    /** Identifier syntax only; root ticket/live delegation and account checks establish authority. */
    public static boolean isOperatorIdentifier(String value) {
        return value != null && value.matches("[A-Za-z_][A-Za-z0-9_.-]{0,63}\\$?");
    }
    @Override public String toString() { return "CdsExportActorDto[protected]"; }
}
