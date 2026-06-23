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
package io.github.carlos_emr.carlos.billings.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * Lightweight DTO for Ontario billing list views. Eliminates the EAGER-loaded
 * BillingONItem collection (with CascadeType.ALL) that is fetched on every
 * BillingONCHeader1 entity access. Carries 16 fields vs 113 on the entity.
 *
 * <p>Omits: full BillingONItem EAGER collection, HIN/ver/DOB (PHI), internal
 * transaction codes, reference numbers, provider OHIP/RMA numbers, etc.</p>
 *
 * <p><b>No {@code fromEntity(...)} helper:</b> {@code BillingONCHeader1} stores
 * {@code billingDate}, {@code billingTime}, and {@code admissionDate} as
 * {@code String} fields but exposes them only through {@code Date}-returning
 * getters (one of which declares a checked {@code ParseException}). Matching
 * the JPQL field-access projection from an in-memory entity would require a
 * fragile {@code SimpleDateFormat} round-trip. Construct this DTO via the
 * JPQL {@code SELECT NEW} projection (the only supported path in practice)
 * rather than from an entity instance.</p>
 *
 * @since 2026-04-11
 */
public class BillingONCListItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private Integer demographicNo;
    private String providerNo;
    private Integer appointmentNo;
    private String billingDate;
    private String billingTime;
    private String status;
    private String payProgram;
    private String visitType;
    private String admissionDate;
    private String faciltyNum;
    private BigDecimal total;
    private BigDecimal paid;
    private Date timestamp;
    private String clinic;
    private String demographicName;

    /** Default constructor for serialization/framework binding. */
    public BillingONCListItemDTO() {
    }

    /**
     * Projection constructor for JPQL constructor expressions.
     *
     * @param id Integer billing header identifier
     * @param demographicNo Integer demographic primary key
     * @param providerNo String provider identifier
     * @param appointmentNo Integer appointment identifier
     * @param billingDate String billing date string
     * @param billingTime String billing time string
     * @param status String billing status code
     * @param payProgram String payment program
     * @param visitType String visit type code
     * @param admissionDate String admission date string
     * @param faciltyNum String facility number
     * @param total BigDecimal billed total
     * @param paid BigDecimal paid amount
     * @param timestamp Date record timestamp
     * @param clinic String clinic identifier/name
     * @param demographicName String patient display name
     */
    public BillingONCListItemDTO(Integer id, Integer demographicNo, String providerNo,
                                 Integer appointmentNo, String billingDate, String billingTime,
                                 String status, String payProgram, String visitType,
                                 String admissionDate, String faciltyNum, BigDecimal total,
                                 BigDecimal paid, Date timestamp, String clinic,
                                 String demographicName) {
        this.id = id;
        this.demographicNo = demographicNo;
        this.providerNo = providerNo;
        this.appointmentNo = appointmentNo;
        this.billingDate = billingDate;
        this.billingTime = billingTime;
        this.status = status;
        this.payProgram = payProgram;
        this.visitType = visitType;
        this.admissionDate = admissionDate;
        this.faciltyNum = faciltyNum;
        this.total = total;
        this.paid = paid;
        this.timestamp = timestamp;
        this.clinic = clinic;
        this.demographicName = demographicName;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getDemographicNo() { return demographicNo; }
    public void setDemographicNo(Integer demographicNo) { this.demographicNo = demographicNo; }
    public String getProviderNo() { return providerNo; }
    public void setProviderNo(String providerNo) { this.providerNo = providerNo; }
    public Integer getAppointmentNo() { return appointmentNo; }
    public void setAppointmentNo(Integer appointmentNo) { this.appointmentNo = appointmentNo; }
    public String getBillingDate() { return billingDate; }
    public void setBillingDate(String billingDate) { this.billingDate = billingDate; }
    public String getBillingTime() { return billingTime; }
    public void setBillingTime(String billingTime) { this.billingTime = billingTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPayProgram() { return payProgram; }
    public void setPayProgram(String payProgram) { this.payProgram = payProgram; }
    public String getVisitType() { return visitType; }
    public void setVisitType(String visitType) { this.visitType = visitType; }
    public String getAdmissionDate() { return admissionDate; }
    public void setAdmissionDate(String admissionDate) { this.admissionDate = admissionDate; }
    public String getFaciltyNum() { return faciltyNum; }
    public void setFaciltyNum(String faciltyNum) { this.faciltyNum = faciltyNum; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public BigDecimal getPaid() { return paid; }
    public void setPaid(BigDecimal paid) { this.paid = paid; }
    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
    public String getClinic() { return clinic; }
    public void setClinic(String clinic) { this.clinic = clinic; }
    public String getDemographicName() { return demographicName; }
    public void setDemographicName(String demographicName) { this.demographicName = demographicName; }
}