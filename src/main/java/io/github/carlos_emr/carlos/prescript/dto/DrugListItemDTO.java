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
package io.github.carlos_emr.carlos.prescript.dto;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

import io.github.carlos_emr.carlos.commn.model.Drug;

/**
 * Lightweight data transfer object for prescription list and history views,
 * optimized for JPQL constructor expression projection. Carries only the 20
 * fields needed for medication display out of Drug's 101 fields.
 *
 * <p>Omits: ATC codes, regional identifiers, GCN sequence numbers, renal dosing,
 * compliance tracking, protocol fields, pharmacy IDs, and other internal data.</p>
 *
 * @since 2026-04-11
 */
public class DrugListItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private Integer demographicId;
    private String brandName;
    private String genericName;
    private String customName;
    private String dosage;
    private String route;
    private String freqCode;
    private String duration;
    private String durUnit;
    private String quantity;
    private Integer repeat;
    private Date rxDate;
    private Date endDate;
    private Date lastRefillDate;
    private boolean archived;
    private Boolean longTerm;
    private String providerNo;
    private String special;
    private Integer scriptNo;

    public DrugListItemDTO() {
    }

    /**
     * Projection constructor for JPQL constructor expressions.
     *
     * @param id Integer the drug/prescription ID
     * @param demographicId Integer the patient demographic number
     * @param brandName String the brand name (BN)
     * @param genericName String the generic name (GN)
     * @param customName String the custom/compound name
     * @param dosage String the dosage
     * @param route String the route of administration
     * @param freqCode String the frequency code
     * @param duration String the duration
     * @param durUnit String the duration unit
     * @param quantity String the quantity
     * @param repeat Integer the number of repeats/refills
     * @param rxDate Date the prescription date
     * @param endDate Date the end date
     * @param lastRefillDate Date the last refill date
     * @param archived boolean whether the prescription is archived
     * @param longTerm Boolean whether this is a long-term medication
     * @param providerNo String the prescribing provider number
     * @param special String the special instructions
     * @param scriptNo Integer the script number
     */
    public DrugListItemDTO(Integer id, Integer demographicId, String brandName, String genericName,
                           String customName, String dosage, String route, String freqCode,
                           String duration, String durUnit, String quantity, Integer repeat,
                           Date rxDate, Date endDate, Date lastRefillDate, boolean archived,
                           Boolean longTerm, String providerNo, String special, Integer scriptNo) {
        this.id = id;
        this.demographicId = demographicId;
        this.brandName = brandName;
        this.genericName = genericName;
        this.customName = customName;
        this.dosage = dosage;
        this.route = route;
        this.freqCode = freqCode;
        this.duration = duration;
        this.durUnit = durUnit;
        this.quantity = quantity;
        this.repeat = repeat;
        this.rxDate = rxDate;
        this.endDate = endDate;
        this.lastRefillDate = lastRefillDate;
        this.archived = archived;
        this.longTerm = longTerm;
        this.providerNo = providerNo;
        this.special = special;
        this.scriptNo = scriptNo;
    }

    /**
     * Creates a DrugListItemDTO from a full Drug entity.
     *
     * @param d Drug the entity to convert; must not be null
     * @return DrugListItemDTO a lightweight projection
     */
    public static DrugListItemDTO fromEntity(Drug d) {
        Objects.requireNonNull(d, "Drug entity must not be null for DTO conversion");
        return new DrugListItemDTO(
                d.getId(), d.getDemographicId(), d.getBrandName(), d.getGenericName(),
                d.getCustomName(), d.getDosage(), d.getRoute(), d.getFreqCode(),
                d.getDuration(), d.getDurUnit(), d.getQuantity(), d.getRepeat(),
                d.getRxDate(), d.getEndDate(), d.getLastRefillDate(), d.isArchived(),
                d.getLongTerm(), d.getProviderNo(), d.getSpecial(), d.getScriptNo()
        );
    }

    /**
     * Returns the best available display name for this medication: brand name
     * first, then generic name, then custom name.
     *
     * @return String the display name, or empty string if all names are null
     */
    public String getDisplayName() {
        if (brandName != null && !brandName.trim().isEmpty()) return brandName;
        if (genericName != null && !genericName.trim().isEmpty()) return genericName;
        if (customName != null && !customName.trim().isEmpty()) return customName;
        return "";
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getDemographicId() { return demographicId; }
    public void setDemographicId(Integer demographicId) { this.demographicId = demographicId; }
    public String getBrandName() { return brandName; }
    public void setBrandName(String brandName) { this.brandName = brandName; }
    public String getGenericName() { return genericName; }
    public void setGenericName(String genericName) { this.genericName = genericName; }
    public String getCustomName() { return customName; }
    public void setCustomName(String customName) { this.customName = customName; }
    public String getDosage() { return dosage; }
    public void setDosage(String dosage) { this.dosage = dosage; }
    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }
    public String getFreqCode() { return freqCode; }
    public void setFreqCode(String freqCode) { this.freqCode = freqCode; }
    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }
    public String getDurUnit() { return durUnit; }
    public void setDurUnit(String durUnit) { this.durUnit = durUnit; }
    public String getQuantity() { return quantity; }
    public void setQuantity(String quantity) { this.quantity = quantity; }
    public Integer getRepeat() { return repeat; }
    public void setRepeat(Integer repeat) { this.repeat = repeat; }
    public Date getRxDate() { return rxDate; }
    public void setRxDate(Date rxDate) { this.rxDate = rxDate; }
    public Date getEndDate() { return endDate; }
    public void setEndDate(Date endDate) { this.endDate = endDate; }
    public Date getLastRefillDate() { return lastRefillDate; }
    public void setLastRefillDate(Date lastRefillDate) { this.lastRefillDate = lastRefillDate; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public Boolean getLongTerm() { return longTerm; }
    public void setLongTerm(Boolean longTerm) { this.longTerm = longTerm; }
    public String getProviderNo() { return providerNo; }
    public void setProviderNo(String providerNo) { this.providerNo = providerNo; }
    public String getSpecial() { return special; }
    public void setSpecial(String special) { this.special = special; }
    public Integer getScriptNo() { return scriptNo; }
    public void setScriptNo(Integer scriptNo) { this.scriptNo = scriptNo; }
}
