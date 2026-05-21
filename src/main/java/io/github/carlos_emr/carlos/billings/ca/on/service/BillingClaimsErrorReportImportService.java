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

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingErrorReportDto;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimsErrorReportRecordDto;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;

/**
 * Imports MOH claims-error-report files into typed report rows. Spring-
 * managed {@code @Service @Transactional} bean — the entire read loop runs
 * inside one transaction, and the IOException/SIOOBE catch arms throw
 * {@link BillingFileImportException} so a mid-stream failure rolls back
 * every per-line delete/insert atomically.
 *
 * @since 2026-04-30
 */
@Service
@Transactional
public class BillingClaimsErrorReportImportService {

    private static final String IMPORT_FAILURE_MSG_PREFIX =
            "Claims-error report import rolled back for file=";

    private final BillingOnErrorReportService erRepObj;

    public BillingClaimsErrorReportImportService(BillingOnErrorReportService erRepObj) {
        this.erRepObj = erRepObj;
    }

    /**
     * Parse the supplied claims-error-report stream and persist the rows
     * atomically. Returns a {@link BillingClaimsErrorReportParser} whose
     * {@code verdict} flag and {@code claimsErrorReportRecords} list reflect
     * the outcome (existing JSP contract). On any I/O or substring failure
     * mid-stream the entire batch is rolled back via the surrounding
     * {@code @Transactional} boundary and a {@link BillingFileImportException}
     * propagates to the caller.
     *
     * @param file     input stream — consumed and closed by this method
     * @param filename original filename, used for audit fields on the persisted rows
     * @return parser carrying parsed records and a {@code verdict=true} flag on success
     * @throws BillingFileImportException if the file cannot be fully read/parsed
     */
    public BillingClaimsErrorReportParser importStream(FileInputStream file, String filename) {
        List<BillingClaimsErrorReportRecordDto> records = new ArrayList<>();
        parseAndPersist(file, filename, records);

        return BillingClaimsErrorReportParser.successful(records);
    }

