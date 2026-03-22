/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.commn.model;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.integration.fhir.interfaces.ImmunizationInterface;

/**
 * Represents a prevention/immunization record in the CARLOS EMR system.
 *
 * <p>Maps to the {@code preventions} table and tracks immunizations, screenings, and other
 * preventive healthcare measures administered to patients. Implements the
 * {@link ImmunizationInterface} for FHIR interoperability, allowing prevention records
 * to be exposed as standardized immunization resources.</p>
 *
 * <p>Prevention records support multiple states:</p>
 * <ul>
 *   <li><strong>Administered:</strong> normal completed prevention with a date</li>
 *   <li><strong>Refused:</strong> patient declined the prevention (refused='1')</li>
 *   <li><strong>Ineligible:</strong> patient is not eligible (refused='2')</li>
 *   <li><strong>Completed Externally:</strong> done outside this clinic (refused='3')</li>
 *   <li><strong>Never:</strong> prevention should never be given (never='1')</li>
 *   <li><strong>Deleted:</strong> soft-deleted record (deleted='1')</li>
 * </ul>
 *
 * <p>Extended properties (lot number, route, dose, manufacturer, etc.) are stored in
 * the related {@link PreventionExt} table via a bidirectional one-to-many relationship.
 * The {@link ImmunizationInterface} methods delegate to these extension properties.</p>
 *
 * @see PreventionExt
 * @see ImmunizationInterface
 * @see DemographicData
 * @since 2012-01-11
 */
@Entity
@Table(name = "preventions")
public class Prevention extends AbstractModel<Integer> implements Serializable, ImmunizationInterface, DemographicData {

    private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id = null;

    @Column(name = "demographic_no")
    private Integer demographicId = null;

