/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.pageUtil;

import java.util.Properties;

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

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.plugin.CarlosProperties;
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
 * {@code billregion} property and the null-safe handling when
 * {@link CarlosProperties#getProperties()} returns null.
 *
 * @since 2026-04-27
 */
@DisplayName("Billing2Action (cross-province router)")
@Tag("unit")
@Tag("billing")
class Billing2ActionUnitTest extends CarlosUnitTestBase {

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockedStatic<CarlosProperties> carlosPropertiesMock;
    private AutoCloseable mockitoCloseable;

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

        carlosPropertiesMock = mockStatic(CarlosProperties.class);
        // Default: no deployment-wide billregion configured. Individual tests
        // override this to exercise the fall-back path.
        carlosPropertiesMock.when(CarlosProperties::getProperties).thenReturn(new Properties());

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
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

    @Test
    void shouldFallBackToProperty_whenBillRegionParamIsAbsent() {
        Properties props = new Properties();
        props.setProperty("billregion", "ON");
        carlosPropertiesMock.when(CarlosProperties::getProperties).thenReturn(props);

        assertThat(new Billing2Action().execute()).isEqualTo("ON");
    }

    @Test
    void shouldFallBackToProperty_whenBillRegionParamIsEmptyString() {
        mockRequest.setParameter("billRegion", "");
        Properties props = new Properties();
        props.setProperty("billregion", "ON");
        carlosPropertiesMock.when(CarlosProperties::getProperties).thenReturn(props);

        assertThat(new Billing2Action().execute()).isEqualTo("ON");
    }

    @Test
    void shouldDefaultToBC_whenNoParamAndPropertyMissing() {
        // Properties present but billregion key absent.
        carlosPropertiesMock.when(CarlosProperties::getProperties).thenReturn(new Properties());

        assertThat(new Billing2Action().execute()).isEqualTo("BC");
    }

    @Test
    void shouldDefaultToBC_whenCarlosPropertiesReturnsNull() {
        // Real-world scenario observed in dev: getProperties() returns null
        // before configuration is loaded. The original action NPE'd here;
        // the new router handles it gracefully and falls back to BC.
        carlosPropertiesMock.when(CarlosProperties::getProperties).thenReturn(null);

        assertThat(new Billing2Action().execute()).isEqualTo("BC");
    }

    @Test
    void shouldTreatUnknownRegionAsBC() {
        mockRequest.setParameter("billRegion", "AB");

        assertThat(new Billing2Action().execute()).isEqualTo("BC");
    }
}
