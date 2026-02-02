//CHECKSTYLE:OFF
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
package io.github.carlos_emr.carlos.webserv.rest.to.model;

import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.util.Date;

@XmlRootElement
public class DocumentReviewTo1 implements Serializable {
    private Integer id;
    private Integer documentNo;
    private String providerNo;
    private ProviderTo1 reviewer;
    private Date dateTimeReviewed;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getDocumentNo() {
        return documentNo;
    }

    public void setDocumentNo(Integer documentNo) {
        this.documentNo = documentNo;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public ProviderTo1 getReviewer() {
        return reviewer;
    }

    public void setReviewer(ProviderTo1 reviewer) {
        this.reviewer = reviewer;
    }

    public Date getDateTimeReviewed() {
        return dateTimeReviewed;
    }

    public void setDateTimeReviewed(Date dateTimeReviewed) {
        this.dateTimeReviewed = dateTimeReviewed;
    }
}
