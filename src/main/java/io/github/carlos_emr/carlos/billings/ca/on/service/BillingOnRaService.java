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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.billings.ca.on.BillingMoney;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingRaDetailDto;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.RaDetailDao;
import io.github.carlos_emr.carlos.commn.dao.RaHeaderDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.RaDetail;
import io.github.carlos_emr.carlos.commn.model.RaHeader;
import io.github.carlos_emr.carlos.utility.FileValidationException;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;

import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
// NOTE: this service is read+write — addOneRADtRecord/importDocumentBeanFile
// call raDetailDao.persist, raHeaderDao.persist, cheader1Dao.merge (lines
// 96, 184, 275, 335, 579). The class-level annotation MUST NOT be
// readOnly=true; Hibernate would silently skip the flush on those writes
// (or throw on commit, depending on the dialect).
//
// rollbackFor = Exception.class is required because importRAFile() declares
// `throws Exception` and the loop chain throws checked IOException on
// readLine failures. Spring's default rollback rule does NOT roll back
// checked exceptions, so without this attribute a mid-file IOException
// would COMMIT the partial RaHeader insert at line ~189.
/**
 * Parses a Remittance Advice (RA) flat file from MOH and writes the
 * resulting {@code RaHeader} / {@code RaDetail} rows. Also exposes lookups
 * keyed by RA header number ({@code getPropBillNoRAHeaderNo},
 * {@code getRAClaimNo4BillingNo}) and the per-bill status flip used during
 * settlement ({@code updateBillingStatus}).
 *
 * <p>{@code importRAFile(String)} validates the requested path against
 * {@code DOCUMENT_DIR} before opening the file, so direct callers cannot
 * bypass the web-layer upload/path guard.</p>
 *
 * <p>Web security is enforced at the action layer before invocation.</p>
 */
@org.springframework.stereotype.Service
@org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
public class BillingOnRaService {
    private static final Logger _logger = MiscUtils.getLogger();

    private final RaDetailDao raDetailDao;
    private final RaHeaderDao raHeaderDao;
    private final BillingONCHeader1Dao cheader1Dao;

    /** Test-friendly constructor — package-private, takes DAO mocks directly. */
    BillingOnRaService(RaDetailDao raDetailDao, RaHeaderDao raHeaderDao, BillingONCHeader1Dao cheader1Dao) {
        this.raDetailDao = raDetailDao;
        this.raHeaderDao = raHeaderDao;
        this.cheader1Dao = cheader1Dao;
    }

    public int addOneRADtRecord(BillingRaDetailDto val) {
        RaDetail r = new RaDetail();
        r.setRaHeaderNo(Integer.parseInt(val.getRaheader_no()));
        r.setProviderOhipNo(val.getProviderohip_no());
        r.setBillingNo(Integer.parseInt(val.getBilling_no()));
        r.setServiceCode(val.getService_code());
        r.setServiceCount(val.getService_count());
        r.setHin(val.getHin());
        r.setAmountClaim(val.getAmountclaimAmount() == null ? null : BillingMoney.format(val.getAmountclaimAmount()));
        r.setAmountPay(val.getAmountpayAmount() == null ? null : BillingMoney.format(val.getAmountpayAmount()));
        r.setServiceDate(val.getService_date());
        r.setErrorCode(val.getError_code());
        r.setBillType(val.getBilltype());
        r.setClaimNo(val.getClaim_no());

        raDetailDao.persist(r);

        return r.getId();
    }

    public Properties getPropBillNoRAHeaderNo(String raheader_no) {
        Properties retval = new Properties();

        List<RaDetail> rr = raDetailDao.findByRaHeaderNo(Integer.parseInt(raheader_no));
        for (RaDetail r : rr) {
            retval.setProperty(String.valueOf(r.getBillingNo()), raheader_no);
        }

        return retval;
    }

