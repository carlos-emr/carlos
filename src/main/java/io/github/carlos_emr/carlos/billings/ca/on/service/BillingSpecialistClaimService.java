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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.billings.ca.on.BillingMoney;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.support.BillingOnConstants;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimItemDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingSpecialistClaim;
import io.github.carlos_emr.carlos.billings.ca.on.command.BillingSpecialistClaimCommand;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Typed service for building and saving BillingSpec ON claims.
 */
@Service
@Transactional
public class BillingSpecialistClaimService {
    private static final Logger _logger = MiscUtils.getLogger();

    private final BillingOnClaimPersister claimPersister;
    private final ServiceCodeLoader serviceCodeLoader;

    public BillingSpecialistClaimService(BillingOnClaimPersister claimPersister, ServiceCodeLoader serviceCodeLoader) {
        this.claimPersister = claimPersister;
        this.serviceCodeLoader = serviceCodeLoader;
    }

    public boolean addBillingRecord(BillingSpecialistClaim claim) {
        int billingNo = claimPersister.addOneClaimHeaderRecord(claim.header());
        if (billingNo == 0) {
            return false;
        }
        if (!claim.items().isEmpty()) {
            claimPersister.addItemRecord(claim.items(), billingNo);
            return true;
        }
        _logger.error("No billing item for billing # " + billingNo);
        return false;
    }

    public BillingSpecialistClaim buildBillingClaim(BillingSpecialistClaimCommand request) {
        BillingClaimHeaderDto header = getClaimHeader1Obj(request);
        return new BillingSpecialistClaim(header, getItemObj(request));
    }

    public BillingSpecialistClaim buildInrBillingClaim(BillingSpecialistClaimCommand request) {
        BillingClaimHeaderDto header = getClaimHeader1InrObj(request);
        return new BillingSpecialistClaim(header, List.of(getItemInrObj(request)));
    }

    private BillingClaimHeaderDto getClaimHeader1Obj(BillingSpecialistClaimCommand val) {
        BillingClaimHeaderDto claim1Header = new BillingClaimHeaderDto();

        claim1Header = claim1Header.withTransactionId(BillingOnConstants.CLAIMHEADER1_TRANSACTIONIDENTIFIER);
        claim1Header = claim1Header.withRecordId(BillingOnConstants.CLAIMHEADER1_REORDIDENTIFICATION);
        String[] hinVer = getHinVer(val.demoHin());
        claim1Header = claim1Header.withHin(hinVer[0]);
        claim1Header = claim1Header.withVer(hinVer[1]);
        claim1Header = claim1Header.withDob(val.demoDob());
        String hctype = normalizeHcType(val.demoHctype());

        claim1Header = claim1Header.withPayProgram(getPayProgram(val.xmlBilltype(), hctype));
        claim1Header = claim1Header.withPayee(!val.payMethod().isEmpty() ? val.payMethod() : BillingOnConstants.CLAIMHEADER1_PAYEE);
        claim1Header = claim1Header.withReferralNumber("");

        claim1Header = claim1Header.withFacilityNumber(val.clinicRefCode());
        claim1Header = claim1Header.withAdmissionDate("");
        claim1Header = claim1Header.withReferringLabNumber("");
        claim1Header = claim1Header.withManualReview("");
        claim1Header = claim1Header.withLocation(val.clinicNo());
        claim1Header = claim1Header.withDemographicNo(val.functionId());
        claim1Header = claim1Header.withProviderNo(afterPipe(val.providers()));
        claim1Header = claim1Header.withAppointmentNo(val.appointmentNo());
        claim1Header = claim1Header.withDemographicName(val.demoName());
        String[] temp = getPatientLF(val.demoName());
        claim1Header = claim1Header.withLastName(temp[0]);
        claim1Header = claim1Header.withFirstName(temp[1]);
        claim1Header = claim1Header.withSex(val.demoSex());
        claim1Header = claim1Header.withProvince(hctype);
        claim1Header = claim1Header.withBillingDate(val.apptDate());
        claim1Header = claim1Header.withBillingTime("00:00:00");
        claim1Header = claim1Header.withUpdateDateTime(UtilDateUtilities.getToday("yyyy-MM-dd HH:mm:ss"));
        claim1Header = claim1Header.withTotal(totalForCsvCodes(val.svcCode()));
        claim1Header = claim1Header.withPaid("");
        claim1Header = claim1Header.withStatus(getStatus(val.xmlBilltype()));
        claim1Header = claim1Header.withComment("");
        claim1Header = claim1Header.withVisitType(firstTwo(val.xmlVisittype()));
        claim1Header = claim1Header.withProviderOhipNo(beforePipe(val.providers()));
        claim1Header = claim1Header.withProviderRmaNo("");
        claim1Header = claim1Header.withAppointmentProviderNo(val.apptProvider());
        claim1Header = claim1Header.withAssistantProviderNo("");
        claim1Header = claim1Header.withCreator(val.creator());

        return claim1Header;
    }

