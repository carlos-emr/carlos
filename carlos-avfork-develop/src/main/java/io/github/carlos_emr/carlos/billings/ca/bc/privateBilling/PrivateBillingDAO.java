package io.github.carlos_emr.carlos.billings.ca.bc.privateBilling;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.dao.AbstractJpaDao;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Data Access Object for managing private billing records in British Columbia.
 *
 * <p>This DAO handles database operations for private billing invoices that are not submitted
 * to the provincial Medical Services Plan (MSP). Private billing is used for non-insured services,
 * third-party billing, or services where the patient pays directly.</p>
 *
 * <p>The DAO provides functionality to:</p>
 * <ul>
 *   <li>Retrieve billing recipient information including name and address details</li>
 *   <li>List private billing items for a specific patient and recipient</li>
 *   <li>List all private billing records for a specific healthcare provider</li>
 * </ul>
 *
 * <p>Executes read-only native SQL via the JPA {@link jakarta.persistence.EntityManager}
 * inherited from {@link AbstractJpaDao}, participating in Spring's transaction context.</p>
 *
 * @see PrivateBillingModel
 * @since 2026-01-24
 */
@Repository
@Transactional(readOnly = true)
public class PrivateBillingDAO extends AbstractJpaDao {
    private static final Logger log = MiscUtils.getLogger();

    /**
     * Retrieves billing recipient information by recipient ID.
     *
     * <p>Queries the {@code bill_recipients} table to fetch contact information for the
     * specified recipient. The returned map contains the following keys:</p>
     * <ul>
     *   <li>{@code name} - String recipient's full name</li>
     *   <li>{@code address} - String street address</li>
     *   <li>{@code city} - String city name</li>
     *   <li>{@code province} - String province code (e.g., "BC", "ON")</li>
     *   <li>{@code postal} - String postal code</li>
     * </ul>
     *
     * <p>If the recipient is not found, the map is returned with empty string values for all keys.</p>
     *
     * @param recipientId String the unique identifier of the billing recipient
     * @return HashMap&lt;String, String&gt; map containing recipient contact information with keys:
     *         name, address, city, province, postal. Returns empty strings for all values if recipient not found.
     */
    public HashMap<String, String> getRecipientById(String recipientId) {
        HashMap<String, String> recipient = new HashMap<String, String>() {{
            put("name", "");
            put("address", "");
            put("city", "");
            put("province", "");
            put("postal", "");
        }};

        try {
            String sqlstmt = "SELECT name, address, city, province, postal FROM bill_recipients WHERE id = :recipientId";
            Query query = entityManager().createNativeQuery(sqlstmt);
            query.setParameter("recipientId", recipientId);

            @SuppressWarnings("unchecked")
            List<Object[]> rows = query.getResultList();
            for (Object[] row : rows) {
                recipient.put("name", asString(row[0]));
                recipient.put("address", asString(row[1]));
                recipient.put("city", asString(row[2]));
                recipient.put("province", asString(row[3]));
                recipient.put("postal", asString(row[4]));
            }
        } catch (RuntimeException e) {
            log.error("Failed to retrieve recipient by ID: {}", recipientId, e);
        }

        return recipient;
    }

    /**
     * Retrieves a list of private billing items for a specific patient and recipient.
     *
     * <p>Performs a complex join across multiple billing tables to retrieve detailed invoice
     * information for private billing transactions. The query filters by:</p>
     * <ul>
     *   <li>Billing type = 'PRI' (private billing)</li>
     *   <li>Billing status = 'P' (presumably "posted" or "processed")</li>
     *   <li>Specific demographic number (patient ID)</li>
     *   <li>Recipient name</li>
     * </ul>
     *
     * <p>Each item in the returned list contains a HashMap with billing/invoice detail keys.
     * Results are ordered by billing date in descending order (most recent first).</p>
     *
     * @param demographicNumber String the patient's demographic number (patient ID)
     * @param recipientName String the name of the billing recipient to filter by
     * @return List&lt;HashMap&lt;String, String&gt;&gt; list of invoice items, each represented as a map
     *         of billing details. Returns an empty list if no matching records are found.
     */
    public List<HashMap<String, String>> listPrivateBillItems(String demographicNumber, String recipientName) {
        List<HashMap<String, String>> bills = new ArrayList<HashMap<String, String>>();

        try {
            String sqlstmt = String.join(" ",
                    "SELECT COALESCE(br.id,'') recipient,",
                    "br.name,",
                    "b.billing_no,",
                    "b.demographic_no,",
                    "b.provider_no,",
                    "b.demographic_name,",
                    "b.billing_date,",
                    "b.total,",
                    "b.status,",
                    "bm.payee_no,",
                    "bm.billing_unit,",
                    "bm.bill_amount,",
                    "bm.billingmaster_no,",
                    "bm.billing_code,",
                    "bm.billing_unit,",
                    "bm.gst,",
                    "bm.gst_no,",
                    "bh.amount,",
                    "SUM(bh.amount_received) AS amount_received,",
                    "bs.description",
                    "FROM bill_recipients br",
                    "RIGHT JOIN billing b ON (b.billing_no = br.billingNo)",
                    "LEFT JOIN billingmaster bm USING (billing_no)",
                    "LEFT JOIN billing_history bh ON (bm.billingmaster_no = bh.billingmaster_no)",
                    "INNER JOIN billingservice bs ON (bm.billing_code = bs.service_code)",
                    "WHERE b.billingtype = 'PRI'",
                    "AND bh.billingtype = 'PRI'",
                    "AND bm.billingstatus LIKE 'P'",
                    "AND b.demographic_no LIKE :demographicNumber",
                    "AND COALESCE(br.name,'') = :recipientName",
                    "GROUP BY bh.billingmaster_no",
                    "HAVING bh.billingmaster_no",
                    "ORDER BY b.billing_date DESC");
            Query query = entityManager().createNativeQuery(sqlstmt);
            query.setParameter("demographicNumber", demographicNumber);
            query.setParameter("recipientName", recipientName);

            @SuppressWarnings("unchecked")
            List<Object[]> rows = query.getResultList();
            for (Object[] row : rows) {
                HashMap<String, String> invoiceItem = new HashMap<String, String>();
                // Column order matches the SELECT clause above (0-based indices)
                invoiceItem.put("name", asString(row[1]));
                invoiceItem.put("billing_no", asString(row[2]));
                invoiceItem.put("demographic_no", asString(row[3]));
                invoiceItem.put("provider_no", asString(row[4]));
                invoiceItem.put("demographic_name", asString(row[5]));
                invoiceItem.put("billing_date", asString(row[6]));
                invoiceItem.put("total", asString(row[7]));
                invoiceItem.put("status", asString(row[8]));
                invoiceItem.put("payee_no", asString(row[9]));
                invoiceItem.put("billing_unit", asString(row[10]));
                invoiceItem.put("bill_amount", asString(row[11]));
                invoiceItem.put("billingmaster_no", asString(row[12]));
                invoiceItem.put("billing_code", asString(row[13]));
                // index 14 is the duplicate billing_unit column (preserved from the original query)
                invoiceItem.put("gst", asString(row[15]));
                invoiceItem.put("gstNo", asString(row[16]));
                invoiceItem.put("amount", asString(row[17]));
                invoiceItem.put("amount_received", asString(row[18]));
                invoiceItem.put("description", asString(row[19]));
                bills.add(invoiceItem);
            }
        } catch (RuntimeException e) {
            log.error("Failed to list private bill items", e);
        }

        return bills;
    }

