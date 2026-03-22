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


/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package io.github.carlos_emr.carlos.demographic.pageUtil;

import java.io.File;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import javax.xml.datatype.XMLGregorianCalendar;

import io.github.carlos_emr.carlos.hospitalReportManager.xsd.DateFullOrPartial;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.OmdCds;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PatientRecord;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PersonNameSimple;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.ReportClass;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.ReportContent;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.ReportFormat;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.ReportMedia;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.ReportsReceived;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.ReportsReceived.OBRContent;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.TransactionInformation;
import io.github.carlos_emr.carlos.utility.MiscUtils;


/**
 * Parses Hospital Report Manager (HRM) XML files and provides access to report data.
 *
 * <p>This class unmarshals HRM OmdCds XML documents using JAXB to extract
 * reports received and transaction information. It provides methods to access
 * report content, author physician details, dates, OBR content, and
 * transaction message IDs.</p>
 *
 * <p>HRM files conform to the Ontario OMD CDS (Clinical Data Standard) schema
 * for electronic health report interchange between healthcare facilities.</p>
 *
 * @see io.github.carlos_emr.carlos.hospitalReportManager.xsd.OmdCds
 * @since 2026-03-17
 */
public class ReadHRMFile {
    private List<ReportsReceived> reportsReceived = null;
    private List<TransactionInformation> transactionInformation = null;
/*
    private Demographics demographics = null;
    private ReportsReceived[] reportsReceived = null;
    private TransactionInformation[] transactionInformation = null;
 *
 */

    /**
     * Constructs a ReadHRMFile by parsing the specified HRM XML file.
     *
     * @param hrmFile String the absolute file path to the HRM XML document, or null
     */
    public ReadHRMFile(String hrmFile) {
        try {
            if (hrmFile == null) {
                return;
            }
            File hrm = new File(hrmFile);
            if (!hrm.exists()) {
                return;
            }
            JAXBContext jc = JAXBContext.newInstance("io.github.carlos_emr.carlos.hospitalReportManager.xsd");
            Unmarshaller u = jc.createUnmarshaller();
            OmdCds root = (OmdCds) u.unmarshal(hrm);

            PatientRecord pr = root.getPatientRecord();
            reportsReceived = pr.getReportsReceived();
            transactionInformation = pr.getTransactionInformation();

        } catch (JAXBException ex) {
            MiscUtils.getLogger();
        }
    }

    /**
     * Returns the total number of reports received in the HRM document.
     *
     * @return int the count of reports, or 0 if none
     */
    public int getReportsReceivedTotal() {
        if (reportsReceived == null) return 0;
        return reportsReceived.size();
    }

    /**
     * Returns the author physician's name for the specified report.
     *
     * @param r int the zero-based report index
     * @return HashMap&lt;String, String&gt; map with "firstname" and "lastname" keys
     */
    public HashMap<String, String> getReportAuthorPhysician(int r) {
        HashMap<String, String> authorPhysician = new HashMap<String, String>();
        if (getReportsReceived(r) == null) return authorPhysician;

        PersonNameSimple author = getReportsReceived(r).getAuthorPhysician();
        if (author != null) {
            authorPhysician.put("firstname", author.getFirstName());
            authorPhysician.put("lastname", author.getLastName());
        }
        return authorPhysician;
    }

    /**
     * Returns string metadata fields for the specified report.
     *
     * @param r int the zero-based report index
     * @return HashMap&lt;String, String&gt; map with keys such as "class", "subclass", "format", "media"
     */
    public HashMap<String, String> getReportStrings(int r) {
        HashMap<String, String> strings = new HashMap<String, String>();
        if (getReportsReceived(r) == null) return strings;

        ReportsReceived rp = getReportsReceived(r);

        ReportClass rptClass = rp.getClazz();
        String subClass = rp.getSubClass();
        String ext = rp.getFileExtensionAndVersion();
        ReportFormat format = rp.getFormat();
        ReportMedia media = rp.getMedia();
        String resultStatus = rp.getResultStatus();
        String reviewerId = rp.getReviewingOHIPPhysicianId();
        String sendingFacility = rp.getSendingFacility();
        String sendingFacilityRptNum = rp.getSendingFacilityReportNumber();

        if (rptClass != null) strings.put("class", rptClass.toString());
        if (subClass != null) strings.put("subclass", subClass);
        if (ext != null) strings.put("fileextension&version", ext);
        if (format != null) strings.put("format", format.toString());
        if (media != null) strings.put("media", media.toString());
        if (resultStatus != null) strings.put("resultstatus", resultStatus.toString());
        if (reviewerId != null) strings.put("reviewingohipphysicianid", reviewerId);
        if (sendingFacility != null) strings.put("sendingfacility", sendingFacility);
        if (sendingFacilityRptNum != null) strings.put("sendingfacilityreportnumber", sendingFacilityRptNum);

        return strings;
    }