    private List<BillingClaimItemDto> getItemObj(BillingSpecialistClaimCommand val) {
        return Arrays.stream(val.svcCode().split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .map(code -> item(code, val.apptDate(), val.dxCode()))
                .toList();
    }

    private BillingClaimHeaderDto getClaimHeader1InrObj(BillingSpecialistClaimCommand val) {
        BillingClaimHeaderDto claim1Header = new BillingClaimHeaderDto();

        claim1Header = claim1Header.withTransactionId(BillingOnConstants.CLAIMHEADER1_TRANSACTIONIDENTIFIER);
        claim1Header = claim1Header.withRecordId(BillingOnConstants.CLAIMHEADER1_REORDIDENTIFICATION);
        String[] hinVer = getHinVer(val.demoHin());
        claim1Header = claim1Header.withHin(hinVer[0]);
        claim1Header = claim1Header.withVer(hinVer[1]);
        claim1Header = claim1Header.withDob(val.demoDob());
        String hctype = normalizeHcType(val.demoHctype());

        claim1Header = claim1Header.withPayProgram(getPayProgram(val.xmlBilltype(), hctype));
        claim1Header = claim1Header.withPayee(!val.payMethod().isEmpty() ? val.payMethod() : BillingOnConstants.CLAIMHEADER1_PAYEE);
        claim1Header = claim1Header.withReferralNumber("");
        claim1Header = claim1Header.withFacilityNumber("");
        claim1Header = claim1Header.withAdmissionDate("");
        claim1Header = claim1Header.withReferringLabNumber("");
        claim1Header = claim1Header.withManualReview("");
        claim1Header = claim1Header.withLocation(val.clinicNo());
        claim1Header = claim1Header.withDemographicNo(val.functionId());
        claim1Header = claim1Header.withProviderNo(afterPipe(val.providers()));
        claim1Header = claim1Header.withAppointmentNo(val.appointmentNo());
        claim1Header = claim1Header.withDemographicName(val.demoName());
        String[] temp = getPatientLF(val.demoName());
        claim1Header = claim1Header.withLastName(temp[0]);
        claim1Header = claim1Header.withFirstName(temp[1]);
        claim1Header = claim1Header.withSex(val.demoSex());
        claim1Header = claim1Header.withProvince(hctype);
        claim1Header = claim1Header.withBillingDate(val.apptDate());
        claim1Header = claim1Header.withBillingTime("00:00:00");
        claim1Header = claim1Header.withUpdateDateTime(UtilDateUtilities.getToday("yyyy-MM-dd HH:mm:ss"));
        claim1Header = claim1Header.withTotal(feeForCode(val.svcCode()));
        claim1Header = claim1Header.withPaid("");
        claim1Header = claim1Header.withStatus(getStatus(val.xmlBilltype()));
        claim1Header = claim1Header.withComment("");
        claim1Header = claim1Header.withVisitType(firstTwo(val.xmlVisittype()));
        claim1Header = claim1Header.withProviderOhipNo(beforePipe(val.providers()));
        claim1Header = claim1Header.withProviderRmaNo("");
        claim1Header = claim1Header.withAppointmentProviderNo(val.apptProvider());
        claim1Header = claim1Header.withAssistantProviderNo("");
        claim1Header = claim1Header.withCreator(val.creator());

        return claim1Header;
    }

    private BillingClaimItemDto getItemInrObj(BillingSpecialistClaimCommand val) {
        return item(val.svcCode(), val.apptDate(), val.dxCode());
    }

    private BillingClaimItemDto item(String serviceCode, String serviceDate, String dxCode) {
        BillingClaimItemDto claimItem = new BillingClaimItemDto();
        claimItem = claimItem.withTransactionId(BillingOnConstants.ITEM_TRANSACTIONIDENTIFIER);
        claimItem = claimItem.withRecordId(BillingOnConstants.ITEM_REORDIDENTIFICATION);
        claimItem = claimItem.withServiceCode(serviceCode);
        claimItem = claimItem.withFee(feeForCode(serviceCode));
        claimItem = claimItem.withServiceNumber("1");
        claimItem = claimItem.withServiceDate(serviceDate);
        claimItem = claimItem.withDx(dxCode);
        claimItem = claimItem.withDx1("");
        claimItem = claimItem.withDx2("");
        claimItem = claimItem.withStatus("O");
        return claimItem;
    }

    private String totalForCsvCodes(String svcCode) {
        BigDecimal runningTotal = BillingMoney.zero();
        for (String code : svcCode.split(",")) {
            runningTotal = runningTotal.add(BillingMoney.amount(feeForCode(code.trim())));
        }
        return BillingMoney.isNonZero(runningTotal) ? BillingMoney.format(runningTotal) : "";
    }

    private String feeForCode(String serviceCode) {
        java.util.List<io.github.carlos_emr.carlos.billings.ca.on.dto.BillingCodeAttribute> attrs =
                serviceCodeLoader.getBillingCodeAttr(serviceCode);
        return (attrs == null || attrs.isEmpty()) ? "" : attrs.get(0).value();
    }

    private static String[] getHinVer(String val) {
        String[] ret = {"", ""};
        for (int i = 0; i < val.length(); i++) {
            if (("" + val.charAt(i)).matches("\\d")) {
                ret[0] += val.charAt(i);
            } else {
                ret[1] += val.charAt(i);
            }
        }
        return ret;
    }

    private static String getPayProgram(String val, String hcType) {
        String ret = val.length() >= 3 ? val.substring(0, 3) : val;
        if (val.startsWith("PAT")) {
            ret = BillingOnConstants.CLAIMHEADER1_PAYMENTPROGRAM_PRIVATE;
        } else if (val.startsWith("ODP")) {
            ret = "ON".equals(hcType) ? "HCP" : "RMB";
        }
        return ret;
    }

    private static String[] getPatientLF(String val) {
        String[] ret = {"", ""};
        if (val.contains(",")) {
            String[] split = val.split(",", 2);
            ret[0] = split[0].replaceAll("\\W", "");
            ret[0] = ret[0].length() > 9 ? ret[0].substring(0, 9) : ret[0];
            ret[1] = split.length > 1 ? split[1].replaceAll("\\W", "") : "";
            ret[1] = ret[1].length() > 5 ? ret[1].substring(0, 5) : ret[1];
        }
        return ret;
    }

    private static String getStatus(String payProg) {
        return payProg.startsWith("NOT") ? "N" : "O";
    }

    private static String normalizeHcType(String hctype) {
        return hctype.isEmpty() || "null".equals(hctype) ? "ON" : hctype;
    }

    private static String firstTwo(String value) {
        return value.length() < 2 ? value : value.substring(0, 2);
    }

    private static String beforePipe(String value) {
        int pipe = value.indexOf("|");
        return pipe >= 0 ? value.substring(0, pipe) : value;
    }

    private static String afterPipe(String value) {
        int pipe = value.indexOf("|");
        return pipe >= 0 ? value.substring(pipe + 1) : "";
    }
}
