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
package io.github.carlos_emr.carlos.PMmodule.utility;

import io.github.carlos_emr.carlos.PMmodule.dao.ProgramAccessDAO;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramAccess;
import io.github.carlos_emr.carlos.PMmodule.model.AccessType;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.MockedStatic;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@ResourceLock("ProgramAccessCache")
class ProgramAccessCacheUnitTest {
    private final ProgramAccessDAO dao = mock(ProgramAccessDAO.class);
    private ProgramAccessDAO originalDao;
    private Map<String, Map> originalMaps;
    private MockedStatic<SpringUtils> spring;

    @BeforeEach void bindIsolatedCache() {
        spring = mockStatic(SpringUtils.class);
        spring.when(() -> SpringUtils.getBean(ProgramAccessDAO.class)).thenReturn(dao);
        originalDao = ProgramAccessCache.programAccessDAO;
        originalMaps = ProgramAccessCache.accessMaps;
        assertThat(originalMaps).isInstanceOf(java.util.concurrent.ConcurrentMap.class);
        ProgramAccessCache.programAccessDAO = dao;
        ProgramAccessCache.accessMaps = new ConcurrentHashMap<>();
    }

    @AfterEach void restoreCache() {
        ProgramAccessCache.programAccessDAO = originalDao;
        ProgramAccessCache.accessMaps = originalMaps;
        spring.close();
    }

    @Test void shouldPublishEveryRealMap_whenManyRequestsMissConcurrently() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        when(dao.getAccessListByProgramId(anyLong())).thenAnswer(call -> {
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            return List.of(access("program-" + call.getArgument(0)));
        });
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Future<?>> results = new ArrayList<>();
            for (long id = 1; id <= 64; id++) {
                final long program = id;
                results.add(pool.submit(() -> assertThat(ProgramAccessCache.getAccessMap(program))
                        .containsKey("program-" + program)));
            }
            start.countDown();
            for (var result : results) result.get(10, TimeUnit.SECONDS);
        }
        assertThat(ProgramAccessCache.accessMaps).hasSize(64);
    }

    @Test void shouldKeepAMissUnpublishedAndRetryRealData_whenTheDatabaseReadFails() {
        when(dao.getAccessListByProgramId(1L)).thenThrow(new IllegalStateException("maintenance"))
                .thenReturn(List.of(access("real")));
        assertThatThrownBy(() -> ProgramAccessCache.getAccessMap(1L)).isInstanceOf(IllegalStateException.class);
        assertThat(ProgramAccessCache.accessMaps).isEmpty();
        assertThat(ProgramAccessCache.getAccessMap(1L)).containsKey("real");
    }

    @Test void shouldPreserveAPriorMap_whenItsRefreshFails() {
        when(dao.getAccessListByProgramId(1L)).thenReturn(List.of(access("prior")))
                .thenThrow(new IllegalStateException("maintenance"));
        var prior = ProgramAccessCache.getAccessMap(1L);
        assertThatThrownBy(() -> ProgramAccessCache.setAccessMap(1L)).isInstanceOf(IllegalStateException.class);
        assertThat(ProgramAccessCache.getAccessMap(1L)).isSameAs(prior).containsKey("prior");
    }

    private static ProgramAccess access(String name) {
        var type = new AccessType(); type.setName(name);
        var access = new ProgramAccess(); access.setAccessType(type);
        return access;
    }
}
