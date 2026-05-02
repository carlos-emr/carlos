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

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimBatchAcknowledgementReportRecordDto;
import io.github.carlos_emr.carlos.utility.MiscUtils;
/**
 * Bean handler for {@code BillingClaimBatchAcknowledgementReportParser}.
 *
 * <p>The handler models fixed-format Ontario billing report data so callers can
 * parse, inspect, and render records without duplicating report conventions in
 * JSPs.</p>
 */

public class BillingClaimBatchAcknowledgementReportParser {

    ArrayList batchAcknowledgementRecords = new ArrayList();
    public boolean verdict = true;

    public BillingClaimBatchAcknowledgementReportParser(FileInputStream file) {
        verdict = init(file);
    }

    public boolean init(FileInputStream file) {


        String nextline;

        try (InputStreamReader reader = new InputStreamReader(file);
             BufferedReader input = new BufferedReader(reader)) {
            while ((nextline = input.readLine()) != null) {
                String headerCount = nextline.substring(2, 3);
                if (headerCount.compareTo("1") == 0) {

                    String batchNumber = nextline.substring(6, 11);
                    String operatorNumber = nextline.substring(11, 17);
                    String providerNumber = nextline.substring(56, 62);
                    String groupNumber = nextline.substring(52, 56);
                    String batchCreateDate = nextline.substring(17, 25);
                    String batchSequenceNumber = nextline.substring(25, 29);
                    String microStart = nextline.substring(29, 40);
                    String microEnd = nextline.substring(40, 45);
                    String microType = nextline.substring(45, 52);
                    String claimNumber = nextline.substring(62, 67);
                    String recordNumber = nextline.substring(67, 73);
                    String batchProcessDate = nextline.substring(73, 81);
                    String explain = nextline.substring(81, 111);

                    BillingClaimBatchAcknowledgementReportRecordDto CBABean = new BillingClaimBatchAcknowledgementReportRecordDto(batchNumber,
                            operatorNumber,
                            providerNumber,
                            groupNumber,
                            batchCreateDate,
                            batchSequenceNumber,
                            microStart,
                            microEnd,
                            microType,
                            claimNumber,
                            recordNumber,
                            batchProcessDate,
                            explain);
                    batchAcknowledgementRecords.add(CBABean);
                }
            }
        } catch (IOException ioe) {
            // Flip verdict false AND log so the operator's import-result
            // page reflects a partial-read mid-stream failure rather than
            // surfacing a clean-parse outcome.
            MiscUtils.getLogger().error("Batch ack parse failed (IOException)", ioe);
            verdict = false;
            batchAcknowledgementRecords.clear();
        } catch (StringIndexOutOfBoundsException ioe) {
            MiscUtils.getLogger().error("Batch ack parse failed (malformed record layout)", ioe);
            verdict = false;
            batchAcknowledgementRecords.clear();
        }
        return verdict;
    }


    public ArrayList getBatchAcknowledgementRecords() {
        return batchAcknowledgementRecords;
    }

}
