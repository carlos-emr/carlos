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
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.commn.dao.BatchBillingDAO;
import io.github.carlos_emr.carlos.commn.model.BatchBilling;

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

    private final BillingOnHeaderCreationService headerCreationService;
    private final BatchBillingDAO batchBillingDAO;

    public BatchBillingSubmissionService(BillingOnHeaderCreationService headerCreationService,
                                         BatchBillingDAO batchBillingDAO) {
        this.headerCreationService = headerCreationService;
        this.batchBillingDAO = batchBillingDAO;
    }

    public void submitAll(List<Row> rows, String clinicView, Date billingDate, String currentUser) {
        for (Row row : rows) {
            String total = headerCreationService.createBill(row.providerNo(), row.demographicNo(),
                    row.serviceCode(), row.dxCode(), clinicView, billingDate, currentUser);

            List<BatchBilling> batchBillingList = batchBillingDAO.find(row.demographicNo(), row.serviceCode());
            BatchBilling batchBilling = batchBillingList.get(0);
            batchBilling.setBillingAmount(total);
            batchBilling.setLastBilledDate(billingDate);
            batchBillingDAO.merge(batchBilling);
        }
    }
}
