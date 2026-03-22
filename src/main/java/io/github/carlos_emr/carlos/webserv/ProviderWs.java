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

package io.github.carlos_emr.carlos.webserv;

import java.util.List;

import jakarta.jws.WebService;

import org.apache.cxf.annotations.GZIP;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.transfer_objects.ProviderPropertyTransfer;
import io.github.carlos_emr.carlos.webserv.transfer_objects.ProviderTransfer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * SOAP web service endpoint for healthcare provider operations in the inter-EMR Integrator system.
 *
 * <p>Provides methods for retrieving and synchronizing provider information
 * (including properties and credentials) between CARLOS EMR installations.
 *
 * @since 2012-08-13
 */
@WebService(targetNamespace = "http://ws.oscarehr.org/")
@Component
@GZIP(threshold = AbstractWs.GZIP_THRESHOLD)
public class ProviderWs extends AbstractWs {
    @Autowired
    private ProviderManager2 providerManager;

    public ProviderTransfer getLoggedInProviderTransfer() {
        LoggedInInfo loggedInInfo = getLoggedInInfo();
        return (ProviderTransfer.toTransfer(loggedInInfo.getLoggedInProvider()));
    }

    /**
     * @deprecated 2013-03-27 parameter should have been an object to allow nulls
     */
    @Deprecated
    public ProviderTransfer[] getProviders(boolean active) {
        return (getProviders2(active));
    }

    public ProviderTransfer[] getProviders2(Boolean active) {
        List<Provider> tempResults = providerManager.getProviders(getLoggedInInfo(), active);
        ProviderTransfer[] results = ProviderTransfer.toTransfers(tempResults);
        return (results);
    }

    public ProviderPropertyTransfer[] getProviderProperties(String providerNo, String propertyName) {
        List<Property> tempResults = providerManager.getProviderProperties(getLoggedInInfo(), providerNo, propertyName);
        ProviderPropertyTransfer[] results = ProviderPropertyTransfer.toTransfers(tempResults);
        return (results);
    }
}
