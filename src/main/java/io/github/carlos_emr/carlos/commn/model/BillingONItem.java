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

import java.io.Serializable;
import java.util.Date;
import java.util.Set;
import jakarta.persistence.*;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * @author mweston4
 */
@Entity
@Table(name = "billing_on_item")
public class BillingONItem extends AbstractModel<Integer> implements Serializable {

    private static final Logger logger = MiscUtils.getLogger();

    public static final String OPEN = "O";
    public static final String SETTLED = "S";
    public static final String DELETED = "D";
    public static final String BILLED = "B";
    public static final String PATIENT_BILLED = "P";
    public static final String NOT_BILLED = "N";
    public static final String INDEPENDENT = "I";
    public static final String WCB = "W";
    public static final String ACKNOWLEDGED = "A";

    /** See {@code BillingONCHeader1.KNOWN_STATUSES} — same closed set. */
    private static final Set<String> KNOWN_STATUSES = Set.of(
            OPEN, SETTLED, DELETED, BILLED, PATIENT_BILLED,
            NOT_BILLED, INDEPENDENT, WCB, ACKNOWLEDGED);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "ch1_id")
    private Integer ch1Id;

    @Column(name = "transc_id")
    private String transcId;

    @Column(name = "rec_id")
    private String recId;

    @Column(name = "service_code")
    private String serviceCode;

    @Column(name = "fee")
    private String fee;

    @Column(name = "ser_num")
    private String serviceCount;

    @Temporal(TemporalType.DATE)
    @Column(name = "service_date")
    private Date serviceDate;

    @Column(name = "dx")
    private String dx;

    @Column(name = "dx1")
    private String dx1;

    @Column(name = "dx2")
    private String dx2;

    @Column(name = "status")
    private String status;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "timestamp")
    private Date lastEditDT;

    @Override
    public Integer getId() {
        return id;
    }

    public String getTranscId() {
        return transcId;
    }

    public void setTranscId(String transcId) {
        this.transcId = transcId;
    }

    public String getRecId() {
        return recId;
    }

    public void setRecId(String recId) {
        this.recId = recId;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public void setServiceCode(String serviceCode) {
        this.serviceCode = serviceCode;
    }

    public String getFee() {
        return fee;
    }

    /**
     * Set the fee as a parseable {@link java.math.BigDecimal} string, or null
     * for "no fee". Invalid (unparseable) values throw at write-time so a
     * future caller's typo doesn't propagate as an
     * {@code Optional.empty()} from
     * {@code BillingONCHeader1.recomputeTotalFromItems()} that the user
     * sees as a generic failure.
     *
     * @param fee BigDecimal-parseable string, or {@code null}
     * @throws IllegalArgumentException if {@code fee} is non-null and not
     *                                  a valid {@code BigDecimal}
     */
    public void setFee(String fee) {
        if (fee != null) {
            try {
                new java.math.BigDecimal(fee);
            } catch (NumberFormatException e) {
                logger.warn("Rejecting unparseable BillingONItem fee value (length={})",
                        fee.length());
                throw new IllegalArgumentException(
                        "BillingONItem fee is not a valid BigDecimal; see logs for length");
            }
        }
        this.fee = fee;
    }

    public String getServiceCount() {
        return serviceCount;
    }

    public void setServiceCount(String serviceCount) {
        this.serviceCount = serviceCount;
    }

    public Date getServiceDate() {
        return serviceDate;
    }

    public void setServiceDate(Date serviceDate) {
        this.serviceDate = serviceDate;
    }

    public String getDx() {
        return dx;
    }

    public void setDx(String dx) {
        this.dx = dx;
    }

    public String getDx1() {
        return dx1;
    }

    public void setDx1(String dx1) {
        this.dx1 = dx1;
    }

    public String getDx2() {
        return dx2;
    }

    public void setDx2(String dx2) {
        this.dx2 = dx2;
    }

    public String getStatus() {
        return status;
    }

    /**
     * Set the status code. Validates against the {@link #KNOWN_STATUSES}
     * whitelist. See {@code BillingONCHeader1#setStatus(String)}.
     *
     * @throws IllegalArgumentException if {@code status} is non-null and not
     *                                  in {@link #KNOWN_STATUSES}
     */
    public void setStatus(String status) {
        if (status != null && !KNOWN_STATUSES.contains(status)) {
            logger.warn("Rejecting unknown BillingONItem status value {} (allowed: {})",
                    status, KNOWN_STATUSES);
            throw new IllegalArgumentException(
                    "BillingONItem status is not in the known set; see logs for the offending value");
        }
        this.status = status;
    }

    /**
     * @return {@code true} when this item has not been soft-deleted
     *         (status != {@link #DELETED}). Replaces inline
     *         {@code !"D".equals(item.getStatus())} comparisons across
     *         the codebase.
     */
    public boolean isActive() {
        return !DELETED.equals(this.status);
    }

    /** @return {@code true} when this item has been soft-deleted. */
    public boolean isDeleted() {
        return DELETED.equals(this.status);
    }

    /**
     * Soft-delete this item by setting its status to {@link #DELETED}. Mirrors
     * {@code BillingONCHeader1#markSettled()} so callers can avoid the
     * {@code setStatus("D")} string-literal anti-pattern.
     *
     * <p>Routes through {@link #setStatus(String)} so the whitelist invariant
     * stays the single source of truth.</p>
     */
    public void markDeleted() {
        setStatus(DELETED);
    }

    public Date getLastEditDT() {
        return lastEditDT;
    }

    public void setLastEditDT(Date lastEditDT) {
        this.lastEditDT = lastEditDT;
    }

    public Integer getCh1Id() {
        return ch1Id;
    }

    public void setCh1Id(Integer ch1Id) {
        this.ch1Id = ch1Id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BillingONItem item = (BillingONItem) o;

        if ((id != null) && (item.getId() != null))
            return id.equals(item.getId());
        // Null-safe natural-key fallback. Two transient items with all-null
        // (ch1Id, serviceCode) are equal; one with a partial key set is not
        // equal to one with a different partial key. Pre-fix this branch
        // NPE'd on any transient item with one of the two fields unset
        // (e.g., bItem.setCh1Id(parent.getId()) where parent is unsaved).
        return java.util.Objects.equals(ch1Id, item.getCh1Id())
                && java.util.Objects.equals(serviceCode, item.getServiceCode());
    }

    @Override
    public int hashCode() {
        // Must mirror equals() to honour the equals/hashCode contract:
        // equals() falls back to (ch1Id, serviceCode) when either id is null,
        // so a transient item (id=null) and a persisted item with the same
        // (ch1Id, serviceCode) are .equals() — they MUST therefore hash the
        // same. The previous implementation hashed on id alone (returning 0
        // for transient instances) which silently broke any future hash-keyed
        // dedup of pre-persistence items. Hashing on the business-natural key
        // is correct in both branches: two persisted rows with the same
        // (ch1Id, serviceCode) would also share an id (PK constraint), so
        // the hash stays consistent for both equals branches.
        int h = (ch1Id != null ? ch1Id.hashCode() : 0);
        h = 31 * h + (serviceCode != null ? serviceCode.hashCode() : 0);
        return h;
    }

    @PrePersist
    @PreUpdate
    protected void jpa_updateTimestamp() {
        this.lastEditDT = new Date();
    }
}
