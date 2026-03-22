/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.hospitalReportManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;

import javax.xml.datatype.XMLGregorianCalendar;

import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PersonNameSimple;
import org.apache.commons.codec.binary.Base64;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.DateFullOrPartial;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.Demographics;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.OmdCds;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PersonNameStandard;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.ReportFormat;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.ReportsReceived.OBRContent;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.util.StringUtils;

/**
 * Represents a parsed Hospital Report Manager (HRM) report, providing convenient
 * access to patient demographics, report content, and metadata from the underlying
 * OMD CDS (Ontario Medical Data - Clinical Document Standard) XML structure.
 *
 * <p>Wraps the JAXB-unmarshalled {@link OmdCds} document and exposes commonly needed
 * fields such as patient name, health card number (HCN), gender, address, phone number,
 * report content, and sending facility information.</p>
 *
 * @see HRMReportParser
 * @see OmdCds
 * @since 2008-11-05
 */
public class HRMReport {

    private OmdCds hrmReport;
    private Demographics demographics;
    private String fileLocation;
    private String fileData;

    private Integer hrmDocumentId;
    private Integer hrmParentDocumentId;

    /**
     * Constructs an HRM report wrapper from a parsed OMD CDS document.
     *
     * @param hrmReport OmdCds the JAXB-unmarshalled report document
     */
    public HRMReport(OmdCds hrmReport) {
        this.hrmReport = hrmReport;
        this.demographics = hrmReport.getPatientRecord().getDemographics();
    }

    /**
     * Constructs an HRM report wrapper with file location and raw file data.
     *
     * @param root OmdCds the JAXB-unmarshalled report document
     * @param hrmReportFileLocation String the filesystem path to the report XML file
     * @param fileData String the raw UTF-8 content of the report file
     */
    public HRMReport(OmdCds root, String hrmReportFileLocation, String fileData) {
        this.fileData = fileData;
        this.fileLocation = hrmReportFileLocation;
        this.hrmReport = root;
        this.demographics = hrmReport.getPatientRecord().getDemographics();
    }

    /**
     * Returns the root OMD CDS document containing the full report structure.
     *
     * @return OmdCds the JAXB-unmarshalled document root
     */
    public OmdCds getDocumentRoot() {
        return hrmReport;
    }

    /**
     * Returns the raw UTF-8 content of the report XML file.
     *
     * @return String the file data, or {@code null} if constructed without file data
     */
    public String getFileData() {
        return fileData;
    }

    /**
     * Returns the filesystem path to the report XML file.
     *
     * @return String the file location path
     */
    public String getFileLocation() {
        return fileLocation;
    }

    /**
     * Sets the filesystem path to the report XML file.
     *
     * @param fileLocation String the file location path
     */
    public void setFileLocation(String fileLocation) {
        this.fileLocation = fileLocation;
    }

    /**
     * Returns the patient's legal name in "LastName, FirstName" format.
     *
     * @return String the formatted legal name
     */
    public String getLegalName() {
        PersonNameStandard name = demographics.getNames();
        return name.getLegalName().getLastName().getPart() + ", " + name.getLegalName().getFirstName().getPart();
    }

    /**
     * Returns the patient's legal last name.
     *
     * @return String the last name
     */
    public String getLegalLastName() {
        PersonNameStandard name = demographics.getNames();
        return name.getLegalName().getLastName().getPart();
    }

    /**
     * Returns the patient's legal first name.
     *
     * @return String the first name
     */
    public String getLegalFirstName() {
        PersonNameStandard name = demographics.getNames();
        return name.getLegalName().getFirstName().getPart();
    }

    /**
     * Returns the patient's date of birth as a list of integer components.
     *
     * @return List&lt;Integer&gt; a three-element list containing [year, month, day]
     */
    public List<Integer> getDateOfBirth() {
        List<Integer> dateOfBirthList = new ArrayList<Integer>();
        XMLGregorianCalendar fullDate = dateFP(demographics.getDateOfBirth());
        dateOfBirthList.add(fullDate.getYear());
        dateOfBirthList.add(fullDate.getMonth());
        dateOfBirthList.add(fullDate.getDay());

        return dateOfBirthList;
    }

    /**
     * Returns the patient's date of birth formatted as "YYYY-MM-DD".
     *
     * @return String the formatted date of birth string
     */
    public String getDateOfBirthAsString() {
        List<Integer> dob = getDateOfBirth();
        return dob.get(0) + "-" + String.format("%02d", dob.get(1)) + "-" + String.format("%02d", dob.get(2));
    }

