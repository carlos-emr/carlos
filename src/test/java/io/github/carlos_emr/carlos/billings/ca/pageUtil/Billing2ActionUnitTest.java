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
package io.github.carlos_emr.carlos.billings.ca.pageUtil;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the cross-province billing entry router. The router is
 * deliberately tiny: privilege check + decide BC vs ON. These tests pin
 * exactly that contract, including the fall-back to the deployment-wide
 * {@code billregion} property and the router's defensive guard against a null
 * {@link CarlosProperties#getInstance()}. That guard never fires in production
 * — the singleton is eagerly initialized — so the case is reachable only by
 * stubbing the static accessor, which is what the corresponding test does; it
 * is pinned so a future move to lazy initialization cannot NPE the router.
 *
 * <p>The property fall-back is stubbed on {@link CarlosProperties} — the
 * singleton actually backed by {@code carlos.properties}. The action previously
 * read a same-named holder in {@code carlos.util.plugin} that nothing ever
 * populated, so the fall-back was dead in production while these tests passed
 * against the stub; every request without an explicit {@code billRegion} was
 * routed to BC, and on an Ontario install that ends in "CARLOS Error: 500".</p>
 *
 * @since 2026-04-27
 */
@DisplayName("Billing2Action (cross-province router)")
@Tag("unit")
@Tag("billing")
class Billing2ActionUnitTest extends CarlosUnitTestBase {

    private static final String BILLREGION = "billregion";

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockedStatic<CarlosProperties> carlosPropertiesMock;
    private AutoCloseable mockitoCloseable;

    /** The real singleton, captured before the static mock is installed. */
    private CarlosProperties realProperties;
    /** Whatever {@code billregion} the suite found configured, restored on teardown. */
    private Object originalBillRegion;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);

        mockRequest = new MockHttpServletRequest();
        mockRequest.setMethod("GET");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        // Capture the real singleton BEFORE stubbing the static accessor, then
        // hand that same object back from getInstance(). Tests then drive the
        // fall-back through the class's real getProperty() validation rather
        // than a mock that cannot go stale against it.
        realProperties = CarlosProperties.getInstance();
        // Hashtable.get, not getProperty: the override logs and substitutes a
        // default for a missing key, which would corrupt the saved value.
        originalBillRegion = realProperties.get(BILLREGION);

        carlosPropertiesMock = mockStatic(CarlosProperties.class);
        carlosPropertiesMock.when(CarlosProperties::getInstance).thenReturn(realProperties);
        // Default: no deployment-wide billregion configured. Individual tests
        // set one to exercise the fall-back path.
        withoutBillRegion();

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (realProperties != null) {
            if (originalBillRegion == null) {
                realProperties.remove(BILLREGION);
            } else {
                realProperties.put(BILLREGION, originalBillRegion);
            }
        }
        if (carlosPropertiesMock != null) carlosPropertiesMock.close();
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldThrowSecurityException_whenMissingBillingReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> new Billing2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");
    }

    @Test
    void shouldReturnON_whenBillRegionParamIsON() {
        mockRequest.setParameter("billRegion", "ON");

        assertThat(new Billing2Action().execute()).isEqualTo("ON");
    }

    @Test
    void shouldReturnBC_whenBillRegionParamIsBC() {
        mockRequest.setParameter("billRegion", "BC");

        assertThat(new Billing2Action().execute()).isEqualTo("BC");
    }

    /**
     * Also the regression guard for the Ontario 3rd-Party / Bonus-Codes 500:
     * the bill-type dropdown on billingON.jsp re-opens the billing form through
     * this router, and if the deployment-wide fall-back does not resolve, an
     * Ontario install is handed billingBC.jsp and dies on BC-only tables.
     */
    @Test
    void shouldFallBackToProperty_whenBillRegionParamIsAbsent() {
        withBillRegion("ON");

        assertThat(new Billing2Action().execute()).isEqualTo("ON");
    }

    @Test
    void shouldFallBackToProperty_whenBillRegionParamIsEmptyString() {
        mockRequest.setParameter("billRegion", "");
        withBillRegion("ON");

        assertThat(new Billing2Action().execute()).isEqualTo("ON");
    }

    @Test
    void shouldDefaultToBC_whenNoParamAndPropertyMissing() {
        // Properties present but billregion key absent.
        withoutBillRegion();

        assertThat(new Billing2Action().execute()).isEqualTo("BC");
    }

    @Test
    void shouldDefaultToBC_whenCarlosPropertiesReturnsNull() {
        // Contract check, not a production state: the real singleton is eagerly
        // initialized and never null, so this is reachable only through the
        // static stub. It pins the router's defensive guard against a future
        // lazy-init change.
        carlosPropertiesMock.when(CarlosProperties::getInstance).thenReturn(null);

        assertThat(new Billing2Action().execute()).isEqualTo("BC");
    }

    @Test
    void shouldTreatUnknownRegion_asBC() {
        mockRequest.setParameter("billRegion", "AB");

        assertThat(new Billing2Action().execute()).isEqualTo("BC");
    }

    private void withBillRegion(String value) {
        realProperties.setProperty(BILLREGION, value);
    }

    private void withoutBillRegion() {
        realProperties.remove(BILLREGION);
    }
}