    /**
     * Returns date fields for the specified report (event, received, reviewed).
     *
     * @param r int the zero-based report index
     * @return HashMap&lt;String, Calendar&gt; map with date keys "eventdatetime", "receiveddatetime", "revieweddatetime"
     */
    public HashMap<String, Calendar> getReportDates(int r) {
        HashMap<String, Calendar> dates = new HashMap<String, Calendar>();
        if (getReportsReceived(r) == null) return dates;

        ReportsReceived rp = getReportsReceived(r);

        DateFullOrPartial eventDateTime = rp.getEventDateTime();
        DateFullOrPartial receivedDateTime = rp.getReceivedDateTime();
        DateFullOrPartial reviewedDateTime = rp.getReviewedDateTime();

        if (dateFPtoCal(eventDateTime) != null) dates.put("eventdatetime", dateFPtoCal(eventDateTime));
        if (dateFPtoCal(receivedDateTime) != null) dates.put("receiveddatetime", dateFPtoCal(receivedDateTime));
        if (dateFPtoCal(reviewedDateTime) != null) dates.put("revieweddatetime", dateFPtoCal(reviewedDateTime));

        return dates;
    }

    /**
     * Returns the content of the specified report (text or binary media).
     *
     * @param r int the zero-based report index
     * @return HashMap&lt;String, Object&gt; map with "textcontent" or "media" key, or null if no content
     */
    public HashMap<String, Object> getReportContent(int r) {
        HashMap<String, Object> rptContent = new HashMap<String, Object>();
        if (getReportsReceived(r) == null) return rptContent;

        ReportContent content = getReportsReceived(r).getContent();
        if (content == null) return null;

        if (content.getTextContent() != null) rptContent.put("textcontent", content.getTextContent());
        else if (content.getMedia() != null) rptContent.put("media", content.getMedia());

        return rptContent;
    }

    /**
     * Returns a specific OBR content entry from a report.
     *
     * @param r int the zero-based report index
     * @param o int the zero-based OBR content index within the report
     * @return OBRContent the OBR content, or null if not found
     */
    public OBRContent getReportOBRContent(int r, int o) {
        if (getReportsReceived(r) == null) return null;

        List<OBRContent> obr = getReportsReceived(r).getOBRContent();
        if (obr.size() <= r) return null;

        return obr.get(o);
    }

    /**
     * Returns the number of OBR content entries in the specified report.
     *
     * @param r int the zero-based report index
     * @return int the count of OBR entries, or 0 if none
     */
    public int getReportOBRContentTotal(int r) {
        if (getReportsReceived(r) == null) return 0;
        return getReportsReceived(r).getOBRContent().size();
    }

    /**
     * Returns string fields from a specific OBR content entry.
     *
     * @param r int the zero-based report index
     * @param o int the zero-based OBR content index
     * @return HashMap&lt;String, String&gt; map with "accompanyingdescription", "accompanyingmnemonic", "accompanyingsubclass"
     */
    public HashMap<String, String> getReportOBRStrings(int r, int o) {
        HashMap<String, String> strings = new HashMap<String, String>();
        if (getReportOBRContent(r, o) == null) return strings;

        OBRContent obr = getReportOBRContent(r, o);

        String description = obr.getAccompanyingDescription();
        String mnemonic = obr.getAccompanyingMnemonic();
        String subclass = obr.getAccompanyingSubClass();

        if (description != null) strings.put("accompanyingdescription", description);
        if (mnemonic != null) strings.put("accompanyingmnemonic", mnemonic);
        if (subclass != null) strings.put("accompanyingsubclass", subclass);

        return strings;
    }

    /**
     * Returns the observation date/time from a specific OBR content entry.
     *
     * @param r int the zero-based report index
     * @param o int the zero-based OBR content index
     * @return Calendar the observation date/time, or null if not available
     */
    public Calendar getReportOBRObservationDateTime(int r, int o) {
        if (getReportOBRContent(r, o) == null) return null;

        return dateFPtoCal(getReportOBRContent(r, o).getObservationDateTime());
    }

    /**
     * Returns the unique message ID from the transaction information at the given index.
     *
     * @param r int the zero-based transaction index
     * @return String the message unique ID, or null if not available
     */
    public String getTransactionMessageUniqueID(int r) {
        if (transactionInformation == null) return null;
        if (transactionInformation.size() <= r) return null;

        return transactionInformation.get(r).getMessageUniqueID();
    }

    /**
     * Returns the ReportsReceived element at the given index.
     *
     * @param r int the zero-based report index
     * @return ReportsReceived the report element, or null if index is out of bounds
     */
    public ReportsReceived getReportsReceived(int r) {
        if (reportsReceived == null) return null;
        if (reportsReceived.size() <= r) return null;
        return reportsReceived.get(r);
    }


    private Calendar dateFPtoCal(DateFullOrPartial dfp) {
        if (dfp == null) return null;

        XMLGregorianCalendar xgc = dfp.getDateTime();
        if (xgc == null) xgc = dfp.getFullDate();
        if (xgc == null) xgc = dfp.getYearMonth();
        if (xgc == null) xgc = dfp.getYearOnly();

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(xgc.toGregorianCalendar().getTimeInMillis());

        return cal;
    }
}