    /**
     * Returns the patient's Health Card Number (HCN).
     *
     * @return String the health card number
     */
    public String getHCN() {
        return demographics.getHealthCard().getNumber();
    }

    /**
     * Returns the version code of the patient's health card.
     *
     * @return String the health card version code
     */
    public String getHCNVersion() {
        return demographics.getHealthCard().getVersion();
    }

    /**
     * Returns the expiry date of the patient's health card.
     *
     * @return Calendar the health card expiry date
     */
    public Calendar getHCNExpiryDate() {
        return demographics.getHealthCard().getExpirydate().toGregorianCalendar();
    }

    /**
     * Returns the province code associated with the patient's health card.
     *
     * @return String the province code (e.g. "ON" for Ontario)
     */
    public String getHCNProvinceCode() {
        return demographics.getHealthCard().getProvinceCode();
    }

    /**
     * Returns the patient's gender value from the demographics.
     *
     * @return String the gender value
     */
    public String getGender() {
        return demographics.getGender().value();
    }

    /**
     * Returns the unique vendor ID sequence from the patient demographics.
     *
     * @return String the unique vendor identifier
     */
    public String getUniqueVendorIdSequence() {
        return demographics.getUniqueVendorIdSequence();
    }

    /**
     * Returns the first line of the patient's structured address.
     *
     * @return String address line 1, or empty string if no address is available
     */
    public String getAddressLine1() {
        if (demographics.getAddress() == null || demographics.getAddress().isEmpty()) {
            return "";
        }
        return demographics.getAddress().get(0).getStructured().getLine1();
    }

    /**
     * Returns the second line of the patient's structured address.
     *
     * @return String address line 2, or empty string if no address is available
     */
    public String getAddressLine2() {
        if (demographics.getAddress() == null || demographics.getAddress().isEmpty()) {
            return "";
        }
        return demographics.getAddress().get(0).getStructured().getLine2();
    }

    /**
     * Returns the city from the patient's structured address.
     *
     * @return String the city name, or empty string if no address is available
     */
    public String getAddressCity() {
        if (demographics.getAddress() == null || demographics.getAddress().isEmpty()) {
            return "";
        }
        return demographics.getAddress().get(0).getStructured().getCity();
    }

    /**
     * Returns the country subdivision code (province/state) from the patient's address.
     *
     * @return String the subdivision code, or empty string if no address is available
     */
    public String getCountrySubDivisionCode() {
        if (demographics.getAddress() == null || demographics.getAddress().isEmpty()) {
            return "";
        }
        return demographics.getAddress().get(0).getStructured().getCountrySubdivisionCode();
    }

    /**
     * Returns the Canadian postal code from the patient's address.
     *
     * @return String the postal code, or empty string if no address is available
     */
    public String getPostalCode() {
        if (demographics.getAddress() == null || demographics.getAddress().isEmpty()) {
            return "";
        }
        return demographics.getAddress().get(0).getStructured().getPostalZipCode().getPostalCode();

    }

    /**
     * Returns the US zip code from the patient's address.
     *
     * @return String the zip code, or empty string if no address is available
     */
    public String getZipCode() {
        if (demographics.getAddress() == null || demographics.getAddress().isEmpty()) {
            return "";
        }
        return demographics.getAddress().get(0).getStructured().getPostalZipCode().getZipCode();
    }

    /**
     * Returns the patient's primary phone number.
     *
     * @return String the phone number, or empty string if none is available
     */
    public String getPhoneNumber() {
        if (demographics.getPhoneNumber() == null || demographics.getPhoneNumber().isEmpty()) {
            return "";
        }
        return demographics.getPhoneNumber().get(0).getContent().get(0).getValue();
    }

    /**
     * Returns the patient's enrollment status.
     *
     * @return String the enrollment status value
     */
    public String getEnrollmentStatus() {
        return demographics.getEnrollmentStatus();
    }

    /**
     * Returns the patient's person status code (e.g. active, deceased).
     *
     * @return String the person status value
     */
    public String getPersonStatus() {
        return demographics.getPersonStatusCode().value();
    }

