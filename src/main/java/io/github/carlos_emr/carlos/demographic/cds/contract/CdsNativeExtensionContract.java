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

/** Vendor residual fields with explicit semantics shared by the two transfer directions. */
public final class CdsNativeExtensionContract {
    private CdsNativeExtensionContract() { }
    /** The pinned allergy category has no native record-active/archive field. */
    public static final String ALLERGY_ARCHIVED = "CARLOS.Allergy.Archived.v1";
    /** Exact native age text; this extension does not invent a unit or a birth date. */
    public static final String ALLERGY_AGE_OF_ONSET = "CARLOS.Allergy.AgeOfOnset.v1";
    public static final String ALLERGY_ONSET_OF_REACTION = "CARLOS.Allergy.OnsetOfReaction.v1";
    public static final String BOOLEAN_TYPE = "boolean";
    public static final String STRING_TYPE = "string";
    /** Labels match the native allergy vocabulary; no other code means Unknown. */
    public static final java.util.Map<String, String> ALLERGY_ONSET_VALUES = java.util.Map.of(
            "0", "Unknown", "1", "Immediate", "2", "Gradual", "3", "Slow");
}
