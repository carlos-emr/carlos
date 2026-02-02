//CHECKSTYLE:OFF

package ca.openosp.openo.entities;

/**
 * Entity representing an S23/S24 record type in the BC MSP (Medical Services Plan) Teleplan billing system.
 *
 * <p>This entity captures detailed payment reconciliation and adjustment information for healthcare provider
 * billing in British Columbia. S23 records contain itemized adjustment details including adjustment codes,
 * percentages, amounts, and balance calculations that complement the summary information in S21 records.</p>
 *
 * <p>Key components tracked by this entity include:</p>
 * <ul>
 *   <li>Payment identification and sequencing (payee number, MSP control number, data centre sequence)</li>
 *   <li>Adjustment codes and calculations (AJC, AJI, AJM with corresponding percentages and amounts)</li>
 *   <li>Financial reconciliation (gross amount, running amount, outstanding amount, balance forward)</li>
 *   <li>Adjustment tracking (adjustments made and outstanding)</li>
 * </ul>
 *
 * <p>This entity is used in the Teleplan billing reconciliation process to parse incoming payment files
 * from BC MSP and track the detailed breakdown of adjustments applied to provider billings.</p>
 *
 * @see S21
 * @see ca.openosp.openo.billing.CA.BC.model.TeleplanS23
 * @see ca.openosp.openo.billing.CA.BC.dao.TeleplanS23Dao
 * @since 2026-01-23
 */
public class S23 {

    private String s23Id;
    private String s21Id;
    private String fileName;
    private String s23Type;
    private String dataCentre;
    private String dataSeq;
    private String paymentDate;
    private String lineCode;
    private String payeeNo;
    private String payeeName;
    private String mspCTLno;
    private String ajc;
    private String aji;
    private String ajm;
    private String calcMethod;
    private String rpercent;
    private String opercent;
    private String gamount;
    private String ramount;
    private String oamount;
    private String balanceFwd;
    private String adjmade;
    private String adjOutstanding;
    private String filler;

    /**
     * Default constructor for creating an empty S23 entity.
     *
     * <p>Initializes a new S23 instance with all fields set to their default values (null for String fields).
     * This constructor is typically used by persistence frameworks and during manual entity creation.</p>
     */
    public S23() {
    }

    /**
     * Gets the unique identifier for this S23 record.
     *
     * @return String the unique S23 record identifier
     */
    public String getS23Id() {
        return s23Id;
    }

    /**
     * Sets the unique identifier for this S23 record.
     *
     * @param s23Id String the unique S23 record identifier to set
     */
    public void setS23Id(String s23Id) {
        this.s23Id = s23Id;
    }

    /**
     * Gets the associated S21 summary record identifier.
     *
     * @return String the S21 parent record identifier
     */
    public String getS21Id() {
        return s21Id;
    }

    /**
     * Sets the associated S21 summary record identifier.
     *
     * @param s21Id String the S21 parent record identifier to set
     */
    public void setS21Id(String s21Id) {
        this.s21Id = s21Id;
    }

    /**
     * Gets the Teleplan file name from which this record was parsed.
     *
     * @return String the source Teleplan file name
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Sets the Teleplan file name from which this record was parsed.
     *
     * @param fileName String the source Teleplan file name to set
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * Gets the S23 record type indicator (S23 or S24).
     *
     * @return String the record type (typically "S23" or "S24")
     */
    public String getS23Type() {
        return s23Type;
    }

    /**
     * Sets the S23 record type indicator.
     *
     * @param s23Type String the record type to set (typically "S23" or "S24")
     */
    public void setS23Type(String s23Type) {
        this.s23Type = s23Type;
    }

    /**
     * Gets the BC MSP data centre code that processed this record.
     *
     * @return String the data centre code
     */
    public String getDataCentre() {
        return dataCentre;
    }

