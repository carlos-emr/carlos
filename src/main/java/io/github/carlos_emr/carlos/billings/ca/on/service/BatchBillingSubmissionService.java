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

import java.util.Date;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.commn.dao.BatchBillingDAO;
import io.github.carlos_emr.carlos.commn.model.BatchBilling;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;

/**
 * Transactional write side for expanding selected batch-billing rows into
 * concrete Ontario bills.
 *
 * @since 2026-05-01
 */
@Service
@Transactional
public class BatchBillingSubmissionService {

    public record Row(String serviceCode, String dxCode, Integer demographicNo, String providerNo) { }
    public record RowFailure(int rowIndex, Row row, String message) { }
    public record SubmitResult(int submittedCount, List<RowFailure> failures) { }

    private final BillingOnHeaderCreationService headerCreationService;
    private final BatchBillingDAO batchBillingDAO;

    public BatchBillingSubmissionService(BillingOnHeaderCreationService headerCreationService,
                                         BatchBillingDAO batchBillingDAO) {
        this.headerCreationService = headerCreationService;
        this.batchBillingDAO = batchBillingDAO;
    }

    public SubmitResult submitAll(List<Row> rows, String clinicView, Date billingDate, String currentUser) {
        if (currentUser == null || currentUser.isBlank()) {
            throw new SecurityException("missing current user for batch billing submission");
        }
        List<RowFailure> failures = validateRows(rows);
        if (!failures.isEmpty()) {
            return new SubmitResult(0, failures);
        }
        int submitted = 0;
        for (Row row : rows) {
            List<BatchBilling> batchBillingList = batchBillingDAO.findForUpdate(row.demographicNo(), row.serviceCode());
            if (batchBillingList.isEmpty()) {
                throw new BillingValidationException(
                        "Batch billing row disappeared for demographicNo=" + row.demographicNo()
                                + " serviceCode=" + row.serviceCode());
            }
            BatchBilling batchBilling = batchBillingList.get(0);
            if (batchBilling.getLastBilledDate() != null) {
                throw new BillingValidationException(
                        "Batch billing row was already billed for demographicNo=" + row.demographicNo()
                                + " serviceCode=" + row.serviceCode());
            }
            String total = headerCreationService.createBill(row.providerNo(), row.demographicNo(),
                    row.serviceCode(), row.dxCode(), clinicView, billingDate, currentUser);
            batchBilling.setBillingAmount(total);
            batchBilling.setLastBilledDate(billingDate);
            batchBillingDAO.merge(batchBilling);
            submitted++;
        }
        return new SubmitResult(submitted, List.of());
    }

    private List<RowFailure> validateRows(List<Row> rows) {
        List<RowFailure> failures = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            try {
                headerCreationService.validateBillableDemographic(row.demographicNo());
            } catch (BillingValidationException e) {
                failures.add(new RowFailure(i, row, e.getMessage()));
            }
        }
        return failures;
    }
}
