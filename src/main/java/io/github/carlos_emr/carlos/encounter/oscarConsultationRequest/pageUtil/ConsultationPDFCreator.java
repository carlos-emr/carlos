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

package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import org.openpdf.text.*;
import org.openpdf.text.pdf.*;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.fax.core.FaxRecipient;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.DigitalSignatureManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.clinic.ClinicData;
import io.github.carlos_emr.carlos.prescript.data.RxProviderData;
import io.github.carlos_emr.carlos.prescript.data.RxProviderData.Provider;

import javax.servlet.http.HttpServletRequest;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Generates a formatted PDF document for a clinical consultation request (referral letter).
 *
 * <p>Produces a letter-sized PDF containing a tabular layout with clinic letterhead, specialist
 * and patient information, clinical details (reason for consultation, medications, allergies,
 * concurrent problems), provider digital signature, and referring practitioner / MRP footer.
 * Supports multi-site configurations with per-site logos and letterhead customization.</p>
 *
 * <p>The PDF is built using OpenPDF's table-based layout ({@link PdfPTable}/{@link PdfPCell})
 * and extends {@link PdfPageEventHelper} for page lifecycle participation. Consultation data
 * is loaded via {@link EctConsultationFormRequestUtil} from the request ID parameter.</p>
 *
 * <p>When used for fax transmission, an {@link EctConsultationFaxForm} can be provided to
 * include fax copy-to recipient information in the specialist section.</p>
 *
 * @see ImagePDFCreator
 * @see EctConsultationFormRequestUtil
 * @see EctConsultationFaxForm
 * @since 2012-04-09
 */
public class ConsultationPDFCreator extends PdfPageEventHelper {

    private static Logger logger = MiscUtils.getLogger();
    private OutputStream os;
    private Document document;
    private BaseFont bf;
    private Font font;
    private Font boldFontHeading;
    private Font heading;
    private EctConsultationFormRequestUtil reqFrm;
    private OscarProperties props;
    private ClinicData clinic;
    private ResourceBundle oscarR;
    private EctConsultationFaxForm ectConsultationFaxForm;

    /**
     * Constructs a ConsultationPDFCreator for generating a consultation request PDF.
     *
     * <p>Initializes fonts (Helvetica at 10pt and 12pt), loads consultation request data from
     * the {@code reqId} parameter, and resolves the locale-specific resource bundle for
     * localized field labels.</p>
     *
     * @param request HttpServletRequest containing the {@code reqId} parameter (or attribute)
     *                identifying the consultation request to render
     * @param os      OutputStream where the generated PDF will be written
     */
    public ConsultationPDFCreator(HttpServletRequest request, OutputStream os) {

        // Instantiate dependencies
        try {
            bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            font = new Font(bf, 10, Font.NORMAL);
            // boldFont = new Font(bf, 10, Font.BOLD);
            heading = new Font(bf, 12, Font.NORMAL);
            boldFontHeading = new Font(bf, 12, Font.BOLD);
        } catch (DocumentException e) {
            logger.error("error", e);
        } catch (IOException e) {
            logger.error("error", e);
        }

        this.os = os;
        reqFrm = new EctConsultationFormRequestUtil();
        reqFrm.estRequestFromId(LoggedInInfo.getLoggedInInfoFromSession(request), request.getParameter("reqId") == null ? (String) request.getAttribute("reqId") : request.getParameter("reqId"));
        props = OscarProperties.getInstance();
        clinic = new ClinicData();
        oscarR = ResourceBundle.getBundle("oscarResources", request.getLocale());
    }

    /**
     * Constructs a ConsultationPDFCreator for fax transmission with copy-to recipient details.
     *
     * @param ectConsultationFaxForm EctConsultationFaxForm containing the HTTP request and
     *                               fax-specific data including copy-to recipients
     * @param os                     OutputStream where the generated PDF will be written
     */
    public ConsultationPDFCreator(EctConsultationFaxForm ectConsultationFaxForm, OutputStream os) {
        this(ectConsultationFaxForm.getRequest(), os);
        this.ectConsultationFaxForm = ectConsultationFaxForm;
    }