    /**
     * Sets the BC MSP data centre code that processed this record.
     *
     * @param dataCentre String the data centre code to set
     */
    public void setDataCentre(String dataCentre) {
        this.dataCentre = dataCentre;
    }

    /**
     * Gets the sequential data number for this record within the payment batch.
     *
     * @return String the data sequence number
     */
    public String getDataSeq() {
        return dataSeq;
    }

    /**
     * Sets the sequential data number for this record within the payment batch.
     *
     * @param dataSeq String the data sequence number to set
     */
    public void setDataSeq(String dataSeq) {
        this.dataSeq = dataSeq;
    }

    /**
     * Gets the payment date for this billing reconciliation record.
     *
     * @return String the payment date
     */
    public String getPaymentDate() {
        return paymentDate;
    }

    /**
     * Sets the payment date for this billing reconciliation record.
     *
     * @param paymentDate String the payment date to set
     */
    public void setPaymentDate(String paymentDate) {
        this.paymentDate = paymentDate;
    }

    /**
     * Gets the line code identifying the type of transaction or adjustment.
     *
     * @return String the line code
     */
    public String getLineCode() {
        return lineCode;
    }

    /**
     * Sets the line code identifying the type of transaction or adjustment.
     *
     * @param lineCode String the line code to set
     */
    public void setLineCode(String lineCode) {
        this.lineCode = lineCode;
    }

    /**
     * Gets the payee number (healthcare provider MSP practitioner number).
     *
     * @return String the payee number
     */
    public String getPayeeNo() {
        return payeeNo;
    }

    /**
     * Sets the payee number (healthcare provider MSP practitioner number).
     *
     * @param payeeNo String the payee number to set
     */
    public void setPayeeNo(String payeeNo) {
        this.payeeNo = payeeNo;
    }

    /**
     * Gets the name of the payee (healthcare provider).
     *
     * @return String the payee name
     */
    public String getPayeeName() {
        return payeeName;
    }

    /**
     * Sets the name of the payee (healthcare provider).
     *
     * @param payeeName String the payee name to set
     */
    public void setPayeeName(String payeeName) {
        this.payeeName = payeeName;
    }

    /**
     * Gets the MSP control number for tracking and reconciliation purposes.
     *
     * @return String the MSP control number
     */
    public String getMspCTLno() {
        return mspCTLno;
    }

    /**
     * Sets the MSP control number for tracking and reconciliation purposes.
     *
     * @param mspCTLno String the MSP control number to set
     */
    public void setMspCTLno(String mspCTLno) {
        this.mspCTLno = mspCTLno;
    }

    /**
     * Gets the AJC (Adjustment Code C) value for billing adjustments.
     *
     * @return String the AJC adjustment code
     */
    public String getAjc() {
        return ajc;
    }

    /**
     * Sets the AJC (Adjustment Code C) value for billing adjustments.
     *
     * @param ajc String the AJC adjustment code to set
     */
    public void setAjc(String ajc) {
        this.ajc = ajc;
    }

    /**
     * Gets the AJI (Adjustment Code I) value for billing adjustments.
     *
     * @return String the AJI adjustment code
     */
    public String getAji() {
        return aji;
    }

    /**
     * Sets the AJI (Adjustment Code I) value for billing adjustments.
     *
     * @param aji String the AJI adjustment code to set
     */
    public void setAji(String aji) {
        this.aji = aji;
    }

    /**
     * Gets the AJM (Adjustment Code M) value for billing adjustments.
     *
     * @return String the AJM adjustment code
     */
    public String getAjm() {
        return ajm;
    }

    /**
     * Sets the AJM (Adjustment Code M) value for billing adjustments.
     *
     * @param ajm String the AJM adjustment code to set
     */
    public void setAjm(String ajm) {
        this.ajm = ajm;
    }

    /**
     * Gets the calculation method used for determining payment adjustments.
     *
     * @return String the calculation method code
     */
    public String getCalcMethod() {
        return calcMethod;
    }

