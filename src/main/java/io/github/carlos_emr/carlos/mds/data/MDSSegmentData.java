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
package io.github.carlos_emr.carlos.mds.data;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.MdsMSHDao;
import io.github.carlos_emr.carlos.commn.dao.MdsNTEDao;
import io.github.carlos_emr.carlos.commn.dao.MdsOBRDao;
import io.github.carlos_emr.carlos.commn.dao.MdsOBXDao;
import io.github.carlos_emr.carlos.commn.dao.MdsPV1Dao;
import io.github.carlos_emr.carlos.commn.dao.MdsZMCDao;
import io.github.carlos_emr.carlos.commn.dao.MdsZMNDao;
import io.github.carlos_emr.carlos.commn.dao.MdsZRGDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.MdsPV1;
import io.github.carlos_emr.carlos.commn.model.MdsZMC;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.ConversionUtils;
/**
 * Loads one legacy MDS lab report for {@code oscarMDS/SegmentDisplay.jsp}.
 *
 * <p>MDS reports exist only in databases imported from OSCAR; nothing in CARLOS writes them. Their
 * detail tables ({@code mdsOBX}, {@code mdsZMN}, {@code mdsNTE}, ...) have no primary key, and the
 * entities map the segmentID every row of a report shares as {@code @Id}. Every multi-row read here
 * therefore uses a scalar projection: an entity query would resolve all rows of the report to the
 * first one (issue #4567).</p>
 *
 * <p>A query failure is logged and recorded in {@link #isLoadFailed()} rather than thrown, so the
 * page can still render the patient and routing blocks and tell the reader that the results could
 * not be loaded, instead of presenting a partial report as a complete one.</p>
 */

public class MDSSegmentData {

    static Logger logger = MiscUtils.getLogger();

    public String segmentID;
    public String reportDate;
    public String reportStatus;
    public String clientNo;
    public String accessionNo;
    /** Never null: the page reads it even when the report could not be loaded. */
    public ProviderData providers = new ProviderData("", "", "");
    public ArrayList<Headers> headersArray = new ArrayList<Headers>();
    public ArrayList<ReportStatus> statusArray = new ArrayList<ReportStatus>();
    private boolean loadFailed;

