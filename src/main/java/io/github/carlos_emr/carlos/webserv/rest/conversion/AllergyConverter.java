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
 */
package io.github.carlos_emr.carlos.webserv.rest.conversion;

import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.AllergyTo1;

import io.github.carlos_emr.carlos.util.StringUtils;

public class AllergyConverter extends AbstractConverter<Allergy, AllergyTo1> {

    @Override
    public Allergy getAsDomainObject(LoggedInInfo loggedInInfo, AllergyTo1 t) throws ConversionException {
        throw new ConversionException("not yet implemented");
    }

    @Override
    public AllergyTo1 getAsTransferObject(LoggedInInfo loggedInInfo, Allergy d) throws ConversionException {
        AllergyTo1 t = new AllergyTo1();

        t.setAgccs(d.getAgccs());
        t.setAgcsp(d.getAgcsp());
        t.setAgeOfOnset(d.getAgeOfOnset());
        t.setArchived(d.getArchived());
        t.setDemographicNo(d.getDemographicNo());
        t.setDescription(d.getDescription());
        t.setDrugrefId(d.getDrugrefId());
        t.setEntryDate(d.getEntryDate());
        t.setHiclSeqno(d.getHiclSeqno());
        t.setHicSeqno(d.getHicSeqno());
        t.setId(d.getId());
        t.setLastUpdateDate(d.getLastUpdateDate());
        t.setLifeStage(d.getLifeStage());
        t.setOnsetOfReaction(d.getOnsetOfReaction());
        t.setPosition(d.getPosition());
        t.setProviderNo(d.getProviderNo());
        t.setReaction(StringUtils.isNullOrEmpty(d.getReaction()) ? "No reaction noted" : d.getReaction());
        t.setRegionalIdentifier(d.getRegionalIdentifier());
        t.setSeverityOfReaction(d.getSeverityOfReaction());
        t.setStartDate(d.getStartDate());
        t.setTypeCode(d.getTypeCode());

        return t;
    }


}