    public boolean importRAFile(String filePathName) throws Exception {
        String filename = "", header = "", headerCount = "", total = "", paymentdate = "", payable = "", totalStatus = "";
        String providerno = "", account = "", newhin = "", hin = "", ver = "", billtype = "";
        String servicedate = "", serviceno = "", servicecode = "", amountsubmit = "", amountpay = "", amountpaysign = "", explain = "";
        String balancefwd = "", abf_ca = "", abf_ad = "", abf_re = "", abf_de = "";
        String transaction = "", trans_code = "", cheque_indicator = "", trans_date = "", trans_amount = "", trans_message = "";
        String message_txt = "";
        String claimno = "";
        String xml_ra = "";

        int accountno = 0, totalsum = 0, recFlag = 0, count = 0, tCount = 0, amountPaySum = 0, amountSubmitSum = 0;
        String raNo = "";
        boolean currentHeaderStarted = false;
        String documentDir = CarlosProperties.getInstance()
                .getProperty("DOCUMENT_DIR", "").trim();
        if (documentDir.isEmpty()) {
            throw new BillingValidationException("Cannot import RA file: DOCUMENT_DIR is not configured");
        }
        File safeFile;
        try {
            safeFile = PathValidationUtils.validateExistingPath(
                    Path.of(filePathName).toFile(),
                    Path.of(documentDir).toFile());
        } catch (FileValidationException e) {
            _logger.error("RA import rejected invalid path: {}", LogSanitizer.sanitize(filePathName), e);
            throw new SecurityException("Invalid RA import path", e);
        }
        filename = safeFile.getName();

        // try-with-resources: any throw in the parse loop below must close
        // the file handle. Without this, an IOException from readLine
        // (or any RuntimeException downstream) leaks the FD AND with the
        // class-level rollbackFor=Exception.class above, the transaction
        // rolls back so partial RaHeader/RaDetail rows are not committed.
        try (FileInputStream file = new FileInputStream(safeFile);
             InputStreamReader reader = new InputStreamReader(file);
             BufferedReader input = new BufferedReader(reader)) {
        String nextline;

        while ((nextline = input.readLine()) != null) {
            header = nextline.substring(0, 1);

            if (header.compareTo("H") == 0) {
                headerCount = nextline.substring(2, 3);

                if (headerCount.compareTo("1") == 0) {
                    if (currentHeaderStarted) {
                        mergeRaHeaderSummary(filename, paymentdate, total, transaction,
                                abf_ca, abf_ad, abf_re, abf_de, count, tCount);
                    }
                    transaction = "";
                    message_txt = "";
                    abf_ca = "";
                    abf_ad = "";
                    abf_re = "";
                    abf_de = "";
                    count = 0;
                    tCount = 0;
                    recFlag = 0;
                    raNo = "";
                    currentHeaderStarted = true;

                    paymentdate = nextline.substring(21, 29);
                    payable = nextline.substring(29, 59);
                    total = nextline.substring(59, 68);
                    totalStatus = nextline.substring(68, 69);

                    total = formatRaSignedAmount(total, totalStatus, 2, true);

                    List<RaHeader> headers = raHeaderDao.findCurrentByFilenamePaymentDate(filename, paymentdate);
                    for (RaHeader h : headers) {
                        raNo = "" + h.getId();
                    }

                    // judge if it is empty in table radt
                    int radtNum = 0;
                    if (raNo != null && raNo.length() > 0) {
                        // can't make sure the record has only one result here
                        radtNum = raDetailDao.findByRaHeaderNo(Integer.parseInt(raNo)).size();

                        // if there is no radt record for the rahd, update the
                        // rahd status to "D"
                        // if (radtNum == 0) update rahd
                    }

                    if (raNo.compareTo("") == 0 || raNo == null || radtNum == 0) {
                        recFlag = 1;

                        RaHeader h = new RaHeader();
                        h.setFilename(filename);
                        h.setPaymentDate(paymentdate);
                        h.setPayable(payable);
                        h.setTotalAmount(total);
                        h.setRecords("0");
                        h.setClaims("0");
                        h.setStatus("N");
                        h.setReadDate(UtilDateUtilities.DateToString(new Date(), "yyyy/MM/dd"));
                        h.setContent("<xml_cheque>" + total + "</xml_cheque>");

                        raHeaderDao.persist(h);

                        raNo = h.getId().toString();
                    }
                } // ends with "1"

                if (headerCount.compareTo("4") == 0) {
                    claimno = nextline.substring(3, 14);
                    providerno = nextline.substring(15, 21);
                    account = nextline.substring(23, 31);
                    hin = nextline.substring(52, 64);
                    ver = nextline.substring(64, 66);
                    billtype = nextline.substring(66, 69);

                    count = count + 1;

                    String validnum = "0123456789- ";
                    boolean valid = true;
                    for (int i = 0; i < account.length(); i++) {
                        char c = account.charAt(i);
                        if (validnum.indexOf(c) == -1) {
                            valid = false;
                            break;
                        }
                    }

                    if (valid) {
                        if ("".equals(account.trim())) {
                            accountno = 0;
                            account = "0";
                        } else {
                            accountno = Integer.parseInt(account.trim());
                            account = String.valueOf(accountno);
                        }
                    } else {
                        accountno = -1;
                        account = "-1";
                    }
                }

                if (headerCount.compareTo("5") == 0) {
                    claimno = nextline.substring(3, 14);
                    servicedate = nextline.substring(15, 23);
                    serviceno = nextline.substring(23, 25);
                    servicecode = nextline.substring(25, 30);
                    amountsubmit = nextline.substring(31, 37);
                    amountpay = nextline.substring(37, 43);
                    amountpaysign = nextline.substring(43, 44);
                    explain = nextline.substring(44, 46);

                    tCount = tCount + 1;
                    amountPaySum = Integer.parseInt(amountpay);
                    amountpay = String.valueOf(amountPaySum);
                    if (amountpay.compareTo("0") == 0) amountpay = "000";
                    if (amountpay.length() > 2) {
                        amountpay = amountpay.substring(0, amountpay.length() - 2) + "." + amountpay.substring(amountpay.length() - 2);
                    } else {
                        if (amountpay.length() == 2) {
                            amountpay = "0." + amountpay;
                        } else {
                            amountpay = "0.0" + amountpay;
                        }
                    }

                    amountSubmitSum = Integer.parseInt(amountsubmit);
                    amountsubmit = String.valueOf(amountSubmitSum);
                    if (amountsubmit.compareTo("0") == 0) amountsubmit = "000";

                    if (amountsubmit.length() == 1) {
                        amountsubmit = "0.0" + amountsubmit;
                    } else {
                        amountsubmit = amountsubmit.substring(0, amountsubmit.length() - 2) + "." + amountsubmit.substring(amountsubmit.length() - 2);
                    }
                    newhin = hin + ver;

                    // if it needs to write a radt record for the rahd record
                    if (recFlag > 0) {
                        RaDetail r = new RaDetail();
                        r.setRaHeaderNo(Integer.parseInt(raNo));
                        r.setProviderOhipNo(providerno);
                        r.setBillingNo(Integer.parseInt(account));
                        r.setServiceCode(servicecode);
                        r.setServiceCount(serviceno);
                        r.setHin(newhin);
                        r.setAmountClaim(amountsubmit);
                        r.setAmountPay((amountpaysign + amountpay).trim());
                        r.setServiceDate(servicedate);
                        r.setErrorCode(explain);
                        r.setBillType(billtype);
                        r.setClaimNo(claimno);

                        raDetailDao.persist(r);
                    }
                }

                if (headerCount.compareTo("6") == 0) {
                    // balancefwd = "<table width='100%' border='0'
                    // cellspacing='0' cellpadding='0'><tr><td
                    // colspan='4'>Balance Forward Record - Amount Brought
                    // Forward (ABF)</td></tr><tr><td>Claims
                    // Adjustment</td><td>Advances</td><td>Reductions</td><td>Deductions</td></tr><tr><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td></tr></table>";
                    SignedRaAmount claimsAdjustment = signedRaAmount(nextline, 3, 7, 2);
                    SignedRaAmount advances = signedRaAmount(nextline, claimsAdjustment.nextIndex(), 7, 2);
                    SignedRaAmount reductions = signedRaAmount(nextline, advances.nextIndex(), 7, 2);
                    SignedRaAmount deductions = signedRaAmount(nextline, reductions.nextIndex(), 7, 2);
                    abf_ca = claimsAdjustment.value();
                    abf_ad = advances.value();
                    abf_re = reductions.value();
                    abf_de = deductions.value();
                }

                if (headerCount.compareTo("7") == 0) {
                    trans_code = nextline.substring(3, 5);
                    if (trans_code.compareTo("10") == 0) trans_code = "Advance";
                    if (trans_code.compareTo("20") == 0) trans_code = "Reduction";
                    if (trans_code.compareTo("30") == 0) trans_code = "Unused";
                    if (trans_code.compareTo("40") == 0) trans_code = "Advance repayment";
                    if (trans_code.compareTo("50") == 0) trans_code = "Accounting adjustment";
                    if (trans_code.compareTo("70") == 0) trans_code = "Attachments";
                    cheque_indicator = nextline.substring(5, 6);
                    if (cheque_indicator.compareTo("M") == 0) cheque_indicator = "Manual Cheque issued";
                    if (cheque_indicator.compareTo("C") == 0) cheque_indicator = "Computer Cheque issued";
                    if (cheque_indicator.compareTo("I") == 0)
                        cheque_indicator = "Interim payment Cheque/Direct Bank Deposit issued";

                    trans_date = nextline.substring(6, 14);
                    String transAmountSign = "";
                    int transMessageStart = 23;
                    if (nextline.length() > 22 && isRaSignChar(nextline.charAt(22))) {
                        transAmountSign = nextline.substring(22, 23);
                    }
                    trans_amount = formatRaSignedAmount(nextline.substring(14, 22), transAmountSign, 2, false);
                    trans_message = nextline.substring(transMessageStart,
                            Math.min(nextline.length(), transMessageStart + 50));

                    transaction = transaction + "<tr><td width='14%'>" + trans_code + "</td><td width='12%'>" + trans_date + "</td><td width='17%'>" + cheque_indicator + "</td><td width='13%'>" + trans_amount + "</td><td width='44%'>" + SafeEncode.forHtmlContent(trans_message) + "</td></tr>";
                }

                if (headerCount.compareTo("8") == 0) {
                    message_txt = message_txt + SafeEncode.forHtmlContent(nextline.substring(3, 73)) + "<br>";
                }

            } // ends with header "H"
        }
        } // try-with-resources closes file/reader/input

        if (currentHeaderStarted) {
            mergeRaHeaderSummary(filename, paymentdate, total, transaction,
                    abf_ca, abf_ad, abf_re, abf_de, count, tCount);
        }

        return true;
    }

