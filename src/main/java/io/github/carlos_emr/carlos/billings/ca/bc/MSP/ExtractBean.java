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


package io.github.carlos_emr.carlos.billings.ca.bc.MSP;

import io.github.carlos_emr.carlos.utility.LogSafe;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.billing.CA.BC.dao.LogTeleplanTxDao;
import io.github.carlos_emr.carlos.billing.CA.BC.model.LogTeleplanTx;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Wcb;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.utility.DateRange;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.Misc;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.entities.Billingmaster;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;

import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.File;
import java.io.Serializable;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;


public class ExtractBean extends Object implements Serializable {
    private static Logger logger = MiscUtils.getLogger();
    private final java.util.Set<Integer> billIdsToMark = new java.util.LinkedHashSet<>();
    private final java.util.Set<Integer> masterIdsToMark = new java.util.LinkedHashSet<>();
    public java.util.Set<Integer> getBillIdsToMark() { return java.util.Set.copyOf(billIdsToMark); }
    public java.util.Set<Integer> getMasterIdsToMark() { return java.util.Set.copyOf(masterIdsToMark); }

    private LogTeleplanTxDao logTeleplanTxDao = SpringUtils.getBean(LogTeleplanTxDao.class);
    private BillingmasterDAO billingmasterDao = SpringUtils.getBean(BillingmasterDAO.class);


    private String ohipRecord;
    private String ohipClaim;
    private String ohipReciprocal;
    private String ohipFilename;
    private String htmlFilename;
    private String value;
    private String query;
    private String providerNo;
    private String ohipVer;
    private String ohipCenter;
    private String demoName;
    private String invNo;
    private String batchHeader;
    private int invCount = 0;
    private java.util.Date today;
    private String output;
    private SimpleDateFormat formatter;
    private int recordCount = 0;
    private int patientCount = 0;
    private String rCount = "";
    private String pCount = "";
    private String hcCount = "";
    private String htmlCode = "";
    private String htmlContent = "";
    private String htmlContentHeader = "";
    private String htmlFooter = "";
    private String htmlValue = "";
    private BigDecimal BigTotal = new BigDecimal(0).setScale(2, BigDecimal.ROUND_HALF_UP);
    private DateRange dateRange = null;
    private String eFlag = "";
    private int vsFlag = 0;
    private String logNo = "";
    private String logValue = "";
    public String[] dbParam;
    public String errorMsg;

    public CheckBillingData checkData = new CheckBillingData();

    public ExtractBean() {
        formatter = new SimpleDateFormat("yyyyMMddHmm");
        today = new java.util.Date();
        output = formatter.format(today);
    }