    /**
     * Sets the calculation method used for determining payment adjustments.
     *
     * @param calcMethod String the calculation method code to set
     */
    public void setCalcMethod(String calcMethod) {
        this.calcMethod = calcMethod;
    }

    /**
     * Gets the R percentage (running adjustment percentage) applied to the billing.
     *
     * @return String the running adjustment percentage value
     */
    public String getRpercent() {
        return rpercent;
    }

    /**
     * Sets the R percentage (running adjustment percentage) applied to the billing.
     *
     * @param rpercent String the running adjustment percentage value to set
     */
    public void setRpercent(String rpercent) {
        this.rpercent = rpercent;
    }

    /**
     * Gets the O percentage (outstanding adjustment percentage) applied to the billing.
     *
     * @return String the outstanding adjustment percentage value
     */
    public String getOpercent() {
        return opercent;
    }

    /**
     * Sets the O percentage (outstanding adjustment percentage) applied to the billing.
     *
     * @param opercent String the outstanding adjustment percentage value to set
     */
    public void setOpercent(String opercent) {
        this.opercent = opercent;
    }

    /**
     * Gets the gross amount before adjustments are applied.
     *
     * @return String the gross billing amount
     */
    public String getGamount() {
        return gamount;
    }

    /**
     * Sets the gross amount before adjustments are applied.
     *
     * @param gamount String the gross billing amount to set
     */
    public void setGamount(String gamount) {
        this.gamount = gamount;
    }

    /**
     * Gets the running amount (cumulative adjusted amount).
     *
     * @return String the running adjusted amount
     */
    public String getRamount() {
        return ramount;
    }

    /**
     * Sets the running amount (cumulative adjusted amount).
     *
     * @param ramount String the running adjusted amount to set
     */
    public void setRamount(String ramount) {
        this.ramount = ramount;
    }

    /**
     * Gets the outstanding amount remaining after adjustments.
     *
     * @return String the outstanding amount
     */
    public String getOamount() {
        return oamount;
    }

    /**
     * Sets the outstanding amount remaining after adjustments.
     *
     * @param oamount String the outstanding amount to set
     */
    public void setOamount(String oamount) {
        this.oamount = oamount;
    }

    /**
     * Gets the balance forward amount from previous reconciliation periods.
     *
     * @return String the balance forward amount
     */
    public String getBalanceFwd() {
        return balanceFwd;
    }

    /**
     * Sets the balance forward amount from previous reconciliation periods.
     *
     * @param balanceFwd String the balance forward amount to set
     */
    public void setBalanceFwd(String balanceFwd) {
        this.balanceFwd = balanceFwd;
    }

    /**
     * Gets the total adjustments made in this reconciliation period.
     *
     * @return String the adjustments made amount
     */
    public String getAdjmade() {
        return adjmade;
    }

    /**
     * Sets the total adjustments made in this reconciliation period.
     *
     * @param adjmade String the adjustments made amount to set
     */
    public void setAdjmade(String adjmade) {
        this.adjmade = adjmade;
    }

    /**
     * Gets the adjustments still outstanding and not yet resolved.
     *
     * @return String the outstanding adjustments amount
     */
    public String getAdjOutstanding() {
        return adjOutstanding;
    }

    /**
     * Sets the adjustments still outstanding and not yet resolved.
     *
     * @param adjOutstanding String the outstanding adjustments amount to set
     */
    public void setAdjOutstanding(String adjOutstanding) {
        this.adjOutstanding = adjOutstanding;
    }

    /**
     * Gets the filler field for padding and future use in the Teleplan record format.
     *
     * @return String the filler content
     */
    public String getFiller() {
        return filler;
    }

    /**
     * Sets the filler field for padding and future use in the Teleplan record format.
     *
     * @param filler String the filler content to set
     */
    public void setFiller(String filler) {
        this.filler = filler;
    }
}