    private void parseAndPersist(FileInputStream file, String filename,
                                 List<BillingClaimsErrorReportRecordDto> records) {
        String nextline;
        BillingClaimsErrorReportRecordDto record = new BillingClaimsErrorReportRecordDto();
        boolean isNewHin = false;

        BillingErrorReportDto erObj = null;
        String claimError = "";
        try (InputStreamReader reader = new InputStreamReader(file);
             BufferedReader input = new BufferedReader(reader)) {
            while ((nextline = input.readLine()) != null) {
                String codeError = "";

                String headerCount = "";
                if (nextline.length() >= 3) {
                    headerCount = nextline.substring(2, 3);
                } else {
                    MiscUtils.getLogger().warn(
                            "Rejecting short claims-error-report line in file [{}]; length={}",
                            LogSafe.sanitize(filename), nextline.length());
                    throw new BillingFileImportException(
                            IMPORT_FAILURE_MSG_PREFIX + filename + " (malformed short line)",
                            new IllegalArgumentException("claims-error-report line shorter than 3 characters"));
                }
                if (headerCount.compareTo("1") == 0) {
                    erObj = new BillingErrorReportDto();
                    record = new BillingClaimsErrorReportRecordDto();
                    record.setTechSpec(nextline.substring(3, 6));
                    record.setMOHoffice(nextline.substring(6, 7));
                    record.setProviderNumber(nextline.substring(27, 33));
                    record.setGroupNumber(nextline.substring(23, 27));
                    record.setOperatorNumber(nextline.substring(17, 23));
                    record.setSpecialtyCode(nextline.substring(33, 35));
                    record.setStationNumber(nextline.substring(35, 38));
                    record.setClaimProcessDate(nextline.substring(38, 46));
                    records.add(record);

                    erObj.setProviderohip_no(nextline.substring(27, 33));
                    erObj.setGroup_no(nextline.substring(23, 27));
                    erObj.setSpecialty(nextline.substring(33, 35));
                    erObj.setProcess_date(nextline.substring(38, 46));
                }

                if (headerCount.compareTo("H") == 0
                        || headerCount.compareTo("R") == 0
                        || headerCount.compareTo("T") == 0
                        || headerCount.compareTo("8") == 0) {
                    requireHeader(erObj, filename, headerCount);
                }

                if (headerCount.compareTo("H") == 0) {
                    isNewHin = true;
                    record = new BillingClaimsErrorReportRecordDto();
                    record.setHin(nextline.substring(3, 13));
                    record.setVer(nextline.substring(13, 15));
                    record.setDob(nextline.substring(15, 23));
                    record.setAccount(nextline.substring(23, 31));
                    record.setBilltype(nextline.substring(31, 34));
                    record.setPayee(nextline.substring(34, 35));
                    record.setReferNumber(nextline.substring(35, 41));
                    record.setFacilityNumber(nextline.substring(41, 45));
                    record.setAdmitDate(nextline.substring(45, 53));
                    record.setReferLab(nextline.substring(53, 57));
                    record.setLocation(nextline.substring(57, 61));
                    record.setHeCode1(nextline.substring(64, 67));
                    record.setHeCode2(nextline.substring(67, 70));
                    record.setHeCode3(nextline.substring(70, 73));
                    record.setHeCode4(nextline.substring(73, 76));
                    record.setHeCode5(nextline.substring(76, 79));

                    erObj.setHin(nextline.substring(3, 13));
                    erObj.setVer(nextline.substring(13, 15));
                    erObj.setDob(nextline.substring(15, 23));
                    erObj.setBilling_no(nextline.substring(23, 31));

                    if (erObj.getBilling_no().startsWith("FC")) {
                        erObj.setBilling_no(erObj.getBilling_no().substring(2));
                    }
                    erObj.setRef_no(nextline.substring(35, 41));
                    erObj.setFacility(nextline.substring(41, 45));
                    erObj.setAdmitted_date(nextline.substring(45, 53));
                    claimError = nextline.substring(64, 67).trim() + " " + nextline.substring(67, 70).trim() + " "
                            + nextline.substring(70, 73).trim() + " " + nextline.substring(73, 76).trim() + " "
                            + nextline.substring(76, 79);
                    // erObj.setClaim_error()
                    erRepObj.deleteErrorReport(erObj);

                }

                if (headerCount.compareTo("R") == 0) {
                    record = new BillingClaimsErrorReportRecordDto();
                    record.setRegNumber(nextline.substring(3, 15));
                    record.setPatient_last(nextline.substring(15, 24));
                    record.setPatient_first(nextline.substring(24, 29));
                    record.setPatient_sex(nextline.substring(29, 30));
                    record.setProvince_code(nextline.substring(30, 32));
                    record.setReCode1(nextline.substring(64, 67));
                    record.setReCode2(nextline.substring(67, 70));
                    record.setReCode3(nextline.substring(70, 73));
                    record.setReCode4(nextline.substring(73, 76));
                    record.setReCode5(nextline.substring(76, 79));
                    records.add(record);

                    claimError += nextline.substring(64, 67).trim() + " " + nextline.substring(67, 70).trim() + " "
                            + nextline.substring(70, 73).trim() + " " + nextline.substring(73, 76).trim() + " "
                            + nextline.substring(76, 79);
                }

                if (headerCount.compareTo("T") == 0) {
                    if (!isNewHin) {
                        record = new BillingClaimsErrorReportRecordDto();
                    } else {
                        isNewHin = false;
                    }
                    record.setServicecode(nextline.substring(3, 8));
                    record.setAmountsubmitStoredCents(nextline.substring(10, 16));
                    record.setServiceno(nextline.substring(16, 18));
                    record.setServicedate(nextline.substring(18, 26));
                    record.setDxcode(nextline.substring(26, 30));
                    record.setCode1(nextline.substring(64, 67));
                    record.setCode2(nextline.substring(67, 70));
                    record.setCode3(nextline.substring(70, 73));
                    record.setCode4(nextline.substring(73, 76));
                    record.setCode5(nextline.substring(76, 79));
                    records.add(record);

                    erObj.setCode(nextline.substring(3, 8));
                    erObj.setFeeStoredCents(nextline.substring(10, 16));
                    erObj.setUnit(nextline.substring(16, 18));
                    erObj.setCode_date(nextline.substring(18, 26));
                    erObj.setDx(nextline.substring(26, 30));

                    codeError = nextline.substring(64, 67).trim() + " " + nextline.substring(67, 70).trim() + " "
                            + nextline.substring(70, 73).trim() + " " + nextline.substring(73, 76).trim() + " "
                            + nextline.substring(76, 79);
                    erObj.setCode_error(codeError);
                    erObj.setClaim_error(claimError);

                    erObj.setExp("");
                }

                if (headerCount.compareTo("8") == 0) {
                    record = new BillingClaimsErrorReportRecordDto();
                    record.setExplain(nextline.substring(3, 5));
                    record.setError(nextline.substring(5, 60));
                    records.add(record);

                    erObj.setExp(nextline.substring(3, 5) + "|" + nextline.substring(5, 60));
                }

                if (headerCount.compareTo("T") == 0) {
                    // save the record
                    erObj.setReport_name(filename);
                    erObj.setStatus("N");
                    erObj.setComment("");
                    erRepObj.addErrorReportRecord(erObj);
                }

                if (headerCount.compareTo("9") == 0) {
                    record = new BillingClaimsErrorReportRecordDto();
                    record.setHeader1Count(nextline.substring(3, 10));
                    record.setHeader2Count(nextline.substring(10, 17));
                    record.setItemCount(nextline.substring(17, 24));
                    record.setMessageCount(nextline.substring(24, 31));
                    records.add(record);
                }

            }
        } catch (IOException ioe) {
            // Throw so the surrounding @Transactional rolls back every per-line
            // delete/insert performed before this point — leaving partial
            // commits behind would silently desync the report against MOH.
            throw new BillingFileImportException(
                    IMPORT_FAILURE_MSG_PREFIX + filename + " (I/O error)", ioe);
        } catch (StringIndexOutOfBoundsException sioobe) {
            // Same rationale as the IOException catch above.
            throw new BillingFileImportException(
                    IMPORT_FAILURE_MSG_PREFIX + filename + " (malformed line)", sioobe);
        } catch (BillingValidationException validationFailure) {
            throw new BillingFileImportException(
                    IMPORT_FAILURE_MSG_PREFIX + filename + " (invalid amount)", validationFailure);
        }
    }

    private static void requireHeader(BillingErrorReportDto erObj, String filename, String headerCount) {
        if (erObj == null) {
            throw new BillingFileImportException(
                    IMPORT_FAILURE_MSG_PREFIX + filename
                            + " (record " + headerCount + " appeared before header 1)",
                    new IllegalStateException("dependent record appeared before header 1"));
        }
    }
}
