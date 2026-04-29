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

/**
 * Immutable view model for {@code billing/CA/ON/inr/updateINRbilling.jsp}.
 * Built by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.assembler.InrBillingUpdateViewModelAssembler}
 * and exposed as request attribute {@code inrUpdateModel}.
 *
 * <p>Captures the demographic-display fields and pre-filled service/dx
 * codes the JSP previously pulled out of {@link
 * io.github.carlos_emr.carlos.commn.dao.DemographicDao} via a
 * scriptlet-local {@code SpringUtils.getBean} lookup.</p>
 *
 * @since 2026-04-26
 */
public final class InrBillingUpdateViewModel {

    private final String demoNo;
    private final String billingInrNo;
    private final String demoName;
    private final String demoHin;
    private final String demoDob;
    private final String serviceCode;
    private final String dxCode;

    private InrBillingUpdateViewModel(Builder b) {
        this.demoNo = nullToEmpty(b.demoNo);
        this.billingInrNo = nullToEmpty(b.billingInrNo);
        this.demoName = nullToEmpty(b.demoName);
        this.demoHin = nullToEmpty(b.demoHin);
        this.demoDob = nullToEmpty(b.demoDob);
        this.serviceCode = nullToEmpty(b.serviceCode);
        this.dxCode = nullToEmpty(b.dxCode);
    }

    public String getDemoNo()       { return demoNo; }
    public String getBillingInrNo() { return billingInrNo; }
    public String getDemoName()     { return demoName; }
    public String getDemoHin()      { return demoHin; }
    public String getDemoDob()      { return demoDob; }
    public String getServiceCode()  { return serviceCode; }
    public String getDxCode()       { return dxCode; }

    public static Builder builder() { return new Builder(); }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    public static final class Builder {
        private String demoNo;
        private String billingInrNo;
        private String demoName;
        private String demoHin;
        private String demoDob;
        private String serviceCode;
        private String dxCode;

        public Builder demoNo(String v)       { this.demoNo = v; return this; }
        public Builder billingInrNo(String v) { this.billingInrNo = v; return this; }
        public Builder demoName(String v)     { this.demoName = v; return this; }
        public Builder demoHin(String v)      { this.demoHin = v; return this; }
        public Builder demoDob(String v)      { this.demoDob = v; return this; }
        public Builder serviceCode(String v)  { this.serviceCode = v; return this; }
        public Builder dxCode(String v)       { this.dxCode = v; return this; }

        public InrBillingUpdateViewModel build() {
            return new InrBillingUpdateViewModel(this);
        }
    }
}
