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
package io.github.carlos_emr.carlos.billings.ca.on.support;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
/**
 * Legacy Ontario billing constants and lookup tables shared by disk, claim,
 * save, and correction flows.
 */

public final class BillingOnConstants {
    public static final int FIELD_SERVICE_NUM = 10;
    public static final int FIELD_MAX_SERVICE_NUM = 20;

    public static final String BATCHHEADER_TRANSACTIONIDENTIFIER = "HE";
    public static final String BATCHHEADER_REORDIDENTIFICATION = "B";
    public static final String BATCHHEADER_SPECID = "V03";
    public static final String CLAIMHEADER1_TRANSACTIONIDENTIFIER = "HE";
    public static final String CLAIMHEADER1_REORDIDENTIFICATION = "H";
    public static final String CLAIMHEADER1_PAYMENTPROGRAM_PRIVATE = "PAT";
    public static final String CLAIMHEADER1_PAYEE = "P";

    public static final String ITEM_TRANSACTIONIDENTIFIER = "HE";
    public static final String ITEM_REORDIDENTIFICATION = "T";

    public static final String BILLINGFILE_STATUS_UNCERT = "U";
    public static final String BILLINGFILE_STATUS_DELETED = "D";
    public static final String BILLINGFILE_STATUS_BILLED = "B";

    public static final String BILLINGACTION_CREATE = "create";
    public static final String BILLINGACTION_UPDATE = "update";

    public static final String BILLINGMATCHSTRING_3RDPARTY = "PAT|OCF|ODS|CPP|STD|IFH";

    // UH: update billing_on_cheader1, refer to issue#233 https://github.com/oscaremr/oscar/issues/233
    public enum ACTION_TYPE {C, R, U, D, UH}


    public static final Properties propMonthCode = new Properties();
    public static final Properties propBillingCenter = new Properties();
    public static final Properties propBillingType = new Properties();
    public static final List<String> vecPaymentType = Collections.unmodifiableList(Arrays.asList(
            "HCP",
            "Bill OHIP",
            "RMB",
            "Reciprocal Medical Billing",
            "WCB",
            "Worker's Compensation Board",
            "PAT",
            "Bill Patient",
            "OCF",
            "OCF",
            "ODS",
            "ODS",
            "CPP",
            "Canada Pension Plan",
            "STD",
            "Short/Long Term Disability",
            "IFH",
            "Interm Federal Health",
            "NOT",
            "Do Not Bill"
    ));

    static {
        propMonthCode.setProperty("1", "A");
        propMonthCode.setProperty("2", "B");
        propMonthCode.setProperty("3", "C");
        propMonthCode.setProperty("4", "D");
        propMonthCode.setProperty("5", "E");
        propMonthCode.setProperty("6", "F");
        propMonthCode.setProperty("7", "G");
        propMonthCode.setProperty("8", "H");
        propMonthCode.setProperty("9", "I");
        propMonthCode.setProperty("10", "J");
        propMonthCode.setProperty("11", "K");
        propMonthCode.setProperty("12", "L");

        propBillingCenter.setProperty("G", "Hamilton");
        propBillingCenter.setProperty("J", "Kingston");
        propBillingCenter.setProperty("P", "London");
        propBillingCenter.setProperty("E", "Mississauga");
        propBillingCenter.setProperty("F", "Oshawa");
        propBillingCenter.setProperty("D", "Ottawa");
        propBillingCenter.setProperty("R", "Sudbury");
        propBillingCenter.setProperty("U", "Thunder Bay");
        propBillingCenter.setProperty("N", "Toronto");

        propBillingType.setProperty("O", "Bill OHIP");
        propBillingType.setProperty("B", "Submitted OHIP");
        propBillingType.setProperty("N", "Do Not Bill");
        propBillingType.setProperty("P", "Bill Patient");
        propBillingType.setProperty("W", "Bill WCB");
        propBillingType.setProperty("H", "Capitated");
        propBillingType.setProperty("S", "Settled");
        propBillingType.setProperty("D", "Deleted");
        propBillingType.setProperty("X", "Bad Debt");
    }
}
