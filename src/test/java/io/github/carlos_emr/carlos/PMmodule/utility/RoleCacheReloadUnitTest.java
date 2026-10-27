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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.PMmodule.dao.DefaultRoleAccessDAO;
import io.github.carlos_emr.carlos.PMmodule.model.DefaultRoleAccess;
import io.github.carlos_emr.carlos.commn.dao.CaisiAccessTypeDao;
import io.github.carlos_emr.carlos.commn.model.CaisiAccessType;
import io.github.carlos_emr.carlos.daos.security.SecroleDao;
import io.github.carlos_emr.carlos.model.security.Secrole;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Tag("unit")
@Isolated("RoleCache retains a process-wide policy snapshot")
class RoleCacheReloadUnitTest {
    private final SecroleDao roles = mock(SecroleDao.class);
    private final CaisiAccessTypeDao types = mock(CaisiAccessTypeDao.class);
    private final DefaultRoleAccessDAO defaults = mock(DefaultRoleAccessDAO.class);
    private Object originalSnapshot;
    private MockedStatic<SpringUtils> spring;

    @BeforeEach
    void bindIsolatedDaos() {
        spring = bindContextDaos();
        originalSnapshot = snapshot().getAndSet(null);
    }

    private MockedStatic<SpringUtils> bindContextDaos() {
        var context = mockStatic(SpringUtils.class);
        context.when(() -> SpringUtils.getBean(SecroleDao.class)).thenReturn(roles);
        context.when(() -> SpringUtils.getBean(CaisiAccessTypeDao.class)).thenReturn(types);
        context.when(() -> SpringUtils.getBean(DefaultRoleAccessDAO.class)).thenReturn(defaults);
        return context;
    }

    @AfterEach
    void restoreCache() {
        snapshot().set(originalSnapshot);
        spring.close();
    }

    @Test
    void shouldRemainExplicitlyUnavailable_whenTheFirstDatabaseLoadIsBlocked() {
        var blocked = new IllegalStateException("synthetic maintenance barrier");
        when(types.findAll()).thenThrow(blocked);
        assertThatThrownBy(RoleCache::reload).isSameAs(blocked);
        assertThatThrownBy(() -> RoleCache.getRole(1L)).hasMessage("ROLE_CACHE_NOT_INITIALIZED");
        assertThatThrownBy(() -> RoleCache.hasAccess("first", 1L))
                .hasMessage("ROLE_CACHE_NOT_INITIALIZED");
        verifyNoInteractions(roles, defaults);
    }

    @Test
    void shouldPreserveTheWholePriorPolicy_whenALaterReloadFails() {
        Secrole first = policy(1, "first", 11);
        RoleCache.reload();
        policy(2, "second", 22);
        var failure = new IllegalStateException("synthetic final query failure");
        when(defaults.findAll()).thenThrow(failure);
        assertThatThrownBy(RoleCache::reload).isSameAs(failure);
        assertThat(RoleCache.getRole(1L)).isSameAs(first);
        assertThat(RoleCache.getRole(2L)).isNull();
        assertThat(RoleCache.hasAccess("first", 1L)).isTrue();
        assertThat(RoleCache.hasAccess("second", 2L)).isFalse();
    }

