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
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsProviderMatchDto.Outcome;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class CdsProviderResolverUnitTest {
    private final ProviderDao dao = mock(ProviderDao.class);
    private final CdsProviderResolver resolver = new CdsProviderResolver(dao);
    @Test void shouldRequireAgreement_betweenUniqueIssuedOhipAndCpso() {
        when(dao.findCdsProviderNosByOhip("001234")).thenReturn(List.of("T001"));
        when(dao.findCdsProviderNosByCpso("009901")).thenReturn(List.of("T001"));
        var result = resolver.resolve("001234", "009901", "Source", "Author", true);
        assertEquals("T001", result.localProviderNo());
        assertEquals(Outcome.MATCHED_IDENTIFIERS, result.outcome());
        verify(dao, never()).findCdsProviderNosByExactName(anyString(), anyString());
    }
    @Test void shouldNeverCrossMatchOrFallBackToNames_forIdentifiersFromDifferentNamespaces() {
        when(dao.findCdsProviderNosByOhip("001234")).thenReturn(List.of("T001"));
        when(dao.findCdsProviderNosByCpso("001234")).thenReturn(List.of("T002"));
        assertEquals(Outcome.CONFLICTING_IDENTIFIERS, resolver.resolve("001234", "001234", "Jane", "Smith", true).outcome());
        verify(dao, never()).findCdsProviderNosByExactName(anyString(), anyString());
    }
    @Test void shouldRequireReviewForDuplicateIdentifier_withoutChoosingFirst() {
        when(dao.findCdsProviderNosByOhip("001234")).thenReturn(List.of("T001", "T002"));
        var result = resolver.resolve("001234", null, "Jane", "Smith", true);
        assertEquals(Outcome.AMBIGUOUS_IDENTIFIER, result.outcome()); assertNull(result.localProviderNo());
    }
    @Test void shouldNotLaunderUnmatchedStrongIdentifier_byNameMatch() {
        when(dao.findCdsProviderNosByOhip("001234")).thenReturn(List.of());
        assertEquals(Outcome.UNMATCHED_IDENTIFIER, resolver.resolve("001234", null, "Jane", "Smith", true).outcome());
        verify(dao, never()).findCdsProviderNosByExactName(anyString(), anyString());
    }
    @Test void shouldRequireBothSuppliedIdentifiersToResolve_notJustOne() {
        when(dao.findCdsProviderNosByOhip("001234")).thenReturn(List.of("T001"));
        when(dao.findCdsProviderNosByCpso("009901")).thenReturn(List.of());
        assertFalse(resolver.resolve("001234", "009901", "Jane", "Smith", true).matched());
    }
    @Test void shouldRequireExistingExplicitOption_forExactNameMatching() {
        assertEquals(Outcome.NAME_MATCH_DISABLED, resolver.resolve(null, null, "Jane", "Smith", false).outcome());
        verifyNoInteractions(dao);
    }
    @Test void shouldBindExactUniqueName_withoutInventingSignatureOrAuthority() {
        when(dao.findCdsProviderNosByExactName("Jane", "Smith")).thenReturn(List.of("T001"));
        var result = resolver.resolve(null, null, "Jane", "Smith", true);
        assertEquals(Outcome.MATCHED_NAME, result.outcome()); assertTrue(result.matched());
    }
    @Test void shouldNeverUseImportActor_forAmbiguousOrMissingName() {
        when(dao.findCdsProviderNosByExactName("Jane", "Smith")).thenReturn(List.of("T001", "T002"));
        assertEquals(Outcome.AMBIGUOUS_NAME, resolver.resolve(null, null, "Jane", "Smith", true).outcome());
        assertEquals(Outcome.SOURCE_IDENTITY_ABSENT, resolver.resolve(null, null, "Jane", null, true).outcome());
        assertEquals(Outcome.UNMATCHED_NAME, resolver.resolve(null, null, "Other", "Source", true).outcome());
    }
}