    /**
     * Generates the consultation request PDF and writes it to the configured output stream.
     *
     * <p>Creates a letter-sized PDF document containing the full consultation request layout:
     * clinic header, specialist/patient details, clinical information, signature, and
     * referring practitioner footer.</p>
     *
     * @param loggedInInfo LoggedInInfo the authenticated session context for the current provider
     * @throws DocumentException when OpenPDF encounters an error constructing the PDF
     */
    public void printPdf(LoggedInInfo loggedInInfo) throws DocumentException {

        // Create the document we are going to write to
        document = new Document();
        PdfWriter.getInstance(document, os);
//		PdfWriterFactory.newInstance( document, os, FontSettings.HELVETICA_10PT );
        document.setPageSize(PageSize.LETTER);
        document.addTitle(getResource("msgConsReq"));
        document.addCreator("CARLOS EMR");
        document.open();

        PdfPTable maintable = createConsultationRequest(loggedInInfo);
        document.add(maintable);
        document.close();
    }

    /**
     * Builds the main table structure containing all sections of the consultation request.
     *
     * <p>Assembles the full-page layout in order: logo/clinic header, consultation request
     * title, date line, reply instructions, specialist and patient info side-by-side,
     * clinical detail sections, digital signature, and referring practitioner/MRP footer.</p>
     *
     * @param loggedInInfo LoggedInInfo the authenticated session context
     * @return PdfPTable the fully assembled consultation request table
     */
    private PdfPTable createConsultationRequest(LoggedInInfo loggedInInfo) {

        float[] tableWidths = {1f, 1f};

        PdfPTable border1, border2;

        // Creating a single column border table for the entire page.
        PdfPTable border = new PdfPTable(1);
        border.setSplitLate(false);
        border.setWidthPercentage(100);

        if (props.getProperty("faxLogoInConsultation") != null) {
            // Creating container for logo and clinic information table.
            border1 = new PdfPTable(tableWidths);
            border1.setSpacingAfter(5f);

            // Adding fax logo
            addToTable(border1, createLogoHeader(), false);

            // Adding clinic information to the border.
            addToTable(border1, createClinicInfoHeader(), false);

            // add the new header table to the main table.
            addTable(border, border1);

        } else {
            // Adding clinic information to the border.
            addTable(border, createClinicInfoHeader());
        }

        //TODO: ADD SWITCHES FOR PDF SEGMENTS BELOW.


        // Add a consultation request header
        addToTable(border, createConsultationRequestHeader(PdfPCell.ALIGN_CENTER), false);

        // Add a date line
        addToTable(border, createDateLine(PdfPCell.ALIGN_RIGHT), false);

        // Add the reply info
        addToTable(border, createReplyHeader(PdfPCell.ALIGN_CENTER), false);

        // Creating container for specialist and patient table.
        border2 = new PdfPTable(tableWidths);

        // Adding specialist info to container.
        addToTable(border2, createSpecialistTable(), false);

        // Adding patient info to main table.
        addToTable(border2, createPatientTable(), new int[]{PdfPCell.LEFT}, null);

        // add the specialist table to the main table.
        addToTable(border, border2, new int[]{PdfPCell.TOP, PdfPCell.BOTTOM}, new int[]{0, 5, 0, 5});

        // Creating a table with details for the consultation request.
        addTable(border, createConsultDetailTable());

        // Add the providers's signature.
        if (getlen(reqFrm.signatureImg) > 0) {
            addSignature(border);
        }

        // Add referring practitioner and mrp details
        addTable(border, createReferringPracAndMRPDetailTable(loggedInInfo));

        return border;
    }

    /**
     * Add's the table 'add' to the table 'main' (with no border surrounding it.)
     *
     * @param main the host table
     * @param add  the table being added
     * @return the cell containing the table being added to the main table.
     */
    private PdfPCell addTable(PdfPTable main, PdfPTable add) {
        return addToTable(main, add, false);
    }

    /**
     * Add's the table 'add' to the table 'main'.
     *
     * @param main   the host table
     * @param add    the table being added
     * @param border true if a border should surround the table being added
     * @return the cell containing the table being added to the main table.	 *
     */
    private PdfPCell addToTable(PdfPTable main, PdfPTable add, boolean border) {

        // 0 is no border.
        int[] borderarray = new int[]{0};

        if (border) {
            borderarray = new int[]{PdfPCell.LEFT, PdfPCell.TOP, PdfPCell.RIGHT, PdfPCell.BOTTOM};
        }

        return addToTable(main, add, borderarray, null);
    }

