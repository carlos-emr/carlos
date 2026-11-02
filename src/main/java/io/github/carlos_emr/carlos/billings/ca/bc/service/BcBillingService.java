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
package io.github.carlos_emr.carlos.billings.ca.bc.service;

import io.github.carlos_emr.carlos.billings.MSP.ExtractBean;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Owns the legacy extract's generated log identifiers and all related billing status changes. */
@Service
@Transactional(rollbackFor = Exception.class)
public class BcBillingService {
    private final BillingmasterDAO billingmasterDao;

    public BcBillingService(@Qualifier("billingmasterDAO") BillingmasterDAO billingmasterDao) {
        this.billingmasterDao = billingmasterDao;
    }

    /** Builds the output before committing; publishes the HTML file only after a successful return. */
    public void generateExtract(ExtractBean extract) {
        extract.buildExtract();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                extract.publishExtract();
            }
        });
    }

    public void markBillBilled(int id) {
        billingmasterDao.markBcBillBilled(id);
    }

    public void markMasterBilled(int id) {
        billingmasterDao.markBcMasterBilled(id);
    }
}
