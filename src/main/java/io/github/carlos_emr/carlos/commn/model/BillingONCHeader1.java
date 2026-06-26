/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

package io.github.carlos_emr.carlos.commn.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.hibernate.Hibernate;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import org.apache.cxf.common.util.StringUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
/**
 * Legacy Ontario billing claim header entity.
 *
 * <p>This model still carries the persisted column vocabulary used throughout
 * older billing code, including ministry-facing status and pay-program values.
 * The newer services wrap it with DTOs/view models where a more explicit
 * workflow contract is needed, but the entity remains the source of truth for
 * stored invoice/header state.</p>
 */
@Entity
@Table(name = "billing_on_cheader1")
public class BillingONCHeader1 extends AbstractModel<Integer> implements Serializable {

    private static final Logger logger = MiscUtils.getLogger();
    private static final long serialVersionUID = 1L;

    // Status constants are re-exported from BillingStatus so existing
    // callers (BillingONCHeader1.OPEN etc.) keep compiling, while the
    // canonical whitelist lives in one place.
    public static final String OPEN = BillingStatus.OPEN;
    public static final String SETTLED = BillingStatus.SETTLED;
    public static final String DELETED = BillingStatus.DELETED;
    public static final String BILLED = BillingStatus.BILLED;
    /** Patient-billed (callers in BillingClaimSubmissionService). */
    public static final String PATIENT_BILLED = BillingStatus.PATIENT_BILLED;
    /** No-charge / not billed (BillingClaimSubmissionService when payProg starts with NOT). */
    public static final String NOT_BILLED = BillingStatus.NOT_BILLED;
    /** Independent / BON billing (BillingClaimSubmissionService when payProg starts with BON). */
    public static final String INDEPENDENT = BillingStatus.INDEPENDENT;
    /** WCB billing (BillingClaimSubmissionService when payProg starts with WCB). */
    public static final String WCB = BillingStatus.WCB;
    /** Acknowledgement (legacy values seen in tests / DB). */
    public static final String ACKNOWLEDGED = BillingStatus.ACKNOWLEDGED;

