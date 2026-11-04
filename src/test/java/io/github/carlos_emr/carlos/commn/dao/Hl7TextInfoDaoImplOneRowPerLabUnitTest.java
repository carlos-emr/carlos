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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.SystemPreferences;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every query shape of {@link Hl7TextInfoDaoImpl#findLabAndDocsViaMagic} groups by lab, whatever the
 * clinic's inbox date search type (#5016).
 *
 * <p>A lab routed to two providers has two routing rows, and an Any provider search listed it once
 * per row. {@code Hl7TextInfoDaoIntegrationTest.LabRoutedToTwoProviders} runs that against H2 for
 * the shapes H2 can execute. The provider-wide list of labs and documents and both unmatched lists
 * also order by the routing row's id, which H2 refuses once a lab's group holds two different ids
 * ("must be in the GROUP BY list"); MariaDB takes any one of them, as the provider-wide list has
 * always done. Those shapes are pinned here on the statement itself, and against MariaDB by
 * {@code scripts/inboxhub-health-number-ack-today-playwright-checks.js}.</p>
 */
@Tag("unit")
@Tag("dao")
@Tag("lab")
@DisplayName("Hl7TextInfoDaoImpl lists each lab once in every Inbox query shape")
class Hl7TextInfoDaoImplOneRowPerLabUnitTest extends CarlosUnitTestBase {

    private static final Pattern GROUPED_BEFORE_OUTER_ORDER = Pattern.compile("GROUP BY info\\.lab_no\\s+ORDER BY");

    @ParameterizedTest(name = "{0}, mixLabsAndDocs={1}, {2}")
    @CsvSource({"provider,true,serviceObservation", "provider,false,serviceObservation",
            "patient,true,serviceObservation", "patient,false,serviceObservation",
            "nameSearch,true,serviceObservation", "nameSearch,false,serviceObservation",
            "unmatched,true,serviceObservation", "unmatched,false,serviceObservation",
            "provider,true,receivedCreated", "provider,false,receivedCreated",
            "patient,true,receivedCreated", "patient,false,receivedCreated",
            "nameSearch,true,receivedCreated", "nameSearch,false,receivedCreated",
            "unmatched,true,receivedCreated", "unmatched,false,receivedCreated"})
    @DisplayName("should group the Any provider query by lab before its outer ordering")
    void shouldGroupByLab_inEveryQueryShape(String shape, boolean mixLabsAndDocs, String dateSearchType) {
        SystemPreferencesDao preferences = createAndRegisterMock(SystemPreferencesDao.class);
        when(preferences.findPreferenceByName(any(SystemPreferences.LAB_DISPLAY_PREFERENCE_KEYS.class)))
                .thenReturn("receivedCreated".equals(dateSearchType)
                        ? new SystemPreferences("inboxDateSearchType", dateSearchType) : null);
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        Hl7TextInfoDaoImpl dao = new Hl7TextInfoDaoImpl();
        injectDependency(dao, "entityManager", entityManager);
        String demographicNo = "patient".equals(shape) ? "101" : "unmatched".equals(shape) ? "0" : null;
        String lastName = "nameSearch".equals(shape) ? "Fixture" : "";

        dao.findLabAndDocsViaMagic("", demographicNo, "", lastName, "", "", false, 0, 100,
                mixLabsAndDocs, null, false, !lastName.isEmpty(), null, null, null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sql.capture());
        assertThat(sql.getValue()).containsPattern(GROUPED_BEFORE_OUTER_ORDER);
        assertThat(sql.getValue().split("GROUP BY info\\.lab_no", -1)).as("grouped exactly once").hasSize(2);
    }
}
