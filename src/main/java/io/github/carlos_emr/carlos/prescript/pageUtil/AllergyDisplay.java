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


package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.commn.model.Allergy;

/**
 * Display transfer object for allergy information in the prescription UI.
 * <p>
 * Wraps allergy data with formatted display values including severity, onset,
 * and type descriptions derived from the {@link Allergy} model. Used to pass
 * allergy display data between the action layer and JSP views.
 *
 * @since 2026-03-17
 */
public final class AllergyDisplay {
    private Integer id;
    private String entryDate;
    private String description;
    private int typeCode;
    private String severityCode;
    private String onSetCode;
    private String reaction;
    private String startDate;
    private String archived;
    private String lastUpdateDate;

    public Integer getId() {
        return (id);
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getEntryDate() {
        return (entryDate);
    }

    public void setEntryDate(String entryDate) {
        this.entryDate = entryDate;
    }

    public String getDescription() {
        return (description);
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getTypeCode() {
        return (typeCode);
    }

    public void setTypeCode(int typeCode) {
        this.typeCode = typeCode;
    }

    public String getSeverityCode() {
        return (severityCode);
    }

    public void setSeverityCode(String severityCode) {
        this.severityCode = severityCode;
    }

    public String getOnSetCode() {
        return (onSetCode);
    }

    public void setOnSetCode(String onSetCode) {
        this.onSetCode = onSetCode;
    }

    public String getReaction() {
        return (reaction);
    }

    public void setReaction(String reaction) {
        this.reaction = reaction;
    }

    public String getStartDate() {
        return (startDate);
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getOnSetDesc() {
        return (Allergy.getOnSetOfReactionDesc(onSetCode));
    }

    public String getSeverityDesc() {
        return (Allergy.getSeverityOfReactionDesc(severityCode));
    }

    public String getTypeDesc() {
        return (Allergy.getTypeDesc(typeCode));
    }

    public String getArchived() {
        return archived;
    }

    public void setArchived(String archived) {
        this.archived = archived;
    }

    public String getLastUpdateDate() {
        return lastUpdateDate;
    }

    public void setLastUpdateDate(String lastUpdateDate) {
        this.lastUpdateDate = lastUpdateDate;
    }

}
