//CHECKSTYLE:OFF
package ca.openosp.openo.billings.ca.bc.privateBilling;

/**
 * Data model representing private billing information for British Columbia healthcare providers.
 *
 * <p>This model encapsulates billing details for non-MSP (Medical Services Plan) billing transactions,
 * including patient demographics, provider information, billing status, and financial details.
 * Private billing is used for services not covered under BC's public healthcare system, such as
 * third-party insurance claims, WorkSafeBC claims, or patient self-pay transactions.</p>
 *
 * <p>The model tracks both the billing transaction details (number, date, type, status) and the
 * parties involved (patient/demographic, provider, recipient/payer), along with financial information
 * such as outstanding balance.</p>
 *
 * @since 2026-01-23
 */
public class PrivateBillingModel {

    private int billingCount;
    private String billingNumber;
    private String billingDate;
    private String billingType;
    private String billingStatus;
    private String demographicName;
    private String demographicNumber;
    private String providerNumber;
    private String recipientId;
    private String recipientName;
    private String balance;
    private String status;

    /**
     * Retrieves the current status of the billing record.
     *
     * @return String the billing record status
     */
    public String getStatus() {
        return this.status;
    }

    /**
     * Sets the status of the billing record.
     *
     * @param value String the billing record status to set
     */
    public void setStatus(String value) {
        this.status = value;
    }

    /**
     * Retrieves the unique billing number for this transaction.
     *
     * @return String the billing number identifier
     */
    public String getBillingNumber() {
        return this.billingNumber;
    }

    /**
     * Sets the unique billing number for this transaction.
     *
     * @param value String the billing number identifier to set
     */
    public void setBillingNumber(String value) {
        this.billingNumber = value;
    }

    /**
     * Retrieves the count of billing records or line items.
     *
     * @return int the billing count
     */
    public int getBillingCount() {
        return this.billingCount;
    }

    /**
     * Sets the count of billing records or line items.
     *
     * @param value int the billing count to set
     */
    public void setBillingCount(int value) {
        this.billingCount = value;
    }

    /**
     * Retrieves the date of the billing transaction.
     *
     * @return String the billing date
     */
    public String getBillingDate() {
        return this.billingDate;
    }

    /**
     * Sets the date of the billing transaction.
     *
     * @param value String the billing date to set
     */
    public void setBillingDate(String value) {
        this.billingDate = value;
    }

    /**
     * Retrieves the type of billing transaction (e.g., third-party insurance, WorkSafeBC, self-pay).
     *
     * @return String the billing type
     */
    public String getBillingType() {
        return this.billingType;
    }

    /**
     * Sets the type of billing transaction.
     *
     * @param value String the billing type to set
     */
    public void setBillingType(String value) {
        this.billingType = value;
    }

    /**
     * Retrieves the processing status of the billing transaction (e.g., pending, submitted, paid, rejected).
     *
     * @return String the billing processing status
     */
    public String getBillingStatus() {
        return this.billingStatus;
    }

    /**
     * Sets the processing status of the billing transaction.
     *
     * @param value String the billing processing status to set
     */
    public void setBillingStatus(String value) {
        this.billingStatus = value;
    }

    /**
     * Retrieves the patient's demographic number (unique patient identifier in the EMR system).
     *
     * @return String the patient's demographic number
     */
    public String getDemographicNumber() {
        return demographicNumber;
    }

    /**
     * Sets the patient's demographic number (unique patient identifier in the EMR system).
     *
     * @param value String the patient's demographic number to set
     */
    public void setDemographicNumber(String value) {
        this.demographicNumber = value;
    }

    /**
     * Retrieves the patient's full name.
     *
     * @return String the patient's full name
     */
    public String getDemographicName() {
        return demographicName;
    }

    /**
     * Sets the patient's full name.
     *
     * @param value String the patient's full name to set
     */
    public void setDemographicName(String value) {
        this.demographicName = value;
    }

    /**
     * Retrieves the healthcare provider's identification number (BC practitioner number).
     *
     * @return String the provider's identification number
     */
    public String getProviderNumber() {
        return this.providerNumber;
    }

    /**
     * Sets the healthcare provider's identification number (BC practitioner number).
     *
     * @param value String the provider's identification number to set
     */
    public void setProviderNumber(String value) {
        this.providerNumber = value;
    }

    /**
     * Retrieves the unique identifier of the billing recipient (payer/insurance company).
     *
     * @return String the recipient's unique identifier
     */
    public String getRecipientId() {
        return this.recipientId;
    }

    /**
     * Sets the unique identifier of the billing recipient (payer/insurance company).
     *
     * @param value String the recipient's unique identifier to set
     */
    public void setRecipientId(String value) {
        this.recipientId = value;
    }

    /**
     * Retrieves the name of the billing recipient (payer/insurance company name).
     *
     * @return String the recipient's name
     */
    public String getRecipientName() {
        return this.recipientName;
    }

    /**
     * Sets the name of the billing recipient (payer/insurance company name).
     *
     * @param value String the recipient's name to set
     */
    public void setRecipientName(String value) {
        this.recipientName = value;
    }

    /**
     * Retrieves the outstanding balance amount for this billing transaction.
     *
     * @return String the outstanding balance amount (typically in CAD)
     */
    public String getBalance() {
        return this.balance;
    }

    /**
     * Sets the outstanding balance amount for this billing transaction.
     *
     * @param value String the outstanding balance amount to set (typically in CAD)
     */
    public void setBalance(String value) {
        this.balance = value;
    }
}