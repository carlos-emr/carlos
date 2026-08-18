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

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingCodeAttribute;
import io.github.carlos_emr.carlos.billings.ca.on.dto.PrivateBillingCode;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.util.ConversionUtils;

/**
 * Read-only access to the {@code billing_service} table — billing-code lookup
 * helpers used by the correction-review and edit-private-code pages. Split out
 * of the former {@code BillingONServiceCodeService} (which combined reads and
 * writes under a misleading double-{@code Service} name); writes now live on
 * {@link ServiceCodePersister}.
 *
 * @since 2026-04-27
 */
@Service
@Transactional(readOnly = true)
public class ServiceCodeLoader {

    private final BillingServiceDao dao;

    /** Test-friendly constructor — package-private, takes a DAO mock directly. */
    ServiceCodeLoader(BillingServiceDao dao) {
        this.dao = dao;
    }

    public List<BillingCodeAttribute> getBillingCodeAttr(String serviceCode) {
        List<BillingCodeAttribute> ret = new ArrayList<>();
        List<BillingService> bs = dao.getBillingCodeAttr(serviceCode);
        for (BillingService b : bs) {
            ret.add(new BillingCodeAttribute(
                    serviceCode,
                    b.getDescription(),
                    b.getValue(),
                    b.getPercentage(),
                    ConversionUtils.toDateString(b.getBillingserviceDate()),
                    String.valueOf(b.getGstFlag())));
        }
        return ret;
    }

    public Properties getCodeDescByNames(List serviceCodeNames) {
        Properties ret = new Properties();
        List<String> serviceCodeList = new ArrayList<String>();
        serviceCodeList.addAll(serviceCodeNames);
        List<BillingService> bs = dao.findByServiceCodes(serviceCodeList);
        for (BillingService b : bs) {
            ret.setProperty(b.getServiceCode(), b.getDescription());
        }
        return ret;
    }

    /**
     * Passthrough to the DAO's projection-record return.
     *
     * <p>The {@link io.github.carlos_emr.carlos.commn.dao.BillingServiceDao#finAllPrivateCodes}
     * method does the JPQL constructor projection itself; the loader stays
     * present so the {@code @Transactional(readOnly=true)} boundary and the
     * security-context entry point remain stable for callers.</p>
     */
    public List<PrivateBillingCode> getPrivateBillingCodeDesc() {
        return dao.finAllPrivateCodes();
    }
}