    private void mergeRaHeaderSummary(String filename, String paymentdate, String total,
                                      String transaction, String abfCa, String abfAd,
                                      String abfRe, String abfDe, int count, int tCount) {
        String transactionXml = "";
        if (transaction.compareTo("") != 0) {
            transactionXml = "<xml_transaction><table width='100%' border='0' cellspacing='0' cellpadding='0'><tr><td colspan='5'>Accounting Transaction Record</td></tr><tr><td width='14%'>Transaction</td><td width='12%'>Transaction Date</td><td width='17%'>Cheque Issued</td><td width='13%'>Amount</td><td width='44%'>Message</td></tr>" + transaction + "</table></xml_transaction>";
        }

        String balancefwd = "<xml_balancefwd><table width='100%' border='0' cellspacing='0' cellpadding='0'><tr><td colspan='4'>Balance Forward Record - Amount Brought Forward (ABF)</td></tr><tr><td>Claims Adjustment</td><td>Advances</td><td>Reductions</td><td>Deductions</td></tr><tr><td>" + abfCa + "</td><td>" + abfAd + "</td><td>" + abfRe + "</td><td>" + abfDe + "</td></tr></table></xml_balancefwd>";
        String xmlRa = transactionXml + balancefwd + "<xml_cheque>" + total + "</xml_cheque>";

        List<RaHeader> headers = raHeaderDao.findByFilenamePaymentDate(filename, paymentdate);
        for (RaHeader h : headers) {
            h.setTotalAmount(total);
            h.setRecords(String.valueOf(count));
            h.setClaims(String.valueOf(tCount));
            h.setContent(xmlRa);
            raHeaderDao.merge(h);
        }
    }

