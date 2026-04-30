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

import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingPercLimitDao;
import io.github.carlos_emr.carlos.billing.CA.dao.BillingDetailDao;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins the date-format contract on
 * {@link BillingShortcutPg2Service#buildPg1RedirectUrl}.
 *
 * <p>Pre-fix: the method emitted {@code appointment_date=YYYY-M-D} (raw
 * Calendar fields without zero-padding). On any single-digit-month or
 * single-digit-day day, the URL contained e.g. {@code 2026-4-7} which then
 * round-tripped into Pg1's hidden form input echoes — visually wrong even
 * when {@code SimpleDateFormat} parsing is lenient.
 *
 * <p>The fix uses {@code String.format("%d-%02d-%02d", ...)}. We can't drive
 * the {@code GregorianCalendar} clock without a refactor, so the test asserts
 * the date matches {@code \d{4}-\d{2}-\d{2}} on the live "now" value — the
 * regex catches the bug on any date that has a single-digit M or d, and
 * passes deterministically once padding is in place.
 *
 * @since 2026-04-29
 */
@DisplayName("BillingShortcutPg2 redirect URL date format")
@Tag("unit")
@Tag("billing")
class BillingShortcutPg2RedirectUrlUnitTest {

    @Test
    void shouldEmitZeroPaddedDate_inAppointmentDateQueryParam() throws Exception {
        BillingShortcutPg2Service assembler = new BillingShortcutPg2Service(
                mock(BillingDao.class),
                mock(BillingDetailDao.class),
                mock(ProviderDao.class),
                mock(DemographicDao.class),
                mock(BillingServiceDao.class),
                mock(BillingPercLimitDao.class),
                mock(BillingClaimSubmissionService.class));

        // The DemoContext type is package-private static — instantiate via
        // reflection so the test stays decoupled from its public API surface
        // (the only field we touch through the redirect builder is
        // demo.first / demo.last via URLEncoder, both null-safe via nullToEmpty).
        Class<?> demoContextClass = Class.forName(
                "io.github.carlos_emr.carlos.billings.ca.on.service.BillingShortcutPg2Service$DemoContext");
        Constructor<?> demoCtor = demoContextClass.getDeclaredConstructor();
        demoCtor.setAccessible(true);
        Object demo = demoCtor.newInstance();

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setContextPath("/carlos");

        Method m = BillingShortcutPg2Service.class.getDeclaredMethod(
                "buildPg1RedirectUrl",
                jakarta.servlet.http.HttpServletRequest.class,
                demoContextClass,
                String.class);
        m.setAccessible(true);

        String url = (String) m.invoke(assembler, req, demo, "12345");

        assertThat(url)
                .as("redirect URL must contain a zero-padded yyyy-MM-dd appointment_date")
                .containsPattern("appointment_date=\\d{4}-\\d{2}-\\d{2}&");
        assertThat(url)
                .as("must NOT contain a single-digit month or day after the dash")
                .doesNotContainPattern("appointment_date=\\d{4}-\\d-")
                .doesNotContainPattern("appointment_date=\\d{4}-\\d{2}-\\d&");
    }
}