    /**
     * Method above sets a full border based on a boolean switch.
     * This method allows the setting of individual boarders in a Rectangle.
     * <p>
     * borderarray is set with PdfPCell border location enumerators
     * paddingarray is set similar to CSS clockwise
     */
    private PdfPCell addToTable(PdfPTable main, PdfPTable add, int[] borderarray, int[] paddingarray) {

        PdfPCell cell = new PdfPCell(add);
        cell.setBorder(0);

        for (int border : borderarray) {
            if (border > 0) {
                cell.enableBorderSide(border);
            }
        }

        if (paddingarray != null) {
            cell.setPaddingLeft(paddingarray[0]);
            cell.setPaddingTop(paddingarray[1]);
            cell.setPaddingRight(paddingarray[2]);
            cell.setPaddingBottom(paddingarray[3]);
        }

        cell.setColspan(1);
        main.addCell(cell);
        main.getNumberOfColumns();

        return cell;
    }

    /**
     * Creates a single-row table displaying the referral date or "Patient Will Book" indicator.
     *
     * @param alignment Integer horizontal alignment constant (e.g., {@link PdfPCell#ALIGN_RIGHT})
     * @return PdfPTable containing the formatted date line
     */
    protected PdfPTable createDateLine(Integer alignment) {

        PdfPTable datelineborder = new PdfPTable(1);
        datelineborder.setWidthPercentage(100f);
        PdfPCell datecell = new PdfPCell();
        datecell.setPhrase(new Phrase(String.format("%s %s", getResource("msgDate"), reqFrm.pwb.equals("1") ? getResource("pwb") : reqFrm.referalDate)));
        datecell.setBorder(0);
        datecell.setColspan(1);
        datecell.setPaddingTop(5f);
        datecell.setPaddingBottom(5f);

        if (alignment != null) {
            datecell.setHorizontalAlignment(alignment);
        }

        datelineborder.addCell(datecell);

        return datelineborder;
    }

    /**
     * Creates the "Consultation Request" heading row.
     *
     * @param alignment Integer horizontal alignment constant (e.g., {@link PdfPCell#ALIGN_CENTER})
     * @return PdfPTable containing the bold heading text
     */
    protected PdfPTable createConsultationRequestHeader(Integer alignment) {

        PdfPTable headerborder = new PdfPTable(1);
        headerborder.setWidthPercentage(100f);
        PdfPCell headercell = new PdfPCell();
        headercell.setPhrase(new Phrase(getResource("consultationRequestHeader"), boldFontHeading));
        headercell.setBorder(0);
        headercell.setColspan(1);
        headercell.setPaddingTop(5f);
        headercell.setPaddingBottom(5f);

        if (alignment != null) {
            headercell.setHorizontalAlignment(alignment);
        }

        headerborder.addCell(headercell);

        return headerborder;
    }

    /**
     * @deprecated use the createLogo method in the ClinicLogoUtility at io.github.carlos_emr.carlos.utility
     */
    @Deprecated
    private PdfPTable createLogoHeader() {

        PdfPTable infoTable = new PdfPTable(1);

        String filename = "";
        if (props.getProperty("multisites") != null && "on".equalsIgnoreCase(props.getProperty("multisites"))) {
            DocumentDao documentDao = (DocumentDao) SpringUtils.getBean(DocumentDao.class);
            SiteDao siteDao = (SiteDao) SpringUtils.getBean(SiteDao.class);
            Site site = siteDao.getById(Integer.valueOf(reqFrm.siteName));
            if (site != null) {
                if (site.getSiteLogoId() != null) {
                    io.github.carlos_emr.carlos.commn.model.Document d = documentDao.getDocument(String.valueOf(site.getSiteLogoId()));
                    String dir = props.getProperty("DOCUMENT_DIR");
                    filename = dir.concat(d.getDocfilename());
                } else {
                    //If no logo file uploaded for this site, use the default one defined in carlos properties file.
                    filename = props.getProperty("faxLogoInConsultation");
                }
            }
        } else {
            filename = props.getProperty("faxLogoInConsultation");
        }

        Path path = Paths.get(filename);
        if (Files.exists(path)) {
            addImage(infoTable, filename, PageSize.LETTER.getWidth() * 0.5f, 50f);
        }
        return infoTable;

    }