    private static SignedRaAmount signedRaAmount(String line, int start, int wholeDigits, int fractionDigits) {
        int end = start + wholeDigits + fractionDigits;
        String rawAmount = line.substring(start, end);
        String sign = "";
        if (line.length() > end && isRaSignChar(line.charAt(end))) {
            sign = line.substring(end, end + 1);
            end++;
        }
        return new SignedRaAmount(formatRaSignedAmount(rawAmount, sign, fractionDigits, false), end);
    }

    private static String formatRaSignedAmount(String rawAmount, String sign, int fractionDigits,
                                               boolean stripWholeLeadingZeros) {
        String digits = rawAmount == null ? "" : rawAmount.trim();
        if (digits.isEmpty()) {
            digits = "0";
        }
        if (!digits.matches("\\d+")) {
            throw new NumberFormatException("RA amount is not numeric");
        }
        while (digits.length() <= fractionDigits) {
            digits = "0" + digits;
        }
        String whole = digits.substring(0, digits.length() - fractionDigits);
        String fraction = digits.substring(digits.length() - fractionDigits);
        if (stripWholeLeadingZeros) {
            whole = new java.math.BigInteger(whole).toString();
        }
        return ("-".equals(sign) ? "-" : "") + whole + "." + fraction;
    }

    private static boolean isRaSignChar(char ch) {
        return ch == '-' || ch == '+' || ch == ' ';
    }

