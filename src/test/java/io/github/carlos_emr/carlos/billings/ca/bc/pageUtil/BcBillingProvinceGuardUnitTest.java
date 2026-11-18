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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billing.CA.BC.dao.BillRecipientsDao;
import io.github.carlos_emr.carlos.billing.CA.BC.dao.BillingHistoryDao;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The BC-only routes in #5530 must never reach BC tables or views on an Ontario install. */
@Tag("unit")
@Tag("billing")
@Tag("security")
@DisplayName("BC billing deployment province guard")
class BcBillingProvinceGuardUnitTest extends CarlosUnitTestBase {

    enum Route {
        REPROCESS(BillingReProcessBill2Action::new, "list", "w"),
        WCB_FORMS(ViewWcbForms2Action::new, ActionSupport.SUCCESS, "r"),
        PRIVATE_STATEMENT(ViewPrivateBillingStatement2Action::new, ActionSupport.SUCCESS, "r");

        final Supplier<ActionSupport> action;
        final String result;
        final String privilege;

        Route(Supplier<ActionSupport> action, String result, String privilege) {
            this.action = action;
            this.result = result;
            this.privilege = privilege;
        }
    }

    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<CarlosProperties> propertiesStatic;
    private CarlosProperties properties;
    private SecurityInfoManager security;
    private LoggedInInfo loggedInInfo;
    private BillingmasterDAO billingmasterDao;
    private BillingDao billingDao;
    private BillingHistoryDao historyDao;
    private BillRecipientsDao recipientsDao;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), eq("_billing"), anyString(), isNull())).thenReturn(true);
        billingmasterDao = createAndRegisterMock(BillingmasterDAO.class);
        billingDao = createAndRegisterMock(BillingDao.class);
        historyDao = createAndRegisterMock(BillingHistoryDao.class);
        recipientsDao = createAndRegisterMock(BillRecipientsDao.class);

        request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.getSession().setAttribute("user", "999998");
        loggedInInfo = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        response = new MockHttpServletResponse();
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);

        properties = mock(CarlosProperties.class);
        when(properties.getProperty("billregion", "")).thenReturn("BC");
        propertiesStatic = mockStatic(CarlosProperties.class);
        propertiesStatic.when(CarlosProperties::getInstance).thenReturn(properties);
    }

    @AfterEach
    void tearDown() {
        if (propertiesStatic != null) propertiesStatic.close();
        if (servlet != null) servlet.close();
    }

    static Stream<Arguments> nonBcRoutes() {
        return Arrays.stream(Route.values()).flatMap(route ->
                Stream.of("ON", "on", "AB", "other", "", " ", "BC-invalid")
                        .flatMap(region -> Stream.of("GET", "HEAD", "POST")
                                .map(method -> Arguments.of(route, region, method))));
    }

    @ParameterizedTest(name = "{0}, billregion=[{1}], {2}")
    @MethodSource("nonBcRoutes")
    @DisplayName("should return 404 without rendering or querying BC data on a non-BC install")
    void shouldRefuseRoute_withoutBcSchema(Route route, String region, String method) throws Exception {
        when(properties.getProperty("billregion", "")).thenReturn(region);
        request.setMethod(method);
        // Neither a crawl's demographic parameters nor a forged region may select another schema.
        request.setParameter("demographic_no", "3459");
        request.setParameter("demographicNo", "3459");
        request.setParameter("demoNo", "3459");
        request.setParameter("billRegion", "BC");
        request.setParameter("billregion", "BC");
        request.setParameter("billingmasterNo", "41");

        assertThat(route.action.get().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.isCommitted()).isTrue();
        assertThat(response.getForwardedUrl()).isNull();
        verifyNoInteractions(billingmasterDao, billingDao, historyDao, recipientsDao);
    }

    static Stream<Arguments> bcRoutes() {
        return Arrays.stream(Route.values()).flatMap(route ->
                Stream.of("BC", "bc", " BC ").map(region -> Arguments.of(route, region)));
    }

    @ParameterizedTest(name = "{0}, billregion=[{1}]")
    @MethodSource("bcRoutes")
    @DisplayName("should preserve each route's view and billing privilege on a BC install")
    void shouldOpenPage_withBcSchemaAndPrivilege(Route route, String region) throws Exception {
        when(properties.getProperty("billregion", "")).thenReturn(region);
        // The internal statement dispatch retains its prepared model; standalone bookmarks redirect.
        if (route == Route.PRIVATE_STATEMENT) request.setAttribute("bills", java.util.List.of());

        assertThat(route.action.get().execute()).isEqualTo(route.result);
        assertThat(response.getStatus()).isEqualTo(200);
        verify(security).hasPrivilege(loggedInInfo, "_billing", route.privilege, null);
        verifyNoInteractions(billingmasterDao, billingDao, historyDao, recipientsDao);
    }

    @ParameterizedTest
    @EnumSource(Route.class)
    @DisplayName("should retain the billing privilege gate on a BC install")
    void shouldRejectAccess_withoutBillingPrivilege(Route route) {
        when(security.hasPrivilege(any(), eq("_billing"), anyString(), isNull())).thenReturn(false);

        assertThatThrownBy(() -> route.action.get().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_billing)");
        verifyNoInteractions(billingmasterDao, billingDao, historyDao, recipientsDao);
    }
}
