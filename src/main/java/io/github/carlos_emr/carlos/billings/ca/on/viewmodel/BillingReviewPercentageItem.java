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

import java.util.List;

/**
 * Immutable presentation record for percentage-code rows on the billing
 * review screen.
 */
public record BillingReviewPercentageItem(
        String codeName,
        String codeUnit,
        String codeFee,
        String codeMinFee,
        String codeMaxFee,
        List<String> vecCodeFee,
        List<String> vecCodeTotal,
        String msg) {

    public BillingReviewPercentageItem {
        codeName = BillingViewStrings.nullToEmpty(codeName);
        codeUnit = BillingViewStrings.nullToEmpty(codeUnit);
        codeFee = BillingViewStrings.nullToEmpty(codeFee);
        codeMinFee = BillingViewStrings.nullToEmpty(codeMinFee);
        codeMaxFee = BillingViewStrings.nullToEmpty(codeMaxFee);
        vecCodeFee = vecCodeFee == null ? List.of() : List.copyOf(vecCodeFee);
        vecCodeTotal = vecCodeTotal == null ? List.of() : List.copyOf(vecCodeTotal);
        msg = BillingViewStrings.nullToEmpty(msg);
    }

    public String getCodeName() { return codeName; }
    public String getCodeUnit() { return codeUnit; }
    public String getCodeFee() { return codeFee; }
    public String getCodeMinFee() { return codeMinFee; }
    public String getCodeMaxFee() { return codeMaxFee; }
    public List<String> getVecCodeFee() { return vecCodeFee; }
    public List<String> getVecCodeTotal() { return vecCodeTotal; }
    public String getMsg() { return msg; }
}