    @Test
    void shouldPublishOnlyCompletePolicies_whenReloadAndReadsOverlap() throws Exception {
        Secrole first = policy(1, "first", 11);
        RoleCache.reload();
        Secrole second = policy(2, "second", 22);
        var association = new DefaultRoleAccess();
        association.setRoleId(2);
        association.setAccessTypeId(22);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(defaults.findAll())
                .thenAnswer(
                        call -> {
                            entered.countDown();
                            if (!release.await(5, TimeUnit.SECONDS))
                                throw new AssertionError("reload release timed out");
                            return List.of(association);
                        });
        try (var worker = Executors.newSingleThreadExecutor()) {
            var reloaded = worker.submit(() -> {
                try (var context = bindContextDaos()) {
                    RoleCache.reload();
                }
            });
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(RoleCache.getRole(1L)).isSameAs(first);
                assertThat(RoleCache.getRole(2L)).isNull();
                assertThat(RoleCache.hasAccess("first", 1L)).isTrue();
                assertThat(RoleCache.hasAccess("second", 2L)).isFalse();
            } finally {
                release.countDown();
            }
            reloaded.get(5, TimeUnit.SECONDS);
        }
        assertThat(RoleCache.getRole(1L)).isNull();
        assertThat(RoleCache.getRole(2L)).isSameAs(second);
        assertThat(RoleCache.hasAccess("first", 1L)).isFalse();
        assertThat(RoleCache.hasAccess("second", 2L)).isTrue();
    }

    @Test
    void shouldPreservePriorPolicy_whenAReloadFailsWithAnError() {
        Secrole first = policy(1, "first", 11);
        RoleCache.reload();
        var failure = new AssertionError("synthetic load failure");
        when(defaults.findAll()).thenThrow(failure);
        assertThatThrownBy(RoleCache::reload).isSameAs(failure);
        assertThat(RoleCache.getRole(1L)).isSameAs(first);
        assertThat(RoleCache.hasAccess("first", 1L)).isTrue();
    }

    @Test
    void shouldRetainPermissionPolicy_whenAnInitializedRoleIsUpdated() {
        policy(1, "first", 11);
        RoleCache.reload();
        var replacement = new Secrole("replacement", "synthetic role");
        replacement.setId(1L);
        RoleCache.setRole(1L, replacement);
        assertThat(RoleCache.getRole(1L)).isSameAs(replacement);
        assertThat(RoleCache.hasAccess("first", 1L)).isTrue();
    }

    @Test
    void shouldRepresentAnActuallyEmptyPolicy_whenAllQueriesSucceed() {
        when(types.findAll()).thenReturn(List.of());
        when(roles.getRoles()).thenReturn(List.of());
        when(defaults.findAll()).thenReturn(List.of());
        RoleCache.reload();
        assertThat(RoleCache.getRole(1L)).isNull();
        assertThat(RoleCache.hasAccess("absent", 1L)).isFalse();
    }

    @Test
    void shouldUseTheCurrentContextDaos_whenTheApplicationContextChangesBetweenReloads() {
        policy(1, "first", 11);
        RoleCache.reload();
        when(defaults.findAll()).thenThrow(new AssertionError("prior context must not be retained"));
        SecroleDao nextRoles = mock(SecroleDao.class);
        CaisiAccessTypeDao nextTypes = mock(CaisiAccessTypeDao.class);
        DefaultRoleAccessDAO nextDefaults = mock(DefaultRoleAccessDAO.class);
        when(nextRoles.getRoles()).thenReturn(List.of());
        when(nextTypes.findAll()).thenReturn(List.of());
        when(nextDefaults.findAll()).thenReturn(List.of());
        spring.when(() -> SpringUtils.getBean(SecroleDao.class)).thenReturn(nextRoles);
        spring.when(() -> SpringUtils.getBean(CaisiAccessTypeDao.class)).thenReturn(nextTypes);
        spring.when(() -> SpringUtils.getBean(DefaultRoleAccessDAO.class)).thenReturn(nextDefaults);
        RoleCache.reload();
        assertThat(RoleCache.getRole(1L)).isNull();
        verify(defaults, times(1)).findAll();
        verify(nextRoles).getRoles();
        verify(nextTypes).findAll();
        verify(nextDefaults).findAll();
    }

    private Secrole policy(long role, String name, int type) {
        var accessType = new CaisiAccessType();
        accessType.setId(type);
        accessType.setName(name);
        var selected = new Secrole(name, "synthetic role");
        selected.setId(role);
        var access = new DefaultRoleAccess();
        access.setRoleId(role);
        access.setAccessTypeId(type);
        when(types.findAll()).thenReturn(List.of(accessType));
        when(roles.getRoles()).thenReturn(List.of(selected));
        when(defaults.findAll()).thenReturn(List.of(access));
        return selected;
    }

    @SuppressWarnings("unchecked")
    private static AtomicReference<Object> snapshot() {
        return (AtomicReference<Object>) ReflectionTestUtils.getField(RoleCache.class, "snapshot");
    }
}
