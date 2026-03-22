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

import io.github.carlos_emr.carlos.commn.model.DemographicMerged;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicMergedTo1;

/**
 * Converter for transforming DemographicMerged domain entities to merged demographic transfer objects.
 *
 * @since 2012-08-13
 */
public class DemographicMergedConverter extends AbstractConverter<DemographicMerged, DemographicMergedTo1> {

    @Override
    public DemographicMerged getAsDomainObject(LoggedInInfo loggedInInfo, DemographicMergedTo1 t) throws ConversionException {
        DemographicMerged d = new DemographicMerged();

        d.setId(t.getId());
        d.setDemographicNo(t.getDemographicNo());
        d.setMergedTo(t.getMergedTo());
        d.setDeleted(t.getDeleted());
        d.setLastUpdateUser(t.getLastUpdateUser());
        d.setLastUpdateDate(t.getLastUpdateDate());
        return d;
    }

    @Override
    public DemographicMergedTo1 getAsTransferObject(LoggedInInfo loggedInInfo, DemographicMerged d) throws ConversionException {
        DemographicMergedTo1 t = new DemographicMergedTo1();

        t.setId(d.getId());
        t.setDemographicNo(d.getDemographicNo());
        t.setMergedTo(d.getMergedTo());
        t.setDeleted(d.getDeleted());
        t.setLastUpdateUser(d.getLastUpdateUser());
        t.setLastUpdateDate(d.getLastUpdateDate());
        return t;
    }

}
