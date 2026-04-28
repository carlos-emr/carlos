/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.billings.ca.on.BillingMoney;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingClaimHeader1Data;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingDataHlp;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingItemData;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingSpecClaim;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingSpecClaimRequest;
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
public class BillingSpecService {
    private static final Logger _logger = MiscUtils.getLogger();

    private final BillingONClaimPersister dbObj;
    private final ServiceCodeLoader serviceCodeLoader;

    public BillingSpecService(BillingONClaimPersister dbObj, ServiceCodeLoader serviceCodeLoader) {
        this.dbObj = dbObj;
        this.serviceCodeLoader = serviceCodeLoader;
    }

    public boolean addBillingRecord(BillingSpecClaim claim) {
        int billingNo = dbObj.addOneClaimHeaderRecord(claim.header());
        if (billingNo == 0) {
            return false;
        }
        if (!claim.items().isEmpty()) {
            return dbObj.addItemRecord(claim.items(), billingNo);
        }
        _logger.error("No billing item for billing # " + billingNo);
        return false;
    }

    public BillingSpecClaim buildBillingClaim(BillingSpecClaimRequest request) {
        BillingClaimHeader1Data header = getClaimHeader1Obj(request);
        return new BillingSpecClaim(header, getItemObj(request));
    }

    public BillingSpecClaim buildInrBillingClaim(BillingSpecClaimRequest request) {
        BillingClaimHeader1Data header = getClaimHeader1InrObj(request);
        return new BillingSpecClaim(header, List.of(getItemInrObj(request)));
    }

    private BillingClaimHeader1Data getClaimHeader1Obj(BillingSpecClaimRequest val) {
        BillingClaimHeader1Data claim1Header = new BillingClaimHeader1Data();

        claim1Header.setTransc_id(BillingDataHlp.CLAIMHEADER1_TRANSACTIONIDENTIFIER);
        claim1Header.setRec_id(BillingDataHlp.CLAIMHEADER1_REORDIDENTIFICATION);
        String[] hinVer = getHinVer(val.demoHin());
        claim1Header.setHin(hinVer[0]);
        claim1Header.setVer(hinVer[1]);
        claim1Header.setDob(val.demoDob());
        String hctype = normalizeHcType(val.demoHctype());

        claim1Header.setPay_program(getPayProgram(val.xmlBilltype(), hctype));
        claim1Header.setPayee(!val.payMethod().isEmpty() ? val.payMethod() : BillingDataHlp.CLAIMHEADER1_PAYEE);
        claim1Header.setRef_num("");

        claim1Header.setFacilty_num(val.clinicRefCode());
        claim1Header.setAdmission_date("");
        claim1Header.setRef_lab_num("");
        claim1Header.setMan_review("");
        claim1Header.setLocation(val.clinicNo());
        claim1Header.setDemographic_no(val.functionId());
        claim1Header.setProviderNo(afterPipe(val.providers()));
        claim1Header.setAppointment_no(val.appointmentNo());
        claim1Header.setDemographic_name(val.demoName());
        String[] temp = getPatientLF(val.demoName());
        claim1Header.setLast_name(temp[0]);
        claim1Header.setFirst_name(temp[1]);
        claim1Header.setSex(val.demoSex());
        claim1Header.setProvince(hctype);
        claim1Header.setBilling_date(val.apptDate());
        claim1Header.setBilling_time("00:00:00");
        claim1Header.setUpdate_datetime(UtilDateUtilities.getToday("yyyy-MM-dd HH:mm:ss"));
        claim1Header.setTotal(totalForCsvCodes(val.svcCode()));
        claim1Header.setPaid("");
        claim1Header.setStatus(getStatus(val.xmlBilltype()));
        claim1Header.setComment("");
        claim1Header.setVisittype(firstTwo(val.xmlVisittype()));
        claim1Header.setProvider_ohip_no(beforePipe(val.providers()));
        claim1Header.setProvider_rma_no("");
        claim1Header.setApptProvider_no(val.apptProvider());
        claim1Header.setAsstProvider_no("");
        claim1Header.setCreator(val.creator());

        return claim1Header;
    }