    private record SignedRaAmount(String value, int nextIndex) { }

    public List<Properties> getAllRahd(String status) {
        List<Properties> ret = new ArrayList<Properties>();

        List<RaHeader> headers = raHeaderDao.findAllExcludeStatus(status);
        for (RaHeader h : headers) {
            Properties prop = new Properties();
            prop.setProperty("raheader_no", h.getId().toString());
            prop.setProperty("readdate", h.getReadDate());
            prop.setProperty("paymentdate", h.getPaymentDate());
            prop.setProperty("payable", h.getPayable());
            prop.setProperty("claims", h.getClaims());
            prop.setProperty("records", h.getRecords());
            prop.setProperty("totalamount", h.getTotalAmount());
            prop.setProperty("status", h.getStatus());
            ret.add(prop);
        }

        return ret;
    }

    public List<Properties> getTeamRahd(String status, String provider_no) {
        List<Properties> ret = new ArrayList<Properties>();
        for (RaHeader r : raHeaderDao.findByHeaderDetailsAndProviderMagic(status, provider_no)) {
            Properties prop = new Properties();
            prop.setProperty("raheader_no", "" + r.getId());
            prop.setProperty("readdate", r.getReadDate());
            prop.setProperty("paymentdate", r.getPaymentDate());
            prop.setProperty("payable", r.getPayable());
            prop.setProperty("claims", r.getClaims());
            prop.setProperty("records", r.getRecords());
            prop.setProperty("totalamount", r.getTotalAmount());
            prop.setProperty("status", r.getStatus());
            ret.add(prop);
        }

        return ret;
    }

