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
package io.github.carlos_emr.carlos.prevention;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionExtDao;
import io.github.carlos_emr.carlos.commn.model.PreventionExt;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * How the RH form's injection panel finds the preventions recorded against a workflow (issue #4992, findings 291 and
 * 293). {@link PreventionData#getPreventionDataFromExt(String, String)} passed the prevention entity's reflection-based
 * {@code toString()} to {@link PreventionData#getPreventionById(String)}, which expects a number: every injection was
 * dropped from the panel, and the failed parse wrote the patient's prevention, lot, dose and reason to the error log.
 *
 * @since 2026-10-22
 */
@DisplayName("PreventionData lookup by extension")
@Tag("unit")
@Tag("prevention")
class PreventionDataExtLookupUnitTest extends CarlosUnitTestBase {

    private static final String SYNTHETIC_CLINICAL_TEXT = "FAKE-LOT-4992 antepartum reason";

    private PreventionDao preventionDao;
    private PreventionExtDao preventionExtDao;
    private Object originalPreventionDao;
    private Object originalPreventionExtDao;

    @BeforeEach
    void replaceDaos() {
        preventionDao = mock(PreventionDao.class);
        preventionExtDao = mock(PreventionExtDao.class);
        registerMock(PreventionDao.class, preventionDao);
        registerMock(PreventionExtDao.class, preventionExtDao);
        // PreventionData resolves its DAOs in its static initializer, which may run in this test.
        registerMock(PartialDateDao.class, mock(PartialDateDao.class));
        originalPreventionDao = ReflectionTestUtils.getField(PreventionData.class, "preventionDao");
        originalPreventionExtDao = ReflectionTestUtils.getField(PreventionData.class, "preventionExtDao");
        ReflectionTestUtils.setField(PreventionData.class, "preventionDao", preventionDao);
        ReflectionTestUtils.setField(PreventionData.class, "preventionExtDao", preventionExtDao);
    }

    @AfterEach
    void restoreDaos() {
        ReflectionTestUtils.setField(PreventionData.class, "preventionDao", originalPreventionDao);
        ReflectionTestUtils.setField(PreventionData.class, "preventionExtDao", originalPreventionExtDao);
    }

    private static PreventionExt ext(Integer preventionId) {
        PreventionExt ext = new PreventionExt();
        ext.setPreventionId(preventionId);
        ext.setKeyval("workflowId");
        ext.setVal("7");
        return ext;
    }

    private static Map<String, Object> prevention(String id, String deleted) {
        Map<String, Object> hash = new HashMap<>();
        hash.put("id", id);
        hash.put("deleted", deleted);
        return hash;
    }

    @Test
    @DisplayName("should look each linked prevention up by its id and keep the live ones")
    void shouldLookUpEachPreventionById_whenWorkflowHasInjections() {
        when(preventionExtDao.findByKeyAndValue("workflowId", "7"))
                .thenReturn(List.of(ext(5), ext(null), ext(6), ext(8)));

        // Only the method under test runs for real: with CALLS_REAL_METHODS, stubbing getPreventionById would run it.
        try (MockedStatic<PreventionData> data = mockStatic(PreventionData.class)) {
            data.when(() -> PreventionData.getPreventionDataFromExt("workflowId", "7")).thenCallRealMethod();
            data.when(() -> PreventionData.getPreventionById("5")).thenReturn(prevention("5", "0"));
            data.when(() -> PreventionData.getPreventionById("6")).thenReturn(prevention("6", "1"));
            // An extension can outlive its prevention: the lookup answers null and the rest are still listed.
            data.when(() -> PreventionData.getPreventionById("8")).thenReturn(null);

            List<Map<String, Object>> found = PreventionData.getPreventionDataFromExt("workflowId", "7");

            assertThat(found).extracting(hash -> hash.get("id")).containsExactly("5");
            data.verify(() -> PreventionData.getPreventionById("5"));
            data.verify(() -> PreventionData.getPreventionById("6"));
            data.verify(() -> PreventionData.getPreventionById("8"));
            data.verify(() -> PreventionData.getPreventionById(anyString()), Mockito.times(3));
        }
        // The entity is never loaded only to be printed into the id.
        verifyNoInteractions(preventionDao);
    }

    @Test
    @DisplayName("should refuse a value that is not an id without logging it")
    void shouldRefuseNonNumericId_withoutLoggingTheValue() {
        try (LogCapture logs = LogCapture.forLogger(PreventionData.class)) {
            assertThat(PreventionData.getPreventionById("Prevention[lot=" + SYNTHETIC_CLINICAL_TEXT + "]")).isNull();
            assertThat(PreventionData.getPreventionById(null)).isNull();

            assertThat(logs.messages()).isNotEmpty().noneMatch(message -> message.contains(SYNTHETIC_CLINICAL_TEXT));
            assertThat(logs.events()).noneMatch(event -> event.getThrown() != null);
        }
        verifyNoInteractions(preventionDao);
    }
}
