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
package io.github.carlos_emr.carlos.demographic.cdsimport;

import io.github.carlos_emr.carlos.commn.dao.CdsLegacyProjectionDao;
import io.github.carlos_emr.carlos.commn.model.CdsLegacyProjectionException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Missing migrated representations are corruption, never proof that an export can omit CDS data. */
@Tag("unit")
class CdsLegacyProjectionDaoUnitTest {
    @Test void shouldRefuseMissingSourceRepresentation_afterItsActualReleaseMigration() {
        var dao = dao(Set.of("1.0.59.8"));
        assertThrows(CdsLegacyProjectionException.class, () -> dao.hasSourceFields(42, Set.of()));
        assertThrows(CdsLegacyProjectionException.class, () -> dao.hasSourceFields(42, Set.of("CareElements")));
    }
    @Test void shouldRefuseMissingUnscopedRepresentation_afterItsActualReleaseMigration() {
        var dao = dao(Set.of("1.0.59.8"));
        assertThrows(CdsLegacyProjectionException.class, () -> dao.hasAnySourceFields(Set.of("CareElements")));
    }
    @Test void shouldRefuseMissingLaboratoryRepresentation_afterItsActualReleaseMigration() {
        var dao = dao(Set.of("1.0.59.10"));
        assertThrows(CdsLegacyProjectionException.class, () -> dao.hasTypedLaboratoryResults(42));
    }
    @Test void shouldKeepOptionalRepresentationInactive_beforeItsMigrationWasApplied() {
        var dao = dao(Set.of());
        assertFalse(dao.hasSourceFields(42, Set.of()));
        assertFalse(dao.hasAnySourceFields(Set.of("CareElements")));
        assertFalse(dao.hasTypedLaboratoryResults(42));
    }

    private static CdsLegacyProjectionDao dao(Set<String> installedVersions) {
        var manager = mock(EntityManager.class);
        when(manager.createNativeQuery(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            Query query = mock(Query.class); Map<String, Object> parameters = new HashMap<>();
            when(query.setParameter(anyString(), any())).thenAnswer(binding -> {
                parameters.put(binding.getArgument(0), binding.getArgument(1)); return query;
            });
            when(query.setMaxResults(anyInt())).thenReturn(query);
            when(query.getResultList()).thenAnswer(ignored -> {
                if (sql.contains("information_schema.TABLES")) {
                    return "flyway_schema_history".equals(parameters.get("table")) ? List.of("flyway_schema_history") : List.of();
                }
                return installedVersions.contains(parameters.get("version")) ? List.of(1) : List.of();
            });
            return query;
        });
        var dao = new CdsLegacyProjectionDao(); ReflectionTestUtils.setField(dao, "entityManager", manager); return dao;
    }
}
