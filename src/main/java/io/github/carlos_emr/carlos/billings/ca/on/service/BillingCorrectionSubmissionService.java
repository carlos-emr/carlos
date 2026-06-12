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

import io.github.carlos_emr.MyDateFormat;
import io.github.carlos_emr.carlos.billing.CA.dao.BillingDetailDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillingDetail;
import io.github.carlos_emr.carlos.billings.ca.on.command.BillingCorrectionSubmitCommand;
import io.github.carlos_emr.carlos.billings.ca.on.command.BillingCorrectionSubmitItemCommand;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingCorrectionCodedTokenValidator;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.RecycleBinDao;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.commn.model.RecycleBin;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSafe;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Persists the reviewed correction.
     *
     * @param loggedInInfo the authenticated provider context used for recycle-bin ownership
     * @param command the reviewed correction payload, including the hidden stored-content blob
     * @throws BillingValidationException if the billing number is malformed, the
     *         billing record is missing, or the content blob structure/tokens fail
     *         validation (the blob round-trips through a browser hidden field, so
     *         review-time validation alone is bypassable)
     */
    public void submit(LoggedInInfo loggedInInfo, BillingCorrectionSubmitCommand command) {
        // Re-check the hidden stored content before persistence; a tampered
        // browser field must not bypass review-time XML and coded-token guards.
        BillingCorrectionCodedTokenValidator.validateStoredContent(command.content());
        int billingNo = parseBillingNo(command.billingNo());
        Billing billing = billingDao.find(billingNo);
        if (billing == null) {
            throw new BillingValidationException("Billing correction rejected: billing record not found [billingNo="
                    + LogSafe.sanitize(command.billingNo()) + "]");
        }

        GregorianCalendar now = new GregorianCalendar();

        RecycleBin recycleBin = new RecycleBin();
        recycleBin.setProviderNo(loggedInInfo.getLoggedInProviderNo());
        recycleBin.setTableName("billing");
        recycleBin.setTableContent(command.content());
        recycleBin.setUpdateDateTime(new java.util.Date());
        recycleBinDao.persist(recycleBin);

        for (BillingDetail bd : billingDetailDao.findAllIncludingDeletedByBillingNo(billingNo)) {
            bd.setStatus("D");
            billingDetailDao.merge(bd);
        }

        billing.setHin(command.hin());
        billing.setDob(command.dobText());
        billing.setVisitType(command.visitType());
        billing.setVisitDate(ConversionUtils.fromDateString(command.visitDateText()));
        billing.setClinicRefCode(command.clinicRefCode());
        billing.setProviderNo(command.providerNo());
        billing.setStatus(command.status());
        billing.setUpdateDate(ConversionUtils.fromDateString(
                now.get(Calendar.YEAR) + "-" + (now.get(Calendar.MONTH) + 1) + "-" + now.get(Calendar.DAY_OF_MONTH)));
        billing.setTotal(command.total().amount().toString());
        billing.setContent(command.content());
        billingDao.merge(billing);

        for (BillingCorrectionSubmitItemCommand item : command.items()) {
            BillingDetail bd = new BillingDetail();
            bd.setBillingNo(billingNo);
            bd.setServiceCode(item.serviceCode());
            bd.setServiceDesc(item.description());
            bd.setBillingAmount(item.serviceValueStored());
            bd.setDiagnosticCode(item.diagCode());
            bd.setAppointmentDate(MyDateFormat.getSysDate(command.billingDateText()));
            bd.setStatus(command.status());
            bd.setBillingUnit(item.quantityText());
            billingDetailDao.persist(bd);
        }
    }

    private static int parseBillingNo(String billingNo) {
        try {
            return Integer.parseInt(billingNo);
        } catch (NumberFormatException e) {
            throw new BillingValidationException("Billing correction rejected: invalid billing number ["
                    + LogSafe.sanitize(billingNo) + "]", e);
        }
    }

}
