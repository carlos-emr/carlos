//CHECKSTYLE:OFF

package ca.openosp.openo.entities;

/**
 * Entity representing a Teleplan S22 record for BC MSP (Medical Services Plan) billing.
 *
 * <p>The S22 record type contains detailed payment information for individual healthcare
 * practitioners, including amounts billed, amounts paid, and practitioner identification
 * details. S22 records are associated with S21 records which contain summary payment
 * information for the payee.</p>
 *
 * <p>This entity is part of the British Columbia Teleplan billing system integration,
 * which processes payment remittances from the provincial Medical Services Plan. Each
 * S22 record represents a line item in the payment remittance file showing how much was
 * billed versus how much was actually paid for a specific practitioner's services.</p>
 *
 * <p><strong>Healthcare Context:</strong> In BC's MSP billing workflow, healthcare providers
 * submit claims for services rendered. The MSP processes these claims and returns payment
 * remittance files containing S21 (summary) and S22 (detail) records. The S22 records
 * allow the EMR to reconcile individual practitioner billings and identify any discrepancies
 * between billed and paid amounts.</p>
 *
 * @see S21
 * @see ca.openosp.openo.billing.CA.BC.model.TeleplanS22
 * @see ca.openosp.openo.billing.CA.BC.dao.TeleplanS22Dao
 * @since 2026-01-23
 */
public class S22 {

    private String s22Id;
    private String s21Id;
    private String fileName;
    private String s22Type;
    private String dataCentre;
    private String dataSeq;
    private String paymentDate;
    private String lineCode;
    private String payeeNo;
    private String payeeName;
    private String mspCTLno;
    private String practitionerNo;
    private String practitionerName;
    private String amtBilled;
    private String amtPaid;
    private String filler;

    /**
     * Default constructor for S22 entity.
     *
     * <p>Creates a new S22 instance with all fields initialized to null.
     * This constructor is typically used by frameworks and data access layers
     * when instantiating entities from database records or XML/JSON parsing.</p>
     */
    public S22() {
    }

    /**
     * Gets the unique identifier for this S22 record.
     *
     * @return String the S22 record identifier, or null if not set
     */
    public String getS22Id() {
        return s22Id;
    }

    /**
     * Sets the unique identifier for this S22 record.
     *
     * @param s22Id String the S22 record identifier to set
     */
    public void setS22Id(String s22Id) {
        this.s22Id = s22Id;
    }

    /**
     * Gets the identifier of the parent S21 record.
     *
     * <p>S22 records are detail records associated with a summary S21 record.
     * This field links the S22 detail to its parent S21 summary record.</p>
     *
     * @return String the S21 record identifier, or null if not set
     */
    public String getS21Id() {
        return s21Id;
    }

    /**
     * Sets the identifier of the parent S21 record.
     *
     * @param s21Id String the S21 record identifier to set
     */
    public void setS21Id(String s21Id) {
        this.s21Id = s21Id;
    }

    /**
     * Gets the name of the Teleplan remittance file.
     *
     * <p>This is the filename of the BC MSP remittance file that contained
     * this S22 record. Used for auditing and troubleshooting payment imports.</p>
     *
     * @return String the filename, or null if not set
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Sets the name of the Teleplan remittance file.
     *
     * @param fileName String the filename to set
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * Gets the S22 record type code.
     *
     * <p>Identifies the specific type of S22 record within the Teleplan
     * file format specification.</p>
     *
     * @return String the S22 type code, or null if not set
     */
    public String getS22Type() {
        return s22Type;
    }

    /**
     * Sets the S22 record type code.
     *
     * @param s22Type String the S22 type code to set
     */
    public void setS22Type(String s22Type) {
        this.s22Type = s22Type;
    }

    /**
     * Gets the data centre code.
     *
     * <p>Identifies the BC MSP data centre that processed this payment record.</p>
     *
     * @return String the data centre code, or null if not set
     */
    public String getDataCentre() {
        return dataCentre;
    }

    /**
     * Sets the data centre code.
     *
     * @param dataCentre String the data centre code to set
     */
    public void setDataCentre(String dataCentre) {
        this.dataCentre = dataCentre;
    }

    /**
     * Gets the data sequence number.
     *
     * <p>Sequential number used to maintain the order of records within
     * the Teleplan remittance file.</p>
     *
     * @return String the sequence number, or null if not set
     */
    public String getDataSeq() {
        return dataSeq;
    }

    /**
     * Sets the data sequence number.
     *
     * @param dataSeq String the sequence number to set
     */
    public void setDataSeq(String dataSeq) {
        this.dataSeq = dataSeq;
    }

    /**
     * Gets the payment date for this remittance.
     *
     * <p>The date when the BC MSP issued this payment. Typically in YYYYMMDD format.</p>
     *
     * @return String the payment date, or null if not set
     */
    public String getPaymentDate() {
        return paymentDate;
    }

