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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsProviderMatchDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsProviderMatchDto.Outcome;
import java.util.List;
import org.springframework.stereotype.Service;

/** Matches uniquely issued Ontario identifiers; optional names never overrule contradictory identifiers. */
@Service
public class CdsProviderResolver {
    private final ProviderDao providers;
    public CdsProviderResolver(ProviderDao providers) { this.providers = providers; }

    public CdsProviderMatchDto resolve(String ohip, String cpso, String firstName,
            String lastName, boolean allowExactName) {
        boolean hasOhip = present(ohip), hasCpso = present(cpso);
        if (hasOhip || hasCpso) {
            List<String> ohipMatches = hasOhip ? providers.findCdsProviderNosByOhip(ohip) : List.of();
            List<String> cpsoMatches = hasCpso ? providers.findCdsProviderNosByCpso(cpso) : List.of();
            if (ohipMatches.size() > 1 || cpsoMatches.size() > 1) return unresolved(Outcome.AMBIGUOUS_IDENTIFIER);
            if (hasOhip && ohipMatches.isEmpty() || hasCpso && cpsoMatches.isEmpty()) {
                return unresolved(Outcome.UNMATCHED_IDENTIFIER);
            }
            if (hasOhip && hasCpso && !ohipMatches.getFirst().equals(cpsoMatches.getFirst())) {
                return unresolved(Outcome.CONFLICTING_IDENTIFIERS);
            }
            return new CdsProviderMatchDto(hasOhip ? ohipMatches.getFirst() : cpsoMatches.getFirst(), Outcome.MATCHED_IDENTIFIERS);
        }
        if (!present(firstName) || !present(lastName)) return unresolved(Outcome.SOURCE_IDENTITY_ABSENT);
        if (!allowExactName) return unresolved(Outcome.NAME_MATCH_DISABLED);
        List<String> matches = providers.findCdsProviderNosByExactName(firstName, lastName);
        if (matches.isEmpty()) return unresolved(Outcome.UNMATCHED_NAME);
        if (matches.size() > 1) return unresolved(Outcome.AMBIGUOUS_NAME);
        return new CdsProviderMatchDto(matches.getFirst(), Outcome.MATCHED_NAME);
    }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static CdsProviderMatchDto unresolved(Outcome outcome) { return new CdsProviderMatchDto(null, outcome); }
}
