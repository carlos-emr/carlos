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
package io.github.carlos_emr.carlos.renal;

import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlSeeAlso;

/**
 * JAXB-serializable wrapper containing a list of CKD screening report items.
 *
 * <p>Serves as the root element for XML serialization of the full CKD screening
 * report, wrapping a collection of {@link CKDReportContainer} instances.</p>
 *
 * @since 2026-03-17
 */
@XmlRootElement(name = "CkdScreeningReport")
@XmlSeeAlso(CKDReportContainer.class)
public class CkdScreeningReportContainer {

    private List<CKDReportContainer> items = new ArrayList<CKDReportContainer>();

    /**
     * Returns the list of CKD screening report items.
     *
     * @return List&lt;CKDReportContainer&gt; the report items
     */
    public List<CKDReportContainer> getItems() {
        return items;
    }

    /**
     * Sets the list of CKD screening report items.
     *
     * @param items List&lt;CKDReportContainer&gt; the report items
     */
    public void setItems(List<CKDReportContainer> items) {
        this.items = items;
    }


}