    /**
     * Sets the payment date for this remittance.
     *
     * @param paymentDate String the payment date to set (typically YYYYMMDD format)
     */
    public void setPaymentDate(String paymentDate) {
        this.paymentDate = paymentDate;
    }

    /**
     * Gets the line code.
     *
     * <p>Indicates the type of line item in the remittance record, such as
     * payment, adjustment, or other transaction types defined by Teleplan.</p>
     *
     * @return String the line code, or null if not set
     */
    public String getLineCode() {
        return lineCode;
    }

    /**
     * Sets the line code.
     *
     * @param lineCode String the line code to set
     */
    public void setLineCode(String lineCode) {
        this.lineCode = lineCode;
    }

    /**
     * Gets the payee number.
     *
     * <p>The BC MSP payee identifier for the healthcare provider or organization
     * receiving the payment. This may be a group practice number or billing agent number.</p>
     *
     * @return String the payee number, or null if not set
     */
    public String getPayeeNo() {
        return payeeNo;
    }

    /**
     * Sets the payee number.
     *
     * @param payeeNo String the payee number to set
     */
    public void setPayeeNo(String payeeNo) {
        this.payeeNo = payeeNo;
    }

    /**
     * Gets the payee name.
     *
     * <p>The name of the healthcare provider or organization receiving the payment
     * as it appears in BC MSP records.</p>
     *
     * @return String the payee name, or null if not set
     */
    public String getPayeeName() {
        return payeeName;
    }

    /**
     * Sets the payee name.
     *
     * @param payeeName String the payee name to set
     */
    public void setPayeeName(String payeeName) {
        this.payeeName = payeeName;
    }

    /**
     * Gets the MSP control number.
     *
     * <p>A unique control number assigned by BC MSP for tracking and
     * reconciliation purposes.</p>
     *
     * @return String the MSP control number, or null if not set
     */
    public String getMspCTLno() {
        return mspCTLno;
    }

    /**
     * Sets the MSP control number.
     *
     * @param mspCTLno String the MSP control number to set
     */
    public void setMspCTLno(String mspCTLno) {
        this.mspCTLno = mspCTLno;
    }

    /**
     * Gets the practitioner number.
     *
     * <p>The BC MSP practitioner number (also known as billing number or provider number)
     * of the individual healthcare practitioner who rendered the services. This is
     * distinct from the payee number when billing is done through a group or agent.</p>
     *
     * @return String the practitioner number, or null if not set
     */
    public String getPractitionerNo() {
        return practitionerNo;
    }

    /**
     * Sets the practitioner number.
     *
     * @param practitionerNo String the practitioner number to set
     */
    public void setPractitionerNo(String practitionerNo) {
        this.practitionerNo = practitionerNo;
    }

    /**
     * Gets the practitioner name.
     *
     * <p>The name of the individual healthcare practitioner who rendered the services,
     * as it appears in BC MSP records.</p>
     *
     * @return String the practitioner name, or null if not set
     */
    public String getPractitionerName() {
        return practitionerName;
    }

    /**
     * Sets the practitioner name.
     *
     * @param practitionerName String the practitioner name to set
     */
    public void setPractitionerName(String practitionerName) {
        this.practitionerName = practitionerName;
    }

    /**
     * Gets the amount billed.
     *
     * <p>The total amount billed by the practitioner for services rendered, as submitted
     * in the original claims. This amount is in Canadian dollars and typically includes
     * cents as a decimal value in string format (e.g., "123.45").</p>
     *
     * @return String the amount billed, or null if not set
     */
    public String getAmtBilled() {
        return amtBilled;
    }

    /**
     * Sets the amount billed.
     *
     * @param amtBilled String the amount billed to set (typically in format "123.45")
     */
    public void setAmtBilled(String amtBilled) {
        this.amtBilled = amtBilled;
    }

    /**
     * Gets the amount paid.
     *
     * <p>The actual amount paid by BC MSP for the services. This may differ from the
     * amount billed due to adjustments, partial payments, or denied claims. The amount
     * is in Canadian dollars, typically in decimal string format (e.g., "123.45").</p>
     *
     * <p>Comparing amtPaid to amtBilled allows the EMR to identify payment variances
     * that may require follow-up or appeals.</p>
     *
     * @return String the amount paid, or null if not set
     */
    public String getAmtPaid() {
        return amtPaid;
    }

    /**
     * Sets the amount paid.
     *
     * @param amtPaid String the amount paid to set (typically in format "123.45")
     */
    public void setAmtPaid(String amtPaid) {
        this.amtPaid = amtPaid;
    }

    /**
     * Gets the filler field value.
     *
     * <p>Reserved field in the Teleplan file format. May contain spaces or be used
     * for future extensions to the file format. Not typically used for business logic.</p>
     *
     * @return String the filler field value, or null if not set
     */
    public String getFiller() {
        return filler;
    }

    /**
     * Sets the filler field value.
     *
     * @param filler String the filler field value to set
     */
    public void setFiller(String filler) {
        this.filler = filler;
    }
}
