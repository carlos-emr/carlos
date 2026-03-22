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
package io.github.carlos_emr.carlos.webserv.rest.conversion;

import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicExtTo1;
import org.springframework.stereotype.Component;

@Component
/**
 * Converter for transforming DemographicExt domain entities to demographic extension transfer objects.
 *
 * @since 2012-08-13
 */
public class DemographicExtConverter extends AbstractConverter<DemographicExt, DemographicExtTo1> {

    @Override
    public DemographicExt getAsDomainObject(LoggedInInfo loggedInInfo, DemographicExtTo1 t) throws ConversionException {
        DemographicExt d = new DemographicExt();

        d.setId(t.getId());
        d.setDemographicNo(t.getDemographicNo());
        d.setProviderNo(t.getProviderNo());
        d.setKey(t.getKey());
        d.setValue(t.getValue());
        d.setDateCreated(t.getDateCreated());
        d.setHidden(t.isHidden());
        return d;
    }

    @Override
    public DemographicExtTo1 getAsTransferObject(LoggedInInfo loggedInInfo, DemographicExt d) throws ConversionException {
        DemographicExtTo1 t = new DemographicExtTo1();

        t.setId(d.getId());
        t.setDemographicNo(d.getDemographicNo());
        t.setProviderNo(d.getProviderNo());
        t.setKey(d.getKey());
        t.setValue(d.getValue());
        t.setDateCreated(d.getDateCreated());
        t.setHidden(d.isHidden());
        return t;
    }

}