    public synchronized void dbQuery() {
        if ("1".equals(eFlag) && !org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("A transaction is required for a Teleplan export");
        }
        String dataCenterId = CarlosProperties.getInstance().getProperty("dataCenterId");
        if (HasBillingItemsToSubmit()) {


            if (vsFlag == 0) {
                logNo = getSequence();
                batchHeader = "VS1" + dataCenterId + Misc.forwardZero(logNo, 7) + "V6242" + "OSCAR_MCMASTER           " + "V1.1      " + "20030930" + "OSCAR MCMASTER                          " + "(905) 575-1300 " + Misc.space(25) + Misc.space(57) + "\r";
                errorMsg = checkData.checkVS1("VS1", dataCenterId, Misc.forwardZero(logNo, 7), "V6242", "OSCAR_MCMASTER           ", "V1.1      ", "20030930", "OSCAR MCMASTER                          ", "(905) 575-1300 ", Misc.space(25), Misc.space(57));
                logValue = batchHeader;
                setLog(logNo, logValue);
                vsFlag = 1;
            } else {
                batchHeader = "";
            }

            htmlContentHeader = htmlContentHeaderGen(providerNo, output.substring(0, 8), errorMsg);
            errorMsg = "";

            value = batchHeader;

            BillingmasterDAO bmDao = SpringUtils.getBean(BillingmasterDAO.class);
            StringBuilder dateFilter = new StringBuilder();
            java.util.List<String> dateValues = new java.util.ArrayList<>();
            if (dateRange != null) {
                java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd");
                if (dateRange.getFrom() != null) { dateFilter.append(" AND billing_date >= ?"); dateValues.add(dateFormat.format(dateRange.getFrom())); }
                if (dateRange.getTo() != null) { dateFilter.append(" AND billing_date <= ?"); dateValues.add(dateFormat.format(dateRange.getTo())); }
            }
            for (var invoice : bmDao.findBcExtractBills(providerNo, dateFilter.toString(), dateValues.toArray(String[]::new))) {
                int mastersBeforeInvoice = masterIdsToMark.size();
                invNo = invoice.billingNo().toString();
                demoName = invoice.demographicName();
                String billType = invoice.billingType();
                invCount = 0;

                if (billType.equals("MSP") || billType.equals("ICBC")) {

                    for (var claim : bmDao.findBcExtractClaims(Integer.parseInt(invNo))) {
                        Billingmaster bm = formatterClaim(claim, Integer.parseInt(invNo));
                        logNo = getSequence();
                        String dataLine;
                        BigDecimal amount;
                        try {
                            amount = claimAmount(bm.getBillAmount());
                            dataLine = getClaimDetailRecord(bm, logNo);
                            if (dataLine.length() != 424) throw new IllegalArgumentException("Teleplan claim record length is invalid");
                        } catch (IllegalArgumentException | ArithmeticException invalid) {
                            if ("1".equals(eFlag)) throw invalid;
                            htmlContent += checkData.printErrorMsg(String.valueOf(bm.getBillingmasterNo()),
                                    "Claim cannot be exported: check its amount and fixed-width fields.");
                            continue;
                        }
                        recordCount++;

                        value += "\n" + dataLine + "\r";
                        logValue = dataLine;
                        setLog(logNo, logValue);

                        if (hasNoteRecord(bm)) {
                            String noteLogNo = getSequence();
                            String noteRecordLine = getNoteRecord(bm, noteLogNo);
                            value += "\n" + noteRecordLine + "\r";
                            setLog(noteLogNo, noteRecordLine);
                        }

                        BigTotal = BigTotal.add(amount);

                        if (isMSPInsurer(bm)) {
                            htmlContent += htmlLine("" + bm.getBillingmasterNo(), invNo, demoName, bm.getPhn(), bm.getServiceDate(),
                                    bm.getBillingCode(), bm.getBillAmount(), bm.getDxCode1(),
                                    bm.getDxCode2(), bm.getDxCode3());
                        } else {
                            htmlContent += htmlLine("" + bm.getBillingmasterNo(), invNo, demoName,
                                    bm.getOinRegistrationNo(),
                                    bm.getServiceDate(),
                                    bm.getBillingCode(),
                                    bm.getBillAmount(),
                                    bm.getDxCode1(),
                                    bm.getDxCode2(),
                                    bm.getDxCode3());
                        }


                        errorMsg = checkData.checkC02("" + bm.getBillingmasterNo(), bm);
                        htmlContent += errorMsg;

                        invCount++;
                        setAsBilledMaster("" + bm.getBillingmasterNo());


                    }// while
                } else if (billType.equals("WCB")) {
                    BillingServiceDao bsDao = SpringUtils.getBean(BillingServiceDao.class);

                    java.util.Map<Integer, Wcb> forms = new java.util.LinkedHashMap<>();
                    for (Wcb form : bsDao.findOpenWcbFormsByBillingId(Integer.valueOf(invNo))) {
                        forms.put(form.getId(), form);
                    }
                    java.util.List<Billingmaster> claims = bmDao.findBcPendingExtractClaims(Integer.parseInt(invNo)).stream()
                            .map(claim -> formatterClaim(claim, Integer.parseInt(invNo))).toList();
                    Billingmaster wcbClaim;
                    BigDecimal amount;
                    try {
                        if (forms.size() != 1 || claims.size() != 1) {
                            throw new IllegalArgumentException("A WCB invoice must have one open form and one eligible claim");
                        }
                        wcbClaim = claims.getFirst();
                        Wcb form = forms.values().iterator().next();
                        if (!java.util.Objects.equals(form.getFeeItem(), wcbClaim.getBillingCode())
                                && !java.util.Objects.equals(form.getExtraFeeItem(), wcbClaim.getBillingCode())) {
                            throw new IllegalArgumentException("WCB form and claim fee codes do not match");
                        }
                        amount = claimAmount(wcbClaim.getBillAmount());
                    } catch (IllegalArgumentException | ArithmeticException invalid) {
                        if ("1".equals(eFlag)) throw invalid;
                        htmlContent += checkData.printWarningMsg("WCB invoice " + invNo
                                + " cannot be exported: check its linked form, fee code and amount.");
                        continue;
                    }
                    for (Wcb form : forms.values()) {
                        WcbSb sb = new WcbSb(form, wcbClaim);
                        htmlContent += sb.getHtmlLine();
                        htmlContent += checkData.printWarningMsg("");

                        if (sb.isFormNeeded()) {
                            logNo = getSequence();
                            String lines = sb.Line1(String.valueOf(logNo));
                            value += "\n" + lines + "\r";
                            setLog(logNo, lines);

                            logNo = getSequence();
                            lines = sb.Line2(String.valueOf(logNo));
                            value += "\n" + lines + "\r";
                            setLog(logNo, lines);

                            logNo = getSequence();
                            lines = sb.Line3(String.valueOf(logNo));
                            value += "\n" + lines + "\r";
                            setLog(logNo, lines);

                            logNo = getSequence();
                            lines = sb.Line4(String.valueOf(logNo));
                            value += "\n" + lines + "\r";
                            setLog(logNo, lines);

                            logNo = getSequence();
                            lines = sb.Line5(String.valueOf(logNo));
                            value += "\n" + lines + "\r";
                            setLog(logNo, lines);

                            logNo = getSequence();
                            lines = sb.Line6(String.valueOf(logNo));
                            value += "\n" + lines + "\r";
                            setLog(logNo, lines);

                            logNo = getSequence();
                            lines = sb.Line7(String.valueOf(logNo));
                            value += "\n" + lines + "\r";
                            setLog(logNo, lines);

                            logNo = getSequence();
                            lines = sb.Line8(String.valueOf(logNo));
                            value += "\n" + lines + "\r";
                            setLog(logNo, lines);
                        } else {

                            logNo = getSequence();
                            String lines = sb.Line9(String.valueOf(logNo));
                            value += "\n" + lines + "\r";
                            setLog(logNo, lines);

                        }
                    }

                    setAsBilledMaster(String.valueOf(wcbClaim.getBillingmasterNo()));
                    BigTotal = BigTotal.add(amount);
                    recordCount++;
                    /////////////////////////////////////
                }
                if (masterIdsToMark.size() > mastersBeforeInvoice || !"1".equals(eFlag)) {
                    patientCount++;
                    setAsBilled(invNo);
                }
            }

            pCount = pCount + patientCount;
            rCount = rCount + recordCount;

            htmlFooter = HtmlTeleplanHelper.htmlFooter(providerNo, pCount, BigTotal) + HtmlTeleplanHelper.htmlBottom();
            htmlCode = htmlContentHeader + htmlContent + htmlFooter;

            ohipReciprocal = String.valueOf(hcCount);
            ohipRecord = String.valueOf(rCount);
            ohipClaim = String.valueOf(pCount);


        }
    }


