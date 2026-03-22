/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 *
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.tickler.dto;

import java.io.Serializable;
import java.util.Date;

import org.apache.commons.lang3.time.DateUtils;

/**
 * Lightweight data transfer object for tickler comment display, used for
 * batch loading comments to avoid N+1 query problems when displaying
 * tickler lists.
 *
 * @since 2026-02-27
 */
public class TicklerCommentDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private Integer ticklerNo;
    private String message;
    private Date updateDate;
    private String providerLastName;
    private String providerFirstName;

    /**
     * Default constructor required by frameworks.
     */
    public TicklerCommentDTO() {
    }

    /**
     * Projection constructor for JPQL constructor expressions.
     *
     * @param id Integer the comment ID
     * @param ticklerNo Integer the parent tickler ID
     * @param message String the comment message
     * @param updateDate Date the comment update date
     * @param providerLastName String the commenting provider's last name
     * @param providerFirstName String the commenting provider's first name
     */
    public TicklerCommentDTO(Integer id, Integer ticklerNo, String message, Date updateDate,
                             String providerLastName, String providerFirstName) {
        this.id = id;
        this.ticklerNo = ticklerNo;
        this.message = message;
        this.updateDate = updateDate;
        this.providerLastName = providerLastName;
        this.providerFirstName = providerFirstName;
    }

    /**
     * Returns the commenting provider's formatted name as "LastName, FirstName".
     *
     * @return String the formatted provider name, or empty string if both names are null
     */
    public String getProviderFormattedName() {
        if (providerLastName == null && providerFirstName == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (providerLastName != null) {
            sb.append(providerLastName);
        }
        if (providerFirstName != null) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(providerFirstName);
        }
        return sb.toString();
    }

    /**
     * Checks whether the comment's update date falls on the same calendar day
     * as the provided reference date. Callers should pass a pre-computed
     * reference to avoid creating a new {@link Date} instance per comment.
     *
     * @param referenceDate Date the date to compare against (typically today)
     * @return boolean true if the update date is the same calendar day as referenceDate
     */
    public boolean isSameDayAs(Date referenceDate) {
        return updateDate != null && referenceDate != null && DateUtils.isSameDay(updateDate, referenceDate);
    }

    /** @return Integer the comment ID */
    public Integer getId() {
        return id;
    }

    /** @param id Integer the comment ID */
    public void setId(Integer id) {
        this.id = id;
    }

    /** @return Integer the parent tickler ID */
    public Integer getTicklerNo() {
        return ticklerNo;
    }

    /** @param ticklerNo Integer the parent tickler ID */
    public void setTicklerNo(Integer ticklerNo) {
        this.ticklerNo = ticklerNo;
    }

    /** @return String the comment message text */
    public String getMessage() {
        return message;
    }

    /** @param message String the comment message text */
    public void setMessage(String message) {
        this.message = message;
    }

    /** @return Date the comment update date */
    public Date getUpdateDate() {
        return updateDate;
    }

    /** @param updateDate Date the comment update date */
    public void setUpdateDate(Date updateDate) {
        this.updateDate = updateDate;
    }

    /** @return String the commenting provider's last name */
    public String getProviderLastName() {
        return providerLastName;
    }

    /** @param providerLastName String the commenting provider's last name */
    public void setProviderLastName(String providerLastName) {
        this.providerLastName = providerLastName;
    }

    /** @return String the commenting provider's first name */
    public String getProviderFirstName() {
        return providerFirstName;
    }

    /** @param providerFirstName String the commenting provider's first name */
    public void setProviderFirstName(String providerFirstName) {
        this.providerFirstName = providerFirstName;
    }
}
