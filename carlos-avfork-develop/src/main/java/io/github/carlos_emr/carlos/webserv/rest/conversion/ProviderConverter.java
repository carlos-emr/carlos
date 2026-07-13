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


import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ProviderTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.Sex1;
import org.springframework.stereotype.Component;
import io.github.carlos_emr.carlos.util.ConversionUtils;

@Component
public class ProviderConverter extends AbstractConverter<Provider, ProviderTo1> {

    @Override
    public Provider getAsDomainObject(LoggedInInfo loggedInInfo, ProviderTo1 t) throws ConversionException {
        Provider d = new Provider();

        d.setProviderNo(t.getProviderNo());
        d.setComments(t.getComments());
        d.setPhone(t.getPhone());
        d.setBillingNo(t.getBillingNo());
        d.setWorkPhone(t.getWorkPhone());
        d.setAddress(t.getAddress().getAddress());
        d.setTeam(t.getTeam());
        d.setStatus(ConversionUtils.toBoolString(t.isEnabled()));
        d.setLastUpdateDate(t.getLastUpdateDate());
        d.setProviderType(t.getProviderActivity());
        if (t.getSex() != null) {
            d.setSex(t.getSex().name());
        }
        d.setOhipNo(t.getOhipNo());
        d.setSpecialty(t.getSpecialty());
        d.setDob(t.getDob());
        d.setHsoNo(t.getHsoNo());
        d.setProviderActivity(t.getProviderActivity());
        d.setFirstName(t.getFirstName());
        d.setLastName(t.getLastName());
        d.setRmaNo(t.getRmaNo());
        d.setSignedConfidentiality(t.getSignedConfidentiality());
        d.setPractitionerNo(t.getPractitionerNo());
        d.setEmail(t.getEmail());
        d.setTitle(t.getTitle());
        d.setLastUpdateUser(t.getLastUpdateUser());
        d.setLastUpdateDate(t.getLastUpdateDate());

        return d;
    }

    @Override
    public ProviderTo1 getAsTransferObject(LoggedInInfo loggedInInfo, Provider d) throws ConversionException {
        ProviderTo1 t = new ProviderTo1();

        t.setProviderNo(d.getProviderNo());
        t.setComments(d.getComments());
        t.setPhone(d.getPhone());
        t.setBillingNo(d.getBillingNo());
        t.setWorkPhone(d.getWorkPhone());
        t.getAddress().setAddress(d.getAddress());
        t.setTeam(d.getTeam());
        t.setEnabled(ConversionUtils.fromBoolString(d.getStatus()));
        t.setLastUpdateDate(d.getLastUpdateDate());
        t.setProviderType(d.getProviderActivity());
        if (d.getSex() != null) {
            try {
                t.setSex(Sex1.valueOf(d.getSex().toUpperCase()));
            } catch (Exception e) {
                // swallow
            }
        }
        t.setOhipNo(d.getOhipNo());
        t.setSpecialty(d.getSpecialty());
        t.setDob(d.getDob());
        t.setHsoNo(d.getHsoNo());
        t.setProviderActivity(d.getProviderActivity());
        t.setFirstName(d.getFirstName());
        t.setLastName(d.getLastName());
        t.setName(d.getLastName() + ", " + d.getFirstName());
        t.setRmaNo(d.getRmaNo());
        t.setSignedConfidentiality(d.getSignedConfidentiality());
        t.setPractitionerNo(d.getPractitionerNo());
        t.setEmail(d.getEmail());
        t.setTitle(d.getTitle());
        t.setLastUpdateUser(d.getLastUpdateUser());
        t.setLastUpdateDate(d.getLastUpdateDate());
        return t;
    }

}