    public void setAsBilled(String newInvNo) {
        if ("1".equals(eFlag)) billIdsToMark.add(Integer.valueOf(newInvNo));
    }

    public void setAsBilledMaster(String newInvNo) {
        if ("1".equals(eFlag)) masterIdsToMark.add(Integer.valueOf(newInvNo));
    }

    /**
     * Sets the log value for a billing entry.
     *
     * @param x the entry ID
     * @param logValue the log value to set
     */
    public void setLog(String x, String logValue) {
        if (eFlag.equals("1")) {
            LogTeleplanTx l = this.logTeleplanTxDao.find(Integer.parseInt(x));
            if (l != null) {
                l.setClaim(logValue.getBytes());
                logTeleplanTxDao.merge(l);
            }
        }
    }


    public String getSequence() {
        String n = "1";
        if (eFlag.equals("1")) {
            LogTeleplanTx l = new LogTeleplanTx();
            l.setClaim("New Log".getBytes());
            logTeleplanTxDao.persist(l);
            return l.getId().toString();

        }
        return n;
    }

    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public void writeFile(String content) {
        writeLegacyFile(ohipFilename, content);
    }

    /**
     * Writes the report in export mode after the caller assigns its HTML filename.
     * Preview mode leaves the report in memory without requiring a filename.
     *
     * @param htmlvalue1 HTML report content
     * @throws IllegalStateException if export mode is enabled and the HTML filename is null or blank
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public void writeHtml(String content) {
        if ("1".equals(eFlag)) {
            if (htmlFilename == null || htmlFilename.isBlank()) throw new IllegalStateException("HTML filename must be set before writing a Teleplan report");
            writeLegacyFile(htmlFilename, content);
        }
    }

    private void writeLegacyFile(String filename, String content) {
        if (filename == null || filename.isBlank()) throw new IllegalStateException("Teleplan filename is required");
        try {
            File home = PathValidationUtils.resolveConfiguredDirectory(CarlosProperties.getInstance().getProperty("HOME_DIR"), "Teleplan output directory");
            File safe = PathValidationUtils.validatePath(filename, home);
            java.nio.file.Files.writeString(safe.toPath(), content + System.lineSeparator(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Teleplan file could not be written", failure);
        }
    }

    public String getOhipReciprocal() {
        return ohipReciprocal;
    }

    public String getOhipRecord() {
        return ohipRecord;
    }

    public String getOhipClaim() {
        return ohipClaim;
    }

    public String getTotalAmount() {
        return BigTotal.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    public String getHtmlValue() {
        return htmlValue;
    }

    public String getHtmlCode() {
        return htmlCode;
    }

    public String getValue() {
        return value;
    }

    public String getOhipVer() {
        return ohipVer;
    }

    public synchronized void setOhipVer(String newOhipVer) {
        ohipVer = newOhipVer;
    }

    public synchronized void setGroupNo(String newGroupNo) {
    }

    public synchronized void setSpecialty(String newSpecialty) {
    }

    public String getOhipCenter() {
        return ohipCenter;
    }

    public synchronized void setBatchCount(String newBatchCount) {
    }

    public synchronized void setOhipCenter(String newOhipCenter) {
        ohipCenter = newOhipCenter;
    }

    public synchronized void setProviderNo(String newProviderNo) {
        providerNo = newProviderNo;
    }

    public synchronized void setOhipFilename(String newOhipFilename) {
        ohipFilename = newOhipFilename;
    }

    public synchronized void setHtmlFilename(String newHtmlFilename) {
        htmlFilename = newHtmlFilename;
    }

    public String getInvNo() {
        return invNo;
    }

    public synchronized void setOscarHome(String oscarHOME) {
    }

    public synchronized void seteFlag(String neweFlag) {
        eFlag = neweFlag;
    }

    public synchronized void setVSFlag(int newVSFlag) {
        vsFlag = newVSFlag;
    }

    public synchronized void setDateRange(DateRange newDateRange) {
        dateRange = newDateRange;
    }

    public String roundUp(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A valid Teleplan billing unit is required");
        }
        try {
            return new BigDecimal(value.strip()).setScale(0, java.math.RoundingMode.UP).toPlainString();
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("A valid Teleplan billing unit is required");
        }
    }

    /** Validate decimal dollars before the legacy fixed-width formatter can truncate or mis-scale them. */
    static BigDecimal claimAmount(String source) {
        if (source == null || !source.strip().matches("[0-9]++(?:\\.[0-9]*+)?+")) {
            throw new IllegalArgumentException("A valid Teleplan claim amount is required");
        }
        BigDecimal amount;
        try { amount = new BigDecimal(source.strip()).setScale(2, java.math.RoundingMode.UNNECESSARY); }
        catch (ArithmeticException invalid) { throw new IllegalArgumentException("Teleplan claim amount must use whole cents"); }
        if (amount.compareTo(new BigDecimal("99999.99")) > 0) {
            throw new IllegalArgumentException("Teleplan claim amount exceeds the wire capacity");
        }
        return amount;
    }