    /**
     * Reads an image file and adds it as a scaled element to the given PDF table.
     *
     * @param pdfPTable PdfPTable the table to add the image cell to
     * @param filename  String absolute path to the image file
     * @param width     float maximum width in points to scale the image to
     * @param height    float maximum height in points to scale the image to
     */
    protected void addImage(PdfPTable pdfPTable, String filename, float width, float height) {

        try (FileInputStream fileInputStream = new FileInputStream(filename)) {

            PdfPCell cell = new PdfPCell();
            byte[] faxLogImage = new byte[1024 * 256];
            fileInputStream.read(faxLogImage);
            Image image = Image.getInstance(faxLogImage);
            image.scaleToFit(width, height);
            image.setBorder(0);
            cell.addElement(image);
            cell.setPadding(0);
            cell.setBorder(0);
            pdfPTable.addCell(cell);

        } catch (FileNotFoundException e) {
            logger.error("Failed to locate file at " + filename, e);
        } catch (BadElementException e) {
            logger.error("Unexpected error.", e);
        } catch (MalformedURLException e) {
            logger.error("This image location is malformed " + filename, e);
        } catch (IOException e) {
            logger.error("Unexpected error.", e);
        }
    }

    /**
     * Creates the reply instruction header based on booking configuration.
     *
     * <p>Displays different messages depending on whether the patient will book their own
     * appointment, custom appointment instructions are configured, or multi-site mode is
     * active. Falls back to the standard "Please reply to [clinic name]" message.</p>
     *
     * @param alignment Integer horizontal alignment constant
     * @return PdfPTable containing the reply instruction header
     */
    protected PdfPTable createReplyHeader(Integer alignment) {

        PdfPTable infoTable = new PdfPTable(1);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(0);
        cell.setPadding(0);
        cell.setPaddingTop(10f);
        cell.setPaddingBottom(10f);

        cell.setHorizontalAlignment(alignment);

        cell.setPhrase(new Phrase(getResource("msgConsReq"), boldFontHeading));

        if ("1".equals(reqFrm.pwb)) {
            cell.setPhrase(new Phrase(getResource("msgPleaseReplyPatient"), boldFontHeading));
        }

        // If not set to Patient Will Book then maybe a Custom Appointment Instruction is used.
        else if (OscarProperties.getInstance().getBooleanProperty("CONSULTATION_APPOINTMENT_INSTRUCTIONS_LOOKUP", "true")) {
            cell.setPhrase(new Phrase(reqFrm.getAppointmentInstructionsLabel(), boldFontHeading));
        } else if (IsPropertiesOn.isMultisitesEnable()) {
            cell.setPhrase(new Phrase("Please reply", boldFontHeading));
        } else {
            cell.setPhrase(new Phrase(
                    String.format("%s %s %s", getResource("msgPleaseReplyPart1"),
                            clinic.getClinicName(),
                            getResource("msgPleaseReplyPart2")), boldFontHeading));
        }
        infoTable.addCell(cell);

        //TODO: ADD SETTINGS TO MANAGE HOW AND WHEN THE REPLY HEADING IS DISPLAYED.

        return infoTable;
    }

