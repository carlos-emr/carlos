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

import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Manages Fax number for providers
 */
public class ProviderColourUpdater {

    private String strColName;
    private String provider;

    /**
     * Creates a new instance of ProviderColourUpdater
     */
    public ProviderColourUpdater(String p) {
        strColName = "ProviderColour";
        provider = p;
    }

    /**
     * Retrieve colour for current providers first by querying property table
     */
    public String getColour() {
        PropertyDao dao = SpringUtils.getBean(PropertyDao.class);
        List<Property> props = dao.findByNameAndProvider(strColName, provider);
        for (Property prop : props) {
            return prop.getValue() != null ? prop.getValue() : "";
        }
        return "";
    }

    /**
     * set colour in property table
     */
    public boolean setColour(String c) {
        PropertyDao dao = SpringUtils.getBean(PropertyDao.class);
        List<Property> props = dao.findByNameAndProvider(strColName, provider);
        Property property = null;
        for (Property p : props) {
            property = p;
            break;
        }

        if (property == null) {
            property = new Property();
        }

        property.setValue(c);
        property.setName(strColName);
        property.setProviderNo(provider);

        dao.saveEntity(property);

        return true;
    }
}
