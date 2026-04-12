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
package io.github.carlos_emr.carlos.casemgmt.dto;

import java.io.Serializable;
import java.util.Date;

import io.github.carlos_emr.carlos.utility.DtoFormatUtils;

/**
 * Lightweight DTO for clinical note list views. Eliminates the 3 EAGER/lazy=false
 * relationships in the HBM mapping (provider, issues set, extend set) and avoids
 * loading the full note text, history, and formula-computed properties.
 *
 * <p>Pre-joins provider name via LEFT JOIN to Provider. Omits: note text (LOB),
 * history, issues set, editors list, extend set, password, formula columns
 * (roleName, programName, revision, create_date).</p>
 *
 * <p>HBM property names used in HQL: {@code id}, {@code update_date},
 * {@code observation_date}, {@code demographic_no}, {@code signed},
 * {@code providerNo}, {@code signing_provider_no}, {@code encounter_type},
 * {@code billing_code}, {@code program_no}, {@code uuid}, {@code locked},
 * {@code archived}, {@code appointmentNo}.</p>
 *
 * @since 2026-04-11
 */
public class CaseManagementNoteListDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Date updateDate;
    private Date observationDate;
    /**
     * Demographic number as stored on the underlying entity. {@code CaseManagementNote.demographic_no}
     * is typed as {@code String} in the HBM mapping (unlike most other demographic-linked
     * entities which use {@code Integer}), so this field mirrors that type to avoid
     * an unnecessary parse at the DAO boundary.
     */
    private String demographicNo;
    private boolean signed;
    private String providerNo;
    private String signingProviderNo;
    private String encounterType;
    private String billingCode;
    private String programNo;
    private String uuid;
    private boolean locked;
    private boolean archived;
    private Integer appointmentNo;
    // Pre-joined from Provider
    private String providerLastName;
    private String providerFirstName;

    public CaseManagementNoteListDTO() {
    }

    /**
     * Projection constructor for HQL constructor expressions.
     * Parameter order must match the SELECT NEW clause exactly.
     */
    public CaseManagementNoteListDTO(Long id, Date updateDate, Date observationDate,
                                     String demographicNo, boolean signed, String providerNo,
                                     String signingProviderNo, String encounterType,
                                     String billingCode, String programNo, String uuid,
                                     boolean locked, boolean archived, Integer appointmentNo,
                                     String providerLastName, String providerFirstName) {
        this.id = id;
        this.updateDate = updateDate;
        this.observationDate = observationDate;
        this.demographicNo = demographicNo;
        this.signed = signed;
        this.providerNo = providerNo;
        this.signingProviderNo = signingProviderNo;
        this.encounterType = encounterType;
        this.billingCode = billingCode;
        this.programNo = programNo;
        this.uuid = uuid;
        this.locked = locked;
        this.archived = archived;
        this.appointmentNo = appointmentNo;
        this.providerLastName = providerLastName;
        this.providerFirstName = providerFirstName;
    }

    /**
     * Returns the provider's formatted name as "LastName, FirstName".
     *
     * @return String the formatted provider name
     */
    public String getProviderName() {
        return DtoFormatUtils.formatName(providerLastName, providerFirstName, "");
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Date getUpdateDate() { return updateDate; }
    public void setUpdateDate(Date updateDate) { this.updateDate = updateDate; }
    public Date getObservationDate() { return observationDate; }
    public void setObservationDate(Date observationDate) { this.observationDate = observationDate; }
    public String getDemographicNo() { return demographicNo; }
    public void setDemographicNo(String demographicNo) { this.demographicNo = demographicNo; }
    public boolean isSigned() { return signed; }
    public void setSigned(boolean signed) { this.signed = signed; }
    public String getProviderNo() { return providerNo; }
    public void setProviderNo(String providerNo) { this.providerNo = providerNo; }
    public String getSigningProviderNo() { return signingProviderNo; }
    public void setSigningProviderNo(String signingProviderNo) { this.signingProviderNo = signingProviderNo; }
    public String getEncounterType() { return encounterType; }
    public void setEncounterType(String encounterType) { this.encounterType = encounterType; }
    public String getBillingCode() { return billingCode; }
    public void setBillingCode(String billingCode) { this.billingCode = billingCode; }
    public String getProgramNo() { return programNo; }
    public void setProgramNo(String programNo) { this.programNo = programNo; }
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public Integer getAppointmentNo() { return appointmentNo; }
    public void setAppointmentNo(Integer appointmentNo) { this.appointmentNo = appointmentNo; }
    public String getProviderLastName() { return providerLastName; }
    public void setProviderLastName(String providerLastName) { this.providerLastName = providerLastName; }
    public String getProviderFirstName() { return providerFirstName; }
    public void setProviderFirstName(String providerFirstName) { this.providerFirstName = providerFirstName; }
}