    /**
     * Whitelist of recognized status values. Aliased to
     * {@link BillingStatus#KNOWN} so {@link BillingONCHeader1} and
     * {@link BillingONItem} share one source of truth.
     */
    private static final Set<String> KNOWN_STATUSES = BillingStatus.KNOWN;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name = "header_id", nullable = false)
    private Integer headerId;
    @Column(name = "transc_id")
    private String transcId = "HE";
    @Column(name = "rec_id")
    private String recId = "H";
    private String hin = null;
    private String ver = "";
    private String dob = null;
    @Column(name = "pay_program")
    private String payProgram = "HCP";
    private String payee = "P";
    @Column(name = "ref_num")
    private String refNum = null;
    @Column(name = "facilty_num")
    private String faciltyNum = null;
    @Column(name = "admission_date")
    private String admissionDate = null;
    @Column(name = "ref_lab_num")
    private String refLabNum = null;
    @Column(name = "man_review")
    private String manReview = null;
    private String location = null;
    @Column(name = "demographic_no", nullable = false)
    private Integer demographicNo = 0;
    @Column(name = "provider_no", nullable = false)
    private String providerNo = "";
    @Column(name = "appointment_no")
    private Integer appointmentNo = null;
    @Column(name = "demographic_name")
    private String demographicName = null;
    private String sex = "1";
    private String province = "ON";
    @Column(name = "billing_date")
    private String billingDate = null;
    @Column(name = "billing_time")
    private String billingTime = "00:00:00"; //time format
    private BigDecimal total = null;
    private BigDecimal paid = null;
    private String status = null;
    @Column(name = "comment1")
    private String comment = null;
    @Column(name = "visittype")
    private String visitType = null;
    @Column(name = "provider_ohip_no")
    private String providerOhipNo = null;
    @Column(name = "provider_rma_no")
    private String providerRmaNo = null;
    @Column(name = "apptProvider_no")
    private String apptProviderNo = null;
    @Column(name = "asstProvider_no")
    private String asstProviderNo = null;
    private String creator = null;
    @Column(name = "timestamp1", insertable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date timestamp;
    private String clinic = null;
    // LAZY: callers that need the items must load via
    // BillingONCHeader1Dao.findWithItems / findByDemoNoWithItems, or be
    // inside an open Hibernate session. Plain DAO.find() returns a header
    // whose collection is uninitialised — accessing it outside a session
    // throws LazyInitializationException.
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "ch1_id", referencedColumnName = "id")
    private List<BillingONItem> billingItems = new ArrayList<BillingONItem>();

    public BillingONCHeader1() {
    }

    @Override
    public Integer getId() {
        return id;
    }

    public Integer getHeaderId() {
        return headerId;
    }

    public void setHeaderId(Integer headerId) {
        this.headerId = headerId;
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

    public String getHin() {
        return hin;
    }

    public void setHin(String hin) {
        this.hin = hin;
    }

    public String getVer() {
        return ver;
    }

    public void setVer(String ver) {
        this.ver = ver;
    }

    public String getDob() {
        return dob;
    }

    public void setDob(String dob) {
        this.dob = dob;
    }

    public String getPayProgram() {
        return payProgram;
    }

    public void setPayProgram(String payProgram) {
        this.payProgram = payProgram;
    }

    public String getPayee() {
        return payee;
    }

    public void setPayee(String payee) {
        this.payee = payee;
    }

    public String getRefNum() {
        return refNum;
    }

    public void setRefNum(String refNum) {
        this.refNum = refNum;
    }

    public String getFaciltyNum() {
        return faciltyNum;
    }

    public void setFaciltyNum(String faciltyNum) {
        this.faciltyNum = faciltyNum;
    }

    public Date getAdmissionDate() throws ParseException {
        if (StringUtils.isEmpty(this.admissionDate)) return null;
        return (new SimpleDateFormat("yyyy-MM-dd")).parse(this.admissionDate);
    }

    public void setAdmissionDate(Date admissionDate) {
        if (admissionDate == null) {
            this.admissionDate = "";
            return;
        }
        this.admissionDate = (new SimpleDateFormat("yyyy-MM-dd")).format(admissionDate);
    }

    public String getRefLabNum() {
        return refLabNum;
    }

    public void setRefLabNum(String refLabNum) {
        this.refLabNum = refLabNum;
    }

    public String getManReview() {
        return manReview;
    }

    public void setManReview(String manReview) {
        this.manReview = manReview;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Integer getDemographicNo() {
        return demographicNo;
    }

    public void setDemographicNo(Integer demographicNo) {
        this.demographicNo = demographicNo;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public Integer getAppointmentNo() {
        return appointmentNo;
    }

    public void setAppointmentNo(Integer appointmentNo) {
        this.appointmentNo = appointmentNo;
    }

    public String getDemographicName() {
        return demographicName;
    }

    public void setDemographicName(String demographicName) {
        this.demographicName = demographicName;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public Date getBillingDate() {
        // Return null if billing date is null or empty to avoid unecessary parse exception call
        if (billingDate == null || billingDate.trim().isEmpty()) {
            return null;
        }

        try {
            return (new SimpleDateFormat("yyyy-MM-dd")).parse(this.billingDate);
        } catch (ParseException e) {
            logger.error("Error getting billing date:", e);
            return null;
        }
    }

    public void setBillingDate(Date billingDate) {
        this.billingDate = (new SimpleDateFormat("yyyy-MM-dd")).format(billingDate);
    }

    public Date getBillingTime() {
        // Return null if billing date is null or empty to avoid unecessary parse exception call
        if (billingTime == null || billingTime.trim().isEmpty()) {
            return null;
        }

        try {
            return (new SimpleDateFormat("HH:mm:ss")).parse(this.billingTime);
        } catch (ParseException e) {
            logger.error("Error getting billing time:", e);
            return null;
        }
    }

    public void setBillingTime(Date billingTime) {
        this.billingTime = (new SimpleDateFormat("HH:mm:ss")).format(billingTime);
    }

    public BigDecimal getTotal() {
        return this.total;
    }

    /**
     * Set the invoice total. Negative values are not legal because refunds are
     * tracked on the payments table, not by negating the invoice total.
     *
     * @param total BigDecimal the total amount; null is allowed (legacy contract)
     * @throws IllegalArgumentException when {@code total} is negative
     */
    public void setTotal(BigDecimal total) {
        if (total != null && total.signum() < 0) {
            throw new IllegalArgumentException(
                    "BillingONCHeader1 total cannot be negative");
        }
        this.total = total;
    }

    public BigDecimal getPaid() {
        return this.paid;
    }

    /**
     * Set the running paid total. Negative values are permitted because
     * refunds (R-type payments in {@code BillingONPaymentDaoImpl#createPayment})
     * subtract from the running total — a refund issued before any
     * matching P-type payment will produce a negative paid balance, which
     * downstream reports treat as "patient/third-party owes money back".
     *
     * @param paid BigDecimal the paid amount; null is allowed (legacy contract)
     */
    public void setPaid(BigDecimal paid) {
        this.paid = paid;
    }

    public String getStatus() {
        return this.status;
    }

    /**
     * Set the status code. Unknown legacy statuses are accepted during the
     * deprecation period because historical workflows can still pass request
     * values that are not in {@link #KNOWN_STATUSES}; they are logged so the
     * database can be audited before switching callers to strict validation.
     *
     * @param value one of the {@code public static final String} constants
     *              on this class, or {@code null} (legacy contract permits
     *              null status)
     */
    public void setStatus(String value) {
        // Keep the permissive setter for old Struts/request-driven paths that
        // still round-trip raw status tokens from legacy pages or imports.
        if (value != null && !KNOWN_STATUSES.contains(value)) {
            BillingStatus.recordUnknownStatusWarning();
            logger.warn("Accepting unknown BillingONCHeader1 status value during deprecation: {} (allowed: {})",
                    LogSafe.sanitize(value), KNOWN_STATUSES);
        }
        this.status = value;
    }

    /**
     * Strict status setter for new code paths that have already normalized the
     * status vocabulary.
     *
     * @param value one of {@link #KNOWN_STATUSES}, or {@code null}
     * @throws IllegalArgumentException if {@code value} is non-null and not
     *                                  in {@link #KNOWN_STATUSES}
     */
    public void setStatusStrict(String value) {
        // New typed service flows should call this variant so status drift is
        // rejected at the boundary instead of being re-persisted indefinitely.
        if (value != null && !KNOWN_STATUSES.contains(value)) {
            logger.warn("Rejecting unknown BillingONCHeader1 status value {} (allowed: {})", // NOSONAR javasecurity:S5145 (SonarCloud alert #26175) — false positive: value is sanitized via LogSafe (OWASP Encode.forJava escapes CR/LF/control chars and caps length), neutralizing log injection; Sonar does not model the LogSafe wrapper as a sanitizer
                    LogSafe.sanitize(value), KNOWN_STATUSES);
            throw new IllegalArgumentException(
                    "BillingONCHeader1 status is not in the known set; see logs for the offending value");
        }
        this.status = value;
    }

    // --- domain queries (pure, no DAO calls) -----------------------------

    /**
     * @return {@code true} when this header pays through OHIP (the
     *         {@code payProgram} column carries {@code "HCP"} for ON-resident
     *         OHIP claims, {@code "RMB"} for out-of-province reciprocal billing).
     */
    public boolean isOhipBill() {
        return "HCP".equals(this.payProgram);
    }

    /**
     * @return {@code true} when this claim has been marked settled by the
     *         remittance-advice import flow ({@link #SETTLED}).
     */
    public boolean isSettled() {
        return SETTLED.equals(this.status);
    }

    /** @return {@code true} when this claim has not been soft-deleted. */
    public boolean isActive() {
        return !DELETED.equals(this.status);
    }

    /** @return {@code true} when this claim has been soft-deleted. */
    public boolean isDeleted() {
        return DELETED.equals(this.status);
    }

    /**
     * Marks this claim as settled. Callers that previously did
     * {@code header.setStatus(BillingONCHeader1.SETTLED)} should call this
     * instead so any future "what else happens on settle?" logic
     * (audit, event, derived field) has one home.
     *
     * <p>Routes through {@link #setStatus(String)} so the whitelist
     * invariant is honored. {@link #SETTLED} is in {@link #KNOWN_STATUSES},
     * so this is purely structural — but it means a future change to
     * {@code setStatus} (e.g., adding an audit hook) automatically applies
     * here too.</p>
     */
    public void markSettled() {
        setStatus(SETTLED);
    }

    /**
     * @return {@code true} iff {@code paidTotal >= total}. Pure check on
     *         supplied values — caller resolves payments through the
     *         payment DAO and passes the sum.
     */
    public boolean isPaidInFull(BigDecimal paidTotal) {
        if (this.total == null || paidTotal == null) {
            return false;
        }
        return paidTotal.compareTo(this.total) >= 0;
    }

    /**
     * Sums the active items' fees onto a new BigDecimal. Pure read of the
     * managed collection — does <em>not</em> mutate {@link #total} or call
     * the DAO. Returns {@link java.util.Optional#empty()} if any active
     * item's fee is null or unparseable.
     *
     * <p>Callers that need to persist the recomputed total should:</p>
     * <pre>
     *   header.recomputeTotalFromItems().ifPresent(header::setTotal);
     *   dao.merge(header);
     * </pre>
     *
     * @throws BillingItemsNotLoadedException when the {@code billingItems}
     *         collection is a Hibernate proxy that is not yet initialized
     *         and the current thread has no open session to lazy-load from.
     *         The caller should re-load the entity through
     *         {@code BillingONCHeader1Dao.findWithItems} (the JOIN-FETCH
     *         companion) and retry.
     */
    public java.util.Optional<BigDecimal> recomputeTotalFromItems() {
        if (this.billingItems == null) {
            return java.util.Optional.of(BigDecimal.ZERO);
        }
        if (!Hibernate.isInitialized(this.billingItems)) {
            // A LAZY proxy only exists on a managed-or-detached entity, both
            // of which have a non-null id by definition. Transient entities
            // hold a plain ArrayList that Hibernate.isInitialized treats as
            // initialised, so the throw path is unreachable for id == null.
            throw new BillingItemsNotLoadedException(
                    "BillingONCHeader1.billingItems is a LAZY proxy that is not initialized; "
                            + "load via BillingONCHeader1Dao.findWithItems before calling "
                            + "recomputeTotalFromItems outside a session.",
                    this.id);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BillingONItem item : this.billingItems) {
            if (item.isDeleted()) continue;
            String fee = item.getFee();
            if (fee == null) return java.util.Optional.empty();
            try {
                sum = sum.add(new BigDecimal(fee));
            } catch (NumberFormatException e) {
                // Defense-in-depth: BillingONItem.setFee now rejects unparseable
                // values at write-time, so a fresh row can't reach this branch.
                // The catch remains for legacy rows persisted before that
                // invariant landed; report Optional.empty so callers don't
                // silently total a corrupt row as zero.
                return java.util.Optional.empty();
            }
        }
        return java.util.Optional.of(sum);
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getVisitType() {
        return visitType;
    }

    public void setVisitType(String visitType) {
        this.visitType = visitType;
    }

    public String getProviderOhipNo() {
        return providerOhipNo;
    }

    public void setProviderOhipNo(String providerOhipNo) {
        this.providerOhipNo = providerOhipNo;
    }

    public String getProviderRmaNo() {
        return providerRmaNo;
    }

    public void setProviderRmaNo(String providerRmaNo) {
        this.providerRmaNo = providerRmaNo;
    }

    public String getApptProviderNo() {
        return apptProviderNo;
    }

    public void setApptProviderNo(String apptProviderNo) {
        this.apptProviderNo = apptProviderNo;
    }

    public String getAsstProviderNo() {
        return asstProviderNo;
    }

    public void setAsstProviderNo(String asstProviderNo) {
        this.asstProviderNo = asstProviderNo;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public String getClinic() {
        return clinic;
    }

    public void setClinic(String clinic) {
        this.clinic = clinic;
    }

    /**
     * @return an unmodifiable view of the line items. Never returns
     *         {@code null}: when the underlying collection is null (e.g.,
     *         a transient header that never had {@code setBillingItems}
     *         called), this returns {@link Collections#emptyList()} so
     *         every caller can iterate without a null-check.
     *         Production callers must mutate via
     *         {@link #addBillingItem(BillingONItem)} or
     *         {@link #removeBillingItem(BillingONItem)} so JPA collection
     *         identity is preserved.
     * @throws BillingItemsNotLoadedException when the {@code billingItems}
     *         collection is a LAZY Hibernate proxy that has not been
     *         initialized — caller fetched the header outside a session,
     *         did not use
     *         {@link io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao#findWithItems(Integer)},
     *         or
     *         iterated after the session closed. Throwing the typed
     *         exception here mirrors the {@link #recomputeTotalFromItems()}
     *         contract; without this check, callers got a raw
     *         {@code LazyInitializationException} from inside the
     *         {@code Collections.unmodifiableList} wrapper, which the
     *         calling JSP cannot turn into a sensible error message.
     */
    public List<BillingONItem> getBillingItems() {
        if (billingItems == null) {
            // Defensive floor: a transient header that never had setBillingItems
            // called would otherwise return null and force every caller to
            // null-check. The empty list is safe under the unmodifiable-view
            // contract because there's nothing to mutate.
            return Collections.emptyList();
        }
        if (!Hibernate.isInitialized(billingItems)) {
            // Same reasoning as recomputeTotalFromItems: LAZY proxy implies
            // a managed-or-detached entity whose id is non-null.
            throw new BillingItemsNotLoadedException(
                    "BillingONCHeader1.billingItems is a LAZY proxy that is not initialized; "
                            + "fetch via findWithItems(...) / findByDemoNoWithItems(...) "
                            + "or access only inside an open Hibernate session.",
                    this.id);
        }
        return Collections.unmodifiableList(billingItems);
    }

    /**
     * Serialization-friendly companion for callers that must treat missing
     * or detached line items as "not available" rather than throwing.
     *
     * @return unmodifiable items when loaded, otherwise an empty list
     */
    public List<BillingONItem> getBillingItemsOrEmpty() {
        if (billingItems == null || !Hibernate.isInitialized(billingItems)) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(billingItems);
    }

    /**
     * Wholesale-replaces the line-items collection with a defensive copy.
     * Should only be called on transient (not yet persisted) entities —
     * production code that mutates a managed header must use
     * {@link #addBillingItem(BillingONItem)} and
     * {@link #removeBillingItem(BillingONItem)} instead.
     *
     * <p>The runtime guard below covers both managed and detached entities
     * (anything with an assigned id). Hibernate's dirty-check tracks the
     * PersistentBag identity, so replacing it with a fresh ArrayList fires
     * the orphan-removal cascade — every existing child is deleted and the
     * new contents are re-inserted. That is almost never the caller's
     * intent and silently breaks dirty tracking on managed entities.</p>
     *
     * <p>The defensive copy is essential for the {@link #getBillingItems()}
     * unmodifiable-view contract: without it, a caller could pass in a
     * mutable list, retain the reference, and bypass the unmodifiable view
     * by mutating the live backing collection.</p>
     *
     * @param billingItems the new collection; {@code null} clears it.
     * @throws IllegalStateException if called on a managed-or-detached
     *         header (id has been assigned).
     */
    @Deprecated(since = "2026-04-30", forRemoval = false)
    public void setBillingItems(List<BillingONItem> billingItems) {
        // Hibernate uses field access on this entity, so flush sees the field
        // directly rather than going through getBillingItems(). Replacing the
        // PersistentBag with an ArrayList on a managed-or-detached header
        // silently breaks dirty tracking — the orphan-removal cascade
        // re-deletes the existing children and re-inserts the new list, which
        // is almost never the caller's intent. Guard at runtime so the misuse
        // surfaces immediately rather than as a downstream Hibernate quirk.
        if (this.id != null) {
            throw new IllegalStateException(
                    "setBillingItems must not be called on a managed-or-detached header — "
                            + "use addBillingItem/removeBillingItem instead. ch1.id=" + this.id);
        }
        this.billingItems = billingItems == null ? null : new ArrayList<>(billingItems);
    }

    /**
     * Append an item to the live line-items collection. Preserves JPA
     * collection identity so dirty-tracking still works on managed entities.
     *
     * @param item the item to append; ignored when null
     */
    public void addBillingItem(BillingONItem item) {
        if (item == null) {
            return;
        }
        if (this.billingItems == null) {
            this.billingItems = new ArrayList<>();
        }
        this.billingItems.add(item);
    }

    /**
     * Remove an item from the live line-items collection. Preserves JPA
     * collection identity so dirty-tracking still works on managed entities.
     *
     * @param item the item to remove
     * @return {@code true} if the collection contained the item
     */
    public boolean removeBillingItem(BillingONItem item) {
        return this.billingItems != null && this.billingItems.remove(item);
    }

    @PostPersist
    public void postPersist() {
        // Null-guard: setBillingItems(null) is a legal-but-rare state used
        // by tests; without this guard the post-persist callback would NPE
        // and Hibernate would surface as a generic flush failure.
        if (this.billingItems == null) {
            return;
        }
        for (BillingONItem b : this.billingItems) {
            b.setCh1Id(this.id);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BillingONCHeader1 bill = (BillingONCHeader1) o;

        if (id != null && bill.id != null) {
            return id.equals(bill.id);
        }
        if (id != null || bill.id != null) {
            return false;
        }
        if (!hasCompleteNaturalKey() || !bill.hasCompleteNaturalKey()) {
            return false;
        }

        return Objects.equals(headerId, bill.headerId)
                && Objects.equals(demographicNo, bill.demographicNo)
                && Objects.equals(providerNo, bill.providerNo)
                && Objects.equals(appointmentNo, bill.appointmentNo)
                && Objects.equals(billingDate, bill.billingDate)
                && Objects.equals(billingTime, bill.billingTime);
    }

    @Override
    public int hashCode() {
        if (id != null) {
            return id.hashCode();
        }
        if (!hasCompleteNaturalKey()) {
            return System.identityHashCode(this);
        }
        return Objects.hash(headerId, demographicNo, providerNo, appointmentNo,
                billingDate, billingTime);
    }

    private boolean hasCompleteNaturalKey() {
        return headerId != null
                && demographicNo != null
                && demographicNo != 0
                && hasText(providerNo)
                && appointmentNo != null
                && hasText(billingDate)
                && hasText(billingTime);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
