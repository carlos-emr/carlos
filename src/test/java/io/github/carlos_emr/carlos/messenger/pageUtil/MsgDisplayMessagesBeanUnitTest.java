/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 */
package io.github.carlos_emr.carlos.messenger.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.OscarCommLocationsDao;
import io.github.carlos_emr.carlos.commn.model.OscarCommLocations;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the {@code getCurrentLocationId} logic on
 * {@link MsgDisplayMessagesBean}, covering the fix from reference equality
 * ({@code currentLocationId == "0"}) to value equality
 * ({@code "0".equals(currentLocationId)}).
 */
@Tag("unit")
@Tag("messenger")
class MsgDisplayMessagesBeanUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should resolve default location when currentLocationId equals sentinel \"0\"")
    void shouldResolveDefaultLocation_whenCurrentLocationIdIsZero() {
        OscarCommLocationsDao dao = createAndRegisterMock(OscarCommLocationsDao.class);
        OscarCommLocations loc = new OscarCommLocations();
        loc.setId(42);
        when(dao.findByCurrent1(1)).thenReturn(Collections.singletonList(loc));

        MsgDisplayMessagesBean bean = new MsgDisplayMessagesBean();

        String result = bean.getCurrentLocationId();

        assertThat(result).isEqualTo("42");
        verify(dao).findByCurrent1(1);
    }

    @Test
    @DisplayName("should not query DAO when currentLocationId already set to a non-sentinel value")
    void shouldSkipDaoLookup_whenCurrentLocationIdAlreadySet() {
        OscarCommLocationsDao dao = createAndRegisterMock(OscarCommLocationsDao.class);
        MsgDisplayMessagesBean bean = new MsgDisplayMessagesBean();
        injectDependency(bean, "currentLocationId", "7");

        String result = bean.getCurrentLocationId();

        assertThat(result).isEqualTo("7");
        verify(dao, never()).findByCurrent1(1);
    }

    @Test
    @DisplayName("should return sentinel when DAO returns null")
    void shouldReturnSentinel_whenDaoReturnsNull() {
        OscarCommLocationsDao dao = createAndRegisterMock(OscarCommLocationsDao.class);
        when(dao.findByCurrent1(1)).thenReturn(null);

        MsgDisplayMessagesBean bean = new MsgDisplayMessagesBean();

        String result = bean.getCurrentLocationId();

        assertThat(result).isEqualTo("0");
    }

    @Test
    @DisplayName("should return sentinel when DAO returns empty list (no IndexOutOfBoundsException)")
    void shouldReturnSentinel_whenDaoReturnsEmptyList() {
        OscarCommLocationsDao dao = createAndRegisterMock(OscarCommLocationsDao.class);
        when(dao.findByCurrent1(1)).thenReturn(Collections.emptyList());

        MsgDisplayMessagesBean bean = new MsgDisplayMessagesBean();

        String result = bean.getCurrentLocationId();

        assertThat(result).isEqualTo("0");
    }

    @Test
    @DisplayName("should resolve default location when sentinel set via a different String instance")
    void shouldResolveDefaultLocation_whenSentinelIsDifferentStringInstance() {
        OscarCommLocationsDao dao = createAndRegisterMock(OscarCommLocationsDao.class);
        OscarCommLocations loc = new OscarCommLocations();
        loc.setId(9);
        when(dao.findByCurrent1(1)).thenReturn(List.of(loc));

        MsgDisplayMessagesBean bean = new MsgDisplayMessagesBean();
        // A non-interned "0" instance - reference equality (== "0") would have failed here.
        injectDependency(bean, "currentLocationId", new String("0"));

        String result = bean.getCurrentLocationId();

        assertThat(result).isEqualTo("9");
        verify(dao).findByCurrent1(1);
    }
}