    public List<Properties> getSiteRahd(String status, String provider_no) {
        List<Properties> ret = new ArrayList<Properties>();
        for (RaHeader r : raHeaderDao.findByStatusAndProviderMagic(status, provider_no)) {
            Properties prop = new Properties();
            prop.setProperty("raheader_no", "" + r.getId());
            prop.setProperty("readdate", r.getReadDate());
            prop.setProperty("paymentdate", r.getPaymentDate());
            prop.setProperty("payable", r.getPayable());
            prop.setProperty("claims", r.getClaims());
            prop.setProperty("records", r.getRecords());
            prop.setProperty("totalamount", r.getTotalAmount());
            prop.setProperty("status", r.getStatus());
            ret.add(prop);
        }
        return ret;
    }

    private String getPointNum(String strNum) {
        String ret = null;
        if (strNum.length() > 2) {
            ret = strNum.substring(0, strNum.length() - 2) + "." + strNum.substring(strNum.length() - 2);
        } else {
            ret = "0.00".substring(0, 4 - strNum.length()) + strNum;
        }
        return ret;
    }

    public List<Properties> getProviderListFromRAReport(String id) {
        List<Properties> ret = new ArrayList<Properties>();
        for (Object[] o : raHeaderDao.findHeadersAndProvidersById(ConversionUtils.fromIntString(id))) {
            RaDetail r = (RaDetail) o[0];
            Provider p = (Provider) o[1];

            Properties prop = new Properties();
            prop.setProperty("providerohip_no", r.getProviderOhipNo());
            prop.setProperty("last_name", p.getLastName());
            prop.setProperty("first_name", p.getFirstName());
            ret.add(prop);
        }

        return ret;
    }

    public List<Properties> getRAErrorReport(String raNo, String providerOhipNo, String[] notErrorCode) {
        List<Properties> ret = new ArrayList<Properties>();

        try {
            for (Integer billingNo : raDetailDao.findDistinctIdOhipWithError(ConversionUtils.fromIntString(raNo), providerOhipNo, Arrays.asList(notErrorCode))) {
                String account = "" + billingNo;
                String demoLast = "";
                String billingDate = "";

                BillingONCHeader1 billing = cheader1Dao.find(billingNo);
                if (billing != null) {
                    demoLast = billing.getDemographicName();
                    billingDate = ConversionUtils.toDateString(billing.getBillingDate());
                }

                for (RaDetail rr : raDetailDao.findByHeaderAndBillingNos(ConversionUtils.fromIntString(raNo), billingNo)) {
                    try {
                        Properties prop = new Properties();
                        String explain = rr.getErrorCode();
                        if (explain == null || explain.compareTo("") == 0) {
                            explain = "**";
                        }
                        String serviceDate = rr.getServiceDate();
                        serviceDate = serviceDate.length() == 8 ? (serviceDate.substring(0, 4) + "-" + serviceDate.substring(4, 6) + "-" + serviceDate.substring(6)) : serviceDate;
                        prop.setProperty("servicecode", rr.getServiceCode());
                        prop.setProperty("servicedate", serviceDate);
                        prop.setProperty("serviceno", rr.getServiceCount());
                        prop.setProperty("explain", explain);
                        prop.setProperty("amountsubmit", rr.getAmountClaim());
                        prop.setProperty("amountpay", rr.getAmountPay());

                        prop.setProperty("account", account);
                        if (!billingDate.equals(serviceDate)) {
                            demoLast = "";
                        }
                        prop.setProperty("demoLast", demoLast);
                        ret.add(prop);
                    } catch (SecurityException sec) {
                        throw sec;
                    } catch (RuntimeException rowFailure) {
                        _logger.error(
                                "Failed to render RA error-report row (raNo={}, billingNo={}, claimNo={}, serviceCode={})",
                                LogSanitizer.sanitize(raNo),
                                LogSanitizer.sanitize(String.valueOf(billingNo)),
                                LogSanitizer.sanitize(rr == null ? "" : rr.getClaimNo()),
                                LogSanitizer.sanitize(rr == null ? "" : rr.getServiceCode()),
                                rowFailure);
                        appendLoadFailureMarker(ret);
                    }
                }
            }
        } catch (SecurityException sec) {
            throw sec;
        } catch (Exception e) {
            _logger.error("Failed to load RA error report (raNo={}, providerOhipNo={}); reconciliation grid may silently drop entries",
                    LogSanitizer.sanitize(raNo),
                    LogSanitizer.sanitize(providerOhipNo), e);
            // Append a sentinel row so downstream consumers can detect the
            // partial load and refuse to persist a $-balance computed from
            // an incomplete grid. Mirrors the marker pattern in getRASummary.
            appendLoadFailureMarker(ret);
        }
        return ret;
    }

