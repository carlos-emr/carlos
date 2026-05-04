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

import io.github.carlos_emr.carlos.billings.ca.on.support.BillingViewStrings;

/**
 * Visit, clinic, and default-input presentation data for {@code billingON.jsp}.
 */
public record BillingOnVisitPresentation(
        String clinicView,
        String clinicNo,
        String visitType,
        boolean singleClickEnabled,
        boolean hospitalBilling,
        String dxCode,
        String xmlVisitType,
        String xmlLocation,
        String visitDate,
        String defaultLocation,
        String admissionDate,
        String defaultXmlVdate,
        String dxCodeDefault,
        String serviceDateDefault) {

    public static final BillingOnVisitPresentation EMPTY = new BillingOnVisitPresentation(
            "", "", "", false, false, "", "", "", "", "", "", "", "", "");

    public BillingOnVisitPresentation {
        clinicView = BillingViewStrings.nullToEmpty(clinicView);
        clinicNo = BillingViewStrings.nullToEmpty(clinicNo);
        visitType = BillingViewStrings.nullToEmpty(visitType);
        dxCode = BillingViewStrings.nullToEmpty(dxCode);
        xmlVisitType = BillingViewStrings.nullToEmpty(xmlVisitType);
        xmlLocation = BillingViewStrings.nullToEmpty(xmlLocation);
        visitDate = BillingViewStrings.nullToEmpty(visitDate);
        defaultLocation = BillingViewStrings.nullToEmpty(defaultLocation);
        admissionDate = BillingViewStrings.nullToEmpty(admissionDate);
        defaultXmlVdate = BillingViewStrings.nullToEmpty(defaultXmlVdate);
        dxCodeDefault = BillingViewStrings.nullToEmpty(dxCodeDefault);
        serviceDateDefault = BillingViewStrings.nullToEmpty(serviceDateDefault);
    }
}
