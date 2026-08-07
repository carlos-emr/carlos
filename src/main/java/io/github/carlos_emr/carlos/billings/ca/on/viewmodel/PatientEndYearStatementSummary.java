/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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

import io.github.carlos_emr.carlos.billings.ca.on.support.BillingViewStrings;

import java.util.Date;

/**
 * Patient end-year statement summary: header fields rendered above the
 * per-invoice grid in {@code endYearStatement.jsp}. Immutable so the
 * persisted aggregation can't be mutated by the JSP.
 *
 */
public final class PatientEndYearStatementSummary {

    private final String patientName;
    private final String patientNo;
    private final String hin;
    private final String address;
    private final String phone;
    private final String invoiced;
    private final String paid;
    private final String count;
    private final Date fromDate;
    private final Date toDate;
    private final String fromDateParam;
    private final String todateParam;

    private PatientEndYearStatementSummary(Builder b) {
        this.patientName = BillingViewStrings.nullToEmpty(b.patientName);
        this.patientNo = BillingViewStrings.nullToEmpty(b.patientNo);
        this.hin = BillingViewStrings.nullToEmpty(b.hin);
        this.address = BillingViewStrings.nullToEmpty(b.address);
        this.phone = BillingViewStrings.nullToEmpty(b.phone);
        this.invoiced = b.invoiced == null ? "0.00" : b.invoiced;
        this.paid = b.paid == null ? "0.00" : b.paid;
        this.count = b.count == null ? "0" : b.count;
        this.fromDate = b.fromDate == null ? null : new Date(b.fromDate.getTime());
        this.toDate = b.toDate == null ? null : new Date(b.toDate.getTime());
        this.fromDateParam = BillingViewStrings.nullToEmpty(b.fromDateParam);
        this.todateParam = BillingViewStrings.nullToEmpty(b.todateParam);
    }

    public static Builder builder() { return new Builder(); }

    public String getPatientName() { return patientName; }
    public String getPatientNo() { return patientNo; }
    public String getHin() { return hin; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public String getInvoiced() { return invoiced; }
    public String getPaid() { return paid; }
    public String getCount() { return count; }
    public Date getFromDate() { return fromDate == null ? null : new Date(fromDate.getTime()); }
    public Date getToDate() { return toDate == null ? null : new Date(toDate.getTime()); }
    public String getFromDateParam() { return fromDateParam; }
    public String getTodateParam() { return todateParam; }

    public static final class Builder {
        private String patientName;
        private String patientNo;
        private String hin;
        private String address;
        private String phone;
        private String invoiced;
        private String paid;
        private String count;
        private Date fromDate;
        private Date toDate;
        private String fromDateParam;
        private String todateParam;

        public Builder patientName(String v) { this.patientName = v; return this; }
        public Builder patientNo(String v) { this.patientNo = v; return this; }
        public Builder hin(String v) { this.hin = v; return this; }
        public Builder address(String v) { this.address = v; return this; }
        public Builder phone(String v) { this.phone = v; return this; }
        public Builder invoiced(String v) { this.invoiced = v; return this; }
        public Builder paid(String v) { this.paid = v; return this; }
        public Builder count(String v) { this.count = v; return this; }
        public Builder fromDate(Date v) { this.fromDate = v == null ? null : new Date(v.getTime()); return this; }
        public Builder toDate(Date v) { this.toDate = v == null ? null : new Date(v.getTime()); return this; }
        public Builder fromDateParam(String v) { this.fromDateParam = v; return this; }
        public Builder todateParam(String v) { this.todateParam = v; return this; }

        public PatientEndYearStatementSummary build() {
            return new PatientEndYearStatementSummary(this);
        }
    }
}
