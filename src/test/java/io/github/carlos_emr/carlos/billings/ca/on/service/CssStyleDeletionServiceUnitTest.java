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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.CSSStylesDAO;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.commn.model.CssStyle;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CssStyleDeletionService} — the atomic
 * soft-delete + cascade for css_styles. Pins:
 * <ul>
 *   <li>null styleId → false, no DAO writes</li>
 *   <li>no matching style → false, no DAO writes</li>
 *   <li>case-insensitive match → soft-delete + null every referencing
 *       billing_service.display_style → true</li>
 *   <li>mid-cascade failure → propagates so the @Transactional proxy can
 *       roll back the partial cascade</li>
 * </ul>
 */
@DisplayName("CssStyleDeletionService")
@Tag("unit")
@Tag("billing")
class CssStyleDeletionServiceUnitTest extends CarlosUnitTestBase {

    @Mock private CSSStylesDAO cssStylesDao;
    @Mock private BillingServiceDao billingServiceDao;

    private CssStyleDeletionService svc;
    private AutoCloseable mockitoCloseable;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        svc = new CssStyleDeletionService(cssStylesDao, billingServiceDao);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() throws Exception {
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldReturnFalse_whenStyleIdIsNull() {
        boolean result = svc.deleteByStyleId(null);

        assertThat(result).isFalse();
        verify(cssStylesDao, never()).findAll();
        verify(billingServiceDao, never()).findBillingCodesByFontStyle(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void shouldReturnFalse_whenNoMatchingStyleFound() {
        CssStyle other = mock(CssStyle.class, "other");
        when(other.getStyle()).thenReturn("blue-glow");
        when(cssStylesDao.findAll()).thenReturn(List.of(other));

        boolean result = svc.deleteByStyleId("red-flash");

        assertThat(result).isFalse();
        verify(cssStylesDao, never()).merge(org.mockito.ArgumentMatchers.any(CssStyle.class));
        verify(billingServiceDao, never()).findBillingCodesByFontStyle(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void shouldSoftDeleteAndNullReferencingBillingServices_whenStyleMatchesCaseInsensitive() {
        CssStyle target = mock(CssStyle.class, "target");
        when(target.getStyle()).thenReturn("RED-FLASH");
        when(target.getId()).thenReturn(42);
        when(cssStylesDao.findAll()).thenReturn(List.of(target));

        BillingService b1 = new BillingService();
        b1.setDisplayStyle(42);
        BillingService b2 = new BillingService();
        b2.setDisplayStyle(42);
        when(billingServiceDao.findBillingCodesByFontStyle(42)).thenReturn(List.of(b1, b2));

        boolean result = svc.deleteByStyleId("red-flash");  // lowercase

        assertThat(result).isTrue();
        verify(target).setStatus(CssStyle.DELETED);
        verify(cssStylesDao).merge(same(target));
        // Both referencing billing_service rows must have display_style nulled.
        assertThat(b1.getDisplayStyle()).isNull();
        assertThat(b2.getDisplayStyle()).isNull();
        verify(billingServiceDao).merge(same(b1));
        verify(billingServiceDao).merge(same(b2));
    }

    @Test
    void shouldPropagate_whenBillingServiceMergeThrowsMidCascade() {
        CssStyle target = mock(CssStyle.class, "target");
        when(target.getStyle()).thenReturn("red-flash");
        when(target.getId()).thenReturn(42);
        when(cssStylesDao.findAll()).thenReturn(List.of(target));

        BillingService b1 = new BillingService();
        b1.setDisplayStyle(42);
        BillingService b2 = new BillingService();
        b2.setDisplayStyle(42);
        when(billingServiceDao.findBillingCodesByFontStyle(42)).thenReturn(List.of(b1, b2));
        doThrow(new RuntimeException("merge-fail-on-b2"))
                .when(billingServiceDao).merge(same(b2));

        assertThatThrownBy(() -> svc.deleteByStyleId("red-flash"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("merge-fail-on-b2");

        // Pre-throw writes DID happen — the @Transactional proxy is responsible
        // for rolling them back, NOT the service.
        verify(cssStylesDao).merge(same(target));
        verify(billingServiceDao).merge(same(b1));
    }

    private static <T> T mock(Class<T> type, String name) {
        return org.mockito.Mockito.mock(type, name);
    }
}
