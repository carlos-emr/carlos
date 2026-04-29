/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.MyDateFormat;
import io.github.carlos_emr.carlos.billing.CA.dao.BillingDetailDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillingDetail;
import io.github.carlos_emr.carlos.billings.ca.on.command.BillingCorrectionSubmitCommand;
import io.github.carlos_emr.carlos.billings.ca.on.command.BillingCorrectionSubmitItemCommand;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.RecycleBinDao;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.commn.model.RecycleBin;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Persists a reviewed ON billing correction from typed submit data.
 */
@Service
@Transactional
public class BillingCorrectionSubmissionService {

    private final BillingDetailDao billingDetailDao;
    private final RecycleBinDao recycleBinDao;
    private final BillingDao billingDao;

    public BillingCorrectionSubmissionService(BillingDetailDao billingDetailDao,
                                              RecycleBinDao recycleBinDao,
                                              BillingDao billingDao) {
        this.billingDetailDao = billingDetailDao;
        this.recycleBinDao = recycleBinDao;
        this.billingDao = billingDao;
    }

    public void submit(LoggedInInfo loggedInInfo, BillingCorrectionSubmitCommand command) {
        int billingNo = parseBillingNo(command.billingNo());
        String total = command.total().isEmpty() ? "000" : command.total();

        GregorianCalendar now = new GregorianCalendar();

        RecycleBin recycleBin = new RecycleBin();
        recycleBin.setProviderNo(loggedInInfo.getLoggedInProviderNo());
        recycleBin.setTableName("billing");
        recycleBin.setTableContent(command.content());
        recycleBin.setUpdateDateTime(new java.util.Date());
        recycleBinDao.persist(recycleBin);

        for (BillingDetail bd : billingDetailDao.findByBillingNo(billingNo)) {
            bd.setStatus("D");
            billingDetailDao.merge(bd);
        }

        Billing billing = billingDao.find(billingNo);
        if (billing != null) {
            billing.setHin(command.hin());
            billing.setDob(command.dob());
            billing.setVisitType(command.visitType());
            billing.setVisitDate(ConversionUtils.fromDateString(command.visitDate()));
            billing.setClinicRefCode(command.clinicRefCode());
            billing.setProviderNo(command.providerNo());
            billing.setStatus(command.status());
            billing.setUpdateDate(ConversionUtils.fromDateString(
                    now.get(Calendar.YEAR) + "-" + (now.get(Calendar.MONTH) + 1) + "-" + now.get(Calendar.DAY_OF_MONTH)));
            billing.setTotal(parseStoredTotal(total).movePointLeft(2).toString());
            billing.setContent(command.content());
            billingDao.merge(billing);
        }

        for (BillingCorrectionSubmitItemCommand item : command.items()) {
            BillingDetail bd = new BillingDetail();
            bd.setBillingNo(billingNo);
            bd.setServiceCode(item.serviceCode());
            bd.setServiceDesc(item.description());
            bd.setBillingAmount(item.serviceValue());
            bd.setDiagnosticCode(item.diagCode());
            bd.setAppointmentDate(MyDateFormat.getSysDate(command.billingDate()));
            bd.setStatus(command.status());
            bd.setBillingUnit(item.quantity());
            billingDetailDao.persist(bd);
        }
    }

    private static int parseBillingNo(String billingNo) {
        try {
            return Integer.parseInt(billingNo);
        } catch (NumberFormatException e) {
            throw new BillingValidationException("Billing correction rejected: invalid billing number [" + billingNo + "]", e);
        }
    }

    private static BigDecimal parseStoredTotal(String total) {
        try {
            return new BigDecimal(total);
        } catch (NumberFormatException e) {
            throw new BillingValidationException("Billing correction rejected: invalid total [" + total + "]", e);
        }
    }
}
