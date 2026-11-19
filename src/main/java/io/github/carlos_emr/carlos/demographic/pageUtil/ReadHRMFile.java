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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Base64;
import java.util.Calendar;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.transform.sax.SAXSource;

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
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;


/**
 * Reads one stored HRM report file for the legacy CDS export.
 *
 * <p>The file is an XML document whose reports can carry their content as Base64 media. When a
 * spool directory is given, that media is never held in memory: as the parser delivers it, it is
 * decoded straight into a file in the spool directory ({@link #getReportMediaFile(int)}), and
 * the object tree keeps an empty array in its place. A chart with many or large HRM reports then
 * costs the export only the report fields and text, not the media of every report at once.
 *
 * <p>A file that cannot be read or parsed is reported by {@link #isUnreadable()}. It used to be
 * logged and treated as a file with no reports, so the export left the report out without saying.
 *
 * @author ronnie
 */
public class ReadHRMFile {
    private List<ReportsReceived> reportsReceived = null;
    private List<TransactionInformation> transactionInformation = null;
    /** Spooled media by report position; a report without media has no entry. */
    private final Map<Integer, File> mediaFiles = new HashMap<>();
    private boolean unreadable;
/*
    private Demographics demographics = null;
    private ReportsReceived[] reportsReceived = null;
    private TransactionInformation[] transactionInformation = null;
 *
 */

    /**
     * Reads the file with its media held in memory.
     *
     * @param hrmFile path of the stored HRM report file
     */
    public ReadHRMFile(String hrmFile) {
        this(hrmFile, null);
    }

    /**
     * Reads the file, decoding each report's media into a file of its own.
     *
     * @param hrmFile path of the stored HRM report file
     * @param mediaSpoolDirectory where to write the decoded media, or {@code null} to keep it in
     *        memory; the caller owns the directory and the files written to it
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path derived from trusted configuration/constant/DB value, not user-controllable input")
    public ReadHRMFile(String hrmFile, File mediaSpoolDirectory) {
        MediaSpool spool = null;
        try {
            if (hrmFile == null) {
                return;
            }
            File hrm = PathValidationUtils.resolveTrustedPath(new File(hrmFile));
            if (!hrm.exists()) {
                return;
            }
            JAXBContext jc = JAXBContext.newInstance("io.github.carlos_emr.carlos.hospitalReportManager.xsd");
            Unmarshaller u = jc.createUnmarshaller();
            try (FileInputStream fis = new FileInputStream(hrm)) {
                SAXSource source = XmlUtils.createSecureJaxbSource(fis);
                if (mediaSpoolDirectory != null) {
                    spool = new MediaSpool(source.getXMLReader(), mediaSpoolDirectory, mediaFiles);
                    source.setXMLReader(spool);
                }
                OmdCds root = (OmdCds) u.unmarshal(source);
                PatientRecord pr = root.getPatientRecord();
                reportsReceived = pr.getReportsReceived();
                transactionInformation = pr.getTransactionInformation();
            }
        } catch (JAXBException ex) {
            unreadable = true;
            MiscUtils.getLogger().error("Failed to parse HRM file", ex);
        } catch (Exception ex) {
            unreadable = true;
            MiscUtils.getLogger().error("Error reading HRM file", ex);
        } finally {
            if (spool != null) {
                spool.closeQuietly();
            }
            if (unreadable) {
                // Nothing read from a file that failed part-way is offered as its content.
                reportsReceived = null;
                transactionInformation = null;
                discardMediaFiles();
            }
        }
    }

    /**
     * @return whether the file exists but could not be read or parsed; its reports are then
     *         unknown, which is not the same as the file holding none
     */
    public boolean isUnreadable() {
        return unreadable;
    }

    /**
     * The decoded media of one report, when this reader was given a spool directory.
     *
     * @param r the report's position in the file
     * @return the file holding the media bytes, or {@code null} when the report has no media or
     *         the media was kept in memory
     */
    public File getReportMediaFile(int r) {
        return mediaFiles.get(r);
    }

    /** Removes every spooled media file this reader still knows of. */
    public void discardMediaFiles() {
        for (File media : mediaFiles.values()) {
            try {
                Files.deleteIfExists(media.toPath());
            } catch (IOException | RuntimeException cleanup) {
                MiscUtils.getLogger().error("HRM media spool file could not be removed: {}", cleanup.getClass().getSimpleName());
            }
        }
        mediaFiles.clear();
    }

    /**
     * Sits between the XML parser and JAXB. The text of each report's {@code Content/Media}
     * element is Base64; it is decoded into a spool file as it arrives and withheld from JAXB,
     * which therefore sees an empty element. Everything else passes through untouched.
     *
     * <p>A report also has a {@code Media} element of its own (how the report arrived), so the
     * element is recognised by its parent, {@code Content}, inside {@code ReportsReceived}.
     */
    private static final class MediaSpool extends XMLFilterImpl {
        /** Base64 characters decoded per write; a multiple of four. */
        private static final int CHUNK = 8192;

