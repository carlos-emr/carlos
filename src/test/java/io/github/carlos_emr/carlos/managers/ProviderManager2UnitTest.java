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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderExtDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderPreferenceDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.model.ProviderSettings;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ProviderManager2}, focused on the manager-level (defense-in-depth)
 * authorization contract of {@code updateProviderSettings} (issue #2821 — provider settings
 * IDOR).
 *
 * <p>This test pins the self-only authorization rule at the manager boundary so it
 * cannot be removed without a test failing.</p>
 *
 * <p>Scope note: only the deny path is exercised. It short-circuits before the ~100-field
 * {@code ProviderSettings} mapping, so it stays a focused guard test; a bare mock of
 * {@code ProviderSettings} would NPE partway through that mapping on the allow path.</p>
 *
 * @since 2026-07-30
 * @see ProviderManager2#updateProviderSettings
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProviderManager2 Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
class ProviderManager2UnitTest extends CarlosUnitTestBase {

    private static final String TARGET_PROVIDER_NO = "1";

    @Mock
    private ProviderDao providerDao;

    @Mock
    private PropertyDao propertyDao;

    @Mock
    private ProviderPreferenceDao providerPreferenceDao;

    @Mock
    private ProviderExtDao providerExtDao;

    @InjectMocks
    private ProviderManager2 providerManager;

    @Test
    @Tag("update")
    @DisplayName("should persist nothing when a provider targets another provider")
    void shouldNotPersist_whenProviderTargetsAnotherProvider() {
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setLoggedInProvider(new Provider("999998"));
        ProviderSettings settings = mock(ProviderSettings.class);

        assertThatThrownBy(() -> providerManager.updateProviderSettings(loggedInInfo, TARGET_PROVIDER_NO, settings))
            .isInstanceOf(SecurityException.class);

        // A denied edit must block every persistence path. Deleting the manager-level
        // guard makes this assertion fail.
        verifyNoInteractions(providerPreferenceDao, providerExtDao, propertyDao);
    }
}
