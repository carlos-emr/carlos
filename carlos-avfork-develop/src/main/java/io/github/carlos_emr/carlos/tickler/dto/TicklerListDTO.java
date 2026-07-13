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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.utility.LocaleUtils;

/**
 * Lightweight data transfer object for tickler list display, optimized for
 * JPQL constructor expression projection. Eliminates EAGER loading of full
 * entity graphs, reducing database queries from ~25 per page load (one per
 * tickler for demographic, creator, assignee relationships) to a small, fixed
 * set of batched queries.
 *
 * <p>Comments and links are populated via batch loading after the main query.</p>
 *
 * @since 2026-02-27
 */
public class TicklerListDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private String message;
    private Date serviceDate;
    private Date createDate;
    private Tickler.STATUS status;
    private Tickler.PRIORITY priority;
    private Integer demographicNo;
    private String demographicLastName;
    private String demographicFirstName;
    private String creatorLastName;
    private String creatorFirstName;
    private String assigneeLastName;
    private String assigneeFirstName;
    private List<TicklerCommentDTO> comments;
    private List<TicklerLinkDTO> links;

    /**
     * Default constructor required by frameworks.
     */
    public TicklerListDTO() {
    }

    /**
     * Projection constructor for JPQL constructor expressions. Parameter order
     * must match the SELECT NEW clause exactly.
     *
     * @param id Integer the tickler ID
     * @param message String the tickler message
     * @param serviceDate Date the service date
     * @param createDate Date the creation date
     * @param status Tickler.STATUS the tickler status
     * @param priority Tickler.PRIORITY the tickler priority
     * @param demographicNo Integer the patient demographic number
     * @param demographicLastName String the patient's last name
     * @param demographicFirstName String the patient's first name
     * @param creatorLastName String the creating provider's last name
     * @param creatorFirstName String the creating provider's first name
     * @param assigneeLastName String the assigned provider's last name
     * @param assigneeFirstName String the assigned provider's first name
     */
    public TicklerListDTO(Integer id, String message, Date serviceDate, Date createDate,
                          Tickler.STATUS status, Tickler.PRIORITY priority,
                          Integer demographicNo, String demographicLastName, String demographicFirstName,
                          String creatorLastName, String creatorFirstName,
                          String assigneeLastName, String assigneeFirstName) {
        this.id = id;
        this.message = message;
        this.serviceDate = serviceDate;
        this.createDate = createDate;
        this.status = status;
        this.priority = priority;
        this.demographicNo = demographicNo;
        this.demographicLastName = demographicLastName;
        this.demographicFirstName = demographicFirstName;
        this.creatorLastName = creatorLastName;
        this.creatorFirstName = creatorFirstName;
        this.assigneeLastName = assigneeLastName;
        this.assigneeFirstName = assigneeFirstName;
    }

    /**
     * Returns the patient's formatted name as "LastName, FirstName".
     *
     * @return String the formatted demographic name, or "N/A" if both names are null
     */
    public String getDemographicFormattedName() {
        return formatName(demographicLastName, demographicFirstName);
    }

    /**
     * Returns the creator provider's formatted name as "LastName, FirstName".
     *
     * @return String the formatted creator name, or "N/A" if both names are null
     */
    public String getCreatorFormattedName() {
        return formatName(creatorLastName, creatorFirstName);
    }

    /**
     * Returns the assignee provider's formatted name as "LastName, FirstName".
     *
     * @return String the formatted assignee name, or "N/A" if both names are null
     */
    public String getAssigneeFormattedName() {
        return formatName(assigneeLastName, assigneeFirstName);
    }

    /**
     * Returns a localized description of the tickler status using the same
     * resource bundle keys as {@link Tickler#getStatusDesc(Locale)}.
     *
     * @param locale Locale the locale for status description; may be null (falls back to default locale)
     * @return String the localized status description, or empty string if status is null
     */
    public String getStatusDesc(Locale locale) {
        if (status == null) {
            return "";
        }
        switch (status) {
            case A:
                return LocaleUtils.getMessage(locale, "tickler.ticklerMain.stActive");
            case C:
                return LocaleUtils.getMessage(locale, "tickler.ticklerMain.stComplete");
            case D:
                return LocaleUtils.getMessage(locale, "tickler.ticklerMain.stDeleted");
            default:
                return status.name();
        }
    }

    private String formatName(String lastName, String firstName) {
        if (lastName == null && firstName == null) {
            return "N/A";
        }
        StringBuilder sb = new StringBuilder();
        if (lastName != null) {
            sb.append(lastName);
        }
        if (firstName != null) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(firstName);
        }
        return sb.toString();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Date getServiceDate() {
        return serviceDate;
    }

    public void setServiceDate(Date serviceDate) {
        this.serviceDate = serviceDate;
    }

    public Date getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }

    public Tickler.STATUS getStatus() {
        return status;
    }

    public void setStatus(Tickler.STATUS status) {
        this.status = status;
    }

    public Tickler.PRIORITY getPriority() {
        return priority;
    }

    public void setPriority(Tickler.PRIORITY priority) {
        this.priority = priority;
    }

    public Integer getDemographicNo() {
        return demographicNo;
    }

    public void setDemographicNo(Integer demographicNo) {
        this.demographicNo = demographicNo;
    }

    public String getDemographicLastName() {
        return demographicLastName;
    }

    public void setDemographicLastName(String demographicLastName) {
        this.demographicLastName = demographicLastName;
    }

    public String getDemographicFirstName() {
        return demographicFirstName;
    }

    public void setDemographicFirstName(String demographicFirstName) {
        this.demographicFirstName = demographicFirstName;
    }

    public String getCreatorLastName() {
        return creatorLastName;
    }

    public void setCreatorLastName(String creatorLastName) {
        this.creatorLastName = creatorLastName;
    }

    public String getCreatorFirstName() {
        return creatorFirstName;
    }

    public void setCreatorFirstName(String creatorFirstName) {
        this.creatorFirstName = creatorFirstName;
    }

    public String getAssigneeLastName() {
        return assigneeLastName;
    }

    public void setAssigneeLastName(String assigneeLastName) {
        this.assigneeLastName = assigneeLastName;
    }

    public String getAssigneeFirstName() {
        return assigneeFirstName;
    }

    public void setAssigneeFirstName(String assigneeFirstName) {
        this.assigneeFirstName = assigneeFirstName;
    }

    public List<TicklerCommentDTO> getComments() {
        return comments;
    }

    public void setComments(List<TicklerCommentDTO> comments) {
        this.comments = comments != null ? new ArrayList<>(comments) : Collections.emptyList();
    }

    public List<TicklerLinkDTO> getLinks() {
        return links;
    }

    public void setLinks(List<TicklerLinkDTO> links) {
        this.links = links != null ? new ArrayList<>(links) : Collections.emptyList();
    }
}
