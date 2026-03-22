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


package io.github.carlos_emr.carlos.dxresearch.bean;

import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.model.PartialDate;
import io.github.carlos_emr.carlos.utility.SpringUtils;


/**
 * Bean representing a diagnosis research entry associated with a patient.
 *
 * <p>Contains the diagnosis code, coding system type, status, date range,
 * provider information, and a human-readable description. The start date
 * is resolved through {@link PartialDateDao} to support partial-date
 * formatting in the Canadian healthcare context.</p>
 *
 * @since 2026-03-17
 */
public class dxResearchBean {
    private static final PartialDateDao partialDateDao = (PartialDateDao) SpringUtils.getBean(PartialDateDao.class);

    String description;
    String dxResearchNo;
    String dxSearchCode;
    String end_date;
    String start_date;
    String status;
    String type;
    String providerNo;


    /**
     * Default no-argument constructor.
     */
    public dxResearchBean() {
    }

    /**
     * Constructs a fully populated diagnosis research bean.
     *
     * @param description String the human-readable description of the diagnosis
     * @param dxResearchNo String the unique identifier for this research entry
     * @param dxSearchCode String the diagnosis code value
     * @param end_date String the end/update date of the research entry
     * @param start_date String the start date of the research entry
     * @param status String the status of the entry (e.g. "A" for active, "D" for deleted)
     * @param type String the coding system type (e.g. "icd9", "icd10")
     * @param providerNo String the provider number who created this entry
     */
    public dxResearchBean(String description,
                          String dxResearchNo,
                          String dxSearchCode,
                          String end_date,
                          String start_date,
                          String status,
                          String type,
                          String providerNo) {
        this.description = description;
        this.dxResearchNo = dxResearchNo;
        this.dxSearchCode = dxSearchCode;
        this.end_date = end_date;
        this.start_date = start_date;
        this.status = status;
        this.type = type;
        this.providerNo = providerNo;
    }

    /**
     * Returns the human-readable description of the diagnosis.
     *
     * @return String the diagnosis description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the human-readable description of the diagnosis.
     *
     * @param description String the diagnosis description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns the unique identifier for this diagnosis research entry.
     *
     * @return String the dx research number
     */
    public String getDxResearchNo() {
        return dxResearchNo;
    }

    /**
     * Sets the unique identifier for this diagnosis research entry.
     *
     * @param dxResearchNo String the dx research number
     */
    public void setDxResearchNo(String dxResearchNo) {
        this.dxResearchNo = dxResearchNo;
    }

    /**
     * Returns the diagnosis search code value.
     *
     * @return String the diagnosis code
     */
    public String getDxSearchCode() {
        return dxSearchCode;
    }

    public void setDxSearchCode(String dxSearchCode) {
        this.dxSearchCode = dxSearchCode;
    }

    public String getEnd_date() {
        return end_date;
    }

    public void setEnd_date(String end_date) {
        this.end_date = end_date;
    }

    public String getStart_date() {
        return partialDateDao.getDatePartial(start_date, PartialDate.DXRESEARCH, Integer.valueOf(dxResearchNo), PartialDate.DXRESEARCH_STARTDATE);
    }

    public void setStart_date(String start_date) {
        this.start_date = start_date;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public boolean equals(Object o) {
        if (o instanceof dxCodeSearchBean) {
            dxCodeSearchBean bean = (dxCodeSearchBean) o;
            return (dxSearchCode.equals(bean.getDxSearchCode()) && type.equals(bean.getType()));
        } else
            return super.equals(o);
    }
}
