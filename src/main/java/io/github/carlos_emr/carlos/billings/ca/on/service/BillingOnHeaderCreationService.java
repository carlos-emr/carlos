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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billing.CA.dao.GstControlDao;
import io.github.carlos_emr.carlos.billing.CA.model.GstControl;
import io.github.carlos_emr.carlos.billings.ca.on.BillingMoney;
import io.github.carlos_emr.carlos.billings.ca.on.support.BillingOnConstants;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingPercLimit;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;

/**
 * Builds and persists Ontario billing claim headers ({@link BillingONCHeader1})
 * for one or more demographics. Owns the cross-DAO orchestration that used to
 * sit on {@code BillingONCHeader1DaoImpl} (provider lookup, demographic
 * lookup, service-code resolution, GST percent application) so the DAO can
 * stay a pure data-access component.
 *
 * <p>Persistence still flows through {@link BillingONCHeader1Dao#persist} —
 * this service only orchestrates; it does not bypass the entity manager.</p>
 *
 * @since 2026-04-27
 */
@Service
@Transactional
public class BillingOnHeaderCreationService {

    private final BillingONCHeader1Dao headerDao;
    private final ProviderDao providerDao;
    private final DemographicDao demographicDao;
    private final BillingServiceDao billingServiceDao;
    private final GstControlDao gstControlDao;

    public BillingOnHeaderCreationService(BillingONCHeader1Dao headerDao,
                                          ProviderDao providerDao,
                                          DemographicDao demographicDao,
                                          BillingServiceDao billingServiceDao,
                                          GstControlDao gstControlDao) {
        this.headerDao = headerDao;
        this.providerDao = providerDao;
        this.demographicDao = demographicDao;
        this.billingServiceDao = billingServiceDao;
        this.gstControlDao = gstControlDao;
    }

    /** Create a single-line bill (no dx). Returns the dollar total as a string. */
    public String createBill(String provider, Integer demographic, String code,
                             String clinicRefCode, Date serviceDate, String curUser) {
        return createBillInternal(provider, demographic, List.of(code), List.of(),
                clinicRefCode, serviceDate, curUser);
    }

    /** Create a single-line bill with a dx. Returns the dollar total as a string. */
    public String createBill(String provider, Integer demographic, String code, String dxCode,
                             String clinicRefCode, Date serviceDate, String curUser) {
        return createBillInternal(provider, demographic, List.of(code), List.of(dxCode),
                clinicRefCode, serviceDate, curUser);
    }

    /**
     * Batch create — same provider + service codes + dx codes applied to every
     * supplied demographic. Returns the per-claim dollar total (the same value
     * for every demographic, since the codes/dx/date are shared).
     */
    public String createBills(String provider, List<String> demographicNos,
                              List<String> codes, List<String> dxcodes,
                              String clinicRefCode, Date serviceDate, String curUser) {
        Provider prov = providerDao.getProvider(provider);
        CarlosProperties properties = CarlosProperties.getInstance();
        List<Integer> parsedDemographicNos = demographicNos.stream()
                .map(Integer::parseInt)
                .toList();
        for (Integer demographicNo : parsedDemographicNos) {
            validateBillableDemographic(demographicNo);
        }
        String total = calcTotal(codes, serviceDate);

        for (Integer demographic : parsedDemographicNos) {
            BillingONCHeader1 header1 = assembleHeader1(
                    prov, demographic, clinicRefCode, serviceDate,
                    total, curUser, properties);
            addItems(header1, codes, dxcodes, serviceDate);
            headerDao.persist(header1);
        }
        return total;
    }

    /**
     * Validate that a demographic can be billed before a batch starts writing
     * any claim headers.
     */
    public void validateBillableDemographic(Integer demographicNo) {
        requireBillableDemographic(demographicNo);
    }

    private String createBillInternal(String provider, Integer demographic,
                                      List<String> codes, List<String> dxCodes,
                                      String clinicRefCode, Date serviceDate, String curUser) {
        Provider prov = providerDao.getProvider(provider);
        CarlosProperties properties = CarlosProperties.getInstance();
        String total = calcTotal(codes, serviceDate);

        BillingONCHeader1 header1 = assembleHeader1(
                prov, demographic, clinicRefCode, serviceDate, total, curUser, properties);
        addItems(header1, codes, dxCodes, serviceDate);
        headerDao.persist(header1);
        return total;
    }