        private final File directory;
        private final Map<Integer, File> mediaFiles;
        private final Deque<String> path = new ArrayDeque<>();
        private final byte[] pending = new byte[CHUNK];
        private int pendingLength;
        private int report = -1;
        private OutputStream media;

        MediaSpool(XMLReader parent, File directory, Map<Integer, File> mediaFiles) {
            super(parent);
            this.directory = directory;
            this.mediaFiles = mediaFiles;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            if ("ReportsReceived".equals(localName) && "PatientRecord".equals(path.peek())) {
                report++;
            }
            boolean reportMedia = "Media".equals(localName) && "Content".equals(path.peek()) && report >= 0
                    && path.contains("ReportsReceived");
            path.push(localName);
            if (reportMedia) {
                try {
                    File file = Files.createTempFile(directory.toPath(), "hrm-media-", ".spool").toFile();
                    mediaFiles.put(report, file);
                    media = new BufferedOutputStream(new FileOutputStream(file));
                    pendingLength = 0;
                } catch (IOException | RuntimeException spoolFailure) {
                    throw new SAXException("HRM media could not be spooled", asException(spoolFailure));
                }
            }
            super.startElement(uri, localName, qName, attributes);
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (media == null) {
                super.characters(ch, start, length);
                return;
            }
            try {
                for (int i = start; i < start + length; i++) {
                    char c = ch[i];
                    if (Character.isWhitespace(c)) {
                        continue;
                    }
                    if (c > 0x7f) {
                        throw new IOException("HRM media is not Base64");
                    }
                    pending[pendingLength++] = (byte) c;
                    if (pendingLength == CHUNK) {
                        decodePending();
                    }
                }
            } catch (IOException | RuntimeException spoolFailure) {
                throw new SAXException("HRM media could not be spooled", asException(spoolFailure));
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (media != null && "Media".equals(localName)) {
                try {
                    decodePending();
                    media.close();
                } catch (IOException | RuntimeException spoolFailure) {
                    throw new SAXException("HRM media could not be spooled", asException(spoolFailure));
                } finally {
                    media = null;
                }
            }
            path.pop();
            super.endElement(uri, localName, qName);
        }

        /** Decodes what has been collected; only the last chunk of a value may hold padding. */
        private void decodePending() throws IOException {
            if (pendingLength == 0) {
                return;
            }
            try {
                media.write(Base64.getDecoder().decode(Arrays.copyOf(pending, pendingLength)));
            } catch (IllegalArgumentException notBase64) {
                throw new IOException("HRM media is not Base64", notBase64);
            }
            pendingLength = 0;
        }

        void closeQuietly() {
            if (media != null) {
                try {
                    media.close();
                } catch (IOException | RuntimeException cleanup) {
                    MiscUtils.getLogger().error("HRM media spool could not be closed: {}", cleanup.getClass().getSimpleName());
                }
                media = null;
            }
        }

        private static Exception asException(Throwable failure) {
            return failure instanceof Exception exception ? exception : new IllegalStateException(failure);
        }
    }

    public int getReportsReceivedTotal() {
        if (reportsReceived == null) return 0;
        return reportsReceived.size();
    }

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

    public HashMap<String, Object> getReportContent(int r) {
        HashMap<String, Object> rptContent = new HashMap<String, Object>();
        if (getReportsReceived(r) == null) return rptContent;

        ReportContent content = getReportsReceived(r).getContent();
        if (content == null) return null;

        if (content.getTextContent() != null) rptContent.put("textcontent", content.getTextContent());
        else if (content.getMedia() != null) rptContent.put("media", content.getMedia());

        return rptContent;
    }

    public OBRContent getReportOBRContent(int r, int o) {
        if (getReportsReceived(r) == null) return null;

        List<OBRContent> obr = getReportsReceived(r).getOBRContent();
        if (obr.size() <= r) return null;

        return obr.get(o);
    }

    public int getReportOBRContentTotal(int r) {
        if (getReportsReceived(r) == null) return 0;
        return getReportsReceived(r).getOBRContent().size();
    }

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

    public Calendar getReportOBRObservationDateTime(int r, int o) {
        if (getReportOBRContent(r, o) == null) return null;

        return dateFPtoCal(getReportOBRContent(r, o).getObservationDateTime());
    }

    public String getTransactionMessageUniqueID(int r) {
        if (transactionInformation == null) return null;
        if (transactionInformation.size() <= r) return null;

        return transactionInformation.get(r).getMessageUniqueID();
    }

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