    @Column(name = "creation_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date creationDate = new Date();

    @Column(name = "prevention_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date preventionDate = null;

    @Column(name = "provider_no")
    private String providerNo = null;

    @Column(name = "prevention_type")
    private String preventionType = null;

    private char deleted = '0';
    private char refused = '0';
    private char never = '0';

    @Column(name = "next_date")
    @Temporal(TemporalType.DATE)
    private Date nextDate = null;

    @Column(name = "creator")
    private String creatorProviderNo = null;

    private Date lastUpdateDate = null;

    // This is a bi-directional relationship
    @OneToMany(mappedBy = "prevention", fetch = FetchType.EAGER)
    private List<PreventionExt> preventionExts;

    @Transient
    private HashMap<String, String> preventionExtendedProperties;

    public Prevention() {
        this.preventionExts = new ArrayList<PreventionExt>();
    }

    private String snomedId = null;

    public void setDemographicId(Integer demographicId) {
        this.demographicId = demographicId;
    }

    public Integer getDemographicId() {
        return this.demographicId;
    }

    public int getDemographicNo() {
        return this.demographicId;
    }

    public Date getPreventionDate() {
        return preventionDate;
    }

    public void setPreventionDate(Date preventionDate) {
        this.preventionDate = preventionDate;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public String getPreventionType() {
        return preventionType;
    }

    public void setPreventionType(String preventionType) {
        this.preventionType = preventionType;
    }

    /**
     * Returns whether this prevention record has been soft-deleted.
     *
     * @return boolean {@code true} if the record is marked as deleted
     */
    public boolean isDeleted() {
        return deleted == '1';
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted ? '1' : '0';
    }

    /**
     * Returns whether the patient refused this prevention.
     *
     * @return boolean {@code true} if the prevention was refused by the patient
     */
    public boolean isRefused() {
        return refused == '1';
    }

    /**
     * Returns whether the patient is ineligible for this prevention.
     *
     * @return boolean {@code true} if the patient is ineligible
     */
    public boolean isIneligible() {
        return refused == '2';
    }

    /**
     * Returns whether this prevention was completed at an external facility.
     *
     * @return boolean {@code true} if completed outside this clinic
     */
    public boolean isCompletedExternally() {
        return refused == '3';
    }

    public void setRefused(boolean refused) {
        this.refused = refused ? '1' : '0';
    }

    public void setIneligible(boolean ineligible) {
        this.refused = ineligible ? '2' : '0';
    }

    public void setCompletedExternally(boolean completedExternally) {
        this.refused = completedExternally ? '3' : '0';
    }

    public Date getNextDate() {
        return nextDate;
    }

    public void setNextDate(Date nextDate) {
        this.nextDate = nextDate;
    }

    public boolean isNever() {
        return never == '1';
    }

    public void setNever(boolean never) {
        this.never = never ? '1' : '0';
    }

    public String getCreatorProviderNo() {
        return creatorProviderNo;
    }

    public void setCreatorProviderNo(String creatorProviderNo) {
        this.creatorProviderNo = creatorProviderNo;
    }

    public Date getLastUpdateDate() {
        return lastUpdateDate;
    }

    public void setLastUpdateDate(Date lastUpdateDate) {
        this.lastUpdateDate = lastUpdateDate;
    }

    public String getSnomedId() {
        return snomedId;
    }

    public void setSnomedId(String snomedId) {
        this.snomedId = snomedId;
    }

    @Override
    public Integer getId() {
        return id;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    @PreUpdate
    @PrePersist
    protected void autoSetUpdateTime() {
        lastUpdateDate = new Date();
    }

    public String getDeletedRawValue() {
        return String.valueOf(deleted);
    }

    public List<PreventionExt> getPreventionExts() {
        return this.preventionExts;
    }

    public void setPreventionExts(List<PreventionExt> preventionExts) {
        this.preventionExts = preventionExts;
    }

    /**
     * Adds an extension property to this prevention record and establishes
     * the bidirectional relationship.
     *
     * @param preventionExt PreventionExt the extension property to add
     * @return PreventionExt the added extension property
     */
    public PreventionExt addPreventionExt(PreventionExt preventionExt) {
        getPreventionExts().add(preventionExt);
        preventionExt.setPrevention(this);

        return preventionExt;
    }

    /**
     * Adds an immunization property as an extension record using the specified key and value.
     *
     * @param key ImmunizationProperty the property key (e.g., lot, route, dose)
     * @param value String the property value
     */
    public void addPreventionExt(ImmunizationProperty key, String value) {
        PreventionExt preventionExt = new PreventionExt();
        preventionExt.setKeyval(key.name());
        preventionExt.setVal(value);

        addPreventionExt(preventionExt);
    }

    public PreventionExt removePreventionExt(PreventionExt preventionExt) {
        getPreventionExts().remove(preventionExt);
        preventionExt.setPrevention(null);

        return preventionExt;
    }

    public HashMap<String, String> getPreventionExtendedProperties() {
        if (this.preventionExtendedProperties == null) {
            this.preventionExtendedProperties = new HashMap<String, String>();
        }
        return this.preventionExtendedProperties;
    }

    /**
     * There is no listener for this method.
     * This method needs to be invoked "manually" after this entity is instantiated and loaded
     * ie: Prevention.setPreventionExtendedProperties()
     */
    public void setPreventionExtendedProperties() {
        if (this.getPreventionExts() != null) {
            for (PreventionExt property : preventionExts) {
                setPreventionExtendedProperty(property);
            }
        }
    }

    public void setPreventionExtendedProperty(PreventionExt property) {
        getPreventionExtendedProperties().put(property.getkeyval(), property.getVal());
    }

    /**
     * Retrieves the value of a specific immunization property from the extended properties map.
     *
     * @param immunizationProperty ImmunizationProperty the property to retrieve
     * @return String the property value, or {@code null} if not set
     */
    public String getImmunizationProperty(ImmunizationProperty immunizationProperty) {
        return getPreventionExtendedProperties().get(immunizationProperty.name());
    }

    @Override
    public String getLotNo() {
        return getImmunizationProperty(ImmunizationProperty.lot);
    }


    @Override
    public void setLotNo(String lotNo) {
        addPreventionExt(ImmunizationProperty.lot, lotNo);
    }


    @Override
    public String getRoute() {
        return getImmunizationProperty(ImmunizationProperty.route);
    }


    @Override
    public void setRoute(String route) {
        addPreventionExt(ImmunizationProperty.route, route);
    }


    @Override
    public String getDose() {
        return getImmunizationProperty(ImmunizationProperty.dose);
    }


    @Override
    public void setDose(String dose) {
        addPreventionExt(ImmunizationProperty.dose, dose);
    }


    @Override
    public String getComment() {
        return getImmunizationProperty(ImmunizationProperty.comments);
    }


    @Override
    public void setComment(String comment) {
        addPreventionExt(ImmunizationProperty.comments, comment);
    }

    @Override
    public void setImmunizationRefused(boolean refused) {
        this.setRefused(refused);
    }

    @Override
    public boolean getImmunizationRefused() {
        return this.isRefused();
    }


    @Override
    public String getImmunizationRefusedReason() {
        return getImmunizationProperty(ImmunizationProperty.neverReason);
    }


    @Override
    public void setImmunizationRefusedReason(String reason) {
        addPreventionExt(ImmunizationProperty.neverReason, reason);
    }


    @Override
    public String getManufacture() {
        return getImmunizationProperty(ImmunizationProperty.manufacture);
    }


    @Override
    public void setManufacture(String manufacture) {
        addPreventionExt(ImmunizationProperty.manufacture, manufacture);
    }


    @Override
    public String getName() {
        return getImmunizationProperty(ImmunizationProperty.name);
    }


    @Override
    public void setName(String name) {
        addPreventionExt(ImmunizationProperty.name, name);
    }


    @Override
    public String getImmunizationType() {
        return this.preventionType;
    }


    @Override
    public void setImmunizationType(String immunizationType) {
        this.setPreventionType(immunizationType);
    }


    @Override
    public java.util.Date getImmunizationDate() {
        return this.getPreventionDate();
    }


    @Override
    public void setImmunizationDate(java.util.Date immunizationDate) {
        this.setPreventionDate(immunizationDate);
    }

    @Override
    public String getSite() {
        return getImmunizationProperty(ImmunizationProperty.location);
    }

    @Override
    public void setSite(String site) {
        addPreventionExt(ImmunizationProperty.location, site);
    }


    @Override
    public String getVaccineCode2() {
        String brandSnomedId = getImmunizationProperty(ImmunizationProperty.brandSnomedId);
        if (!StringUtils.isEmpty(brandSnomedId)) {
            return brandSnomedId;
        }
        return null;
    }

    @Override
    public void setVaccineCode2(String vaccineCode) {
        addPreventionExt(ImmunizationProperty.brandSnomedId, vaccineCode);
    }

    @Override
    public String getVaccineCode() {

        return getSnomedId();
    }

    @Override
    public void setVaccineCode(String vaccineCode) {
        setSnomedId(vaccineCode);
    }

    @Override
    public boolean isPrimarySource() {
        return !isCompletedExternally();
    }

    @Override
    public void setPrimarySource(boolean truefalse) {
        setCompletedExternally(!truefalse);
    }

    @Override
    public java.util.Date getExpiryDate() {
        String datestring = getImmunizationProperty(ImmunizationProperty.expiryDate);
        Date date = null;

        if (datestring != null) {
            try {
                date = dateFormat.parse(datestring);
            } catch (ParseException e) {
                MiscUtils.getLogger().warn("Given Immunization expiry date [" + datestring + "] was not parsable into a Date object");
            }
        }

        return date;
    }

    @Override
    public void setExpiryDate(java.util.Date expiryDate) {
        String datestring = "";
        if (expiryDate != null) {
            datestring = dateFormat.format(expiryDate);
        }
        addPreventionExt(ImmunizationProperty.expiryDate, datestring);
    }

    /**
     * So far, only immunizations have the "dose" key.
     */
    @Override
    public boolean isImmunization() {
        return (getSnomedId() != null && !getSnomedId().isEmpty());
    }

    @Override
    public boolean isComplete() {
        return (!isNever() && !isRefused());
    }

    @Override
    public String getProviderName() {
        return getImmunizationProperty(ImmunizationProperty.providerName);
    }


    @Override
    public void setProviderName(String providerName) {
        addPreventionExt(ImmunizationProperty.providerName, providerName);
    }

    @Override
    public boolean isHistorical(int days) {
        Instant immunizationDate = getImmunizationDate().toInstant();
        Instant now = Instant.now();
        long daysBetween = ChronoUnit.DAYS.between(immunizationDate, now);
        return (daysBetween > days);
    }

    @Override
    public int getImmunizationId() {
        return getId();
    }
}
