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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import java.util.Set;

/** Exact pinned-wire immunization vocabulary; native configuration/CVC names do not extend this enumeration. */
final class CdsExportImmunizationVocabulary {
    private CdsExportImmunizationVocabulary(){}
    static final Set<String> TYPES=Set.of(
            "BCG",
            "Chol-O",
            "Chol-Ecol-O",
            "CholEcol",
            "DTaP",
            "DTaP-IPV",
            "DTaP-IPV-Hib",
            "DTaP-IPV-Hib-HB",
            "DTaP-IPV-HB",
            "DTaP-Hib",
            "DT-IPV",
            "HA",
            "HAHB",
            "HA-Typh-I",
            "HB",
            "HBTmf",
            "Hib",
            "Inf",
            "IPV",
            "JE",
            "Men",
            "Men B",
            "Men-C",
            "Men-P-AC",
            "Men-P-ACWY",
            "MMR",
            "MR",
            "Pneu",
            "Pneu-C-7",
            "Pneu-P-23",
            "Rab",
            "T",
            "Td",
            "Tdap",
            "TdapIPV",
            "Td-IPV",
            "TBE",
            "Typh",
            "Typh (HA)",
            "Typh-I",
            "Typh-O",
            "Var",
            "YF",
            "HPV",
            "MMR-Var",
            "ROT",
            "Zos",
            "BAtx",
            "CMVIg",
            "DAtx",
            "HBIg",
            "Ig",
            "RabIg",
            "RSVAb",
            "RSVIg",
            "TIg",
            "VarIg",
            "VIG");
}
