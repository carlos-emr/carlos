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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.billings.ca.on.BillingMoney;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingCorrectionReviewViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.command.BillingCorrectionLineCommand;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingCorrectionReviewDraft;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingCorrectionReviewItemDraft;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingCorrectionReviewViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.command.BillingCorrectionValidationCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the typed ON correction review payload from submitted form data.
 */
@Service
@Transactional(readOnly = true)
public class BillingCorrectionReviewPreparationService {

    private final ServiceCodeLoader serviceCodeLoader;
    private final BillingCorrectionReviewViewModelAssembler reviewViewModelAssembler;

    public BillingCorrectionReviewPreparationService(ServiceCodeLoader serviceCodeLoader,
                                                     BillingCorrectionReviewViewModelAssembler reviewViewModelAssembler) {
        this.serviceCodeLoader = serviceCodeLoader;
        this.reviewViewModelAssembler = reviewViewModelAssembler;
    }

    public BillingCorrectionReviewViewModel prepareReview(BillingCorrectionValidationCommand command) {
        return reviewViewModelAssembler.assemble(prepareDraft(command));
    }

    BillingCorrectionReviewDraft prepareDraft(BillingCorrectionValidationCommand command) {
        BigDecimal billingunit = BillingMoney.zero();
        BigDecimal percentPremium = BillingMoney.zero();
        BigDecimal bigTotal = BillingMoney.zero();

        String diagnosticCode = command.diagnosticDetail();
        String diagcode;
        if (diagnosticCode == null || diagnosticCode.isEmpty()) {
            diagnosticCode = "000|Other code";
            diagcode = "000";
        } else {
            diagcode = diagnosticCode.length() >= 3 ? diagnosticCode.substring(0, 3) : diagnosticCode;
            StringBuilder numCode = new StringBuilder();
            for (int i = 0; i < diagcode.length(); i++) {
                char c = diagcode.charAt(i);
                if (c >= 48 && c <= 58) {
                    numCode.append(c);
                }
            }
            if (numCode.length() < 3) {
                diagnosticCode = "000|Other code";
                diagcode = "000";
            }
        }

        String content = buildContent(command);

        String pValue = "0";
        String eCode = "";
        String eDesc = "";
        String ePerc = "";
        String eUnit = "";
        String xCode = "";
        String xDesc = "";
        String xPerc = "";
        String xUnit = "";
        boolean eFlag = false;
        boolean xFlag = false;
        BigDecimal pValue1 = BillingMoney.zero();
        List<BillingCorrectionReviewItemDraft> items = new ArrayList<>();

        for (BillingCorrectionLineCommand line : command.serviceLines()) {
            if (line.serviceCode().isEmpty()) {
                continue;
            }
            io.github.carlos_emr.carlos.billings.ca.on.dto.BillingCodeAttribute details =
                    loadServiceCode(line.serviceCode());
            if (details == null) {
                continue;
            }

            String scode = details.serviceCode();
            String desc = details.description();
            String value = details.value();
            String percentage = details.percentage();
            if (!line.billingAmount().isEmpty()) {
                value = line.billingAmount();
            }

            BigDecimal otherunit2 = BillingMoney.amount(value);
            billingunit = BillingMoney.amount(line.billingUnit());
            otherunit2 = billingunit.multiply(otherunit2).setScale(2, RoundingMode.HALF_UP);

            if (isPremiumServiceCode(scode)) {
                pValue1 = pValue1.add(otherunit2);
                pValue = pValue1.toString();
                if (!eCode.isEmpty()) {
                    eFlag = true;
                }
            }

            if (".00".equals(value)) {
                if ("E411A".equals(scode)) {
                    eCode = scode;
                    eDesc = desc;
                    ePerc = percentage;
                    eUnit = line.billingUnit();
                    eFlag = true;
                } else {
                    xCode = scode;
                    xDesc = desc;
                    xPerc = percentage;
                    xUnit = line.billingUnit();
                    xFlag = true;
                }
            } else {
                bigTotal = bigTotal.add(otherunit2);
                items.add(new BillingCorrectionReviewItemDraft(
                        scode,
                        desc,
                        line.billingUnit(),
                        stripDecimalPoint(otherunit2.toString()),
                        percentage,
                        diagcode));
            }
        }

        if (eFlag) {
            BigDecimal ecodeunit = BillingMoney.amount(pValue);
            BigDecimal percent = new BigDecimal(ePerc).setScale(4, RoundingMode.HALF_UP);
            percentPremium = ecodeunit.multiply(percent).setScale(2, RoundingMode.HALF_UP);
            bigTotal = bigTotal.add(percentPremium);
            items.add(new BillingCorrectionReviewItemDraft(
                    eCode,
                    eDesc,
                    eUnit,
                    stripDecimalPoint(percentPremium.toString()),
                    ePerc,
                    diagcode));
        }

        if (xFlag) {
            BigDecimal xcodeunit = BillingMoney.amount(pValue, 4);
            bigTotal = bigTotal.subtract(percentPremium);
            BigDecimal xpercent = new BigDecimal(xPerc).setScale(4, RoundingMode.HALF_UP);
            BigDecimal xpercentPremium = xpercent.multiply(xcodeunit).setScale(2, RoundingMode.HALF_UP);
            xpercentPremium = billingunit.multiply(xpercentPremium).setScale(2, RoundingMode.HALF_UP);
            bigTotal = bigTotal.add(percentPremium);
            bigTotal = bigTotal.add(xpercentPremium);
            items.add(new BillingCorrectionReviewItemDraft(
                    xCode,
                    xDesc,
                    xUnit,
                    stripDecimalPoint(xpercentPremium.toString()),
                    xPerc,
                    diagcode));
        }

        return new BillingCorrectionReviewDraft(
                true,
                content,
                command.billingNo(),
                command.hin(),
                command.dob(),
                command.visitType(),
                command.visitDate(),
                command.status(),
                command.clinicRefCode(),
                command.providerNo(),
                command.billingDate(),
                command.updateDate(),
                stripDecimalPoint(bigTotal.toString()),
                command.demoName(),
                command.demoAddress(),
                command.demoProvince(),
                command.demoCity(),
                command.demoPostal(),
                command.demoSex(),
                command.referralDoctor(),
                command.referralDoctorOhip(),
                command.hcType(),
                command.manualReview() ? "Yes" : "N/A",
                command.referralChecked() ? "Yes" : "N/A",
                command.rosterStatus(),
                diagcode,
                items);
    }

