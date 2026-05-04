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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.billing.CA.dao.BillingInrDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillingInr;

/**
 * Persists INR billing records submitted from the Ontario billing report flow.
 *
 * @since 2026-05-03
 */
@Service
@Transactional
public class BillingInrCreationService {

    private final BillingInrDao billingInrDao;

    public BillingInrCreationService(BillingInrDao billingInrDao) {
        this.billingInrDao = billingInrDao;
    }

    /**
     * Build and persist one INR billing record.
     *
     * @param command validated request values for the new record
     * @return persisted entity instance
     */
    public BillingInr create(Command command) {
        BillingInr bi = new BillingInr();
        bi.setDemographicNo(command.demographicNo());
        bi.setDemographicName(cap(command.demographicName(), 60));
        bi.setHin(cap(command.hin(), 12));
        bi.setDob(cap(command.dob(), 8));
        bi.setProviderNo(cap(command.providerNo(), 10));
        bi.setProviderOhipNo(cap(command.providerOhipNo(), 20));
        bi.setProviderRmaNo(cap(command.providerRmaNo(), 20));
        bi.setCreator(cap(command.creator(), 6));
        bi.setDiagnosticCode(cap(command.diagnosticCode(), 3));
        bi.setServiceCode(cap(command.serviceCode(), 6));
        bi.setServiceDesc(cap(command.serviceDesc(), 255));
        bi.setBillingAmount(cap(command.billingAmount(), 6));
        bi.setBillingUnit(cap(command.billingUnit(), 1));
        bi.setCreateDateTime(new Date());
        bi.setStatus("N");

        billingInrDao.persist(bi);
        return bi;
    }

    private static String cap(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        return value.length() > maxLen ? value.substring(0, maxLen) : value;
    }

    /** Validated INR create request values. */
    public record Command(int demographicNo,
                          String demographicName,
                          String hin,
                          String dob,
                          String providerNo,
                          String providerOhipNo,
                          String providerRmaNo,
                          String creator,
                          String diagnosticCode,
                          String serviceCode,
                          String serviceDesc,
                          String billingAmount,
                          String billingUnit) {
    }
}