    public String getClaimDetailRecord(Billingmaster bm, String LogNo) {
        String dataLine = Misc.forwardSpace(bm.getClaimcode(), 3)            //p00   3
                + Misc.forwardSpace(bm.getDatacenter(), 5)           //p02   5
                + Misc.forwardZero(logNo, 7)                                  //p04   7
                + Misc.forwardSpace(bm.getPayeeNo(), 5)             //p06   5
                + Misc.forwardSpace(bm.getPractitionerNo(), 5)      //p08   5
                + Misc.forwardZero(bm.getPhn(), 10)                  //p14  10
                + Misc.forwardSpace(bm.getNameVerify(), 4)          //p16   4
                + Misc.forwardSpace(bm.getDependentNum(), 2)        //p18   2
                + Misc.forwardZero(roundUp(bm.getBillingUnit()), 3) //p20   3
                + Misc.forwardZero(bm.getClarificationCode(), 2)    //p22   2
                + Misc.forwardSpace(bm.getAnatomicalArea(), 2)     //p23   2
                + Misc.forwardSpace(bm.getAfterHour(), 1)           //p24   1
                + Misc.forwardZero(bm.getNewProgram(), 2)           //p25   2
                + Misc.forwardZero(bm.getBillingCode(), 5)          //p26   5
                + Misc.forwardZero(claimAmount(bm.getBillAmount()).movePointRight(2).toBigIntegerExact().toString(), 7)           //p27   7
                + Misc.forwardZero(bm.getPaymentMode(), 1)         //p28   1
                + Misc.forwardSpace(bm.getServiceDate(), 8)        //p30   8
                + Misc.forwardZero(bm.getServiceToDay(), 2)        //p32   2
                + Misc.forwardSpace(bm.getSubmissionCode(), 1)     //p34   1
                + Misc.space(1)                                              //p35   1
                + Misc.backwardSpace(bm.getDxCode1(), 5)           //p36   5
                + Misc.backwardSpace(bm.getDxCode2(), 5)           //p37   5
                + Misc.backwardSpace(bm.getDxCode3(), 5)           //p38   5
                + Misc.space(15)                                             //p39  15
                + Misc.forwardSpace(bm.getServiceLocation(), 1)    //p40   1
                + Misc.forwardZero(bm.getReferralFlag1(), 1)       //p41   1
                + Misc.forwardZero(bm.getReferralNo1(), 5)          //p42   5
                + Misc.forwardZero(bm.getReferralFlag2(), 1)        //p44   1
                + Misc.forwardZero(bm.getReferralNo2(), 5)          //p46   5
                + Misc.forwardZero(bm.getTimeCall(), 4)             //p47   4
                + Misc.forwardZero(bm.getServiceStartTime(), 4)    //p48   4
                + Misc.forwardZero(bm.getServiceEndTime(), 4)      //p50   4
                + Misc.forwardZero(bm.getBirthDate(), 8)            //p52   8
                + Misc.forwardZero("" + bm.getBillingmasterNo(), 7)     //p54   7
                + Misc.forwardSpace(bm.getCorrespondenceCode(), 1) //p56   1
                //+ misc.space(20)                                             //p58  20
                + Misc.backwardSpace(bm.getClaimComment(), 20)      //p58  20
                + Misc.forwardSpace(bm.getMvaClaimCode(), 1)       //p60   1
                + Misc.forwardZero(bm.getIcbcClaimNo(), 8)        //p62   8
                + Misc.forwardZero(bm.getOriginalClaim(), 20)     //p64  20
                + Misc.forwardZero(bm.getFacilityNo(), 5)          //p70   5
                + Misc.forwardZero(bm.getFacilitySubNo(), 5)      //p72   5
                + Misc.space(58)                                             //p80  58
                + Misc.backwardSpace(bm.getOinInsurerCode(), 2)    //p100  2
                + Misc.forwardZero(bm.getOinRegistrationNo(), 12)  //p102 12
                + Misc.backwardSpace(bm.getOinBirthdate(), 8)       //p104  8
                + Misc.backwardSpace(bm.getOinFirstName(), 12)     //p106 12
                + Misc.backwardSpace(bm.getOinSecondName(), 1)     //p108  1
                + Misc.backwardSpace(bm.getOinSurname(), 18)        //p110 18
                + Misc.backwardSpace(bm.getOinSexCode(), 1)        //p112  1
                + Misc.backwardSpace(bm.getOinAddress(), 25)        //p114 25
                + Misc.backwardSpace(bm.getOinAddress2(), 25)       //p116 25
                + Misc.backwardSpace(bm.getOinAddress3(), 25)       //p118 25
                + Misc.backwardSpace(bm.getOinAddress4(), 25)       //p120 25
                + Misc.backwardSpace(bm.getOinPostalcode(), 6);     //p122  6
        return dataLine;
    }

