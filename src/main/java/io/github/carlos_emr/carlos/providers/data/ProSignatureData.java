/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.providers.data;


import io.github.carlos_emr.carlos.casemgmt.model.ProviderExt;
import io.github.carlos_emr.carlos.commn.dao.ProviderExtDao;

import io.github.carlos_emr.carlos.utility.SpringUtils;


/**
 * Reads and stores signature text on a provider's extension row.
 * Callers are responsible for authorization before accessing provider data.
 *
 * @since 2026-09-17
 */
public class ProSignatureData {

    private ProviderExtDao providerExtDao = SpringUtils.getBean(ProviderExtDao.class);

    /**
     * Checks for non-null signature text, including a deliberately empty signature.
     *
     * @param proNo provider identifier
     * @return whether the provider has a row with non-null signature text
     */
    public boolean hasSignature(String proNo) {
        boolean retval = false;

        ProviderExt pe = providerExtDao.find(proNo);
        if (pe != null && pe.getSignature() != null) {
            retval = true;
        }

        return retval;
    }

    /**
     * Retrieves the provider's stored signature.
     *
     * @param providerNo provider identifier
     * @return stored text (possibly null), or an empty string when no row exists
     */
    public String getSignature(String providerNo) {
        String retval = "";
        ProviderExt pe = providerExtDao.find(providerNo);
        if (pe != null) {
            retval = pe.getSignature();
        }
        return retval;
    }

    /**
     * Updates an existing provider row, including one with a null signature, or
     * creates a row when none exists. Row existence does not depend on signature text.
     *
     * @param providerNo provider identifier
     * @param signature replacement signature text, possibly null
     */
    public void enterSignature(String providerNo, String signature) {
        ProviderExt existing = providerExtDao.find(providerNo);
        if (existing == null) {
            ProviderExt created = new ProviderExt();
            created.setProviderNo(providerNo);
            created.setSignature(signature);
            providerExtDao.persist(created);
        } else {
            existing.setSignature(signature);
            providerExtDao.merge(existing);
        }
    }
}
