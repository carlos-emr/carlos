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
package io.github.carlos_emr.carlos.billings.ca.on.viewmodel;

import java.util.Collections;
import java.util.List;

/**
 * Immutable view model for {@code billingReportCenter.jsp}, the Ontario
 * billing report-center landing page.
 *
 * <p>The legacy JSP body iterated {@code ReportProviderDao.search_reportprovider("billingreport")}
 * inline, unpacked the {@code Object[]} pairs to access provider names and
 * OHIP numbers, and echoed two date parameters ({@code xml_vdate},
 * {@code xml_appointment_date}) plus the {@code providerview} parameter
 * back into the form. All four pieces are now resolved server-side by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.web.ViewBillingReportCenter2Action}
 * and exposed via this view model.</p>
 *
 * @since 2026-04-25
 */
public final class BillingReportCenterViewModel {

    private final List<ProviderRow> providerRows;
    private final String selectedProviderView;
    private final String xmlVdate;
    private final String xmlAppointmentDate;

    private BillingReportCenterViewModel(Builder b) {
        this.providerRows = b.providerRows == null
                ? Collections.<ProviderRow>emptyList()
                : List.copyOf(b.providerRows);
        this.selectedProviderView = nullToAll(b.selectedProviderView);
        this.xmlVdate = nullToEmpty(b.xmlVdate);
        this.xmlAppointmentDate = nullToEmpty(b.xmlAppointmentDate);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    /** Legacy default for the {@code providerview} parameter when absent. */
    private static String nullToAll(String s) { return s == null ? "all" : s; }

    public static Builder builder() { return new Builder(); }

    public List<ProviderRow> getProviderRows() { return providerRows; }
    public String getSelectedProviderView() { return selectedProviderView; }
    public String getXmlVdate() { return xmlVdate; }
    public String getXmlAppointmentDate() { return xmlAppointmentDate; }

    /** A single row in the {@code providerview} select. */
    public static final class ProviderRow {
        private final String ohip;
        private final String firstName;
        private final String lastName;

        public ProviderRow(String ohip, String firstName, String lastName) {
            this.ohip = nullToEmpty(ohip);
            this.firstName = nullToEmpty(firstName);
            this.lastName = nullToEmpty(lastName);
        }

        public String getOhip() { return ohip; }
        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
        /** "Lastname, Firstname" for the select option label. */
        public String getDisplayName() { return lastName + ", " + firstName; }
    }

    public static final class Builder {
        private List<ProviderRow> providerRows;
        private String selectedProviderView;
        private String xmlVdate;
        private String xmlAppointmentDate;

        public Builder providerRows(List<ProviderRow> v) { this.providerRows = v; return this; }
        public Builder selectedProviderView(String v) { this.selectedProviderView = v; return this; }
        public Builder xmlVdate(String v) { this.xmlVdate = v; return this; }
        public Builder xmlAppointmentDate(String v) { this.xmlAppointmentDate = v; return this; }

        public BillingReportCenterViewModel build() { return new BillingReportCenterViewModel(this); }
    }
}
