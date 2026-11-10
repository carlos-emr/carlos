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

/** Source-author lookup outcome; a match never grants prescribing, signing or import authority. */
public record CdsProviderMatchDto(String localProviderNo, Outcome outcome) {
    public enum Outcome { MATCHED_IDENTIFIERS, MATCHED_NAME, SOURCE_IDENTITY_ABSENT,
        UNMATCHED_IDENTIFIER, AMBIGUOUS_IDENTIFIER, CONFLICTING_IDENTIFIERS,
        NAME_MATCH_DISABLED, UNMATCHED_NAME, AMBIGUOUS_NAME }
    public CdsProviderMatchDto {
        if (outcome == null || ((outcome == Outcome.MATCHED_IDENTIFIERS || outcome == Outcome.MATCHED_NAME)
                != (localProviderNo != null && !localProviderNo.isBlank()))) {
            throw new IllegalArgumentException("Invalid provider-match representation");
        }
    }
    public boolean matched() { return localProviderNo != null; }
}
