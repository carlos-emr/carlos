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
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.webserv.rest.conversion.MeasurementTypeConverter;
import io.github.carlos_emr.carlos.webserv.rest.to.model.MeasurementTypeTo1;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs {@link MeasurementManager#getMeasurementTypes} over the real test database: seeded types
 * come back ordered by display name and {@link MeasurementTypeConverter} maps them to the four
 * public fields (issue #5452). The integration context scans only the DAO package, so the
 * manager is built by hand over the autowired DAO; the other collaborators are not used by this
 * read. The test transaction rolls back, and the DAO's manual {@code measurementTypes} cache is
 * cleared before and after so a snapshot cached by another test cannot hide the seeded rows.
 */
@DisplayName("MeasurementManager.getMeasurementTypes integration")
@Tag("integration")
@Tag("manager")
@Tag("measurement")
@Transactional
class MeasurementManagerTypesIntegrationTest extends CarlosTestBase {

    private static final String CACHE_NAME = "measurementTypes";
    private static final String PREFIX = "FAKE-5452-";

    @Autowired
    private MeasurementTypeDao measurementTypeDao;

    @Autowired
    private CacheManager cacheManager;

    private MeasurementManagerImpl manager;

    @BeforeEach
    void setUpManager() {
        manager = new MeasurementManagerImpl();
        ReflectionTestUtils.setField(manager, "measurementTypeDao", measurementTypeDao);
        clearCache();
    }

    @AfterEach
    void clearCacheAfter() {
        clearCache();
    }

    @Test
    @DisplayName("should return seeded types ordered by display name and map the four public fields")
    void shouldReturnTypesOrderedByName_andConvertToPublicFields() throws Exception {
        // Persisted out of order so the ordering comes from the query, not insertion.
        persist("FAKE-C", PREFIX + "Charlie", "desc-c", "instr-c");
        persist("FAKE-A", PREFIX + "Alpha", "desc-a", "instr-a");
        persist("FAKE-B", PREFIX + "Bravo", "desc-b", "instr-b");
        measurementTypeDao.flush();

        List<MeasurementType> types = manager.getMeasurementTypes(null);
        List<MeasurementTypeTo1> mapped = new MeasurementTypeConverter().getAllAsTransferObjects(null, types);

        List<MeasurementTypeTo1> seeded = mapped.stream()
                .filter(t -> t.getTypeDisplayName() != null && t.getTypeDisplayName().startsWith(PREFIX))
                .toList();
        assertThat(seeded).extracting(MeasurementTypeTo1::getTypeDisplayName)
                .containsExactly(PREFIX + "Alpha", PREFIX + "Bravo", PREFIX + "Charlie");
        assertThat(seeded).extracting(MeasurementTypeTo1::getType)
                .containsExactly("FAKE-A", "FAKE-B", "FAKE-C");
        assertThat(seeded).extracting(MeasurementTypeTo1::getTypeDescription)
                .containsExactly("desc-a", "desc-b", "desc-c");
        assertThat(seeded).extracting(MeasurementTypeTo1::getMeasuringInstruction)
                .containsExactly("instr-a", "instr-b", "instr-c");
    }

    private void persist(String type, String displayName, String description, String instruction) {
        MeasurementType mt = new MeasurementType();
        mt.setType(type);
        mt.setTypeDisplayName(displayName);
        mt.setTypeDescription(description);
        mt.setMeasuringInstruction(instruction);
        measurementTypeDao.persist(mt);
    }

    private void clearCache() {
        org.springframework.cache.Cache cache = cacheManager.getCache(CACHE_NAME);
        assertThat(cache).as("cache %s should be present in the test context", CACHE_NAME).isNotNull();
        cache.clear();
    }
}
