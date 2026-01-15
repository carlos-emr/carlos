//CHECKSTYLE:OFF

package ca.openosp.openo.billings.ca.bc.MSP;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.entities.Provider;
import ca.openosp.openo.entities.S21;
import org.apache.commons.io.IOUtils;
import ca.openosp.openo.utility.DbConnectionFilter;
import ca.openosp.openo.utility.MiscUtils;

import ca.openosp.OscarDocumentCreator;
import ca.openosp.openo.entities.MSPBill;
import ca.openosp.openo.billings.ca.bc.MSP.MSPReconcile.BillSearch;
import ca.openosp.openo.billings.ca.bc.data.PayRefSummary;

/**
 * <p>Title: CreateBillingReport2Action</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2005</p>
 * <p>Company: </p>
 *
 * @author Joel Legris
 * @version 1.0
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class CreateBillingReport2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private Properties reportCfg = new Properties();
    private OscarDocumentCreator osc = new OscarDocumentCreator();
//    private boolean showICBC;
//    private boolean showMSP;
//    private boolean showPriv;
//    private boolean showWCB;
    private static final String REPORTS_PATH = "oscar/oscarBilling/ca/bc/reports/";

    public CreateBillingReport2Action() {
        this.cfgReports();

    }

    /**
     * Performs Report Generation Logic based on the supplied parameters form the submitted form
     */
    public String execute() {
        request.getSession().getServletContext().getServletContextName();
        if (!System.getProperties().containsKey("jasper.reports.compile.class.path")) {
            String classpath = (String) ServletActionContext.getServletContext().getAttribute("org.apache.catalina.jsp_classpath");
            if (classpath == null)
                classpath = (String) request.getSession().getServletContext().getAttribute("com.ibm.websphere.servlet.application.classpath");
            System.setProperty("jasper.reports.compile.class.path", classpath);
        }
        if (!System.getProperties().containsKey("java.awt.headless")) {
            System.setProperty("java.awt.headless", "true");
        }

        //get form field data
        String docFmt = this.getDocFormat();
        String repType = this.getRepType();
        String account = this.getSelAccount();
        String payee = this.getSelPayee();
        String provider = this.getSelProv();
        String startDate = this.getXml_vdate();
        String endDate = this.getXml_appointment_date();
        String repDef = docFmt + "_" + this.reportCfg.getProperty(repType);
        boolean showICBC = Boolean.valueOf(this.getShowICBC()).booleanValue();
        boolean showMSP = Boolean.valueOf(this.getShowMSP()).booleanValue();
        boolean showPriv = Boolean.valueOf(this.getShowPRIV()).booleanValue();
        boolean showWCB = Boolean.valueOf(this.getShowWCB()).booleanValue();
        String insurers = createInsurerList(showICBC, showMSP, showPriv, showWCB);

        //Map of insurer types to be used in bill search criteria
        HashMap reportParams = new HashMap();
        reportParams.put("startDate", startDate);
        reportParams.put("endDate", endDate);
        reportParams.put("insurers", insurers);
        ServletOutputStream outputStream = this.getServletOstream(response);
        MSPReconcile msp = new MSPReconcile();
        BillSearch billSearch = null;

        //open corresponding Jasper Report Definition
        InputStream reportInstream = osc.getDocumentStream(REPORTS_PATH + repDef);
        try {
            //COnfigure Reponse Header
            cfgHeader(response, repType, docFmt);
            //select appropriate report retrieval method
            if (repType.equals(MSPReconcile.REP_ACCOUNT_REC) || repType.equals(MSPReconcile.REP_INVOICE) || repType.equals(MSPReconcile.REP_WO)) {

                billSearch = msp.getBillsByType(account, payee, provider, startDate, endDate, !showWCB, !showMSP, !showPriv, !showICBC, repType);
                String billCnt = String.valueOf(msp.getDistinctFieldCount(billSearch.list, "billing_no"));
                String demNoCnt = String.valueOf(msp.getDistinctFieldCount(billSearch.list, "demoNo"));
                if (repType.equals(MSPReconcile.REP_ACCOUNT_REC)) {
                    reportParams.put("amtSubmitted", msp.getTotalPaidByStatus(billSearch.list, MSPReconcile.SUBMITTED));
                } else if (repType.equals(MSPReconcile.REP_WO)) {
                    Provider payeeProv = msp.getProvider(payee, 1);
                    Provider provProv = msp.getProvider(provider, 0);
                    reportParams.put("providers", provider.equals("ALL") ? "ALL" : payeeProv.getFullName());
                    reportParams.put("payee", payee.equals("ALL") ? "ALL" : provProv.getFullName());
                }

                Provider acctProv = msp.getProvider(account, 0);
                reportParams.put("billCnt", billCnt);
                reportParams.put("demNoCnt", demNoCnt);
                reportParams.put("account", account.equals("ALL") ? "ALL" : acctProv.getFullName());

                //Fill document with report parameter data
                osc.fillDocumentStream(reportParams, outputStream, docFmt, reportInstream, billSearch.list);

            } else if (repType.equals(MSPReconcile.REP_MSPREM)) {
                Provider payeeProv = msp.getProvider(payee, 1);
                reportParams.put("payee", payeeProv.getFullName());
                reportParams.put("payeeno", payee);
                String s21id = request.getParameter("rano");
                osc.fillDocumentStream(reportParams, outputStream, docFmt, reportInstream, msp.getMSPRemittanceQuery(payee, s21id));
            } else if (repType.equals(MSPReconcile.REP_MSPREMSUM)) {
                String s21id = request.getParameter("rano");
                S21 s21 = msp.getS21Record(s21id);

                Provider payeeProv = msp.getProvider(provider, 1);
                reportParams.put("mspBean", msp);
                //set parameters for payee of providers
                reportParams.put("providers", payeeProv.equals("ALL") ? "ALL" : payeeProv.getInitials());
                reportParams.put("providerNo", payeeProv.getProviderNo());
                //set parameters for S21 report header
                reportParams.put("payeeName", s21.getPayeeName());
                reportParams.put("amtBilled", s21.getAmtBilled());
                reportParams.put("amtPaid", s21.getAmtPaid());
                reportParams.put("cheque", s21.getCheque());
                reportParams.put("s21id", s21id);
                reportParams.put("paymentDate", s21.getPaymentDate());
                reportParams.put("payeeNo", s21.getPayeeNo());

                //This is the practitioner summary subreport stream
                InputStream subPractSum = osc.getDocumentStream(REPORTS_PATH + this.reportCfg.getProperty("REP_MSPREMSUM_PRACTSUM"));
                try {
                    reportParams.put("practSum", osc.getJasperReport(subPractSum));
                } finally {
                    IOUtils.closeQuietly(subPractSum);
                }


                //This is the S23 summary subreport stream
                InputStream subS23 = osc.getDocumentStream(REPORTS_PATH + this.reportCfg.getProperty("REP_MSPREMSUM_S23"));
                try {
                    reportParams.put("adj", osc.getJasperReport(subS23));
                } finally {
                    IOUtils.closeQuietly(subS23);
                }

                InputStream subS23orphan = osc.getDocumentStream(REPORTS_PATH + this.reportCfg.getProperty("REP_MSPREMSUM_S23_ORPHAN"));
                try {
                    reportParams.put("orphanAdj", osc.getJasperReport(subS23orphan));
                } finally {
                    IOUtils.closeQuietly(subS23orphan);
                }

                //This is the broadcast messages subreport stream
                InputStream msgs = osc.getDocumentStream(REPORTS_PATH + this.reportCfg.getProperty("MSGS"));
                try {
                    reportParams.put("msgs", osc.getJasperReport(msgs));
                } finally {
                    IOUtils.closeQuietly(msgs);
                }

                try {
                    osc.fillDocumentStream(reportParams, outputStream, docFmt, reportInstream, DbConnectionFilter.getThreadLocalDbConnection());
                } catch (SQLException e) {
                    MiscUtils.getLogger().error("Error", e);
                }

            } else if (repType.equals(MSPReconcile.REP_PAYREF) || repType.equals(MSPReconcile.REP_PAYREF_SUM)) {
                billSearch = msp.getPayments(account, payee, provider, startDate, endDate, !showWCB, !showMSP, !showPriv, !showICBC);
                Provider payeeProv = msp.getProvider(payee, 1);
                Provider acctProv = msp.getProvider(account, 0);
                Provider provProv = msp.getProvider(provider, 0);
                PayRefSummary sumPayed = new PayRefSummary();
                PayRefSummary sumRefunded = new PayRefSummary();
                for (Iterator iter = billSearch.list.iterator(); iter.hasNext(); ) {

                    MSPBill item = (MSPBill) iter.next();
                    double dblValue = Double.parseDouble(item.getAmount());
                    if (dblValue < 0) {
                        sumRefunded.addIncValue(item.getPaymentMethod(), dblValue);
                    }
                    sumPayed.addIncValue(item.getPaymentMethod(), dblValue);
                    sumPayed.addAdjustmentAmount(item.getAdjustmentCodeAmt());
                }
                reportParams.put("sumPayed", sumPayed);
                reportParams.put("sumRefunded", sumRefunded);

                reportParams.put("account", account.equals("ALL") ? "ALL" : acctProv.getFullName());

                reportParams.put("providers", provider.equals("ALL") ? "ALL" : provProv.getInitials());
                reportParams.put("payee", payee.equals("ALL") ? "ALL" : payeeProv.getInitials());
                reportParams.put("startDate", startDate);
                reportParams.put("endDate", endDate);

                //Fill document with report parameter data
                osc.fillDocumentStream(reportParams, outputStream, docFmt, reportInstream, billSearch.list);

            } else if (repType.equals(MSPReconcile.REP_REJ)) {
                billSearch = msp.getBillsByType(account, payee, provider, startDate, endDate, !showWCB, !showMSP, !showPriv, !showICBC, repType);


                Provider payProv = msp.getProvider(payee, 1);
                reportParams.put("account", account.equals("ALL") ? "ALL" : payProv.getFullName());
                //Fill document with report parameter data
                osc.fillDocumentStream(reportParams, outputStream, docFmt, reportInstream, billSearch.list);
            }
        } finally {
            IOUtils.closeQuietly(reportInstream);
        }

        return SUCCESS;
    }

    /**
     * A convenience method that returns a concatenated list of insurer types
     * to be passed into a report
     *
     * @return String
     */
    private String createInsurerList(boolean showICBC, boolean showMSP, boolean showPriv, boolean showWCB) {
        String insurers = "";

        if (showICBC && showMSP && showPriv && showWCB) {
            insurers = "ALL";
        } else {
            if (showICBC) {
                insurers += "ICBC,";
            }
            if (showMSP) {
                insurers += "MSP,";
            }
            if (showPriv) {
                insurers += "Private,";
            }
            if (showWCB) {
                insurers += "WCB,";
            }
        }
        return insurers;
    }

    /**
     * Configures document association settings. Associates a selected report with
     * a specific jasper report definition
     */
    public void cfgReports() {
        this.reportCfg.setProperty(MSPReconcile.REP_INVOICE, "rep_invoice.jrxml");
        this.reportCfg.setProperty(MSPReconcile.REP_PAYREF, "rep_payref.jrxml");
        this.reportCfg.setProperty(MSPReconcile.REP_PAYREF_SUM, "rep_payref_sum.jrxml");
        this.reportCfg.setProperty(MSPReconcile.REP_ACCOUNT_REC, "rep_account_rec.jrxml");
        this.reportCfg.setProperty(MSPReconcile.REP_REJ, "rep_rej.jrxml");

        this.reportCfg.setProperty(MSPReconcile.REP_WO, "rep_wo.jrxml");
        this.reportCfg.setProperty(MSPReconcile.REP_MSPREM, "rep_msprem.jrxml");
        this.reportCfg.setProperty(MSPReconcile.REP_MSPREMSUM, "rep_mspremsum.jrxml");
        this.reportCfg.setProperty(MSPReconcile.REP_MSPREMSUM_PRACTSUM, "msppremsum.practsum.jrxml");
        this.reportCfg.setProperty(MSPReconcile.REP_MSPREMSUM_S23, "msppremsum.s23.jrxml");
        this.reportCfg.setProperty(MSPReconcile.REP_MSPREMSUM_S23_ORPHAN, "msppremsum.s23_orphan.jrxml");
        this.reportCfg.setProperty("MSGS", "broadcastmessages.jrxml");
    }


    /**
     * Configures the response header for upload of specified mime-type
     *
     * @param response HttpServletResponse
     * @param docName  String
     * @param docType  String
     */
    public void cfgHeader(HttpServletResponse response, String docName,
                          String docType) {
        Map<String, String> allowedTypes = Map.of(
            "pdf", "application/pdf",
            "csv", "application/csv"
        );

        // Sanitize and validate docType
        String sanitizedDocType = sanitizeHeaderValue(docType);
        String mimeType = allowedTypes.getOrDefault(
            sanitizedDocType.toLowerCase(), 
            "application/octet-stream"
        );

        // Sanitize docName
        String sanitizedDocName = sanitizeHeaderValue(docName);
        
        response.setContentType(mimeType);
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + sanitizedDocName + "." + sanitizedDocType + "\"");
    }

    private String sanitizeHeaderValue(String input) {
        if (input == null) return "";
    
        // Remove all control characters including CR, LF, and null bytes
        // Also remove quotes and semicolons that could break header parsing
        return input.replaceAll("[\r\n\0]|[\\p{Cntrl}]|[\"';]", "").trim();
    }


    /**
     * A convenience method for retrieving the servlet outputstream without
     * cluttering the calling code with verbose exception handling
     *
     * @param response HttpServletResponse
     * @return ServletOutputStream
     */
    protected ServletOutputStream getServletOstream(HttpServletResponse response) {
        ServletOutputStream outputStream = null;
        try {
            outputStream = response.getOutputStream();
        } catch (IOException ex) {
            MiscUtils.getLogger().error("Error", ex);
        }
        return outputStream;
    }

    private String docFormat;
    private String selAccount;
    private String selPayee;
    private String selProv;
    private String showICBC;
    private String showMSP;
    private String showPRIV;
    private String showWCB;
    private String verCode;
    private String xml_appointment_date;
    private String xml_vdate;
    private String repType;

    public String getDocFormat() {
        return docFormat;
    }

    public void setDocFormat(String docFormat) {
        this.docFormat = docFormat;
    }

    public String getSelAccount() {
        return selAccount;
    }

    public void setSelAccount(String selAccount) {
        this.selAccount = selAccount;
    }

    public String getSelPayee() {
        return selPayee;
    }

    public void setSelPayee(String selPayee) {
        this.selPayee = selPayee;
    }

    public String getSelProv() {
        return selProv;
    }

    public void setSelProv(String selProv) {
        this.selProv = selProv;
    }

    public String getShowICBC() {
        return showICBC;
    }

    public void setShowICBC(String showICBC) {
        this.showICBC = showICBC;
    }

    public String getShowMSP() {
        return showMSP;
    }

    public void setShowMSP(String showMSP) {
        this.showMSP = showMSP;
    }

    public String getShowPRIV() {
        return showPRIV;
    }

    public void setShowPRIV(String showPRIV) {
        this.showPRIV = showPRIV;
    }

    public String getShowWCB() {
        return showWCB;
    }

    public void setShowWCB(String showWCB) {
        this.showWCB = showWCB;
    }

    public String getVerCode() {
        return verCode;
    }

    public void setVerCode(String verCode) {
        this.verCode = verCode;
    }

    public String getXml_appointment_date() {
        return xml_appointment_date;
    }

    public void setXml_appointment_date(String xml_appointment_date) {
        this.xml_appointment_date = xml_appointment_date;
    }

    public String getXml_vdate() {
        return xml_vdate;
    }

    public void setXml_vdate(String xml_vdate) {
        this.xml_vdate = xml_vdate;
    }

    public String getRepType() {
        return repType;
    }

    public void setRepType(String repType) {
        this.repType = repType;
    }
}
