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

package io.github.carlos_emr.carlos.eform.data;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * JAXB root element for eForm database access point configuration XML.
 * Replaces Apache Commons Digester3 parsing of apconfig.xml.
 *
 * @since 2006-05-25
 */
@XmlRootElement(name = "eformap-config")
@XmlAccessorType(XmlAccessType.FIELD)
public class EFormApConfig {

    @XmlElement(name = "databaseap")
    private List<DatabaseAP> databaseAPs = new ArrayList<>();

    /**
     * Returns the list of database access point definitions.
     *
     * @return List of {@link DatabaseAP} instances parsed from the XML
     */
    public List<DatabaseAP> getDatabaseAPs() {
        return databaseAPs;
    }

    /**
     * Sets the list of database access point definitions.
     *
     * @param databaseAPs List of {@link DatabaseAP} instances to set
     */
    public void setDatabaseAPs(List<DatabaseAP> databaseAPs) {
        this.databaseAPs = databaseAPs;
    }
}