    public String getRAClaimNo4BillingNo(String billingNo) {
        String claim_no = "";
        List<RaDetail> claims = raDetailDao.findByBillingNo(Integer.parseInt(billingNo));
        for (RaDetail claim : claims) {
            claim_no = claim.getClaimNo();
        }

        return claim_no;
    }

    public List<String> getRABillingNo4Code(String id, String codes) {
        Set<String> ret = new HashSet<String>();

        for (RaDetail r : raDetailDao.findByRaHeaderNoAndServiceCodes(ConversionUtils.fromIntString(id), Arrays.asList(codes))) {
            ret.add("" + r.getBillingNo());
        }

        return new ArrayList<String>(ret);
    }

    public List<Properties> getRASummary(String id, String providerOhipNo) {
        List<Properties> ret = new ArrayList<Properties>();

        try {
            for (RaDetail r : raDetailDao.findByRaHeaderNoAndProviderOhipNo(ConversionUtils.fromIntString(id), providerOhipNo)) {
                try {
                    String account = "" + r.getBillingNo();
                    String location = "";
                    String demo_name = "";
                    String localServiceDate = "";
                    String demo_hin = r.getHin() != null ? r.getHin() : "";
                    demo_hin = demo_hin.trim();
                    String site = "";
                    String famProviderNo = null;
                    for (Object[] o : cheader1Dao.findBillingsAndDemographicsById(ConversionUtils.fromIntString(account))) {
                        BillingONCHeader1 b = (BillingONCHeader1) o[0];
                        Demographic d = (Demographic) o[1];

                        demo_name = b.getDemographicName();
                        famProviderNo = d.getProviderNo();
                        site = b.getClinic();
                        if (b.getHin() != null) {
                            if (!(b.getHin()).startsWith(demo_hin)) {
                                demo_hin = "";
                                demo_name = "";
                            }
                        } else {
                            demo_hin = "";
                            demo_name = "";
                        }
                        location = b.getVisitType();
                        localServiceDate = ConversionUtils.toDateString(b.getBillingDate());
                    }

                    if (famProviderNo == null) {
                        famProviderNo = "";
                    }
                    // proName =
                    // propProvierName.getProperty(r.getproviderohip_no());
                    String servicecode = r.getServiceCode();
                    String servicedate = r.getServiceDate();
                    String serviceno = r.getServiceCount();
                    String explain = r.getErrorCode();
                    String amountsubmit = r.getAmountClaim();
                    String amountpay = r.getAmountPay();
                    // unparseable flag — exposed to the JSP so the row renders
                    // with a "contact MOH" badge instead of a fake $0.00. Silent
                    // coalesce to "0.00" would prevent the operator's
                    // reconciliation grid from distinguishing a malformed amount
                    // from a legitimate $0 payment.
                    boolean amountUnreadable = false;
                    try {
                        BillingMoney.amount(amountpay);
                    } catch (NumberFormatException e) {
                        amountUnreadable = true;
                        MiscUtils.getLogger().error(
                                "RA reconciliation: header {} row had unreadable amountPay [{}]; flagged for operator follow-up",
                                LogSanitizer.sanitize(id), LogSanitizer.sanitize(amountpay), e);
                        amountpay = "0.00";
                    }

                    Properties prop = new Properties();
                    prop.setProperty("servicecode", servicecode);
                    prop.setProperty("servicedate", servicedate);
                    prop.setProperty("serviceno", serviceno);
                    prop.setProperty("explain", explain);
                    prop.setProperty("amountsubmit", amountsubmit);
                    if (amountUnreadable) {
                        prop.setProperty("amountUnreadable", "true");
                    }
                    prop.setProperty("amountpay", amountpay);
                    prop.setProperty("location", location);
                    prop.setProperty("localServiceDate", localServiceDate);
                    prop.setProperty("account", account);
                    prop.setProperty("demo_name", demo_name);
                    prop.setProperty("demo_hin", demo_hin);
                    prop.setProperty("demo_doc", famProviderNo);
                    prop.setProperty("claimNo", r.getClaimNo());
                    if (site == null) site = "";
                    prop.setProperty("site", site);
                    ret.add(prop);
                } catch (SecurityException sec) {
                    throw sec;
                } catch (RuntimeException rowFailure) {
                    _logger.error(
                            "Failed to render RA summary row (raHeaderId={}, billingNo={}, claimNo={}, serviceCode={})",
                            LogSanitizer.sanitize(id),
                            LogSanitizer.sanitize(r == null ? "" : String.valueOf(r.getBillingNo())),
                            LogSanitizer.sanitize(r == null ? "" : r.getClaimNo()),
                            LogSanitizer.sanitize(r == null ? "" : r.getServiceCode()),
                            rowFailure);
                    appendLoadFailureMarker(ret);
                }
            }
        } catch (SecurityException sec) {
            throw sec;
        } catch (Exception e) {
            _logger.error("Failed to load RA summary (raHeaderId={}, providerOhipNo={}); rows may be silently dropped",
                    LogSanitizer.sanitize(id),
                    LogSanitizer.sanitize(providerOhipNo), e);
            // Append a sentinel marker row so downstream consumers
            // (BillingRaReportService.getRASummary) can detect the partial
            // load and bump xml_partial_count, which propagates into the
            // OnRaSummaryViewModel.partial flag and ultimately blocks the
            // OnRaSummary partial-totals persist-block contract.
            appendLoadFailureMarker(ret);
        }
        return ret;
    }

