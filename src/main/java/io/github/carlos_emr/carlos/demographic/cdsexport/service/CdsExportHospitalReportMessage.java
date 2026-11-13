/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto;
import io.github.carlos_emr.carlos.hospitalReportManager.HRMReportParser;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.DateFullOrPartial;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.OmdCds;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PersonNameSimple;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.ReportsReceived;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.TransactionInformation;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;

/**
 * One stored Hospital Report Manager message, read exactly as the CARLOS report viewer reads it
 * ({@link HRMReportParser#unmarshal}: validated against the OntarioMD HRM 1.1.2 schema). The
 * message is an OntarioMD {@code OmdCds} document: the sender's identification of the patient
 * (Demographics), one report per {@code ReportsReceived} and its routing
 * ({@code TransactionInformation}). Only the report and routing values are kept here; the
 * sender's patient block is the matching identity of the chart the report is filed to.
 *
 * @since 2026-11-13
 */
final class CdsExportHospitalReportMessage {
    /** The largest stored message read into memory; a larger one is attached as stored. */
    static final int MAXIMUM_BYTES = 64 << 20;

    /** A date exactly as the message writes it: its arm and its XML lexical form. */
    record When(String arm, String lexical, XMLGregorianCalendar value) {
        @Override
        public String toString() {
            return "When[protected]";
        }
    }

    /** A person name as the message writes it; either part may be absent. */
    record Name(String first, String last) {
        @Override
        public String toString() {
            return "Name[protected]";
        }
    }

    /** One OBR content block: the accompanying subclass, mnemonic, description and time. */
    record Observation(String subClass, String mnemonic, String description, When observed) {
        @Override
        public String toString() {
            return "Observation[protected]";
        }
    }

    /** One routing entry of the message. */
    record Transaction(String messageUniqueId, String deliverToUserId, Name provider) {
        @Override
        public String toString() {
            return "Transaction[protected]";
        }
    }

    /**
     * One received report. {@code text} is its TextContent and {@code binary} its decoded Media;
     * at most one is present, and {@code content} says whether the Content element was there.
     */
    record Report(
            String media,
            String format,
            String extension,
            boolean content,
            String text,
            byte[] binary,
            String reportClass,
            String subClass,
            When event,
            When received,
            When reviewed,
            Name author,
            String reviewingOhip,
            String sendingFacility,
            String sendingFacilityReport,
            List<Observation> observations,
            String resultStatus) {
        Report {
            observations = List.copyOf(observations);
        }

        @Override
        public String toString() {
            return "Report[protected]";
        }
    }

    /** Every report and routing entry of one message, in document order. */
    record Parsed(List<Report> reports, List<Transaction> transactions) {
        Parsed {
            reports = List.copyOf(reports);
            transactions = List.copyOf(transactions);
        }

        @Override
        public String toString() {
            return "Parsed[protected]";
        }
    }

    /** The stored bytes are not an HRM message the report viewer could show. */
    static final class Unreadable extends Exception {
        private static final long serialVersionUID = 1L;

        Unreadable() {
            super("CDS_EXPORT_HRM_MESSAGE_UNREADABLE");
        }
    }

    private CdsExportHospitalReportMessage() {}

    /**
     * @param message the complete stored message
     * @return its reports and routing
     * @throws Unreadable when it does not parse or validate as an HRM 1.1.2 message
     * @throws IOException when this installation cannot read any HRM message (its bundled schema
     *     or JAXB context is unavailable), which concerns every report, not this one
     */
    static Parsed parse(byte[] message) throws Unreadable, IOException {
        HRMReportParser.requireMessageModel();
        OmdCds root;
        try {
            root = HRMReportParser.unmarshal(new ByteArrayInputStream(message));
        } catch (IOException
                | org.xml.sax.SAXException
                | javax.xml.parsers.ParserConfigurationException
                | jakarta.xml.bind.JAXBException
                | RuntimeException malformed) {
            throw new Unreadable();
        }
        if (root == null || root.getPatientRecord() == null) throw new Unreadable();
        var reports = new ArrayList<Report>();
        for (var report : root.getPatientRecord().getReportsReceived()) reports.add(report(report));
        var transactions = new ArrayList<Transaction>();
        for (TransactionInformation entry : root.getPatientRecord().getTransactionInformation())
            transactions.add(
                    new Transaction(
                            entry.getMessageUniqueID(),
                            entry.getDeliverToUserID(),
                            name(entry.getProvider())));
        return new Parsed(reports, transactions);
    }

