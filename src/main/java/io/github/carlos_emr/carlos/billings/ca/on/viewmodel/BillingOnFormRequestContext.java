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
import java.util.Map;

/**
 * Request, identity, and route context for {@code billingON.jsp}.
 */
public record BillingOnFormRequestContext(
        String userNo,
        String demographicNo,
        String appointmentNo,
        String providerNo,
        String apptProviderNo,
        String demoName,
        String today,
        String billReferenceDate,
        String mReview,
        String ctlBillForm,
        String curBillForm,
        String demoNameUrlEncoded,
        Map<String, String> requestParamEchoes) {

    public static final BillingOnFormRequestContext EMPTY = new BillingOnFormRequestContext(
            "", "", "", "", "", "", "", "", "", "", "", "", Map.of());

    public BillingOnFormRequestContext {
        userNo = BillingViewStrings.nullToEmpty(userNo);
        demographicNo = BillingViewStrings.nullToEmpty(demographicNo);
        appointmentNo = BillingViewStrings.nullToEmpty(appointmentNo);
        providerNo = BillingViewStrings.nullToEmpty(providerNo);
        apptProviderNo = BillingViewStrings.nullToEmpty(apptProviderNo);
        demoName = BillingViewStrings.nullToEmpty(demoName);
        today = BillingViewStrings.nullToEmpty(today);
        billReferenceDate = BillingViewStrings.nullToEmpty(billReferenceDate);
        mReview = BillingViewStrings.nullToEmpty(mReview);
        ctlBillForm = BillingViewStrings.nullToEmpty(ctlBillForm);
        curBillForm = BillingViewStrings.nullToEmpty(curBillForm);
        demoNameUrlEncoded = BillingViewStrings.nullToEmpty(demoNameUrlEncoded);
        requestParamEchoes = requestParamEchoes == null
                ? Collections.emptyMap() : Map.copyOf(requestParamEchoes);
    }
}