    private static void appendLoadFailureMarker(List<Properties> ret) {
        Properties marker = new Properties();
        marker.setProperty(LOAD_FAILURE_MARKER, "true");
        ret.add(marker);
    }

    /**
     * Sentinel property key used by {@link #getRASummary} to flag a
     * partial result from a mid-iteration DAO failure. Consumers detect
     * the marker and bump their unreadable-row count so the OnRaSummary
     * partial-totals persist-block contract still applies.
     */
    public static final String LOAD_FAILURE_MARKER = "_raSummaryLoadFailed";

    public List<String> getRAError35(String id, String providerOhipNo, String codes) {
        List<String> ret = new ArrayList<String>();
        List<Integer> tmp = raDetailDao.findUniqueBillingNoByRaHeaderNoAndProviderAndNotErrorCode(Integer.parseInt(id), providerOhipNo, codes);
        for (Integer t : tmp) {
            ret.add(t.toString());
        }
        return ret;
    }

    public boolean updateBillingStatus(String id, String status) {
        BillingONCHeader1 h = cheader1Dao.find(Integer.parseInt(id));
        if (h == null) {
            MiscUtils.getLogger().warn(
                    "RA settlement: billing header [{}] not found; status [{}] was not applied",
                    io.github.carlos_emr.carlos.utility.LogSanitizer.sanitize(id),
                    io.github.carlos_emr.carlos.utility.LogSanitizer.sanitize(status));
            return false;
        }
        if (h.isActive()) {
            h.setStatusStrict(status);
            cheader1Dao.merge(h);
        }

        return true;
    }

}
