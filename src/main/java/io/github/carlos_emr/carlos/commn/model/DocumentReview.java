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

package io.github.carlos_emr.carlos.commn.model;

import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

@Entity
@Table(name = "document_review")
public class DocumentReview extends AbstractModel<Integer> implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    private Integer id;
    @Column(name = "document_no")
    private Integer documentNo;
    @Column(name = "provider_no")
    private String providerNo;
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "provider_no", referencedColumnName = "provider_no", insertable = false, updatable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    private Provider reviewer;
    @Column(name = "date_reviewed")
    @Temporal(TemporalType.TIMESTAMP)
    private Date dateTimeReviewed;

    public DocumentReview() {
    }

    public DocumentReview(Integer documentNo, String providerNo) {
        this.documentNo = documentNo;
        this.providerNo = providerNo;
    }

    public DocumentReview(Integer documentNo, String providerNo, Date dateTimeReviewed) {
        this.documentNo = documentNo;
        this.providerNo = providerNo;
        this.dateTimeReviewed = dateTimeReviewed;
    }

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

    public Provider getReviewer() {
        return reviewer;
    }

    public void setReviewer(Provider reviewer) {
        this.reviewer = reviewer;
    }

    public Date getDateTimeReviewed() {
        return dateTimeReviewed;
    }

    public void setDateTimeReviewed(Date dateTimeReviewed) {
        this.dateTimeReviewed = dateTimeReviewed;
    }

    public String getDateTimeReviewedString() {
        String dateTimeReviewedString;
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        try {
            dateTimeReviewedString = formatter.format(dateTimeReviewed);
        } catch (Exception e) {
            dateTimeReviewedString = "";
        }

        return dateTimeReviewedString;
    }


    @PrePersist
    protected void jpaPrePersist() {
        if (this.dateTimeReviewed == null) {
            this.dateTimeReviewed = new Date();
        }
    }

}
