/*
 * Copyright (c) 2026 CARLOS Contributors.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details. You should have received a copy of the GNU General
 * Public License along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * CARLOS EMR Project, https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.data;

import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.dao.ValidationsDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.MockedStatic;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
@Isolated
class MeasurementTypesSnapshotUnitTest {
    private Object original;
    private Field singleton;

    @BeforeEach
    void preserveSingleton() throws Exception {
        singleton = MeasurementTypes.class.getDeclaredField("measurementTypes");
        singleton.setAccessible(true);
        original = singleton.get(null);
        singleton.set(null, null);
    }

    @AfterEach
    void restoreSingleton() throws Exception { singleton.set(null, original); }

    private MeasurementType type(int id, String name) {
        MeasurementType result = new MeasurementType();
        org.springframework.test.util.ReflectionTestUtils.setField(result, "id", id);
        result.setType(name); result.setValidation("1");
        return result;
    }

    private MockedStatic<SpringUtils> beans(MeasurementTypeDao types) {
        MockedStatic<SpringUtils> spring = mockStatic(SpringUtils.class);
        spring.when(() -> SpringUtils.getBean(MeasurementTypeDao.class)).thenReturn(types);
        spring.when(() -> SpringUtils.getBean(ValidationsDao.class)).thenReturn(mock(ValidationsDao.class));
        return spring;
    }

    private MeasurementTypes initial() {
        MeasurementTypeDao dao = mock(MeasurementTypeDao.class);
        when(dao.findAll()).thenReturn(List.of(type(101, "OLD")));
        try (var ignored = beans(dao)) { return MeasurementTypes.getInstance(); }
    }

    @Test
    void shouldKeepTheCompletePreviousCatalogReadable_untilReloadCanPublishItsReplacement() throws Exception {
        MeasurementTypes catalog = initial();
        var old = catalog.getByType("OLD");
        assertThat(catalog.getByType(null)).isNull();
        assertThat(catalog.getById(null)).isNull();
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        MeasurementTypeDao replacement = mock(MeasurementTypeDao.class);
        when(replacement.findAll()).thenAnswer(call -> {
            loading.countDown();
            if (!resume.await(5, TimeUnit.SECONDS)) throw new AssertionError("Reload was not resumed");
            return List.of(type(202, "NEW"));
        });
        try (var worker = Executors.newSingleThreadExecutor()) {
            var reloaded = worker.submit(() -> { try (var ignored = beans(replacement)) { catalog.reInit(); } });
            try {
                assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(catalog.getByType("OLD")).isSameAs(old);
                assertThat(catalog.getById("101")).isSameAs(old);
                assertThat(catalog.getByType("NEW")).isNull();
            } finally { resume.countDown(); }
            reloaded.get(5, TimeUnit.SECONDS);
        }
        assertThat(catalog.getByType("OLD")).isNull();
        assertThat(catalog.getByType("NEW")).isNotNull().isSameAs(catalog.getById("202"));
    }

    @Test
    void shouldRetainLastKnownCatalog_whenReloadFails() {
        MeasurementTypes catalog = initial();
        var old = catalog.getByType("OLD");
        MeasurementTypeDao failed = mock(MeasurementTypeDao.class);
        when(failed.findAll()).thenThrow(new IllegalStateException("FAKE database unavailable"));
        try (var ignored = beans(failed)) { catalog.reInit(); }
        assertThat(catalog.getByType("OLD")).isSameAs(old);
        assertThat(catalog.getById("101")).isSameAs(old);
    }

    @Test
    void shouldRetainBothPreviousIndexes_whenValidationFailsAfterBuildingPartOfTheReplacement() {
        MeasurementTypes catalog = initial();
        var old = catalog.getByType("OLD");
        MeasurementTypeDao replacement = mock(MeasurementTypeDao.class);
        when(replacement.findAll()).thenReturn(List.of(type(202, "NEW"), type(303, "LATER")));
        ValidationsDao validations = mock(ValidationsDao.class);
        when(validations.find((Object) Integer.valueOf(1))).thenReturn(null).thenThrow(new IllegalStateException("FAKE validation unavailable"));
        try (var spring = mockStatic(SpringUtils.class)) {
            spring.when(() -> SpringUtils.getBean(MeasurementTypeDao.class)).thenReturn(replacement);
            spring.when(() -> SpringUtils.getBean(ValidationsDao.class)).thenReturn(validations);
            catalog.reInit();
        }
        verify(validations, times(2)).find((Object) Integer.valueOf(1));
        assertThat(catalog.getByType("OLD")).isSameAs(old);
        assertThat(catalog.getById("101")).isSameAs(old);
        assertThat(catalog.getByType("NEW")).isNull();
        assertThat(catalog.getById("202")).isNull();
        assertThat(catalog.getByType("LATER")).isNull();
        assertThat(catalog.getById("303")).isNull();
    }
}
