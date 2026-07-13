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
package io.github.carlos_emr.carlos.prevention.dto;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

import io.github.carlos_emr.carlos.commn.model.Prevention;

/**
 * Lightweight data transfer object for immunization/prevention history list views,
 * optimized for JPQL constructor expression projection. Eliminates the EAGER-loaded
 * PreventionExt collection that is fetched on every Prevention entity load.
 *
 * <p>Omits: entire {@code preventionExts} EAGER collection (lot number, route,
 * dose, site, comments), {@code snomedId}, transient {@code preventionExtendedProperties}.</p>
 *
 * @since 2026-04-11
 */
public class PreventionListItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private Integer demographicNo;
    private String preventionType;
    private Date preventionDate;
    private Date creationDate;
    private String providerNo;
    private String creatorProviderNo;
    private char deleted = '0';
    private char refused = '0';
    private char never = '0';
    private Date nextDate;
    private Date lastUpdateDate;

    /** Default constructor for serialization/framework binding. */
    public PreventionListItemDTO() {
    }

    /**
     * Projection constructor for JPQL constructor expressions.
     *
     * @param id Integer the prevention record ID
     * @param demographicNo Integer the patient demographic number
     * @param preventionType String the prevention/vaccine type name
     * @param preventionDate Date the date the prevention was administered
     * @param creationDate Date the record creation date
     * @param providerNo String the administering provider number
     * @param creatorProviderNo String the record creator provider number
     * @param deleted char deletion flag ('0'=active, '1'=deleted)
     * @param refused char refusal/status flag ('0'=active, '1'=refused, '2'=ineligible, '3'=completedExternally)
     * @param never char never-administer flag ('0'=active, '1'=never)
     * @param nextDate Date the next scheduled date
     * @param lastUpdateDate Date the last update timestamp
     */
    public PreventionListItemDTO(Integer id, Integer demographicNo, String preventionType,
                                 Date preventionDate, Date creationDate, String providerNo,
                                 String creatorProviderNo, char deleted, char refused, char never,
                                 Date nextDate, Date lastUpdateDate) {
        this.id = id;
        this.demographicNo = demographicNo;
        this.preventionType = preventionType;
        this.preventionDate = preventionDate;
        this.creationDate = creationDate;
        this.providerNo = providerNo;
        this.creatorProviderNo = creatorProviderNo;
        this.deleted = deleted;
        this.refused = refused;
        this.never = never;
        this.nextDate = nextDate;
        this.lastUpdateDate = lastUpdateDate;
    }

    /**
     * Creates a DTO from a full {@link Prevention} entity. Preserves the raw
     * multi-valued {@code refused} status ('0'..'3') via {@code getRefusedRawValue()}
     * — the boolean accessor {@code isRefused()} would collapse '2' (ineligible) and
     * '3' (completed externally) back to '0', which this helper avoids.
     *
     * @param p Prevention the entity to convert; must not be null
     * @return PreventionListItemDTO a lightweight projection
     * @since 2026-04-12
     */
    public static PreventionListItemDTO fromEntity(Prevention p) {
        Objects.requireNonNull(p, "Prevention entity must not be null for DTO conversion");
        int refusedRaw = p.getRefusedRawValue();
        char refused = (refusedRaw >= 0 && refusedRaw <= 3) ? (char) ('0' + refusedRaw) : '0';
        return new PreventionListItemDTO(
                p.getId(), p.getDemographicId(), p.getPreventionType(),
                p.getPreventionDate(), p.getCreationDate(), p.getProviderNo(),
                p.getCreatorProviderNo(),
                p.isDeleted() ? '1' : '0',
                refused,
                p.isNever() ? '1' : '0',
                p.getNextDate(), p.getLastUpdateDate()
        );
    }

    /**
     * Returns whether this prevention record is active (not deleted, not refused, not never).
     *
     * @return boolean true if the record is active
     */
    public boolean isActive() {
        return deleted == '0' && refused == '0' && never == '0';
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getDemographicNo() { return demographicNo; }
    public void setDemographicNo(Integer demographicNo) { this.demographicNo = demographicNo; }
    public String getPreventionType() { return preventionType; }
    public void setPreventionType(String preventionType) { this.preventionType = preventionType; }
    public Date getPreventionDate() { return preventionDate; }
    public void setPreventionDate(Date preventionDate) { this.preventionDate = preventionDate; }
    public Date getCreationDate() { return creationDate; }
    public void setCreationDate(Date creationDate) { this.creationDate = creationDate; }
    public String getProviderNo() { return providerNo; }
    public void setProviderNo(String providerNo) { this.providerNo = providerNo; }
    public String getCreatorProviderNo() { return creatorProviderNo; }
    public void setCreatorProviderNo(String creatorProviderNo) { this.creatorProviderNo = creatorProviderNo; }
    public char getDeleted() { return deleted; }
    public void setDeleted(char deleted) { this.deleted = deleted; }
    public char getRefused() { return refused; }
    public void setRefused(char refused) { this.refused = refused; }
    public char getNever() { return never; }
    public void setNever(char never) { this.never = never; }
    public Date getNextDate() { return nextDate; }
    public void setNextDate(Date nextDate) { this.nextDate = nextDate; }
    public Date getLastUpdateDate() { return lastUpdateDate; }
    public void setLastUpdateDate(Date lastUpdateDate) { this.lastUpdateDate = lastUpdateDate; }
}
