/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.allergy.dto;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

import io.github.carlos_emr.carlos.commn.model.Allergy;

/**
 * Lightweight DTO for allergy list views. Carries the 14 fields needed for
 * medication safety display out of Allergy's 91 fields.
 *
 * <p>Omits: HICL/HIC/AGCSP/AGCCS internal codes, ATC, regional identifier,
 * drug ref ID, position, transient pharmacological/chemical/substance fields.</p>
 *
 * @since 2026-04-11
 */
public class AllergyListItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private Integer demographicNo;
    private Date entryDate;
    private String description;
    private String reaction;
    private Boolean archived;
    private Boolean nonDrug;
    private Integer typeCode;
    private Date startDate;
    private String severityOfReaction;
    private String onsetOfReaction;
    private String lifeStage;
    private String reactionType;
    private String providerNo;

    /** Default constructor for serialization/framework binding. */
    public AllergyListItemDTO() {
    }

    /**
     * Projection constructor for JPQL constructor expressions.
     *
     * @param id Integer the allergy record ID
     * @param demographicNo Integer the patient demographic number
     * @param entryDate Date the date the allergy was entered
     * @param description String the allergy description
     * @param reaction String the reaction description
     * @param archived Boolean whether the allergy is archived (null-safe for projections)
     * @param nonDrug Boolean whether this is a non-drug allergy
     * @param typeCode Integer the allergy type code
     * @param startDate Date the allergy start date
     * @param severityOfReaction String the severity of reaction
     * @param onsetOfReaction String the onset of reaction
     * @param lifeStage String the life stage
     * @param reactionType String the reaction type
     * @param providerNo String the provider number
     * @since 2026-04-11
     */
    public AllergyListItemDTO(Integer id, Integer demographicNo, Date entryDate, String description,
                              String reaction, Boolean archived, Boolean nonDrug, Integer typeCode,
                              Date startDate, String severityOfReaction, String onsetOfReaction,
                              String lifeStage, String reactionType, String providerNo) {
        this.id = id;
        this.demographicNo = demographicNo;
        this.entryDate = entryDate;
        this.description = description;
        this.reaction = reaction;
        this.archived = archived;
        this.nonDrug = nonDrug;
        this.typeCode = typeCode;
        this.startDate = startDate;
        this.severityOfReaction = severityOfReaction;
        this.onsetOfReaction = onsetOfReaction;
        this.lifeStage = lifeStage;
        this.reactionType = reactionType;
        this.providerNo = providerNo;
    }

    /**
     * Creates an AllergyListItemDTO from a full Allergy entity.
     *
     * @param a Allergy the entity to convert; must not be null
     * @return AllergyListItemDTO a lightweight projection
     */
    public static AllergyListItemDTO fromEntity(Allergy a) {
        Objects.requireNonNull(a, "Allergy entity must not be null for DTO conversion");
        return new AllergyListItemDTO(
                a.getId(), a.getDemographicNo(), a.getEntryDate(), a.getDescription(),
                a.getReaction(), a.getArchived(), a.isNonDrug(), a.getTypeCode(),
                a.getStartDate(), a.getSeverityOfReaction(), a.getOnsetOfReaction(),
                a.getLifeStage(), a.getReactionType(), a.getProviderNo()
        );
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getDemographicNo() { return demographicNo; }
    public void setDemographicNo(Integer demographicNo) { this.demographicNo = demographicNo; }
    public Date getEntryDate() { return entryDate; }
    public void setEntryDate(Date entryDate) { this.entryDate = entryDate; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getReaction() { return reaction; }
    public void setReaction(String reaction) { this.reaction = reaction; }
    public Boolean getArchived() { return archived; }
    public void setArchived(Boolean archived) { this.archived = archived; }
    public Boolean getNonDrug() { return nonDrug; }
    public void setNonDrug(Boolean nonDrug) { this.nonDrug = nonDrug; }
    public Integer getTypeCode() { return typeCode; }
    public void setTypeCode(Integer typeCode) { this.typeCode = typeCode; }
    public Date getStartDate() { return startDate; }
    public void setStartDate(Date startDate) { this.startDate = startDate; }
    public String getSeverityOfReaction() { return severityOfReaction; }
    public void setSeverityOfReaction(String severityOfReaction) { this.severityOfReaction = severityOfReaction; }
    public String getOnsetOfReaction() { return onsetOfReaction; }
    public void setOnsetOfReaction(String onsetOfReaction) { this.onsetOfReaction = onsetOfReaction; }
    public String getLifeStage() { return lifeStage; }
    public void setLifeStage(String lifeStage) { this.lifeStage = lifeStage; }
    public String getReactionType() { return reactionType; }
    public void setReactionType(String reactionType) { this.reactionType = reactionType; }
    public String getProviderNo() { return providerNo; }
    public void setProviderNo(String providerNo) { this.providerNo = providerNo; }
}