    public boolean hasNoteRecord(Billingmaster bm) {
        boolean retval = false;
        try {
            String correspondenceCode = bm.getCorrespondenceCode();
            if (correspondenceCode.equals("N") || correspondenceCode.equals("B")) {
                retval = true;
            }
        } catch (Exception e) {
            retval = false;
            logger.error("Unexpected error: {}", LogSafe.exceptionTrace(e));
        }
        return retval;
    }

    public String getNoteRecord(Billingmaster bm, String seqNo) {
        MSPBillingNote note = new MSPBillingNote();
        return MSPBillingNote.getN01(bm.getDatacenter(), seqNo, bm.getPayeeNo(), bm.getPractitionerNo(), "A", note.getNote("" + bm.getBillingmasterNo()));
    }

    /**
     * Builds the report table header for one provider. Delegates to {@link HtmlTeleplanHelper} so the
     * provider billing number is HTML-encoded; {@code errorMsg} must already be encoded row markup
     * (as produced by {@link CheckBillingData}).
     */
    public String htmlContentHeaderGen(String providerNo, String output, String errorMsg) {
        htmlContentHeader = HtmlTeleplanHelper.htmlContentHeaderGen(providerNo, output, errorMsg);
        return htmlContentHeader;
    }

    /**
     * Builds one encoded claim row for the simulation/report HTML. Patient name, PHN, codes and amounts
     * come from claim records and are encoded by {@link HtmlTeleplanHelper#htmlLine}.
     */
    public String htmlLine(String billingMasterNo, String invNo, String demoName, String phn, String serviceDate, String billingCode, String billAmount, String dx1, String dx2, String dx3) {
        return HtmlTeleplanHelper.htmlLine(billingMasterNo, invNo, demoName, phn, serviceDate, billingCode, billAmount, dx1, dx2, dx3);
    }