    private BillingONCHeader1 assembleHeader1(Provider prov, Integer demographic, String clinicRefCode,
                                              Date serviceDate, String total, String curUser,
                                              CarlosProperties properties) {
        Demographic demo = requireBillableDemographic(demographic);

        BillingONCHeader1 header1 = new BillingONCHeader1();
        header1.setTranscId(BillingOnConstants.CLAIMHEADER1_TRANSACTIONIDENTIFIER);
        header1.setRecId(BillingOnConstants.CLAIMHEADER1_REORDIDENTIFICATION);
        header1.setHeaderId(0);
        header1.setHin(demo.getHin());
        header1.setVer(demo.getVer());
        header1.setDob(demo.getDateOfBirth());
        header1.setPayProgram(demo.getHcType().equals("ON") ? "HCP" : "RMB");
        header1.setPayee(BillingOnConstants.CLAIMHEADER1_PAYEE);
        header1.setRefNum("");
        header1.setFaciltyNum(clinicRefCode);
        header1.setRefLabNum("");
        header1.setManReview("");
        header1.setLocation(properties.getProperty("clinic_no", ""));
        header1.setDemographicNo(demographic);
        header1.setProviderNo(prov.getProviderNo());
        header1.setAppointmentNo(0);
        header1.setDemographicName(demo.getLastName() + "," + demo.getFirstName());
        header1.setSex(demo.getSex());
        header1.setProvince(demo.getHcType());
        header1.setBillingDate(serviceDate);
        header1.setBillingTime(serviceDate);
        header1.setPaid(new BigDecimal("0.00"));
        header1.setStatus("O");
        header1.setComment("");
        header1.setVisitType("00");
        header1.setProviderOhipNo(prov.getOhipNo());
        header1.setProviderRmaNo(prov.getRmaNo());
        header1.setApptProviderNo("");
        header1.setAsstProviderNo("");
        header1.setCreator(curUser);
        header1.setTotal(io.github.carlos_emr.carlos.billings.ca.on.BillingMoney
                .parseNonNegativeAmount(total, "total"));
        return header1;
    }

    private Demographic requireBillableDemographic(Integer demographicNo) {
        if (demographicNo == null) {
            throw new BillingValidationException("Cannot create batch bill: demographicNo is missing");
        }
        Demographic demo = demographicDao.getDemographicById(demographicNo);
        if (demo == null) {
            throw new BillingValidationException(
                    "Cannot create batch bill: demographic not found for demographicNo=" + demographicNo);
        }
        return demo;
    }

    private void addItems(BillingONCHeader1 h1, List<String> codes, List<String> dxcodes, Date serviceDate) {
        for (String code : codes) {
            BillingONItem item = new BillingONItem();
            item.setTranscId(BillingOnConstants.ITEM_TRANSACTIONIDENTIFIER);
            item.setRecId(BillingOnConstants.ITEM_REORDIDENTIFICATION);
            item.setServiceCode(code);

            BillingService billingService = billingServiceDao.searchBillingCode(code, "ON", serviceDate);
            item.setFee(billingService.getValue());
            item.setServiceCount("1");
            item.setServiceDate(serviceDate);
            item.setStatus("O");

            // Up to 3 dx codes spread across dx / dx1 / dx2; remaining slots blank.
            item.setDx(dxcodes.size() >= 1 ? dxcodes.get(0) : "");
            item.setDx1(dxcodes.size() >= 2 ? dxcodes.get(1) : "");
            item.setDx2(dxcodes.size() >= 3 ? dxcodes.get(2) : "");

            h1.addBillingItem(item);
        }
    }

    /**
     * Calculates the dollar total for a list of service codes on a given date,
     * applying the GST percent (when the code is GST-flagged) and any
     * percentage-based codes (with min/max clamps from {@link BillingPercLimit}).
     * Equivalent to the legacy DAO method this replaced — no behavioral change.
     */
    private String calcTotal(List<String> codes, Date serviceDate) {
        GstControl gstControl = gstControlDao.find(Integer.valueOf(1));
        BigDecimal total = BigDecimal.ZERO;
        List<BillingService> percentCodes = new ArrayList<>();

        for (String code : codes) {
            BillingService bs = billingServiceDao.searchBillingCode(code, "ON", serviceDate);
            if (bs == null) continue;

            if (bs.getPercentage() != null && !bs.getPercentage().isEmpty()) {
                percentCodes.add(bs);
                continue;
            }

            if (bs.getGstFlag()) {
                BigDecimal gst = gstControl.getGstPercent()
                        .divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP)
                        .multiply(BillingMoney.amount(bs.getValue()));
                total = total.add(gst).setScale(2, RoundingMode.HALF_UP);
            }
            total = total.add(BillingMoney.amount(bs.getValue()));
        }

        BigDecimal percBase = total;
        for (BillingService percentcode : percentCodes) {
            BigDecimal percent = BillingMoney.amount(percentcode.getPercentage());
            BigDecimal percentCalc = percBase.multiply(percent).setScale(2, RoundingMode.HALF_UP);
            BillingPercLimit limit = percentcode.getBillingPercLimit();
            if (limit != null) {
                percentCalc = percentCalc.min(BillingMoney.amount(limit.getMax()));
                percentCalc = percentCalc.max(BillingMoney.amount(limit.getMin()));
            }
            total = total.add(percentCalc);
        }
        return total.toString();
    }
}
