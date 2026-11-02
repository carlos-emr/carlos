/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

/*
 * Created on Apr 3, 2004
 */
package io.github.carlos_emr.carlos.billings.MSP;

import io.github.carlos_emr.carlos.utility.SafeEncode;

import io.github.carlos_emr.carlos.entities.Billingmaster;

public class CheckBillingData {

    // check batchHeader VS1
    public String checkVS1(String recordCode, String dataCentreNum,
                           String dataCentreSeq, String vendorMSPDCNum, String softName,
                           String softVer, String softInsDate, String vendorName,
                           String vendorContact, String vendorConName, String filler) {
        String ret = checkVS1DataCenterNum(dataCentreNum);
        ret = printWarningMsg(ret);
        return ret;
    }

    public String checkVS1DataCenterNum(String dataCenterNum) {
        String ret = checkLength(dataCenterNum, 5,
                "VS1:P02 Data Centre Number Wrong! ");
        return ret;
    }

    public String checkLength(String str, int len, String msg) {
        String ret = "";
        if (str == null || str.length() != len) ret = msg;
        return ret;
    }

    public String checkVS1DataCenterSeq(String dataCenterSeq) {
        String ret = checkLength(dataCenterSeq, 7,
                "VS1:P03 Data Centre Sequence Wrong! ");
        return ret;
    }

    public String checkVS1VendorsMSPDCNum(String vendorNum) {
        String ret = checkLength(vendorNum, 5,
                "VS1:P04 Vendors MSP DC Num Wrong! ");
        return ret;
    }

    public String printWarningMsg(String m) {
        String ret = "<tr bgcolor='orange'><td colspan='11'>" + SafeEncode.forHtmlContent(m)
                + "</td></tr>";
        return ret;
    }

    public String printErrorMsg(String billingNo, String m) {
        String billingNoForJsUrl = SafeEncode.forJavaScriptAttribute(SafeEncode.forUriComponent(billingNo));
        String ret = "<tr bgcolor='red'><td colspan='11'>"
                + "<a href='#' onClick=\"openBrWindow('reprocessBill?billingmaster_no="
                + billingNoForJsUrl
                + "','','resizable=yes, scrollbars=yes, top=0, left=0, width=900, height=600'); return false;\">"
                + SafeEncode.forHtmlContent(m) + "</a>" + "</td></tr>";
        return ret;
    }

    public String checkMSPPHN(String m, String ins) {
        String ret = "C02:P14 MSP PHN Wrong! ";
        if (m != null && (m.length() == 9 || m.length() == 10)) {
            if (m.length() == 10) {
                if (checkPHN(m)) ret = "";
            } else if (m.length() == 9) {
                if (checkOldPHN(m)) ret = "";
            }
        }
        if (m != null && m.equals("") && ins != null && !ins.equals(""))
            ret = "";
        return ret;
    }

    public boolean checkOldPHN(String m) {
        boolean ret = false;
        if (m.matches("\\d+")) {
            int sumA = 0;
            int sumB = 0;
            int temp = 0;

            // calculate weight result
            for (int i = 0; i < m.length() - 1; i++) {
                if (i % 2 == 0) {
                    sumA += Integer.parseInt("" + m.charAt(i));
                } else {
                    temp = Integer.parseInt("" + m.charAt(i)) * 2;
                    temp = temp > 9 ? (temp / 10 + (temp - 10)) : temp;
                    sumB += temp;
                }
            }

            // calculate
            temp = sumA + sumB;
            temp = temp % 10;
            temp = 10 - temp;

            // compare to the check digit
            if (("" + temp).equals("" + m.charAt(8))) ret = true;
        }
        return ret;
    }

    public boolean checkPHN(String m) {
        boolean ret = false;
        if (m.matches("\\d+") && m.startsWith("9")) {
            int[] consWeight = {0, 2, 4, 8, 5, 10, 9, 7, 3, 0};
            int temp = 0;

            // calculate weight result
            for (int i = 0; i < m.length(); i++) {
                temp += Integer.parseInt("" + m.charAt(i)) * consWeight[i];
            }

            // calculate
            int weight = temp;
            temp = temp / 11;
            temp = temp * 11;
            temp = weight - temp;
            temp = 11 - temp;

            // compare to the check digit
            if (("" + temp).equals("" + m.charAt(9))) ret = true;
        }
        return ret;
    }