    public static boolean HasBillingItemsToSubmit() {
        return SpringUtils.getBean(BillingmasterDAO.class).hasBcExtractInvoices();
    }

    public boolean isMSPInsurer(Billingmaster bm) {
        boolean retval = true;
        String insurer = bm.getOinInsurerCode();
        if (insurer != null && (insurer.trim().length() > 0)) {
            retval = false;
        }
        return retval;
    }
    /**
     * Rebuilds the claim the fixed-width formatter, the C02 checker and the WCB lines read. It must set
     * every claim field they use: a field left out here is exported blank without any error.
     */
    private static Billingmaster formatterClaim(io.github.carlos_emr.carlos.billings.ca.bc.data.BcBillingRows.ExtractClaimRow source, int billingNo) {
        Billingmaster result = new Billingmaster();
        result.setBillingNo(billingNo);
        result.setBillAmount(source.billAmount());
        result.setBillingmasterNo(source.billingmasterNo());
        result.setPhn(source.phn());
        result.setServiceDate(source.serviceDate());
        result.setBillingCode(source.billingCode());
        result.setDxCode1(source.dxCode1());
        result.setDxCode2(source.dxCode2());
        result.setDxCode3(source.dxCode3());
        result.setClaimcode(source.claimcode());
        result.setDatacenter(source.datacenter());
        result.setPayeeNo(source.payeeNo());
        result.setPractitionerNo(source.practitionerNo());
        result.setNameVerify(source.nameVerify());
        result.setDependentNum(source.dependentNum());
        result.setBillingUnit(source.billingUnit());
        result.setClarificationCode(source.clarificationCode());
        result.setAnatomicalArea(source.anatomicalArea());
        result.setAfterHour(source.afterHour());
        result.setNewProgram(source.newProgram());
        result.setPaymentMode(source.paymentMode());
        result.setServiceToDay(source.serviceToDay());
        result.setSubmissionCode(source.submissionCode());
        result.setServiceLocation(source.serviceLocation());
        result.setReferralFlag1(source.referralFlag1());
        result.setReferralNo1(source.referralNo1());
        result.setReferralFlag2(source.referralFlag2());
        result.setReferralNo2(source.referralNo2());
        result.setTimeCall(source.timeCall());
        result.setServiceStartTime(source.serviceStartTime());
        result.setServiceEndTime(source.serviceEndTime());
        result.setBirthDate(source.birthDate());
        result.setCorrespondenceCode(source.correspondenceCode());
        // P58: without it every exported claim carried twenty spaces instead of its comment.
        result.setClaimComment(source.claimComment());
        result.setMvaClaimCode(source.mvaClaimCode());
        result.setIcbcClaimNo(source.icbcClaimNo());
        result.setOriginalClaim(source.originalClaim());
        result.setFacilityNo(source.facilityNo());
        result.setFacilitySubNo(source.facilitySubNo());
        result.setOinInsurerCode(source.oinInsurerCode());
        result.setOinRegistrationNo(source.oinRegistrationNo());
        result.setOinBirthdate(source.oinBirthdate());
        result.setOinFirstName(source.oinFirstName());
        result.setOinSecondName(source.oinSecondName());
        result.setOinSurname(source.oinSurname());
        result.setOinSexCode(source.oinSexCode());
        result.setOinAddress(source.oinAddress());
        result.setOinAddress2(source.oinAddress2());
        result.setOinAddress3(source.oinAddress3());
        result.setOinAddress4(source.oinAddress4());
        result.setOinPostalcode(source.oinPostalcode());
        return result;
    }

}