    /**
     * Creates a table and populates it with the clinic information for the header.
     *
     * @return the table produced
     */
    private PdfPTable createClinicInfoHeader() {

        String letterheadName = null;
        if (reqFrm.letterheadName != null && reqFrm.letterheadName.startsWith("prog_")) {

            ProgramDao programDao = SpringUtils.getBean(ProgramDao.class);
            Integer programNo = Integer.parseInt(reqFrm.letterheadName.substring(5));
            letterheadName = programDao.getProgramName(programNo);

        } else if (!"-1".equals(reqFrm.letterheadName) && !reqFrm.letterheadName.equals(clinic.getClinicName())) {

            Provider letterheadNameProvider = null;

            if (reqFrm.letterheadName != null) {
                letterheadNameProvider = new RxProviderData().getProvider(reqFrm.letterheadName);
            }

            if (letterheadNameProvider != null && letterheadNameProvider.getSurname() != null) {

                String firstName = "";

                if (reqFrm.letterheadTitle != null && reqFrm.letterheadTitle.equals("Dr")) {
                    firstName = letterheadNameProvider.getFirstName();
                } else {
                    firstName = letterheadNameProvider.getFirstName().replace("Dr. ", "");
                }

                letterheadName = firstName + " " + letterheadNameProvider.getSurname();

            } else {
                letterheadName = clinic.getClinicName();
            }

        } else {
            letterheadName = clinic.getClinicName();
        }

        PdfPTable infoTable = new PdfPTable(1);
        PdfPCell cell = new PdfPCell(new Phrase(letterheadName));
        cell.setBorder(0);
        cell.setPadding(0);
        infoTable.addCell(cell);

        // add the address details
        Phrase addressPhrase = new Phrase("");
        if (reqFrm.letterheadAddress != null && reqFrm.letterheadAddress.trim().length() > 0) {
            addressPhrase.add(reqFrm.getLetterheadAddress());
        } else {
            addressPhrase.add(clinic.getClinicAddress());

            addressPhrase.add(String.format("%s, %s. %s", clinic.getClinicCity(),
                    clinic.getClinicProvince(),
                    clinic.getClinicPostal()));
        }

        cell.setPhrase(addressPhrase);
        infoTable.addCell(cell);

        // add the telecom info
        Phrase telecomPhrase = new Phrase("");
        if (reqFrm.letterheadPhone != null && reqFrm.letterheadPhone.trim().length() > 0) {
            telecomPhrase.add(String.format("Phone: %s", reqFrm.getLetterheadPhone()));
        } else {
            telecomPhrase.add(String.format("Phone: %s", clinic.getClinicPhone()));
        }

        if (reqFrm.letterheadFax != null && reqFrm.letterheadFax.trim().length() > 0) {
            telecomPhrase.add(String.format(" Fax: %s", reqFrm.getLetterheadFax()));
        } else {
            telecomPhrase.add(String.format(" Fax: %s", clinic.getClinicFax()));
        }

        cell.setPhrase(telecomPhrase);
        infoTable.addCell(cell);


        return infoTable;
    }

    /**
     * Creates the table containing information about the specialist.
     *
     * @return the table produced
     */
    private PdfPTable createSpecialistTable() {
        float[] tableWidths = new float[]{1.5f, 2.5f};
        PdfPCell cell = new PdfPCell();
        cell.setPadding(0);
        PdfPTable infoTable = new PdfPTable(tableWidths);
        ProfessionalSpecialist professionalSpecialist = reqFrm.getProfessionalSpecialist();

        infoTable.addCell(setInfoCell(cell, getResource("msgConsultant")));

        // specialist name
        if (professionalSpecialist != null) {
            infoTable.addCell(setDataCell(cell, professionalSpecialist.getFormattedTitle()));
        } else {
            infoTable.addCell(setDataCell(cell, ""));
        }

        infoTable.addCell(setInfoCell(cell, getResource("msgUrgency")));
        infoTable.addCell(setDataCell(cell, (reqFrm.urgency.equals("1") ? getResource("msgUrgent") :
                (reqFrm.urgency.equals("2") ? getResource("msgNUrgent") :
                        (reqFrm.urgency.equals("3")) ? getResource("msgReturn")
                                : "  "))));

        infoTable.addCell(setInfoCell(cell, getResource("msgService")));
        infoTable.addCell(setDataCell(cell, reqFrm.getServiceName(reqFrm.service)));

        infoTable.addCell(setInfoCell(cell, getResource("msgPhone")));
        if (professionalSpecialist != null) {
            infoTable.addCell(setDataCell(cell, professionalSpecialist.getPhoneNumber()));
        } else {
            infoTable.addCell(setDataCell(cell, ""));
        }

        infoTable.addCell(setInfoCell(cell, getResource("msgFax")));

        if (professionalSpecialist != null) {
            infoTable.addCell(setDataCell(cell, professionalSpecialist.getFaxNumber()));
        } else {
            infoTable.addCell(setDataCell(cell, ""));
        }

        infoTable.addCell(setInfoCell(cell, getResource("msgAddr")));

        if (professionalSpecialist != null) {
            infoTable.addCell(setDataCell(cell, divy(professionalSpecialist.getStreetAddress())));
        } else {
            infoTable.addCell(setDataCell(cell, ""));
        }

        // Adding fax copy-to information; if any.
        if (ectConsultationFaxForm != null) {

            infoTable.addCell(setInfoCell(cell, " "));
            infoTable.addCell(setDataCell(cell, " "));

            infoTable.addCell(setInfoCell(cell, "Fax Copy(s) to:"));
            infoTable.addCell(setDataCell(cell, ""));

            Set<FaxRecipient> copiedTo = ectConsultationFaxForm.getCopiedTo();

            for (FaxRecipient faxRecipient : copiedTo) {
                infoTable.addCell(setDataCell(cell, faxRecipient.getFax()));
                infoTable.addCell(setInfoCell(cell, faxRecipient.getName()));
            }
        }

        return infoTable;
    }