    public void populateMDSSegmentData(String SID) {
        this.segmentID = SID;
        Integer segment = ConversionUtils.fromIntString(segmentID);
        try {

            // Get the header info
            MdsMSHDao mshDao = SpringUtils.getBean(MdsMSHDao.class);
            for (Object[] o : mshDao.findMdsSementDataById(segment)) {
                Date dateTime = (Date) o[0];
                String controlId = text(o[1]);
                String reportFormStatus = text(o[2]);

                if (dateTime != null) {
                    reportDate = new SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).format(dateTime);
                }
                reportStatus = (reportFormStatus.equals("1") ? "Final" : "Partial");
                // The message control id is "<client number>-<accession number>".
                String[] controlParts = controlId.split("-");
                clientNo = controlParts[0];
                accessionNo = controlParts.length > 1 ? controlParts[1] : "";
            }

            // Get the providers
            MdsPV1Dao pvDao = SpringUtils.getBean(MdsPV1Dao.class);
            MdsPV1 pv = pvDao.find(segment);
            if (pv != null) {
                providers = new ProviderData(pv.getRefDoctor(), pv.getConDoctor(), pv.getAdmDoctor());
            }

            // Get the lab status
            ProviderLabRoutingDao plrDao = SpringUtils.getBean(ProviderLabRoutingDao.class);
            for (Object[] o : plrDao.findProviderAndLabRoutingByIdAndLabType(segment, "MDS")) {
                Provider provider = (Provider) o[0];
                ProviderLabRoutingModel providerLabRouting = (ProviderLabRoutingModel) o[1];

                // The routing time to the minute: a date-only string would show every
                // acknowledgement at midnight.
                statusArray.add(new ReportStatus(provider.getFirstName() + " " + provider.getLastName(), provider.getProviderNo(), descriptiveStatus(providerLabRouting.getStatus()), providerLabRouting.getComment(), ConversionUtils.toTimestampString(providerLabRouting.getTimestamp()), this.segmentID));
            }

            // Get item descriptions and ranges and read into a hashtable
            Hashtable<String, Mnemonics> mnemonics = new Hashtable<String, Mnemonics>();
            MdsZMNDao zmnDao = SpringUtils.getBean(MdsZMNDao.class);
            for (Object[] o : zmnDao.findMnemonicsBySegmentId(segment)) {
                if (o[0] != null) {
                    mnemonics.put(text(o[0]), new Mnemonics(text(o[1]), text(o[2]), text(o[3])));
                }
            }

            // Process the notes, keyed by the obxID of the result each belongs to
            Hashtable<Integer, ArrayList<String>> notes = new Hashtable<Integer, ArrayList<String>>();
            MdsNTEDao nteDao = SpringUtils.getBean(MdsNTEDao.class);
            MdsZMCDao zmcDao = SpringUtils.getBean(MdsZMCDao.class);
            for (Object[] o : nteDao.findNotesBySegmentId(segment)) {
                String sourceOfComment = text(o[0]);
                String comment = text(o[1]);
                Integer associatedOBX = ConversionUtils.fromIntString(o[2]);
                ArrayList<String> obxNotes = notes.computeIfAbsent(associatedOBX, key -> new ArrayList<String>());

                if (sourceOfComment.equals("M")) {
                    // A plain note carries its text after a three-character prefix.
                    obxNotes.add(comment.length() > 3 ? comment.substring(3) : "");
                } else if (sourceOfComment.equals("MC")) {
                    // A message-code note names the mdsZMC set that holds its text.
                    MdsZMC zmc = comment.length() > 1 ? zmcDao.findByIdAndSetId(segment, comment.substring(1)) : null;
                    if (zmc == null && comment.length() > 2) {
                        zmc = zmcDao.findByIdAndSetId(segment, "%" + comment.substring(1, comment.length() - 1) + "%");
                    }

                    if (zmc != null) {
                        obxNotes.add(zmc.getMessageCodeDesc());
                    }
                } else {
                    logger.info("Found message note in unknown format.  Format:" + sourceOfComment);
                    obxNotes.add("Unknown note format!");
                }
            }

            // Get the report section names
            MdsZRGDao zrgDao = SpringUtils.getBean(MdsZRGDao.class);
            for (Object[] o : zrgDao.findById(segment)) {
                String groupDesc = text(o[0]);
                String groupId = (String) o[1];
                int groupIdCount = ((Number) o[2]).intValue();
                String groupHeading = text(o[3]);

                if (groupIdCount == 1 && !groupHeading.equals("")) {
                    String[] rGH = {groupHeading};
                    headersArray.add(new Headers(groupDesc, groupId, rGH));
                } else if (groupIdCount > 1) {
                    ArrayList<String> tempArray = new ArrayList<String>();
                    for (Object oo : zrgDao.findReportGroupHeadingsById(segment, groupId)) {
                        tempArray.add(text(oo));
                    }

                    String[] reportGroupHeading = new String[tempArray.size()];
                    reportGroupHeading = tempArray.toArray(reportGroupHeading);
                    headersArray.add(new Headers(groupDesc, groupId, reportGroupHeading));
                } else {
                    headersArray.add(new Headers(groupDesc, groupId, null));
                }
            }

            // Create the data structures for each section, grouped by OBR
            MdsOBRDao obrDao = SpringUtils.getBean(MdsOBRDao.class);
            for (int i = 0; i < headersArray.size(); i++) {
                List<String> resultCodes = zmnDao.findResultCodes(segment, headersArray.get(i).reportSequence);
                if (resultCodes.isEmpty()) {
                    // The OBR and OBX queries read an empty code list as no filter, which would list
                    // every result of the report again under a group that files none.
                    continue;
                }
                for (Object[] ooo : obrDao.findByIdAndResultCodes(segment, resultCodes)) {
                    // obrID is an int column; observationDateTime is the raw HL7 timestamp string
                    // GroupedReports parses.
                    String associatedObr = text(ooo[0]);
                    String observationDateTime = (String) ooo[1];
                    (headersArray.get(i)).groupedReportsArray.add(new GroupedReports(associatedObr, observationDateTime, resultCodes));
                }
            }

            MdsOBXDao obxDao = SpringUtils.getBean(MdsOBXDao.class);
            // Get the actual results
            Mnemonics thisOBXMnemonics = new Mnemonics();
            for (int i = 0; i < (headersArray.size()); i++) {
                for (int j = 0; j < (headersArray.get(i)).groupedReportsArray.size(); j++) {
                    GroupedReports group = (headersArray.get(i)).groupedReportsArray.get(j);
                    Integer associatedOBR = ConversionUtils.fromIntString(group.associatedOBR);

                    for (Object[] obx : obxDao.findByIdObrAndCodes(segment, associatedOBR, group.codes)) {
                        Integer obxId = ConversionUtils.fromIntString(obx[0]);
                        String observationIdentifier = text(obx[1]);
                        thisOBXMnemonics.update(mnemonics.get(mnemonicOf(observationIdentifier)));

                        group.resultsArray.add(new Results(thisOBXMnemonics.reportName, thisOBXMnemonics.referenceRange, thisOBXMnemonics.units, text(obx[2]), text(obx[3]), observationIdentifier, text(obx[4]), notes.get(obxId), beforeCaret(text(obx[5]))));
                    }
                }
            }
        } catch (Exception e) {
            loadFailed = true;
            logger.error("In MDS Segment Data", e);
        }
    }

    /**
     * Whether a query failed while the report was loaded. The fields hold whatever was read before
     * the failure, so the page must not present them as the whole report.
     *
     * @return true when {@link #populateMDSSegmentData(String)} caught an exception
     */
    public boolean isLoadFailed() {
        return loadFailed;
    }

    /** A column value as text: null becomes empty, numbers become their decimal string. */
    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * The ZMN mnemonic an OBX observation identifier names: the text between its one-character prefix
     * and the first caret ({@code "XFKGLU^Glucose"} names {@code "FKGLU"}), or the whole remainder when
     * an imported identifier has no caret.
     */
    private static String mnemonicOf(String observationIdentifier) {
        if (observationIdentifier.length() < 2) {
            return "";
        }
        return beforeCaret(observationIdentifier.substring(1));
    }

    /** The first caret-delimited component of an HL7 field, or the whole field when it has none. */
    private static String beforeCaret(String field) {
        int caret = field.indexOf('^');
        return caret < 0 ? field : field.substring(0, caret);
    }

    public String descriptiveStatus(String status) {
        if (status == null || status.isEmpty()) {
            return "Not Acknowledged";
        }
        switch (status.charAt(0)) {
            case 'A':
                return "Acknowledged";
            case 'F':
                return "Filed but not acknowledged";
            case 'U':
                return "N/A";
            default:
                return "Not Acknowledged";
        }
    }

    // returns true if providers has already acknowledged this lab; false otherwise
    public boolean getAcknowledgedStatus(String providerNo) {

        for (int i = 0; i < statusArray.size(); i++) {

            if ((statusArray.get(i)).getProviderNo().equals(providerNo)) {
                // logger.info("Status of "+i+" is : "+ ((ReportStatus) statusArray.get(i)).getStatus() );
                return ((statusArray.get(i)).getStatus().startsWith("Ack"));
            }
        }
        return false;
    }

}