    public String checkDependentNum(String m) {
        String ret = "C02:P18 Dependent Num Wrong! ";
        if (m != null && (m.matches("00|66"))) {
            ret = "";
        }

        return ret;
    }

    public String checkSrvUnits(String m) {
        String ret = "C02:P20 Billed Srv Units Wrong! ";
        if (m != null) ret = "";
        return ret;
    }

    public String checkAfterHourIndicator(String m) {
        String ret = "C02:P24 After Hours Service Indicator Wrong! ";
        if (m != null && m.length() == 1) {
            if (m.matches("[0ENW]")) ret = "";
        }

        return ret;
    }

    public String checkFeeCode(String m) {
        String ret = "C02:P26 Billed Fee Code Wrong! ";
        if (m != null) ret = "";
        return ret;
    }

    public String checkBilledAmount(String m) {
        String ret = "C02:P27 Billed Amount Wrong! ";
        if (m != null && m.matches("[0-9]+\\.{1}[0-9]*")) ret = "";
        return ret;
    }

    public String checkSubmissionCode(String m) {
        String ret = "C02:P34 Submission Code Wrong! ";
        if (m != null && m.matches("[0DERWCIAX]")) ret = "";
        return ret;
    }

    public String checkDxCode1(String m) {
        String ret = "C02:P36 Diagnostic Code 1 Wrong! ";
        if (m != null && m.length() > 2) {
            if (m.matches("\\w+")) ret = "";
        }
        return ret;
    }

    public String checkSrvLocation(String m) {
        String ret = "C02:P40 Service Location CD Wrong! ";
        if (m != null && m.matches("[ROCHIEPDSZ]")) {
            ret = "";
        }
        return ret;
    }

    public String checkReferral(String m1, String m2, String m3) {
        String ret = "C02:[P41P42|P44P46] " + m3 + " Wrong! ";
        if (m1 != null) {
            if ((m1.equals("") && m2.equals(""))
                    || (m1.matches("0") && m2.matches("0{5}"))
                    || (m1.matches("[BT]") && m2.matches("\\w+"))) {
                ret = "";
            }
        }
        return ret;
    }

    public String checkBirthDate(String m) {
        String ret = "C02:P52 Birth Date Wrong! ";
        if (m != null
                && (m.equals("") || m
                .matches("0{8}|[12][90][0-9][0-9][01][0-9][0-3][0-9]"))) {
            ret = "";
        }
        return ret;
    }

    public String checkOfficeFolioNum(String m) {
        String ret = "C02:P54 Office Folio Num Wrong! ";
        if (m != null && m.matches("\\w+")) {
            ret = "";
        }
        return ret;
    }

    public String checkCorrespondenceCode(String m) {
        String ret = "C02:P56 Correspondence Code Wrong! ";
        if (m != null && m.matches("[0CNB]")) {
            ret = "";
        }
        return ret;
    }

    public String checkMVACode(String m) {
        String ret = "C02:P60 MVA Claim Code Wrong! ";
        if (m != null && m.matches("[YN]")) {
            ret = "";
        }
        return ret;
    }

    public String checkOinInsCode(String m) {
        String ret = "C02:P100 OIN Insurer Code Wrong! ";
        if (m != null
                && (m.equals("") || m
                .matches("AB|SK|MB|ON|NS|PE|NF|NL|NT|NB|YT|IN|PP|WC"))) {
            ret = "";
        }
        return ret;
    }

    public String checkOinBirthDate(String m) {
        String ret = "C02:P104 OIN Birth Date Wrong! ";
        if (m != null
                && (m.equals("") || m
                .matches("[12][90][0-9][0-9][01][0-9][0-3][0-9]"))) {
            ret = "";
        }
        return ret;
    }

    public String checkOinSexCode(String m) {
        String ret = "C02:P112 OIN Sex Code Wrong! ";
        if (m != null && (m.equals("") || m.matches("[MF]"))) {
            ret = "";
        }
        return ret;
    }