    /**
     * Creates the table containing information about the patient.
     *
     * @return the table produced
     */
    private PdfPTable createPatientTable() {

        float[] tableWidths = new float[]{2, 2.5f};
        PdfPTable infoTable = new PdfPTable(tableWidths);
        PdfPCell cell = new PdfPCell();
        cell.setPadding(0);
        cell.setPaddingLeft(10f);

        infoTable.addCell(setInfoCell(cell, getResource("msgPat")));
        infoTable.addCell(setDataCell(cell, reqFrm.patientName));

        infoTable.addCell(setInfoCell(cell, getResource("msgAddr")));
        infoTable.addCell(setDataCell(cell, divy(reqFrm.patientAddress)));

        infoTable.addCell(setInfoCell(cell, getResource("msgPhone")));
        infoTable.addCell(setDataCell(cell, reqFrm.patientPhone));

        infoTable.addCell(setInfoCell(cell, getResource("msgCellPhone")));
        infoTable.addCell(setDataCell(cell, reqFrm.patientCellPhone));

        infoTable.addCell(setInfoCell(cell, getResource("msgWPhone")));
        infoTable.addCell(setDataCell(cell, reqFrm.patientWPhone));

        infoTable.addCell(setInfoCell(cell, getResource("msgEmail")));
        infoTable.addCell(setDataCell(cell, reqFrm.patientEmail));

        infoTable.addCell(setInfoCell(cell, getResource("msgBirth")));
        infoTable.addCell(setDataCell(cell, reqFrm.patientDOB + " (y/m/d)"));

        infoTable.addCell(setInfoCell(cell, getResource("msgSex")));
        infoTable.addCell(setDataCell(cell, reqFrm.patientSex));

        infoTable.addCell(setInfoCell(cell, getResource("msgCard")));
        infoTable.addCell(setDataCell(cell, String.format("(%s) %s %s", reqFrm.patientHealthCardType,
                reqFrm.patientHealthNum,
                reqFrm.patientHealthCardVersionCode)));

        if (!reqFrm.pwb.equals("1")) {
            infoTable.addCell(setInfoCell(cell, getResource("msgappDate")));
            infoTable.addCell(setDataCell(cell, reqFrm.pwb.equals("1") ? getResource("pwb") : reqFrm.appointmentDate));
            infoTable.addCell(setInfoCell(cell, getResource("msgTime")));
            infoTable.addCell(setDataCell(cell, String.format("%s%s%s %s", reqFrm.appointmentHour,
                    !reqFrm.appointmentMinute.equals("") ? ":" : "",
                    reqFrm.appointmentMinute,
                    reqFrm.appointmentPm)));
        }

        infoTable.addCell(setInfoCell(cell, getResource("msgChart")));
        infoTable.addCell(setDataCell(cell, reqFrm.patientChartNo));

        return infoTable;
    }

