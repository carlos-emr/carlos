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
package io.github.carlos_emr.carlos.web;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

/**
 * JAXB-serializable bean representing a patient appointment list with its associated JSP template.
 *
 * <p>Wraps a collection of {@link PatientListApptItemBean} entries along with a configurable
 * JSP template path for rendering the list. Defaults to "patientlist/patientList1.jsp".
 *
 * @since 2012-08-13
 */
@XmlRootElement
@XmlType(name = "", propOrder = {"template", "patients"})
public class PatientListApptBean implements Serializable {

    private String template = "patientlist/patientList1.jsp";

    private List<PatientListApptItemBean> patients = new ArrayList<PatientListApptItemBean>();


    /**
     * Returns the JSP template path used to render this patient list.
     *
     * @return String the relative path to the JSP template
     */
    public String getTemplate() {
        return template;
    }


    /**
     * Sets the JSP template path used to render this patient list.
     *
     * @param template String the relative path to the JSP template
     */
    public void setTemplate(String template) {
        this.template = template;
    }


    /**
     * Returns the list of patient appointment items.
     *
     * @return List&lt;PatientListApptItemBean&gt; the patient appointment items
     */
    public List<PatientListApptItemBean> getPatients() {
        return patients;
    }


    /**
     * Sets the list of patient appointment items.
     *
     * @param patients List&lt;PatientListApptItemBean&gt; the patient appointment items to set
     */
    public void setPatients(List<PatientListApptItemBean> patients) {
        this.patients = patients;
    }


}