    private static Report report(ReportsReceived report) {
        var observations = new ArrayList<Observation>();
        for (var obr : report.getOBRContent())
            observations.add(
                    new Observation(
                            obr.getAccompanyingSubClass(),
                            obr.getAccompanyingMnemonic(),
                            obr.getAccompanyingDescription(),
                            when(obr.getObservationDateTime())));
        var content = report.getContent();
        return new Report(
                report.getMedia() == null ? null : report.getMedia().value(),
                report.getFormat() == null ? null : report.getFormat().value(),
                report.getFileExtensionAndVersion(),
                content != null,
                content == null ? null : content.getTextContent(),
                content == null ? null : content.getMedia(),
                report.getClazz() == null ? null : report.getClazz().value(),
                report.getSubClass(),
                when(report.getEventDateTime()),
                when(report.getReceivedDateTime()),
                when(report.getReviewedDateTime()),
                name(report.getAuthorPhysician()),
                report.getReviewingOHIPPhysicianId(),
                report.getSendingFacility(),
                report.getSendingFacilityReportNumber(),
                observations,
                report.getResultStatus());
    }

    private static Name name(PersonNameSimple name) {
        return name == null ? null : new Name(name.getFirstName(), name.getLastName());
    }

    private static When when(DateFullOrPartial date) {
        if (date == null) return null;
        if (date.getDateTime() != null)
            return new When("DateTime", date.getDateTime().toXMLFormat(), date.getDateTime());
        if (date.getFullDate() != null)
            return new When("FullDate", date.getFullDate().toXMLFormat(), date.getFullDate());
        if (date.getYearMonth() != null)
            return new When("YearMonth", date.getYearMonth().toXMLFormat(), date.getYearMonth());
        if (date.getYearOnly() != null)
            return new When("YearOnly", date.getYearOnly().toXMLFormat(), date.getYearOnly());
        return null;
    }

    /**
     * The CDS 5.2 date of a message date at its own precision, or null when CDS cannot carry it
     * (a year outside 1 to 9999). A date-time with an offset keeps that offset; one without is a
     * clinic wall-clock time, placed in the clinic zone as the other native times are.
     */
    static CdsExportDateDto date(When when, ZoneId zone) throws IOException {
        if (when == null) return null;
        var value = when.value();
        int year = value.getYear();
        if (year == DatatypeConstants.FIELD_UNDEFINED || year < 1 || year > 9999) return null;
        try {
            return switch (when.arm()) {
                case "YearOnly" ->
                        new CdsExportDateDto(
                                CdsExportDateDto.Precision.YEAR, String.format("%04d", year));
                case "YearMonth" ->
                        new CdsExportDateDto(
                                CdsExportDateDto.Precision.YEAR_MONTH,
                                String.format("%04d-%02d", year, value.getMonth()));
                case "FullDate" ->
                        new CdsExportDateDto(
                                CdsExportDateDto.Precision.DATE,
                                String.format(
                                        "%04d-%02d-%02d", year, value.getMonth(), value.getDay()));
                case "DateTime" -> dateTime(value, zone);
                default -> null;
            };
        } catch (DateTimeException | IllegalArgumentException invalid) {
            return null;
        }
    }

    private static CdsExportDateDto dateTime(XMLGregorianCalendar value, ZoneId zone)
            throws IOException {
        int nanos =
                value.getFractionalSecond() == null
                        ? 0
                        : value.getFractionalSecond().movePointRight(9).intValue();
        var local =
                LocalDateTime.of(
                        value.getYear(),
                        value.getMonth(),
                        value.getDay(),
                        value.getHour(),
                        value.getMinute(),
                        value.getSecond() == DatatypeConstants.FIELD_UNDEFINED
                                ? 0
                                : value.getSecond(),
                        nanos);
        if (value.getTimezone() == DatatypeConstants.FIELD_UNDEFINED)
            return CdsExportNativeHistoryMapper.clock(local.toString(), zone);
        return new CdsExportDateDto(
                CdsExportDateDto.Precision.DATE_TIME,
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                        local.atOffset(ZoneOffset.ofTotalSeconds(value.getTimezone() * 60))));
    }
}
