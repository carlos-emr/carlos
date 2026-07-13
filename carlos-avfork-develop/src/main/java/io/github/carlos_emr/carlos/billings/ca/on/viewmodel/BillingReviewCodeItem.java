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

/**
 * Presentation row for one billing code on the Ontario review screen.
 *
 * <p>The record preserves the legacy JSP field names because the page still
 * renders against those identifiers while the backing workflow is being
 * modernized.</p>
 */
public record BillingReviewCodeItem(
        String codeName,
        String codeUnit,
        String codeFee,
        String codeTotal,
        String codeAt,
        String msg,
        String codeDescription) {

    public BillingReviewCodeItem {
        codeName = BillingViewStrings.nullToEmpty(codeName);
        codeUnit = BillingViewStrings.nullToEmpty(codeUnit);
        codeFee = BillingViewStrings.nullToEmpty(codeFee);
        codeTotal = BillingViewStrings.nullToEmpty(codeTotal);
        codeAt = BillingViewStrings.nullToEmpty(codeAt);
        msg = BillingViewStrings.nullToEmpty(msg);
        codeDescription = BillingViewStrings.nullToEmpty(codeDescription);
    }

    public String getCodeAt() { return codeAt; }
    public String getCodeFee() { return codeFee; }
    public String getCodeName() { return codeName; }
    public String getCodeUnit() { return codeUnit; }
    public String getMsg() { return msg; }
    public String getCodeTotal() { return codeTotal; }
    public String getCodeDescription() { return codeDescription; }
}
