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


package io.github.carlos_emr.carlos.billings.ca.bc.Teleplan;

import java.util.List;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Deals with storing the teleplan response
 *
 * @author jay
 */
public class TeleplanMessagesDAO {
    static Logger log = MiscUtils.getLogger();
    private PropertyDao propertyDao = SpringUtils.getBean(PropertyDao.class);

    public TeleplanMessagesDAO() {
    }

    private void setMessage(String sequenceNum) {
        Property p = new Property();
        p.setName("teleplan_message");
        p.setValue(sequenceNum);
        propertyDao.persist(p);
    }

    private void updateMessage(String sequenceNum) {
        List<Property> ps = propertyDao.findByName("teleplan_message");
        for (Property p : ps) {
            p.setValue(String.valueOf(sequenceNum));
            propertyDao.merge(p);
        }
    }

    private boolean hasMessage() {
        boolean hasSequence = false;
        List<Property> ps = propertyDao.findByName("teleplan_message");
        if (!ps.isEmpty())
            hasSequence = true;
        return hasSequence;
    }

    public void saveUpdateMessage(String sequenceNum) {
        if (hasMessage()) {
            updateMessage(sequenceNum);
        } else {
            setMessage(sequenceNum);
        }
    }
}