    private String buildContent(BillingCorrectionValidationCommand command) {
        StringBuilder content = new StringBuilder();
        content.append("<rdohip>").append(command.referralDoctorOhip()).append("</rdohip>")
                .append("<rd>").append(command.referralDoctor()).append("</rd>");
        content.append("<xml_referral>").append(command.referralChecked() ? "checked" : "").append("</xml_referral>")
                .append("<mreview>").append(command.manualReview() ? "checked" : "").append("</mreview>");
        content.append("<hctype>").append(command.hcType()).append("</hctype>")
                .append("<demosex>").append(command.hcSex()).append("</demosex>");
        content.append("<specialty>").append(command.specialty()).append("</specialty>");
        content.append("<xml_roster>").append(command.rosterStatus()).append("</xml_roster>");

        for (Map.Entry<String, String> entry : command.xmlParameters().entrySet()) {
            content.append("<").append(entry.getKey()).append(">")
                    .append(SxmlMisc.replaceHTMLContent(entry.getValue()))
                    .append("</").append(entry.getKey()).append(">");
        }
        return content.toString();
    }

    private io.github.carlos_emr.carlos.billings.ca.on.dto.BillingCodeAttribute loadServiceCode(String serviceCode) {
        List<io.github.carlos_emr.carlos.billings.ca.on.dto.BillingCodeAttribute> attrs =
                serviceCodeLoader.getBillingCodeAttr(serviceCode);
        return (attrs == null || attrs.isEmpty()) ? null : attrs.get(0);
    }

    /**
     * Mirrors the premium-eligible service-code list from the old correction JSP.
     */
    private static boolean isPremiumServiceCode(String scode) {
        if (scode == null) return false;
        if ("A001A".equals(scode) || "A003A".equals(scode)
                || "A004A".equals(scode) || "A007A".equals(scode)
                || "A008A".equals(scode) || "A888A".equals(scode)
                || "Z777A".equals(scode) || "P029A".equals(scode)
                || "P028A".equals(scode) || "Z776A".equals(scode)
                || "P042A".equals(scode) || "S768A".equals(scode)
                || "S756A".equals(scode) || "S757A".equals(scode)
                || "S784A".equals(scode) || "S745A".equals(scode)
                || "P010A".equals(scode) || "P009A".equals(scode)
                || "P006A".equals(scode) || "P011A".equals(scode)
                || "P041A".equals(scode) || "P018A".equals(scode)
                || "P038A".equals(scode) || "P020A".equals(scode)
                || "P031A".equals(scode) || "Z552A".equals(scode)
                || "P022A".equals(scode) || "P023A".equals(scode)
                || "P030A".equals(scode) || "Z716A".equals(scode)) {
            return true;
        }
        if (scode.startsWith("S")) return true;
        return scode.endsWith("B") && !scode.endsWith("C988B")
                && !scode.endsWith("C998B") && !scode.endsWith("C999B");
    }

    private static String stripDecimalPoint(String s) {
        StringBuilder sb = new StringBuilder(s);
        int dot = s.indexOf('.');
        if (dot >= 0) {
            sb.deleteCharAt(dot);
        }
        return sb.toString();
    }

}
