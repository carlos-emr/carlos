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
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Owns the legacy extract's log rows and billing status changes in one REQUIRED transaction.
 * Use the Spring-managed proxy so writes join that boundary. The caller assigns the report
 * filename and explicitly writes HTML; generation performs no file I/O.
 *
 * @since 2026-11-02
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class BcBillingService {
    private final BillingmasterDAO billingmasterDao;

    /**
     * Creates the service with the qualified legacy BC billing DAO.
     *
     * @param billingmasterDao BillingmasterDAO whose participating writes require this transaction
     */
    public BcBillingService(@Qualifier("billingmasterDAO") BillingmasterDAO billingmasterDao) {
        this.billingmasterDao = billingmasterDao;
    }

    /**
     * Starts or joins the class-level Spring transaction and builds the extract within it.
     * Requires an actual transaction and synchronization before any build work can write rows.
     * Build failures roll back log rows and status updates. HTML remains in memory so the caller
     * can assign a filename before explicitly writing the report.
     *
     * @param extract ExtractBean containing the prepared provider/date/export selection
     * @throws RuntimeException if building the extract fails; participating writes are rolled back
     * @throws IllegalStateException if invoked without an active transaction and synchronization
     */
    public void generateExtract(ExtractBean extract) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("A transaction is required to generate a BC billing extract");
        }
        extract.buildExtract();
    }

    /**
     * Starts or joins the service transaction and marks its invoice billed.
     *
     * @param id int billing primary key
     * @throws RuntimeException if the participating persistence write fails
     */
    public void markBillBilled(int id) {
        billingmasterDao.markBcBillBilled(id);
    }

    /**
     * Starts or joins the service transaction and marks its claim billed.
     *
     * @param id int billingmaster primary key
     * @throws RuntimeException if the participating persistence write fails
     */
    public void markMasterBilled(int id) {
        billingmasterDao.markBcMasterBilled(id);
    }
}