    private List<BillingItemData> getItemObj(BillingSpecClaimRequest val) {
        return Arrays.stream(val.svcCode().split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .map(code -> item(code, val.apptDate(), val.dxCode()))
                .toList();
    }

    private BillingClaimHeader1Data getClaimHeader1InrObj(BillingSpecClaimRequest val) {
        BillingClaimHeader1Data claim1Header = new BillingClaimHeader1Data();

        claim1Header.setTransc_id(BillingDataHlp.CLAIMHEADER1_TRANSACTIONIDENTIFIER);
        claim1Header.setRec_id(BillingDataHlp.CLAIMHEADER1_REORDIDENTIFICATION);
        String[] hinVer = getHinVer(val.demoHin());
        claim1Header.setHin(hinVer[0]);
        claim1Header.setVer(hinVer[1]);
        claim1Header.setDob(val.demoDob());
        String hctype = normalizeHcType(val.demoHctype());

        claim1Header.setPay_program(getPayProgram(val.xmlBilltype(), hctype));
        claim1Header.setPayee(!val.payMethod().isEmpty() ? val.payMethod() : BillingDataHlp.CLAIMHEADER1_PAYEE);
        claim1Header.setRef_num("");
        claim1Header.setFacilty_num("");
        claim1Header.setAdmission_date("");
        claim1Header.setRef_lab_num("");
        claim1Header.setMan_review("");
        claim1Header.setLocation(val.clinicNo());
        claim1Header.setDemographic_no(val.functionId());
        claim1Header.setProviderNo(afterPipe(val.providers()));
        claim1Header.setAppointment_no(val.appointmentNo());
        claim1Header.setDemographic_name(val.demoName());
        String[] temp = getPatientLF(val.demoName());
        claim1Header.setLast_name(temp[0]);
        claim1Header.setFirst_name(temp[1]);
        claim1Header.setSex(val.demoSex());
        claim1Header.setProvince(hctype);
        claim1Header.setBilling_date(val.apptDate());
        claim1Header.setBilling_time("00:00:00");
        claim1Header.setUpdate_datetime(UtilDateUtilities.getToday("yyyy-MM-dd HH:mm:ss"));
        claim1Header.setTotal(feeForCode(val.svcCode()));
        claim1Header.setPaid("");
        claim1Header.setStatus(getStatus(val.xmlBilltype()));
        claim1Header.setComment("");
        claim1Header.setVisittype(firstTwo(val.xmlVisittype()));
        claim1Header.setProvider_ohip_no(beforePipe(val.providers()));
        claim1Header.setProvider_rma_no("");
        claim1Header.setApptProvider_no(val.apptProvider());
        claim1Header.setAsstProvider_no("");
        claim1Header.setCreator(val.creator());

        return claim1Header;
    }

    private BillingItemData getItemInrObj(BillingSpecClaimRequest val) {
        return item(val.svcCode(), val.apptDate(), val.dxCode());
    }

    private BillingItemData item(String serviceCode, String serviceDate, String dxCode) {
        BillingItemData claimItem = new BillingItemData();
        claimItem.setTransc_id(BillingDataHlp.ITEM_TRANSACTIONIDENTIFIER);
        claimItem.setRec_id(BillingDataHlp.ITEM_REORDIDENTIFICATION);
        claimItem.setService_code(serviceCode);
        claimItem.setFee(feeForCode(serviceCode));
        claimItem.setSer_num("1");
        claimItem.setService_date(serviceDate);
        claimItem.setDx(dxCode);
        claimItem.setDx1("");
        claimItem.setDx2("");
        claimItem.setStatus("O");
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
        List<?> attrs = serviceCodeLoader.getBillingCodeAttr(serviceCode);
        if (attrs == null || attrs.size() <= 2 || attrs.get(2) == null) {
            return "";
        }
        return attrs.get(2).toString();
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
            ret = BillingDataHlp.CLAIMHEADER1_PAYMENTPROGRAM_PRIVATE;
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
