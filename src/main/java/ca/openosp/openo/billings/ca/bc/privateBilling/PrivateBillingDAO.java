//CHECKSTYLE:OFF
package ca.openosp.openo.billings.ca.bc.privateBilling;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
 * <p><strong>Note:</strong> This class uses direct JDBC connections and manual resource management.
 * Callers should be aware that database connections are obtained via {@link DbUtil#getConnection()}
 * and resources are closed in finally blocks.</p>
 *
 * @see PrivateBillingModel
 * @see DbUtil
 * @since 2026-01-24
 */
public class PrivateBillingDAO {
    private Connection connection;
    private PreparedStatement statement;
    private ResultSet rs;

    /**
     * Constructs a new PrivateBillingDAO instance.
     *
     * <p>Initializes database connection resources to null. Actual database connections
     * are created on-demand when methods are invoked.</p>
     */
    public PrivateBillingDAO() {
        connection = null;
        statement = null;
        rs = null;
    }

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
            String sqlstmt = "SELECT name, address, city, province, postal FROM bill_recipients WHERE id=?";
            connection = DbUtil.getConnection();
            statement = connection.prepareStatement(sqlstmt);
            statement.setString(1, recipientId);
            rs = statement.executeQuery();
            while (rs.next()) {
                recipient.put("name", rs.getString("name"));
                recipient.put("address", rs.getString("address"));
                recipient.put("city", rs.getString("city"));
                recipient.put("province", rs.getString("province"));
                recipient.put("postal", rs.getString("postal"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                rs.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                statement.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
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
     * <p>Each item in the returned list contains a HashMap with the following keys:</p>
     * <ul>
     *   <li>{@code name} - String recipient name</li>
     *   <li>{@code billing_no} - String billing number (invoice ID)</li>
     *   <li>{@code demographic_no} - String patient demographic number</li>
     *   <li>{@code provider_no} - String healthcare provider number</li>
     *   <li>{@code demographic_name} - String patient name</li>
     *   <li>{@code billing_date} - String date of billing</li>
     *   <li>{@code total} - String total invoice amount</li>
     *   <li>{@code status} - String billing status code</li>
     *   <li>{@code payee_no} - String payee identifier</li>
     *   <li>{@code billing_unit} - String billing units (may appear twice)</li>
     *   <li>{@code bill_amount} - String billed amount</li>
     *   <li>{@code billingmaster_no} - String billing master record number</li>
     *   <li>{@code billing_code} - String service billing code</li>
     *   <li>{@code gst} - String GST amount</li>
     *   <li>{@code gstNo} - String GST registration number</li>
     *   <li>{@code amount} - String line item amount</li>
     *   <li>{@code amount_received} - String total amount received (aggregated from billing history)</li>
     *   <li>{@code description} - String service description from billing service table</li>
     * </ul>
     *
     * <p>Results are ordered by billing date in descending order (most recent first).</p>
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
                    "AND b.demographic_no LIKE ?", // <- demographic number
                    "AND COALESCE(br.name,'') = ?", // <- recipient name?? why not using recipient Id??
                    "GROUP BY bh.billingmaster_no",
                    "HAVING bh.billingmaster_no",
                    "ORDER BY b.billing_date DESC");
            connection = DbUtil.getConnection();
            statement = connection.prepareStatement(sqlstmt);
            statement.setString(1, demographicNumber);
            statement.setString(2, recipientName);
            rs = statement.executeQuery();
            while (rs.next()) {
                HashMap<String, String> invoiceItem = new HashMap<String, String>();
                invoiceItem.put("name", rs.getString("name"));
                invoiceItem.put("billing_no", rs.getString("billing_no"));
                invoiceItem.put("demographic_no", rs.getString("demographic_no"));
                invoiceItem.put("provider_no", rs.getString("provider_no"));
                invoiceItem.put("demographic_name", rs.getString("demographic_name"));
                invoiceItem.put("billing_date", rs.getString("billing_date"));
                invoiceItem.put("total", rs.getString("total"));
                invoiceItem.put("status", rs.getString("status"));
                invoiceItem.put("payee_no", rs.getString("payee_no"));
                invoiceItem.put("billing_unit", rs.getString("billing_unit"));
                invoiceItem.put("bill_amount", rs.getString("bill_amount"));
                invoiceItem.put("billingmaster_no", rs.getString("billingmaster_no"));
                invoiceItem.put("billing_code", rs.getString("billing_code"));
                invoiceItem.put("billing_unit", rs.getString("billing_unit"));
                invoiceItem.put("gst", rs.getString("gst"));
                invoiceItem.put("gstNo", rs.getString("gst_no"));
                invoiceItem.put("amount", rs.getString("amount"));
                invoiceItem.put("amount_received", rs.getString("amount_received"));
                invoiceItem.put("description", rs.getString("description"));
                bills.add(invoiceItem);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                rs.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                statement.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return bills;
    }

    /**
     * Retrieves a summarized list of all private billing records for a specific healthcare provider.
     *
     * <p>This method aggregates private billing data grouped by patient (demographic number) and
     * recipient name. The query performs complex joins across billing tables to compile:</p>
     * <ul>
     *   <li>Count of billing items per patient/recipient combination</li>
     *   <li>Total balance (sum of all bill amounts) for each group</li>
     *   <li>Billing metadata including dates, status, and provider information</li>
     * </ul>
     *
     * <p>The query filters by:</p>
     * <ul>
     *   <li>Billing type = 'PRI' (private billing)</li>
     *   <li>Billing status = 'P' (presumably "posted" or "processed")</li>
     *   <li>Status NOT LIKE 'A' (excludes archived or cancelled records)</li>
     *   <li>Specific provider number</li>
     * </ul>
     *
     * <p>Results are grouped by patient demographic number and recipient name, and ordered by
     * billing date in descending order (most recent first).</p>
     *
     * <p>Each {@link PrivateBillingModel} in the returned list contains aggregated billing summary
     * information for a unique patient/recipient combination.</p>
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
                    "WHERE bm.billingstatus LIKE 'P' AND bill.provider_no LIKE ?",
                    "GROUP BY bill.demographic_no, bill.name",
                    "ORDER BY bill.billing_date DESC");
            connection = DbUtil.getConnection();
            statement = connection.prepareStatement(sqlstmt);
            statement.setString(1, providerId);

            rs = statement.executeQuery();
            while (rs.next()) {
                PrivateBillingModel model = new PrivateBillingModel();
                model.setBillingCount(rs.getInt("items"));
                model.setBillingNumber(rs.getString("billing_no"));
                model.setBillingDate(rs.getString("billing_date"));
                model.setBillingType(rs.getString("billingtype"));
                model.setBillingStatus(rs.getString("billingstatus"));
                model.setDemographicName(rs.getString("demographic_name"));
                model.setDemographicNumber(rs.getString("demographic_no"));
                model.setProviderNumber(rs.getString("provider_no"));
                model.setRecipientId(rs.getString("recipient"));
                model.setRecipientName(rs.getString("name"));
                model.setBalance(rs.getString("balance"));
                model.setStatus(rs.getString("status"));
                bills.add(model);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                rs.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                statement.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return bills;
    }
}