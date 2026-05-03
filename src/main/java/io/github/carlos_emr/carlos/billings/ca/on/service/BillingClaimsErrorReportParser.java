/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimsErrorReportRecordDto;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.MiscUtils;
/**
 * Parses fixed-format Ontario claims error report files.
 *
 * <p>The parser models report data so callers can inspect and render records
 * without duplicating report conventions in JSPs.</p>
 *
 * <p>State exposure is via accessors, not public fields, so callers can't
 * silently undo the parse-time invariant {@code verdict==true initially}
 * by direct field write. The records list returned by
 * {@link #getClaimsErrorReportRecords()} is an unmodifiable view over the
 * internal buffer; mutations must go through {@link #setClaimsErrorReportRecords}
 * which defensively copies.</p>
 */

public class BillingClaimsErrorReportParser {

    private List<BillingClaimsErrorReportRecordDto> claimsErrorReportRecords = new ArrayList<>();
    private boolean verdict = true;

    private BillingClaimsErrorReportParser() {
    }

    public BillingClaimsErrorReportParser(FileInputStream file) {
        this(file, "unknown");
    }

    public BillingClaimsErrorReportParser(FileInputStream file, String sourceName) {
        init(file, sourceName);
    }

    static BillingClaimsErrorReportParser successful(List<BillingClaimsErrorReportRecordDto> records) {
        BillingClaimsErrorReportParser parser = new BillingClaimsErrorReportParser();
        parser.setVerdict(true);
        parser.setClaimsErrorReportRecords(records);
        return parser;
    }

    /**
     * Parse the supplied stream into the records buffer. Private + invoked
     * exactly once from the constructor — calling this twice would
     * double-populate the records buffer (and on a fresh stream re-read,
     * silently leave a stale verdict from the first pass). The constructor
     * is the single entry point.
     */
    private boolean init(FileInputStream file) {
        return init(file, "unknown");
    }

    private boolean init(FileInputStream file, String sourceName) {
        String nextline;
        int lineNumber = 0;
        BillingClaimsErrorReportRecordDto record = new BillingClaimsErrorReportRecordDto();

        boolean isNewHin = false;
        try (InputStreamReader reader = new InputStreamReader(file);
             BufferedReader input = new BufferedReader(reader)) {
            while ((nextline = input.readLine()) != null) {
                lineNumber++;
                String headerCount = "";
                if (nextline.length() >= 3) {
                    headerCount = nextline.substring(2, 3);
                } else {
                    // Short/malformed line: log sanitized and leave headerCount
                    // empty so none of the dispatch branches below match.
                    MiscUtils.getLogger().warn(
                            "Skipping short or malformed claims-error line (file={}, line={}, length={})",
                            LogSanitizer.sanitize(sourceName), lineNumber, nextline.length());
                }

                if (headerCount.compareTo("1") == 0) {
                    record = new BillingClaimsErrorReportRecordDto();
                    record.setTechSpec(nextline.substring(3, 6));
                    record.setMOHoffice(nextline.substring(6, 7));
                    record.setProviderNumber(nextline.substring(27, 33));
                    record.setGroupNumber(nextline.substring(23, 27));
                    record.setOperatorNumber(nextline.substring(17, 23));
                    record.setSpecialtyCode(nextline.substring(33, 35));
                    record.setStationNumber(nextline.substring(35, 38));
                    record.setClaimProcessDate(nextline.substring(38, 46));
                    claimsErrorReportRecords.add(record);
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
                    claimsErrorReportRecords.add(record);
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
                    claimsErrorReportRecords.add(record);
                }

                if (headerCount.compareTo("8") == 0) {
                    record = new BillingClaimsErrorReportRecordDto();
                    record.setExplain(nextline.substring(3, 5));
                    record.setError(nextline.substring(5, 60));
                    claimsErrorReportRecords.add(record);
                }

                if (headerCount.compareTo("9") == 0) {
                    record = new BillingClaimsErrorReportRecordDto();
                    record.setHeader1Count(nextline.substring(3, 10));
                    record.setHeader2Count(nextline.substring(10, 17));
                    record.setItemCount(nextline.substring(17, 24));
                    record.setMessageCount(nextline.substring(24, 31));
                    claimsErrorReportRecords.add(record);
                }

            }
        } catch (IOException ioe) {
            // Flip verdict false so an unreadable-mid-parse file doesn't
            // surface to the caller as "import succeeded".
            verdict = false;
            claimsErrorReportRecords.clear();
            MiscUtils.getLogger().error(
                    "Claims-error parse failed (file={}, line={}, IOException), verdict=false",
                    LogSanitizer.sanitize(sourceName), lineNumber, ioe);
        } catch (StringIndexOutOfBoundsException ioe) {
            verdict = false;
            claimsErrorReportRecords.clear();
            MiscUtils.getLogger().error(
                    "Claims-error parse failed (file={}, line={}, malformed record layout), verdict=false",
                    LogSanitizer.sanitize(sourceName), lineNumber, ioe);
        } catch (BillingValidationException e) {
            verdict = false;
            claimsErrorReportRecords.clear();
            MiscUtils.getLogger().error(
                    "Claims-error parse failed (file={}, line={}, malformed amount), verdict=false",
                    LogSanitizer.sanitize(sourceName), lineNumber, e);
        }

        return verdict;
    }


    public List<BillingClaimsErrorReportRecordDto> getClaimsErrorReportRecords() {
        return Collections.unmodifiableList(claimsErrorReportRecords);
    }

    public void setClaimsErrorReportRecords(List<BillingClaimsErrorReportRecordDto> records) {
        // Defensive copy so a caller can't mutate the internal buffer
        // through their list reference after handing it off (e.g., the
        // import service builds a transient ArrayList, hands it in, then
        // continues to add rows during a retry).
        // Reject null loudly — silently coalescing to an empty list would
        // contradict the fail-loudly stance everywhere else in this class
        // and let a caller bug ship a "successful" empty parser.
        Objects.requireNonNull(records, "records must not be null");
        this.claimsErrorReportRecords = new ArrayList<>(records);
    }

    public boolean isVerdict() {
        return verdict;
    }

    public void setVerdict(boolean verdict) {
        this.verdict = verdict;
    }

}
