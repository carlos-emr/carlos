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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingReportControlViewModel;
import io.github.carlos_emr.carlos.commn.dao.ReportProviderDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ReportProvider;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Assembles {@link BillingReportControlViewModel} for
 * {@code billing/CA/ON/billingReportControl.jsp}, the parent of the five
 * {@code billingReport_*.jspf} fragments. Hoists the inline scriptlet work
 * the JSP body used to perform: parameter parsing, current-date defaults
 * for the calendar-popup links, and the
 * {@link ReportProviderDao#search_reportprovider} lookup that drives the
 * "Select provider" dropdown.
 *
 * @since 2026-04-25
 */
@org.springframework.stereotype.Service
public class BillingReportControlDataAssembler {

    private final ReportProviderDao reportProviderDao;

    public BillingReportControlDataAssembler(ReportProviderDao reportProviderDao) {
        this.reportProviderDao = reportProviderDao;
    }

    /**
     * Build the parent control view model.
     *
     * @param request live request — supplies {@code reportAction},
     *                {@code providerview}, {@code xml_vdate},
     *                {@code xml_appointment_date}
     */
    public BillingReportControlViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        BillingReportControlViewModel.Builder b = BillingReportControlViewModel.builder();

        String reportAction = request.getParameter("reportAction");
        String providerView = request.getParameter("providerview");
        String xmlVdate = request.getParameter("xml_vdate");
        String xmlAppointmentDate = request.getParameter("xml_appointment_date");

        b.reportAction(reportAction == null ? "" : reportAction);
        b.providerView(providerView == null ? "all" : providerView);
        b.xmlVdate(xmlVdate == null ? "" : xmlVdate);
        b.xmlAppointmentDate(xmlAppointmentDate == null ? "" : xmlAppointmentDate);

        GregorianCalendar now = new GregorianCalendar();
        b.curYear(now.get(Calendar.YEAR));
        b.curMonth(now.get(Calendar.MONTH) + 1);

        b.providerOptions(loadProviderOptions());

        return b.build();
    }

    private List<BillingReportControlViewModel.ProviderOption> loadProviderOptions() {
        List<Object[]> raw = reportProviderDao.search_reportprovider("billingreport");
        if (raw == null) {
            return List.of();
        }
        List<BillingReportControlViewModel.ProviderOption> out = new ArrayList<>(raw.size());
        for (Object[] res : raw) {
            // res[0] is ReportProvider (unused — only Provider names rendered).
            // res[1] is Provider; mirrors the legacy scriptlet's casts.
            @SuppressWarnings("unused")
            ReportProvider rp = (ReportProvider) res[0];
            Provider p = (Provider) res[1];
            out.add(new BillingReportControlViewModel.ProviderOption(
                    nullToEmpty(p.getProviderNo()),
                    nullToEmpty(p.getFirstName()),
                    nullToEmpty(p.getLastName())));
        }
        return out;
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
