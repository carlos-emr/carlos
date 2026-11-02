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


package io.github.carlos_emr.carlos.billings.MSP;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.billing.CA.BC.dao.LogTeleplanTxDao;
import io.github.carlos_emr.carlos.billing.CA.BC.model.LogTeleplanTx;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.entities.Billingmaster;
import io.github.carlos_emr.carlos.billings.ca.bc.MSP.HtmlTeleplanHelper;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.billings.ca.bc.service.BcBillingService;

import java.io.*;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ExtractBean extends Object implements Serializable {
    private static Logger logger = MiscUtils.getLogger();
    private static final Pattern DATE_RANGE_CONDITION = Pattern.compile(
            "(?i)\\band\\s+(billing_date|visitdate)\\s*(=|>=|<=|>|<)\\s*'?(\\d{4}[-/]?\\d{2}[-/]?\\d{2})'?");
    private static final Pattern SAFE_PARAMETERIZED_DATE_RANGE = Pattern.compile(
            "(?i)^\\s*(?:and\\s+(?:billing_date|visitdate)\\s*(?:=|>=|<=|>|<)\\s*\\?\\s*)+$");
    private LogTeleplanTxDao logTeleplanTxDao = SpringUtils.getBean(LogTeleplanTxDao.class);
    private BillingmasterDAO billingmasterDao = SpringUtils.getBean(BillingmasterDAO.class);


    private String ohipRecord;
    private String ohipClaim;
    private String ohipReciprocal;
    private String ohipFilename;
    private String htmlFilename;
    private String value;
    private String providerNo;
    private String ohipVer;
    private String ohipCenter;
    private String demoName;
    private String invNo;
    private String batchHeader;
    private String totalAmount;
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
    private double dFee;
    private BigDecimal BigTotal = new BigDecimal(0).setScale(2, BigDecimal.ROUND_HALF_UP);
    private BigDecimal bdFee = new BigDecimal(0).setScale(2, BigDecimal.ROUND_HALF_UP);
    private String dateRange = "";
    private String[] dateRangeParams = new String[0];
    private String eFlag = "";
    private String oscar_home;
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

    public void dbQuery() {
        SpringUtils.getBean(BcBillingService.class).generateExtract(this);
    }

    /** Publishes the assembled report after the owning transaction commits. */
    public void publishExtract() {
        if ("1".equals(eFlag)) writeHtml(htmlCode);
    }

    /** Called by the Spring service inside one transaction; failures roll back all claim/status writes. */
    public void buildExtract() {
        String dataCenterId = CarlosProperties.getInstance().getProperty("dataCenterId");
        if (vsFlag == 0) {
            logNo = eFlag.equals("1") ? getSequence() : "1";
            batchHeader = "VS1" + dataCenterId + forwardZero(logNo, 7) + "V6242" + "OSCAR_MCMASTER           " + "V1.1      " + "20030930" + "OSCAR MCMASTER                          " + "(905) 575-1300 " + space(25) + space(57) + "\r";
            errorMsg = checkData.checkVS1("VS1", dataCenterId, forwardZero(logNo, 7), "V6242", "OSCAR_MCMASTER           ", "V1.1      ", "20030930", "OSCAR MCMASTER                          ", "(905) 575-1300 ", space(25), space(57));

            logValue = batchHeader;
            if (eFlag.equals("1")) setLog(logNo, logValue);
            vsFlag = 1;
        } else {
            batchHeader = "";
        }

        htmlContentHeader = HtmlTeleplanHelper.htmlContentHeaderGen(providerNo, output.substring(0, 8), errorMsg);
        errorMsg = "";

        value = batchHeader;
        for (Billing bill : billingmasterDao.findBcExtractBills(providerNo, dateRange, dateRangeParams)) {
            patientCount = patientCount + 1;
            invNo = bill.getId().toString();
            demoName = bill.getDemographicName();

            invCount = 0;
            if (invNo == null || !invNo.matches("^[0-9]+$")) {
                logger.warn("Skipping billing record due to invalid invoice number: {}", invNo);
                continue;
            }
            for (Billingmaster bm : billingmasterDao.findBcExtractClaims(Integer.parseInt(invNo))) {
                recordCount = recordCount + 1;

                logNo = eFlag.equals("1") ? getSequence() : "1";

                String dataLine = getClaimDetailRecord(bm, logNo);
                if (dataLine.length() != 424) {
                    MiscUtils.getLogger().debug("dataLine2 {} Len{}", logNo, dataLine.length());
                }
                value = value + "\n" + dataLine + "\r";
                logValue = dataLine;
                if (eFlag.equals("1")) setLog(logNo, logValue);
                dFee = Double.parseDouble(bm.getBillAmount());
                bdFee = new BigDecimal(dFee).setScale(2, BigDecimal.ROUND_HALF_UP);
                BigTotal = BigTotal.add(bdFee);

                if (invCount == 0) {
                    htmlContent = htmlContent + HtmlTeleplanHelper.htmlLine(String.valueOf(bm.getBillingmasterNo()), invNo, demoName,
                            bm.getPhn(), bm.getServiceDate(), bm.getBillingCode(),
                            bm.getBillAmount(), bm.getDxCode1(), bm.getDxCode2(),
                            bm.getDxCode3());
                } else {
                    htmlContent = htmlContent + HtmlTeleplanHelper.continuationLine(String.valueOf(bm.getBillingmasterNo()),
                            bm.getBillingCode(), bm.getBillAmount(), bm.getDxCode1(),
                    bm.getDxCode2(), bm.getDxCode3());
                }

                errorMsg = checkData.checkC02(String.valueOf(bm.getBillingmasterNo()), bm);
                htmlContent += errorMsg;

                invCount = invCount + 1;
                if (eFlag.compareTo("1") == 0) {
                    setAsBilledMaster(String.valueOf(bm.getBillingmasterNo()));
                }
            }
            if (eFlag.compareTo("1") == 0) {
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


    public void setAsBilled(String newInvNo) {
        if (eFlag.equals("1")) SpringUtils.getBean(BcBillingService.class).markBillBilled(Integer.parseInt(newInvNo));
    }

    public void setAsBilledMaster(String newInvNo) {
        if (eFlag.equals("1")) SpringUtils.getBean(BcBillingService.class).markMasterBilled(Integer.parseInt(newInvNo));
    }

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
        LogTeleplanTx l = new LogTeleplanTx();
        l.setClaim("New Log".getBytes());
        logTeleplanTxDao.persist(l);
        return l.getId().toString();
    }


    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public void writeFile(String value1) {
        try {
            String home_dir;
            String userHomePath = System.getProperty("user.home", "user.dir");

            File userHomeDir = PathValidationUtils.validateConfiguredDirectory(userHomePath, "user.home");
            File pFile = PathValidationUtils.validateExistingPath(new File(userHomeDir, oscar_home), userHomeDir);
            Properties ap = new Properties();
            try (FileInputStream pStream = new FileInputStream(pFile)) {
                ap.load(pStream);
            }

            home_dir = ap.getProperty("HOME_DIR");

            File homeDir = PathValidationUtils.resolveConfiguredDirectory(home_dir, "HOME_DIR");
            File outputFile = PathValidationUtils.validateGeneratedChildPath(PathValidationUtils.validateGeneratedFileName(ohipFilename), homeDir);
            try (PrintStream p = new PrintStream(new FileOutputStream(outputFile))) {
                p.println(value1);
            }
        } catch (Exception e) {
            logger.error("Unexpected error", e);
        }
    }

    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public void writeHtml(String htmlvalue1) {
        try {
            String home_dir1;
            String userHomePath1 = System.getProperty("user.home", "user.dir");

            File userHomeDir1 = PathValidationUtils.validateConfiguredDirectory(userHomePath1, "user.home");
            File pFile1 = PathValidationUtils.validateExistingPath(new File(userHomeDir1, oscar_home), userHomeDir1);
            Properties ap1 = new Properties();
            try (FileInputStream pStream1 = new FileInputStream(pFile1)) {
                ap1.load(pStream1);
            }
            home_dir1 = ap1.getProperty("HOME_DIR");

            File homeDir1 = PathValidationUtils.resolveConfiguredDirectory(home_dir1, "HOME_DIR");
            File outputFile1 = PathValidationUtils.validateGeneratedChildPath(PathValidationUtils.validateGeneratedFileName(htmlFilename), homeDir1);
            try (PrintStream p1 = new PrintStream(new FileOutputStream(outputFile1))) {
                p1.println(htmlvalue1);
            }
        } catch (Exception e) {
            logger.error("Unexpected error", e);
        }

    }

    public String space(int i) {
        String returnValue = new String();
        for (int j = 0; j < i; j++) {
            returnValue += " ";
        }
        return returnValue;
    }

    public String backwardSpace(String y, int i) {
        String returnValue = new String();
        for (int j = y.length(); j < i; j++) {
            returnValue += " ";
        }
        return cutBackString(y + returnValue, i);
    }


    public String zero(int x) {
        String returnZeroValue = new String();
        for (int y = 0; y < x; y++) {
            returnZeroValue += "0";
        }
        return returnZeroValue;
    }

    public String forwardZero(String y, int x) {
        String returnZeroValue = new String();
        for (int i = y.length(); i < x; i++) {
            returnZeroValue += "0";
        }

        return cutFrontString(returnZeroValue + y, x);
    }

    public String cutFrontString(String str, int len) {
        return str.substring(str.length() - len, str.length());
    }

    public String cutBackString(String str, int len) {
        return str.substring(0, len);
    }

    public String forwardSpace(String y, int x) {
        String returnZeroValue = new String();
        for (int i = y.length(); i < x; i++) {
            returnZeroValue += " ";
        }
        return cutFrontString(returnZeroValue + y, x);
    }

    public String moneyFormat(String y, int x) {
        String returnZeroValue = forwardZero(y.replaceAll("\\.", ""), x);
        return cutFrontString(returnZeroValue, x);
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
        return totalAmount;
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
        oscar_home = oscarHOME;
    }

    public synchronized void seteFlag(String neweFlag) {
        eFlag = neweFlag;
    }

    public synchronized void setVSFlag(int newVSFlag) {
        vsFlag = newVSFlag;
    }

    public synchronized void setDateRange(String newDateRange) {
        if (newDateRange == null || newDateRange.trim().isEmpty()) {
            dateRange = "";
            dateRangeParams = new String[0];
            return;
        }

        Matcher matcher = DATE_RANGE_CONDITION.matcher(newDateRange);
        StringBuffer parameterized = new StringBuffer();
        List<String> params = new ArrayList<>();
        while (matcher.find()) {
            params.add(matcher.group(3));
            matcher.appendReplacement(parameterized,
                    Matcher.quoteReplacement("and " + matcher.group(1) + " " + matcher.group(2) + " ?"));
        }
        matcher.appendTail(parameterized);

        String parameterizedDateRange = parameterized.toString();
        if (params.isEmpty() || !SAFE_PARAMETERIZED_DATE_RANGE.matcher(parameterizedDateRange).matches()) {
            throw new IllegalArgumentException("Unsupported billing date range filter");
        }

        dateRange = parameterizedDateRange;
        dateRangeParams = params.toArray(new String[0]);
    }

    public String roundUp(String str) {
        String retval = "1";
        try {
            retval = new java.math.BigDecimal(str).setScale(0, BigDecimal.ROUND_UP).toString();
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
        return retval;
    }

    public String getClaimDetailRecord(Billingmaster bm, String LogNo) {
        String dataLine = forwardSpace(bm.getClaimcode(), 3)            //p00   3
                + forwardSpace(bm.getDatacenter(), 5)           //p02   5
                + forwardZero(LogNo, 7)                                  //p04   7
                + forwardSpace(bm.getPayeeNo(), 5)             //p06   5
                + forwardSpace(bm.getPractitionerNo(), 5)      //p08   5
                + forwardZero(bm.getPhn(), 10)                  //p14  10
                + forwardSpace(bm.getNameVerify(), 4)          //p16   4
                + forwardSpace(bm.getDependentNum(), 2)        //p18   2
                + forwardZero(roundUp(bm.getBillingUnit()), 3)          //p20   3
                + forwardZero(bm.getClarificationCode(), 2)    //p22   2
                + forwardSpace(bm.getAnatomicalArea(), 2)     //p23   2
                + forwardSpace(bm.getAfterHour(), 1)           //p24   1
                + forwardZero(bm.getNewProgram(), 2)           //p25   2
                + forwardZero(bm.getBillingCode(), 5)          //p26   5
                + moneyFormat(bm.getBillAmount(), 7)           //p27   7
                + forwardZero(bm.getPaymentMode(), 1)         //p28   1
                + forwardSpace(bm.getServiceDate(), 8)        //p30   8
                + forwardZero(bm.getServiceToDay(), 2)        //p32   2
                + forwardSpace(bm.getSubmissionCode(), 1)     //p34   1
                + space(1)                                              //p35   1
                + backwardSpace(bm.getDxCode1(), 5)           //p36   5
                + backwardSpace(bm.getDxCode2(), 5)           //p37   5
                + backwardSpace(bm.getDxCode3(), 5)           //p38   5
                + space(15)                                             //p39  15
                + forwardSpace(bm.getServiceLocation(), 1)    //p40   1
                + forwardZero(bm.getReferralFlag1(), 1)       //p41   1
                + forwardZero(bm.getReferralNo1(), 5)          //p42   5
                + forwardZero(bm.getReferralFlag2(), 1)        //p44   1
                + forwardZero(bm.getReferralNo2(), 5)          //p46   5
                + forwardZero(bm.getTimeCall(), 4)             //p47   4
                + forwardZero(bm.getServiceStartTime(), 4)    //p48   4
                + forwardZero(bm.getServiceEndTime(), 4)      //p50   4
                + forwardZero(bm.getBirthDate(), 8)            //p52   8
                + forwardZero(String.valueOf(bm.getBillingmasterNo()), 7)     //p54   7
                + forwardSpace(bm.getCorrespondenceCode(), 1) //p56   1
                + space(20)                                             //p58  20
                + forwardSpace(bm.getMvaClaimCode(), 1)       //p60   1
                + forwardZero(bm.getIcbcClaimNo(), 8)        //p62   8
                + forwardZero(bm.getOriginalClaim(), 20)     //p64  20
                + forwardZero(bm.getFacilityNo(), 5)          //p70   5
                + forwardZero(bm.getFacilitySubNo(), 5)      //p72   5
                + space(58)                                             //p80  58
                + backwardSpace(bm.getOinInsurerCode(), 2)    //p100  2
                + backwardSpace(bm.getOinRegistrationNo(), 12)//p102 12
                + backwardSpace(bm.getOinBirthdate(), 8)       //p104  8
                + backwardSpace(bm.getOinFirstName(), 12)     //p106 12
                + backwardSpace(bm.getOinSecondName(), 1)     //p108  1
                + backwardSpace(bm.getOinSurname(), 18)        //p110 18
                + backwardSpace(bm.getOinSexCode(), 1)        //p112  1
                + backwardSpace(bm.getOinAddress(), 25)        //p114 25
                + backwardSpace(bm.getOinAddress2(), 25)       //p116 25
                + backwardSpace(bm.getOinAddress3(), 25)       //p118 25
                + backwardSpace(bm.getOinAddress4(), 25)       //p120 25
                + backwardSpace(bm.getOinPostalcode(), 6);     //p122  6
        return dataLine;
    }
}