    public String checkC02(String billingNo, Billingmaster bm) {
        String ret = checkLength(bm.getClaimcode(), 3,
                "C02:P00 Rec Code In Wrong! "); //P00
        // 3
        ret += checkLength(bm.getDatacenter(), 5,
                "C02:P02 Data Center Number Wrong! "); //P02
        // 5
        // + forwardZero(logNo, 7) //P04 7
        ret += checkLength(bm.getPayeeNo(), 5,
                "C02:P06 Payee Num Wrong! "); //P06
        // 5
        ret += checkLength(bm.getPractitionerNo(), 5,
                "C02:P08 Practitioner Num Wrong! "); //P08
        // 5
        ret += checkMSPPHN(bm.getPhn(), bm.getOinInsurerCode()); //P14

        // 10
        //* + forwardSpace(bm.getNameVerify(), 4) //P16 4 +
        ret += checkDependentNum(bm.getDependentNum()); //P18 2 +
        ret += checkSrvUnits(bm.getBillingUnit()); //P20 3 +

        /*
         * forwardZero(bm.getClarificationCode(), 2) //P22 2 +
         * forwardSpace(bm.getAnatomicalArea(), 2) //P23 2 +
         */

        ret += checkAfterHourIndicator(bm.getAfterHour()); //P24 1 +

        // forwardZero(bm.getNewProgram(), 2) //P25 2 +
        ret += checkFeeCode(bm.getBillingCode()); //P26 5 +
        ret += checkBilledAmount(bm.getBillAmount()); //P27 7 +

        // forwardZero(bm.getPaymentMode(), 1) //P28 1 +
        ret += checkLength(bm.getServiceDate(), 8,
                "C02:P30 Service Date Wrong! "); //P30 8 +
        // forwardZero(bm.getServiceToDay(), 2) //P32 2 +
        ret += checkSubmissionCode(bm.getSubmissionCode()); //P34 1 +
        // space(1) //P35 1 +
        ret += checkDxCode1(bm.getDxCode1()); //P36 5 +
        /*
         * backwardSpace(bm.getDxCode2(), 5) //P37 5 +
         * backwardSpace(bm.getDxCode3(), 5) //P38 5 + space(15) //
         * //P39 15 +
         */

        ret += checkSrvLocation(bm.getServiceLocation()); //P40 1 +
        ret += checkReferral(bm.getReferralFlag1(), bm.getReferralNo1(), "Referral 1"); //P41 1 + P42 5 +
        ret += checkReferral(bm.getReferralFlag2(), bm.getReferralNo2(), "Referral 2"); //P44 1 + P46 5 +
        /*
         * forwardZero(bm.getTimeCall(), 4) //P47 4 + zero(4) //P48 4 // +
         * zero(4) //P50 4 +
         */

        ret += checkBirthDate(bm.getBirthDate()); //P52 8 +
        ret += checkOfficeFolioNum(String.valueOf(bm.getBillingmasterNo())); //P54 7
        // +
        ret += checkCorrespondenceCode(bm.getCorrespondenceCode()); //P56
        // 1 +

        ret += checkMVACode(bm.getMvaClaimCode()); //P60 1
        /*
         * space(20) //P58 20 + forwardSpace(bm.getMvaClaimCode(), 1)
         * //P60 1 + forwardZero(bm.getIcbcClaimNo(), 8) //P62 8 +
         * forwardZero(bm.getOriginalClaim(), 20 ) //P64 20 +
         * forwardZero(bm.getFacilityNo(), 5) //P70 5 +
         * forwardZero(bm.getFacilitySubNo(), 5) //P72 5 + space(58)
         * //p80 58 +
         */

        ret += checkOinInsCode(bm.getOinInsurerCode()); //P100 2

        // backwardSpace(bm.getOinRegistrationNo(), 12)//P102 12 // +
        ret += checkOinBirthDate(bm.getOinBirthdate()); //P104 8 +
        /*
         * backwardSpace(bm.getOinFirstName(), 12) //P106 12 +
         * backwardSpace(bm.getOinSecondName(), 1) //P108 1 +
         * backwardSpace(bm.getOinSurname(), 18) //P110 18 +
         */
        ret += checkOinSexCode(bm.getOinSexCode()); //P112 1 +
        /*
         * backwardSpace(bm.getOinAddress(), 25) //P114 25 +
         * backwardSpace(bm.getOinAddress2(), 25) //P116 25 +
         * backwardSpace(bm.getOinAddress3(), 25) //P118 25 +
         * backwardSpace(bm.getOinAddress4(), 25) //P120 25 +
         * backwardSpace(bm.getOinPostalcode(), 6); //P122 6
         */
        ret = printErrorMsg(billingNo, ret);
        return ret;
    }
}
