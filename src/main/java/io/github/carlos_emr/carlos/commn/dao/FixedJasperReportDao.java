/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.dao;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Fixed, bound Jasper queries. No managed entities or JDBC resources escape this boundary. */
@Repository
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class FixedJasperReportDao {
    // Both queries use the same header eligibility rule within the same snapshot.
    private static final String STATEMENT_ELIGIBILITY = """
            where h.demographic_no = :patient and h.pay_program = 'PAT'
              and (h.status is null or h.status <> 'D')
              and (:fromDate is null or h.billing_date >= :fromDate)
              and (:toDate is null or h.billing_date <= :toDate)
            """;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    /**
     * Detached appointment projection with defensive copies of mutable JDBC dates and times.
     *
     * @param appointmentNo Integer appointment identifier
     * @param providerNo String provider identifier
     * @param appointmentDate java.sql.Date appointment day
     * @param startTime java.sql.Time start time
     * @param endTime java.sql.Time end time
     * @param name String appointment name
     * @param demographicNo Integer patient identifier
     * @param notes String nullable notes
     * @param reason String nullable reason
     * @param location String nullable location
     * @param resources String nullable resources
     * @param type String nullable type
     * @param style String nullable style
     * @param billing String nullable billing marker
     * @param status String nullable status, including cancelled appointments
     * @param createdAt java.sql.Timestamp nullable creation time
     * @param updatedAt java.sql.Timestamp nullable update time
     * @param creator String nullable creator identifier
     * @param remarks String nullable remarks
     */
    public record DaySheetRow(Integer appointmentNo,
                              String providerNo,
                              java.sql.Date appointmentDate,
                              java.sql.Time startTime,
                              java.sql.Time endTime,
                              String name,
                              Integer demographicNo,
                              String notes,
                              String reason,
                              String location,
                              String resources,
                              String type,
                              String style,
                              String billing,
                              String status,
                              java.sql.Timestamp createdAt,
                              java.sql.Timestamp updatedAt,
                              String creator,
                              String remarks) {
        public DaySheetRow {
            appointmentDate = copy(appointmentDate);
            startTime = copy(startTime);
            endTime = copy(endTime);
            createdAt = copy(createdAt);
            updatedAt = copy(updatedAt);
        }
        @Override public Date appointmentDate() { return copy(appointmentDate); }
        @Override public java.sql.Time startTime() { return copy(startTime); }
        @Override public java.sql.Time endTime() { return copy(endTime); }
        @Override public java.sql.Timestamp createdAt() { return copy(createdAt); }
        @Override public java.sql.Timestamp updatedAt() { return copy(updatedAt); }
        private static Date copy(Date value) { return value == null ? null : new Date(value.getTime()); }
        private static java.sql.Time copy(java.sql.Time value) {
            return value == null ? null : new java.sql.Time(value.getTime());
        }
        private static java.sql.Timestamp copy(java.sql.Timestamp value) {
            if (value == null) return null;
            var result = new java.sql.Timestamp(value.getTime());
            result.setNanos(value.getNanos());
            return result;
        }
    }

    /**
     * Detached statement item; values retain SQL NULLs and legacy fee formatting.
     * @param invoiceId Integer owning invoice identifier
     * @param serviceCode String nullable service code
     * @param fee String nullable fee as stored by the billing system
     */
    public record ItemRow(Integer invoiceId, String serviceCode, String fee) { }

    /**
     * Detached invoice and immutable, ordered items. SQL NULL amounts are preserved;
     * the statement service explicitly rejects incomplete monetary totals.
     * @param id Integer invoice identifier
     * @param billingDate java.sql.Date nullable billing date, defensively copied
     * @param total BigDecimal nullable invoice total
     * @param paid BigDecimal nullable paid amount
     * @param items List of ItemRow ordered by service code then item identifier
     */
    public record InvoiceRow(Integer id, Date billingDate, BigDecimal total, BigDecimal paid,
                             List<ItemRow> items) {
        public InvoiceRow {
            billingDate = billingDate == null ? null : new Date(billingDate.getTime());
            items = List.copyOf(items);
        }
        @Override public Date billingDate() {
            return billingDate == null ? null : new Date(billingDate.getTime());
        }
    }

    /**
     * Retains the legacy date-only selection across all providers and statuses.
     * @param date java.time.LocalDate appointment date to select
     * @return List of DaySheetRow detached rows with no guaranteed order
     * @throws org.springframework.dao.DataAccessException if the database query fails
     */
    public List<DaySheetRow> daySheet(java.time.LocalDate date) {
        NativeQuery<?> query = entityManager.createNativeQuery("""
                select appointment_no, provider_no, appointment_date, start_time, end_time, name,
                       demographic_no, notes, reason, location, resources, type, style, billing,
                       status, createdatetime, updatedatetime, creator, remarks
                from appointment where appointment_date = :date
                """)
                .unwrap(NativeQuery.class);
        query.addScalar("appointment_no", Integer.class);
        query.addScalar("provider_no", String.class);
        query.addScalar("appointment_date", java.sql.Date.class);
        query.addScalar("start_time", java.sql.Time.class);
        query.addScalar("end_time", java.sql.Time.class);
        query.addScalar("name", String.class);
        query.addScalar("demographic_no", Integer.class);
        query.addScalar("notes", String.class);
        query.addScalar("reason", String.class);
        query.addScalar("location", String.class);
        query.addScalar("resources", String.class);
        query.addScalar("type", String.class);
        query.addScalar("style", String.class);
        query.addScalar("billing", String.class);
        query.addScalar("status", String.class);
        query.addScalar("createdatetime", java.sql.Timestamp.class);
        query.addScalar("updatedatetime", java.sql.Timestamp.class);
        query.addScalar("creator", String.class);
        query.addScalar("remarks", String.class);
        query.setParameter("date", date);
        return query.getResultList().stream().map(value -> {
            Object[] r = (Object[]) value;
            return new DaySheetRow((Integer) r[0], (String) r[1], (java.sql.Date) r[2], (java.sql.Time) r[3], (java.sql.Time) r[4], (String) r[5], (Integer) r[6], (String) r[7], (String) r[8], (String) r[9], (String) r[10], (String) r[11], (String) r[12], (String) r[13], (String) r[14], (java.sql.Timestamp) r[15], (java.sql.Timestamp) r[16], (String) r[17], (String) r[18]);
        }).toList();
    }

    /**
     * Loads headers and items in one repeatable-read transaction for HTML and PDF consumers.
     * @param patient Integer demographic number
     * @param from java.sql.Date inclusive lower bound, or null for no lower bound
     * @param to java.sql.Date inclusive upper bound, or null for no upper bound
     * @return List of InvoiceRow invoices ordered by id, with their ordered items
     * @throws org.springframework.dao.DataAccessException if either database query fails
     */
    public List<InvoiceRow> statement(Integer patient, Date from, Date to) {
        NativeQuery<?> headers = entityManager.createNativeQuery("""
                select h.id, h.billing_date, h.total, h.paid
                from billing_on_cheader1 h join demographic d on d.demographic_no = h.demographic_no
                """ + STATEMENT_ELIGIBILITY + " order by h.id").unwrap(NativeQuery.class);
        headers.addScalar("id", Integer.class).addScalar("billing_date", Date.class)
                .addScalar("total", BigDecimal.class).addScalar("paid", BigDecimal.class);
        bind(headers, patient, from, to);
        List<?> rows = headers.getResultList();
        if (rows.isEmpty()) return List.of();
        // Join back to the same patient/range instead of constructing an unbounded IN parameter list.
        NativeQuery<?> details = entityManager.createNativeQuery("""
                select bi.ch1_id, bi.service_code, bi.fee
                from billing_on_item bi join billing_on_cheader1 h on h.id = bi.ch1_id
                join demographic d on d.demographic_no = h.demographic_no
                """ + STATEMENT_ELIGIBILITY + """
                  and (bi.status is null or bi.status <> 'D')
                order by bi.ch1_id, bi.service_code, bi.id
                """)
                .unwrap(NativeQuery.class);
        details.addScalar("ch1_id", Integer.class).addScalar("service_code", String.class).addScalar("fee", String.class);
        bind(details, patient, from, to);
        java.util.Map<Integer, List<ItemRow>> items = new java.util.HashMap<>();
        for (Object value : details.getResultList()) {
            Object[] r = (Object[]) value;
            ItemRow item = new ItemRow((Integer) r[0], (String) r[1], (String) r[2]);
            items.computeIfAbsent(item.invoiceId(), ignored -> new ArrayList<>()).add(item);
        }
        return rows.stream().map(value -> {
            Object[] r = (Object[]) value;
            return new InvoiceRow((Integer) r[0], (Date) r[1], (BigDecimal) r[2], (BigDecimal) r[3],
                    items.getOrDefault(r[0], List.of()));
        }).toList();
    }

    private static void bind(NativeQuery<?> query, Integer patient, Date from, Date to) {
        query.setParameter("patient", patient);
        query.setParameter("fromDate", from, Date.class);
        query.setParameter("toDate", to, Date.class);
    }
}
