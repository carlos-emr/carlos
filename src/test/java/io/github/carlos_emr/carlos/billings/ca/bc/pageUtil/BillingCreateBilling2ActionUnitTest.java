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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingBillingManager.BillingItem;
import io.github.carlos_emr.carlos.billings.ca.service.GstSettingsService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.SqlUtils;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * The BC bill form's Continue (billing/CA/BC/CreateBilling): its warnings read reference tables
 * that a Flyway-built BC install ships empty, and {@link SqlUtils#getQueryResultsList(String)}
 * answers {@code null} for no rows. Continue answered HTTP 500 for every bill (issue #4716).
 */
@DisplayName("BillingCreateBilling2Action")
@Tag("unit")
@Tag("billing")
class BillingCreateBilling2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<SqlUtils> sqlUtilsMock;

    @BeforeEach
    void setUp() {
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        registerMock(GstSettingsService.class, mock(GstSettingsService.class));
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(new MockHttpServletRequest());
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
        sqlUtilsMock = mockStatic(SqlUtils.class);
        // billing_msp_servicecode_times with no rows
        sqlUtilsMock.when(() -> SqlUtils.getQueryResultsList(anyString())).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        if (sqlUtilsMock != null) sqlUtilsMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    @Test
    void shouldAddNoTimeWarning_whenNoServiceCodeNeedsATime() throws Exception {
        BillingCreateBilling2Action action = new BillingCreateBilling2Action();
        ArrayList<BillingItem> items = new ArrayList<>();
        items.add(new BillingBillingManager().new BillingItem("00100", "1"));
        List<String> errors = new ArrayList<>();
        Method validate = BillingCreateBilling2Action.class
                .getDeclaredMethod("validateServiceCodeTimes", ArrayList.class, List.class);
        validate.setAccessible(true);

        assertThatCode(() -> validate.invoke(action, items, errors)).doesNotThrowAnyException();
        assertThat(errors).isEmpty();
    }
}
