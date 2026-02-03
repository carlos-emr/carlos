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


package io.github.carlos_emr.carlos.providers.data;

import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Manages Fax number for providers
 */
public class ProviderFaxUpdater {

    private String faxColName;
    private String provider;
    private PropertyDao dao = SpringUtils.getBean(PropertyDao.class);
    private ClinicDAO clinicDao = (ClinicDAO) SpringUtils.getBean(ClinicDAO.class);

    public ProviderFaxUpdater(String p) {

        faxColName = new String("faxnumber");
        provider = p;
    }

    /**
     * Retrieve fax number for current providers first by querying property table then clinic table
     */
    public String getFax() {
        String faxNum = "";

        List<Property> props = dao.findByNameAndProvider(faxColName, provider);
        for (Property p : props) {
            faxNum = p.getValue();
        }

        if (faxNum.equals("")) {
            List<Clinic> clinics = clinicDao.findAll();
            for (Clinic c : clinics) {
                faxNum = c.getClinicFax();
            }
        }

        return faxNum;
    }

    /**
     * set fax number in property table
     */
    public boolean setFax(String fax) {

        Property p = new Property();
        List<Property> props = dao.findByNameAndProvider(faxColName, provider);
        if (props.size() > 0) {
            for (Property pp : props) {
                pp.setValue(fax);
                dao.merge(pp);
            }
        } else {
            p.setName(faxColName);
            p.setValue(fax);
            p.setProviderNo(provider);
            dao.persist(p);
        }
        return true;
    }

}