    /**
     * Retrieves a summarized list of all private billing records for a specific healthcare provider.
     *
     * <p>This method aggregates private billing data grouped by patient (demographic number) and
     * recipient name. The query performs complex joins across billing tables to compile
     * count, balance, and metadata per patient/recipient combination.</p>
     *
     * @param providerId String the healthcare provider's unique identifier
     * @return List&lt;PrivateBillingModel&gt; list of billing summary records grouped by patient and recipient.
     *         Returns an empty list if no matching records are found for the provider.
     * @see PrivateBillingModel
     */
    public List<PrivateBillingModel> listPrivateBills(String providerId) {
        List<PrivateBillingModel> bills = new ArrayList<PrivateBillingModel>();
        try {
            String sqlstmt = String.join(" ",
                    "SELECT COUNT(bill.demographic_no) AS 'items', ",
                    "bill.recipient,",
                    "bill.demographic_name,",
                    "bill.demographic_no,",
                    "bill.billing_no,",
                    "bill.billingtype,",
                    "bill.provider_no,",
                    "bill.name,",
                    "bill.billing_date,",
                    "bill.status,",
                    "bm.billingstatus,",
                    "bill.provider_ohip_no,",
                    "SUM(bm.bill_amount) AS balance,",
                    "bm.billingmaster_no,",
                    "bm.billing_code,",
                    "bm.billing_unit",
                    "FROM billingmaster bm",
                    "INNER JOIN (",
                    "SELECT br.id AS 'recipient', br.name, b.*",
                    "FROM bill_recipients br",
                    "RIGHT JOIN billing b",
                    "ON (b.billing_no = br.billingNo)",
                    "WHERE b.billingtype = 'PRI' AND b.status NOT LIKE 'A') bill",
                    "ON (bill.billing_no = bm.billing_no)",
                    "WHERE bm.billingstatus LIKE 'P' AND bill.provider_no LIKE :providerId",
                    "GROUP BY bill.demographic_no, bill.name",
                    "ORDER BY bill.billing_date DESC");
            Query query = entityManager().createNativeQuery(sqlstmt);
            query.setParameter("providerId", providerId);

            @SuppressWarnings("unchecked")
            List<Object[]> rows = query.getResultList();
            for (Object[] row : rows) {
                PrivateBillingModel model = new PrivateBillingModel();
                // Column order matches the SELECT clause above (0-based indices)
                model.setBillingCount(((Number) row[0]).intValue());
                model.setRecipientId(asString(row[1]));
                model.setDemographicName(asString(row[2]));
                model.setDemographicNumber(asString(row[3]));
                model.setBillingNumber(asString(row[4]));
                model.setBillingType(asString(row[5]));
                model.setProviderNumber(asString(row[6]));
                model.setRecipientName(asString(row[7]));
                model.setBillingDate(asString(row[8]));
                model.setStatus(asString(row[9]));
                model.setBillingStatus(asString(row[10]));
                // index 11 is provider_ohip_no, not mapped onto the model (preserved from original)
                model.setBalance(asString(row[12]));
                bills.add(model);
            }
        } catch (RuntimeException e) {
            log.error("Failed to list private bills for provider: {}", providerId, e);
        }
        return bills;
    }

    /**
     * Null-safe conversion of a query result column to a String, matching the behaviour
     * of {@code ResultSet.getString()} which returns {@code null} for SQL NULL values.
     *
     * @param value the raw column value from the native query (may be null)
     * @return String representation of the value, or {@code null} if the value is null
     */
    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