    /**
     * Determines whether the first report in this document has binary content format.
     *
     * @return boolean {@code true} if the report format is {@link ReportFormat#BINARY}
     */
    public boolean isBinary() {
        if (hrmReport.getPatientRecord().getReportsReceived() != null || hrmReport.getPatientRecord().getReportsReceived().isEmpty()) {
            if (hrmReport.getPatientRecord().getReportsReceived().get(0).getFormat() == ReportFormat.BINARY) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the file extension and version string of the first report attachment.
     *
     * @return String the file extension (e.g. ".pdf", ".jpg"), or empty string if unavailable
     */
    public String getFileExtension() {
        if (hrmReport.getPatientRecord().getReportsReceived() == null || hrmReport.getPatientRecord().getReportsReceived().isEmpty()) {
            return "";
        }
        return hrmReport.getPatientRecord().getReportsReceived().get(0).getFileExtensionAndVersion();
    }

    /**
     * Returns the text content of the first report. For binary reports, returns
     * the Base64-encoded representation of the binary content.
     *
     * @return String the report text content or Base64-encoded binary, or {@code null} on error
     */
    public String getFirstReportTextContent() {
        String result = null;
        if (hrmReport.getPatientRecord().getReportsReceived() != null || hrmReport.getPatientRecord().getReportsReceived().isEmpty()) {
            if (hrmReport.getPatientRecord().getReportsReceived().get(0).getFormat() == ReportFormat.BINARY) {
                return new Base64().encodeToString(getBinaryContent());
            }

            try {
                result = hrmReport.getPatientRecord().getReportsReceived().get(0).getContent().getTextContent();
            } catch (Exception e) {
                MiscUtils.getLogger().error("error", e);
            }
        }
        return result;
    }

    /**
     * Returns the raw binary (Base64-decoded) content of the first report media attachment.
     *
     * @return byte[] the binary content, or {@code null} on error
     */
    public byte[] getBinaryContent() {

        try {
            byte[] tmp = hrmReport.getPatientRecord().getReportsReceived().get(0).getContent().getMedia();
            return tmp;
        } catch (Exception e) {
            MiscUtils.getLogger().error("error", e);
        }
        return null;
    }

    /**
     * Returns the report class of the first received report (e.g. "Diagnostic Imaging Report",
     * "Medical Records Report", "Cardio Respiratory Report").
     *
     * @return String the report class value, or empty string if unavailable
     */
    public String getFirstReportClass() {
        if (hrmReport.getPatientRecord().getReportsReceived() == null || hrmReport.getPatientRecord().getReportsReceived().isEmpty()) {
            return "";
        }
        return hrmReport.getPatientRecord().getReportsReceived().get(0).getClazz().value();
    }

    /**
     * Returns the sub-class of the first received report.
     *
     * @return String the report sub-class, or empty string if unavailable
     */
    public String getFirstReportSubClass() {
        if (hrmReport.getPatientRecord().getReportsReceived() == null || hrmReport.getPatientRecord().getReportsReceived().isEmpty()) {
            return "";
        }
        return hrmReport.getPatientRecord().getReportsReceived().get(0).getSubClass();
    }

    /**
     * Returns the event date/time of the first received report.
     *
     * @return Calendar the event timestamp, or {@code null} if not available
     */
    public Calendar getFirstReportEventTime() {

        if (hrmReport.getPatientRecord().getReportsReceived() != null &&
                !hrmReport.getPatientRecord().getReportsReceived().isEmpty() &&
                hrmReport.getPatientRecord().getReportsReceived().get(0).getEventDateTime() != null)
            return dateFP(hrmReport.getPatientRecord().getReportsReceived().get(0).getEventDateTime()).toGregorianCalendar();
        return null;
    }

    /**
     * Returns the media type of the first received report (e.g. "Text", "Binary").
     *
     * @return String the media type value, or empty string on error
     */
    public String getMediaType() {
        String mediaType = "";
        try {
            mediaType = hrmReport.getPatientRecord().getReportsReceived().get(0).getMedia().value();
        } catch (Exception e) {
            MiscUtils.getLogger().error("error", e);
        }

        return mediaType;
    }

    /**
     * Returns the author physician name components of the first report.
     *
     * <p>Parses the HL7-encoded physician name string (caret-delimited) into individual
     * name components. For a 7-component HL7 string, returns elements at indices
     * 0, 1, 2, 3, and 6.</p>
     *
     * @return List&lt;String&gt; the physician name components, or an empty list if no author is set
     */
    public List<String> getFirstReportAuthorPhysician() {
        List<String> physicianName = new ArrayList<String>();


        if (hrmReport.getPatientRecord().getReportsReceived().get(0).getAuthorPhysician() != null) {

            String physicianHL7String = hrmReport.getPatientRecord().getReportsReceived().get(0).getAuthorPhysician().getLastName();
            if (physicianHL7String != null) {

                if (physicianHL7String.split("\\^").length == 7) {
                    String[] physicianNameArray = physicianHL7String.split("\\^");
                    physicianName.add(physicianNameArray[0]);
                    physicianName.add(physicianNameArray[1]);
                    physicianName.add(physicianNameArray[2]);
                    physicianName.add(physicianNameArray[3]);
                    physicianName.add(physicianNameArray[6]);
                    return physicianName;

                }

                for (String n : physicianHL7String.split("\\^")) {
                    physicianName.add(n);
                }
            }

            physicianHL7String = hrmReport.getPatientRecord().getReportsReceived().get(0).getAuthorPhysician().getFirstName();
            if (physicianHL7String != null) {
                for (String n : physicianHL7String.split("\\^")) {
                    physicianName.add(n);
                }
            }

        }

        return physicianName;
    }

    /**
     * Returns the sending author's formatted name ("FirstName LastName").
     *
     * @return String the author name, or empty string if no author is available
     */
    public String getSendingAuthor() {
        String sourceAuthor = "";
        if (hrmReport.getPatientRecord().getReportsReceived() != null && !hrmReport.getPatientRecord().getReportsReceived().isEmpty()) {
            PersonNameSimple authorPhysician = hrmReport.getPatientRecord().getReportsReceived().get(0).getAuthorPhysician();
            if (authorPhysician != null) {
                sourceAuthor = (StringUtils.noNull(authorPhysician.getFirstName()).trim() + " " + StringUtils.noNull(authorPhysician.getLastName()).trim()).trim();
            }
        }


        return sourceAuthor;
    }


    /**
     * Returns the sending facility identifier.
     *
     * @return String the facility ID, or empty string if unavailable
     */
    public String getSendingFacilityId() {
        if (hrmReport.getPatientRecord().getReportsReceived() == null || hrmReport.getPatientRecord().getReportsReceived().isEmpty()) {
            return "";
        }
        return hrmReport.getPatientRecord().getReportsReceived().get(0).getSendingFacility();
    }

    /**
     * Returns the sending facility's internal report number.
     *
     * @return String the facility report number, or empty string if unavailable
     */
    public String getSendingFacilityReportNo() {
        if (hrmReport.getPatientRecord().getReportsReceived() == null || hrmReport.getPatientRecord().getReportsReceived().isEmpty()) {
            return "";
        }
        return hrmReport.getPatientRecord().getReportsReceived().get(0).getSendingFacilityReportNumber();
    }

    /**
     * Returns the result status of the first report (e.g. "C" for Cancelled).
     *
     * @return String the result status code, or empty string if unavailable
     */
    public String getResultStatus() {
        if (hrmReport.getPatientRecord().getReportsReceived() == null || hrmReport.getPatientRecord().getReportsReceived().isEmpty()) {
            return "";
        }
        return hrmReport.getPatientRecord().getReportsReceived().get(0).getResultStatus();
    }

    public List<List<Object>> getAccompanyingSubclassList() {
        LinkedList<List<Object>> subclassList = new LinkedList<List<Object>>();

        if (hrmReport.getPatientRecord().getReportsReceived() != null || !hrmReport.getPatientRecord().getReportsReceived().isEmpty()) {
            for (OBRContent o : hrmReport.getPatientRecord().getReportsReceived().get(0).getOBRContent()) {
                LinkedList<Object> obrContentList = new LinkedList<Object>();

                obrContentList.add(o.getAccompanyingSubClass());
                obrContentList.add(o.getAccompanyingMnemonic());
                obrContentList.add(o.getAccompanyingDescription());

                if (o.getObservationDateTime() != null) {
                    GregorianCalendar calendar = dateFP(o.getObservationDateTime()).toGregorianCalendar();
                    SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy");
                    sdf.setTimeZone(calendar.getTimeZone());

                    Date date = calendar.getTime();
                    String formattedDate = sdf.format(calendar.getTime());

                    obrContentList.add(date);
                    obrContentList.add(formattedDate);
                }

                subclassList.add(obrContentList);
            }
        }
        return subclassList;
    }

    public String getFirstAccompanyingSubClassDateTime() {
        if (hrmReport.getPatientRecord().getReportsReceived() != null &&
                !hrmReport.getPatientRecord().getReportsReceived().isEmpty() &&
                hrmReport.getPatientRecord().getReportsReceived().get(0).getOBRContent() != null &&
                hrmReport.getPatientRecord().getReportsReceived().get(0).getOBRContent().get(0) != null &&
                hrmReport.getPatientRecord().getReportsReceived().get(0).getOBRContent().get(0).getObservationDateTime() != null) {

            GregorianCalendar calendar = dateFP(hrmReport.getPatientRecord().getReportsReceived().get(0).getOBRContent().get(0).getObservationDateTime()).toGregorianCalendar();
            SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy");
            sdf.setTimeZone(calendar.getTimeZone());

            return sdf.format(calendar.getTime());
        }

        return "";
    }

    public String getFirstAccompanyingSubClass() {
        if (hrmReport.getPatientRecord().getReportsReceived() != null &&
                !hrmReport.getPatientRecord().getReportsReceived().isEmpty() &&
                hrmReport.getPatientRecord().getReportsReceived().get(0).getOBRContent() != null &&
                hrmReport.getPatientRecord().getReportsReceived().get(0).getOBRContent().get(0) != null &&
                hrmReport.getPatientRecord().getReportsReceived().get(0).getOBRContent().get(0).getObservationDateTime() != null) {
            return (hrmReport.getPatientRecord().getReportsReceived().get(0).getOBRContent().get(0).getAccompanyingDescription());
        }

        return null;
    }

    public String getMessageUniqueId() {
        if (hrmReport.getPatientRecord().getTransactionInformation() == null || hrmReport.getPatientRecord().getTransactionInformation().isEmpty()) {
            return "";
        }
        return hrmReport.getPatientRecord().getTransactionInformation().get(0).getMessageUniqueID();
    }

    public String getDeliverToUserId() {
        if (hrmReport.getPatientRecord().getTransactionInformation() == null || hrmReport.getPatientRecord().getTransactionInformation().isEmpty()) {
            return "";
        }
        return hrmReport.getPatientRecord().getTransactionInformation().get(0).getDeliverToUserID();
    }

    public String getDeliverToUserIdFirstName() {
        if (hrmReport.getPatientRecord().getTransactionInformation() == null || hrmReport.getPatientRecord().getTransactionInformation().isEmpty()) {
            return "";
        }
        if (hrmReport.getPatientRecord().getTransactionInformation().get(0).getProvider() == null)
            return null;
        return hrmReport.getPatientRecord().getTransactionInformation().get(0).getProvider().getFirstName();
    }

    public String getDeliverToUserIdLastName() {
        if (hrmReport.getPatientRecord().getTransactionInformation() == null || hrmReport.getPatientRecord().getTransactionInformation().isEmpty()) {
            return "";
        }
        if (hrmReport.getPatientRecord().getTransactionInformation().get(0).getProvider() == null)
            return null;
        return hrmReport.getPatientRecord().getTransactionInformation().get(0).getProvider().getLastName();
    }

    public String getDeliveryToUserIdFormattedName() {
        String name = "";
        if (getDeliverToUserIdLastName() != null) {
            name = getDeliverToUserIdLastName();
        }
        if (getDeliverToUserIdFirstName() != null) {
            if (!name.isEmpty()) {
                name = name + ", " + getDeliverToUserIdFirstName();
            } else {
                name = getDeliverToUserIdFirstName();
            }
        }

        return name;
    }

    public Integer getHrmDocumentId() {
        return hrmDocumentId;
    }

    public void setHrmDocumentId(Integer hrmDocumentId) {
        this.hrmDocumentId = hrmDocumentId;
    }

    public Integer getHrmParentDocumentId() {
        return hrmParentDocumentId;
    }

    public void setHrmParentDocumentId(Integer hrmParentDocumentId) {
        this.hrmParentDocumentId = hrmParentDocumentId;
    }


    XMLGregorianCalendar dateFP(DateFullOrPartial dfp) {
        if (dfp == null) return null;

        if (dfp.getDateTime() != null) return dfp.getDateTime();
        else if (dfp.getFullDate() != null) return dfp.getFullDate();
        else if (dfp.getYearMonth() != null) return dfp.getYearMonth();
        else if (dfp.getYearOnly() != null) return dfp.getYearOnly();
        return null;
    }


}
