/**
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

import java.util.Date;
import java.util.List;

/**
 * @author Eugene Katyukhin
 */
public class PatientEndYearStatementInvoiceBean {
    private Date invoiceDate;
    private int invoiceNo;
    private String invoiced;
    private String paid;
    private List<PatientEndYearStatementServiceBean> services;

    public PatientEndYearStatementInvoiceBean() {
    }

    public PatientEndYearStatementInvoiceBean(int invoiceNo, Date invoiceDate,
                                              String invoiced, String paid) {
        super();
        this.invoiceDate = invoiceDate;
        this.invoiceNo = invoiceNo;
        this.invoiced = invoiced;
        this.paid = paid;
    }

    public Date getInvoiceDate() {
        return invoiceDate;
    }

    public void setInvoiceDate(Date invoiceDate) {
        this.invoiceDate = invoiceDate;
    }

    public int getInvoiceNo() {
        return invoiceNo;
    }

    public void setInvoiceNo(int invoiceNo) {
        this.invoiceNo = invoiceNo;
    }

    public String getInvoiced() {
        return invoiced;
    }

    public void setInvoiced(String invoiced) {
        this.invoiced = invoiced;
    }

    public String getPaid() {
        return paid;
    }

    public void setPaid(String paid) {
        this.paid = paid;
    }

    public List<PatientEndYearStatementServiceBean> getServices() {
        return services;
    }

    public void setServices(List<PatientEndYearStatementServiceBean> services) {
        this.services = services;
    }

}