    /**
     * Creates the table containing additional information
     * about the reason for the consultation request.
     *
     * @return the table produced
     */
    private PdfPTable createConsultDetailTable() {
        PdfPTable infoTable = new PdfPTable(1);
        PdfPCell cell = new PdfPCell();
        cell.setPadding(0);
        cell.setPaddingTop(10f);
        cell.setPaddingBottom(5f);

        infoTable.addCell(setDataCell(cell, reqFrm.reasonForConsultation));

        if (getlen(reqFrm.clinicalInformation) > 1) {
            infoTable.addCell(setInfoCell(cell, getResource("msgClinicalInfom"), boldFontHeading));
            infoTable.addCell(setDataCell(cell, divy(reqFrm.clinicalInformation)));
        }

        if (getlen(reqFrm.concurrentProblems) > 0) {
            if (props.getProperty("significantConcurrentProblemsTitle", "")
                    .length() > 1) {
                infoTable.addCell(setInfoCell(cell, props.getProperty("significantConcurrentProblemsTitle", ""), boldFontHeading));
            } else {
                infoTable.addCell(setInfoCell(cell, getResource("msgSigProb"), boldFontHeading));
            }
            infoTable.addCell(setDataCell(cell, divy(reqFrm.concurrentProblems)));
        }

        if (getlen(reqFrm.currentMedications) > 1) {
            if (props.getProperty("currentMedicationsTitle", "").length() > 1) {
                infoTable.addCell(setInfoCell(cell, props.getProperty("currentMedicationsTitle", ""), boldFontHeading));
            } else {
                infoTable.addCell(setInfoCell(cell, getResource("msgCurrMed"), boldFontHeading));
            }
            infoTable.addCell(setDataCell(cell, divy(reqFrm.currentMedications)));
        }

        if (getlen(reqFrm.allergies) > 1) {
            infoTable.addCell(setInfoCell(cell, getResource("msgAllergies"), boldFontHeading));
            infoTable.addCell(setDataCell(cell, divy(reqFrm.allergies)));
        }

        return infoTable;
    }

    /**
     * Adds the provider's digital signature image to the PDF if available.
     *
     * <p>Retrieves the {@link DigitalSignature} by ID from the consultation request form data,
     * renders it as a scaled image, and appends it to the main table with a "Signature:" label.</p>
     *
     * @param pdfPTable PdfPTable the main consultation request table to append the signature to
     */
    private void addSignature(PdfPTable pdfPTable) {
        DigitalSignature digitalSignature = null;
        String signatureImageId = reqFrm.getSignatureImg();

        if (signatureImageId != null && !signatureImageId.isEmpty()) {
            /*
             *  This is not the preferred way to handle a potential NFE. Unfortunately
             *  this entire thread was not designed well from the beginning.
             *  Now maintainers are required to insert
             *  odd patches in order to save valuable time on a full refactor.
             */
            try {
				DigitalSignatureManager digitalSignatureManager = SpringUtils.getBean(DigitalSignatureManager.class);
				digitalSignature = digitalSignatureManager.getDigitalSignature(Integer.parseInt(signatureImageId));
            } catch (Exception e) {
                // do nothing
                logger.warn("Consultation digital signature {} was not found or the identifier was incorrect", signatureImageId);
            }
        }

        if (digitalSignature != null) {
            float[] tableWidths = new float[]{0.55f, 2.75f};
            PdfPTable table = new PdfPTable(tableWidths);
            PdfPCell cell = new PdfPCell();

            cell.setPhrase(new Phrase(getResource("msgSignature")));
            cell.setBorder(0);
            cell.setPadding(0);
            cell.setPaddingTop(10f);
            cell.setHorizontalAlignment(PdfPCell.ALIGN_BOTTOM);
            cell.setVerticalAlignment(PdfPCell.ALIGN_LEFT);
            table.addCell(cell);

            try {
                Image image = Image.getInstance(digitalSignature.getSignatureImage());
                image.scalePercent(80f);
                image.setBorder(0);
                cell = new PdfPCell(image);
                cell.setBorder(0);
                cell.setPadding(0);
                cell.setPaddingTop(10f);
                table.addCell(cell);
            } catch (BadElementException | IOException e) {
                logger.error("An error occurred while trying to create an image from the signature", e);
            }

            addTable(pdfPTable, table);
        }
    }

    /**
     * Creates the footer table showing the referring practitioner and Most Responsible Provider (MRP).
     *
     * <p>Display of each field is controlled by the {@code printPDF_referring_prac} and
     * {@code mrp_model} properties respectively. Provider OHIP numbers are appended
     * in parentheses when available.</p>
     *
     * @param loggedInInfo LoggedInInfo the authenticated session context for demographic lookup
     * @return PdfPTable a two-column table with referring practitioner and family doctor details
     */
    private PdfPTable createReferringPracAndMRPDetailTable(LoggedInInfo loggedInInfo) {
        ProviderDao proDAO = (ProviderDao) SpringUtils.getBean(ProviderDao.class);
        io.github.carlos_emr.carlos.commn.model.Provider pro = proDAO.getProvider(reqFrm.providerNo);
        DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
        Demographic demo = demographicManager.getDemographic(loggedInInfo, Integer.parseInt(reqFrm.demoNo));
        String ohipNo = pro.getOhipNo();
        String famDocOhipNo = "";
        if (demo.getProviderNo() != null && !demo.getProviderNo().equals("")) {
            pro = proDAO.getProvider(demo.getProviderNo());
            famDocOhipNo = pro.getOhipNo();
        }

        PdfPTable table = new PdfPTable(new float[]{1.0f, 1.0f});
        PdfPCell cell = new PdfPCell();
        if (props.getBooleanProperty("printPDF_referring_prac", "yes")) {
            table.addCell(setFooterCell(cell, getResource("msgAssociated2"), reqFrm.getProviderName(reqFrm.providerNo) + ((getlen(ohipNo) > 0) ? " (" + ohipNo + ")" : "")));
        } else {
            table.addCell(setFooterCell(cell, "", ""));
        }

        if (props.getBooleanProperty("mrp_model", "yes")) {
            table.addCell(setFooterCell(cell, getResource("msgFamilyDoc2"), reqFrm.getFamilyDoctor() + ((getlen(famDocOhipNo) > 0) ? " (" + famDocOhipNo + ")" : "")));
        } else {
            table.addCell(setFooterCell(cell, "", ""));
        }

        return table;
    }

    /**
     * Formats a cell to display information
     *
     * @param cell   the cell to format
     * @param phrase the information to display
     * @return the formatted cell
     */
    private PdfPCell setDataCell(PdfPCell cell, String phrase) {
        return setInfoCell(cell, phrase);
    }

    /**
     * Formats a cell to display information
     */
    private PdfPCell setInfoCell(PdfPCell cell, String phrase) {
        return setInfoCell(cell, phrase, null);
    }

    /**
     * Configures a cell with the given text and font, removing all borders.
     *
     * @param cell   PdfPCell the cell to configure
     * @param phrase String the text content (null-safe, defaults to empty string)
     * @param font   Font the font to use, or null to use the default 10pt Helvetica
     * @return PdfPCell the configured cell
     */
    private PdfPCell setInfoCell(PdfPCell cell, String phrase, Font font) {

        if (phrase == null) {
            phrase = "";
        }

        Phrase phraseObj = new Phrase(phrase, font != null ? font : this.font);
        cell.setPhrase(phraseObj);
        cell.setBorder(0);
        return cell;
    }

    /**
     * Formats a cell to display on the same line both the information style (larger text followed by colon) and
     * the data in a normal font on the same line.
     */
    private PdfPCell setFooterCell(PdfPCell cell, String info, String data) {
        String suffix = (getlen(data) > 0 && data.endsWith(":")) ? ":" : "";
        return setInfoCell(cell, String.format("%s%s %s", info, suffix, data));
    }

    /**
     * Returns a Consultation Request localized value for the key provided.
     *
     * @param key the key to reference
     * @return the value for the key provided
     */
    private String getResource(String key) {
        return oscarR.getString("oscarEncounter.oscarConsultationRequest.consultationFormPrint." + key);
    }


    /**
     * Returns the length of the string provided and 0 if the string is null.
     *
     * @param str the string to check
     * @return the length of str
     */
    private int getlen(String str) {
        if (str == null) {
            return 0;
        }
        return str.length();
    }

    /**
     * Converts breaking lines and non-breaking spaces to their
     * appropriate equivalents for displaying text in the PDF.
     *
     * @param str the string to modify
     * @return the original string with all breaking lines replaced by '\n' and all non-breaking spaces replaced by ' '
     */
    private String divy(String str) {
        if (str == null) {
            return "";
        }
        str = str.replaceAll("<\\s*br\\s*/?>", "\n");
        str = str.replaceAll("&nbsp;", " ");
        return str;
    }
}
