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

import io.github.carlos_emr.carlos.commn.printing.PdfFonts;

import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import org.openpdf.text.*;
import org.openpdf.text.pdf.*;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.fax.core.FaxRecipient;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.DigitalSignatureManager;
import io.github.carlos_emr.carlos.managers.ConsultationSignatureService;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.ClinicLogoUtility;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.clinic.ClinicData;
import io.github.carlos_emr.carlos.prescript.data.RxProviderData;
import io.github.carlos_emr.carlos.prescript.data.RxProviderData.Provider;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

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
 * <p>Two paths share one layout. The request path reads the request named by the request
 * ({@link #ConsultationPDFCreator(HttpServletRequest, OutputStream)}) and looks up the letterhead,
 * clinic, specialist, providers, signature and logo as it always has. The request-free path
 * ({@link #ConsultationPDFCreator(ConsultationLetterDto, OutputStream)}) prints a letter whose
 * values the caller already resolved, with no request, session or lookup, and {@link
 * #printLetter()} reports a requested signature or logo it could not include ({@link Omission})
 * instead of leaving it out silently. Both build a {@link ConsultationLetterDto} and draw it the
 * same way.</p>
 *
 * @see ImagePDFCreator
 * @see EctConsultationFormRequestUtil
 * @see EctConsultationFaxForm
 * @since 2012-04-09
 */
public class ConsultationPDFCreator extends PdfPageEventHelper {

    private static final float SIGNATURE_IMAGE_SCALE_PERCENT = 40f;
    private static final float SIGNATURE_IMAGE_MAX_WIDTH = 200f;
    private static final float SIGNATURE_IMAGE_MAX_HEIGHT = 60f;
    private static final float[] SIGNATURE_ROW_WIDTHS = new float[]{1.55f, 1.0f};
    private static final float SIGNATURE_TOP_PADDING = 10f;
    private static final float SIGNATURE_LABEL_BOTTOM_PADDING = 2f;

    private static Logger logger = MiscUtils.getLogger();
    private OutputStream os;
    private Document document;
    private BaseFont bf;
    private Font font;
    private Font boldFontHeading;
    private Font heading;
    private EctConsultationFormRequestUtil reqFrm;
    private byte[] signatureImageOverride;
    private boolean suppressSignature;
    private CarlosProperties props;
    private ClinicData clinic;
    private ResourceBundle oscarR;
    private EctConsultationFaxForm ectConsultationFaxForm;
    private ConsultationLetterDto letter;
    private final Set<Omission> omissions = EnumSet.noneOf(Omission.class);

    /** A part of the letter that was requested but could not be printed. */
    public enum Omission {
        /** The letter carries a signature, but its image was missing or unreadable. */
        SIGNATURE,
        /** The clinic prints a logo on consultation letters, but it was missing or unreadable. */
        LOGO
    }

    /**
     * Constructs a ConsultationPDFCreator for generating a consultation request PDF.
     *
     * <p>Initializes embedded Unicode fonts (10pt and 12pt), loads consultation request data from
     * the {@code reqId} parameter, and resolves the locale-specific resource bundle for
     * localized field labels.</p>
     *
     * @param request HttpServletRequest containing the {@code reqId} parameter (or attribute)
     *                identifying the consultation request to render
     * @param os      OutputStream where the generated PDF will be written
     */
    public ConsultationPDFCreator(HttpServletRequest request, OutputStream os) {

        try {
            bf = PdfFonts.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            font = new Font(bf, 10, Font.NORMAL);
            heading = new Font(bf, 12, Font.NORMAL);
            boldFontHeading = new Font(bf, 12, Font.BOLD);
        } catch (DocumentException | IOException e) {
            throw new RuntimeException("Cannot create consultation PDF fonts", e);
        }

        this.os = os;
        reqFrm = new EctConsultationFormRequestUtil();
        // THE ATTRIBUTE WINS. Every server-side caller sets "reqId" from an id it has already
        // authorized for this provider and demographic (EctConsultationFormRequest2Action:997 and
        // its siblings). Reading the request PARAMETER first, as this did, let a POST that passed
        // verification on its own requestId name a different consultation in reqId and have that
        // record rendered instead -- another patient's referral in the returned PDF. The parameter
        // remains as a fallback for callers that only have one, and nothing in the webapp passes it.
        Object authorizedRequestId = request.getAttribute("reqId");
        reqFrm.estRequestFromId(LoggedInInfo.getLoggedInInfoFromSession(request),
                authorizedRequestId != null ? (String) authorizedRequestId : request.getParameter("reqId"));
        // The Print button previews work in progress: it POSTs the whole form by AJAX so the
        // clinician keeps their edits and stays on the page. Without this the preview rendered the
        // stored record and the typed text was simply missing from it (issue #3721). Opt-in, and
        // only for the clinical fields the clinician types here -- see ConsultationPreviewOverlay.
        if (ConsultationPreviewOverlay.requested(request)) {
            ConsultationPreviewOverlay.apply(reqFrm, request,
                    ConsultationPreviewOverlay.appointmentInstructionLabelResolver(
                            LoggedInInfo.getLoggedInInfoFromSession(request)));
        }
        Object signatureOverride = request.getAttribute(ConsultationSignatureService.SIGNATURE_IMAGE_OVERRIDE_ATTRIBUTE);
        if (signatureOverride instanceof byte[] byteArray) {
            signatureImageOverride = byteArray;
        }
        suppressSignature = Boolean.TRUE.equals(request.getAttribute(ConsultationSignatureService.SUPPRESS_SIGNATURE_ATTRIBUTE));
        props = CarlosProperties.getInstance();
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
     * Constructs a creator for a letter whose values are already resolved: no HTTP request,
     * session, Spring bean or file is read. Use {@link #printLetter()} to print it.
     *
     * @param letter the letter to print, labels in its locale
     * @param os     OutputStream where the generated PDF will be written
     */
    public ConsultationPDFCreator(ConsultationLetterDto letter, OutputStream os) {
        try {
            bf = PdfFonts.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            font = new Font(bf, 10, Font.NORMAL);
            heading = new Font(bf, 12, Font.NORMAL);
            boldFontHeading = new Font(bf, 12, Font.BOLD);
        } catch (DocumentException | IOException e) {
            throw new RuntimeException("Cannot create consultation PDF fonts", e);
        }
        this.os = os;
        this.letter = Objects.requireNonNull(letter);
        oscarR = ResourceBundle.getBundle("oscarResources", letter.locale());
    }

    /**
     * Prints a request-free letter.
     *
     * @return the requested parts that could not be printed (empty when the letter is complete)
     * @throws DocumentException when OpenPDF cannot build the PDF
     * @throws IllegalStateException when this creator was made from a request
     */
    public Set<Omission> printLetter() throws DocumentException {
        if (reqFrm != null || letter == null) {
            throw new IllegalStateException("A request letter is printed with printPdf");
        }
        printPdf(null);
        return omissions();
    }

    /** The requested parts the last print could not include. */
    public Set<Omission> omissions() {
        return Collections.unmodifiableSet(EnumSet.copyOf(omissions));
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

        omissions.clear();
        // Create the document we are going to write to
        document = new Document();
        PdfWriter.getInstance(document, os);
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

        // The request path resolves its letter here, where it used to start its lookups.
        if (reqFrm != null) {
            letter = requestLetter(loggedInInfo);
        }

        float[] tableWidths = {1f, 1f};

        PdfPTable border1, border2;

        // Creating a single column border table for the entire page.
        PdfPTable border = new PdfPTable(1);
        border.setSplitLate(false);
        border.setWidthPercentage(100);

        if (letter.images().logoRequested()) {
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

        // Add the provider's signature.
        if (letter.images().signatureRequested()) {
            addSignature(border);
        }

        // Add referring practitioner and mrp details
        addTable(border, createReferringPracAndMRPDetailTable());

        return border;
    }

    /**
     * Resolves the request path's letter with exactly the lookups this creator always made while
     * drawing: the letterhead name (program, provider or clinic), the clinic, the specialist and
     * service, the configured titles, the signature (only when it will print), the logo file and
     * the referring practitioner and family doctor with their billing numbers.
     */
    private ConsultationLetterDto requestLetter(LoggedInInfo loggedInInfo) {
        boolean logoRequested = props.getProperty("faxLogoInConsultation") != null;
        boolean signatureRequested = shouldRenderSignature(suppressSignature, signatureImageOverride, reqFrm.signatureImg);
        byte[] signature = null;
        if (signatureRequested) {
            // Skip the stored-signature lookup when override bytes are already present.
            DigitalSignatureManager digitalSignatureManager =
                    signatureImageOverride != null && signatureImageOverride.length > 0
                            ? null
                            : SpringUtils.getBean(DigitalSignatureManager.class);
            signature = resolveSignatureBytes(signatureImageOverride, reqFrm.getSignatureImg(), digitalSignatureManager);
        }
        ProfessionalSpecialist professionalSpecialist = reqFrm.getProfessionalSpecialist();
        ConsultationLetterDto.Specialist specialist = professionalSpecialist == null ? null
                : new ConsultationLetterDto.Specialist(professionalSpecialist.getFormattedTitle(),
                        professionalSpecialist.getPhoneNumber(), professionalSpecialist.getFaxNumber(),
                        professionalSpecialist.getStreetAddress());

        ProviderDao proDAO = (ProviderDao) SpringUtils.getBean(ProviderDao.class);
        // providerNo is nullable on consultationRequests, and either provider number can
        // reference a since-removed provider row. A missing provider must print without
        // an OHIP suffix, not NPE the whole consultation PDF out of print/preview/fax.
        io.github.carlos_emr.carlos.commn.model.Provider pro =
                reqFrm.providerNo != null ? proDAO.getProvider(reqFrm.providerNo) : null;
        DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
        Demographic demo = demographicManager.getDemographic(loggedInInfo, Integer.parseInt(reqFrm.demoNo));
        String ohipNo = pro != null ? pro.getOhipNo() : "";
        String famDocOhipNo = "";
        if (demo.getProviderNo() != null && !demo.getProviderNo().equals("")) {
            pro = proDAO.getProvider(demo.getProviderNo());
            famDocOhipNo = pro != null ? pro.getOhipNo() : "";
        }
        boolean printReferring = props.getBooleanProperty("printPDF_referring_prac", "yes");
        boolean printFamilyDoctor = props.getBooleanProperty("mrp_model", "yes");

        return new ConsultationLetterDto(
                oscarR.getLocale(),
                new ConsultationLetterDto.Letterhead(resolveLetterheadName(), reqFrm.letterheadAddress,
                        reqFrm.letterheadPhone, reqFrm.letterheadFax, clinic.getClinicName(),
                        clinic.getClinicAddress(), clinic.getClinicCity(), clinic.getClinicProvince(),
                        clinic.getClinicPostal(), clinic.getClinicPhone(), clinic.getClinicFax()),
                new ConsultationLetterDto.Request(reqFrm.pwb, reqFrm.referalDate, reqFrm.urgency,
                        reqFrm.getServiceName(reqFrm.service), reqFrm.getAppointmentInstructionsLabel(),
                        CarlosProperties.getInstance().getBooleanProperty("CONSULTATION_APPOINTMENT_INSTRUCTIONS_LOOKUP", "true"),
                        IsPropertiesOn.isMultisitesEnable()),
                specialist,
                new ConsultationLetterDto.Patient(reqFrm.patientName, reqFrm.patientAddress, reqFrm.patientPhone,
                        reqFrm.patientCellPhone, reqFrm.patientWPhone, reqFrm.patientEmail, reqFrm.patientDOB,
                        reqFrm.patientSex, reqFrm.patientHealthCardType, reqFrm.patientHealthNum,
                        reqFrm.patientHealthCardVersionCode, reqFrm.appointmentDate, reqFrm.appointmentHour,
                        reqFrm.appointmentMinute, reqFrm.appointmentPm, reqFrm.patientChartNo),
                new ConsultationLetterDto.Clinical(reqFrm.reasonForConsultation, reqFrm.clinicalInformation,
                        reqFrm.concurrentProblems, reqFrm.currentMedications, reqFrm.allergies,
                        props.getProperty("significantConcurrentProblemsTitle", ""),
                        props.getProperty("currentMedicationsTitle", "")),
                new ConsultationLetterDto.Footer(printReferring,
                        printReferring ? reqFrm.getProviderName(reqFrm.providerNo) : null, ohipNo,
                        printFamilyDoctor, printFamilyDoctor ? reqFrm.getFamilyDoctor() : null, famDocOhipNo),
                new ConsultationLetterDto.Images(signatureRequested, signature, logoRequested,
                        logoRequested ? ClinicLogoUtility.resolveConsultationLogo(reqFrm.siteName) : null, null));
    }

    /** The request path's letterhead name: a program, a provider or the clinic. */
    private String resolveLetterheadName() {
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
        return letterheadName;
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
        datecell.setPhrase(PdfFonts.phrase(String.format("%s %s", getResource("msgDate"), letter.request().patientWillBook().equals("1") ? getResource("pwb") : letter.request().referralDate()), heading));
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
        headercell.setPhrase(PdfFonts.phrase(getResource("consultationRequestHeader"), boldFontHeading));
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
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    // FindSecBugs PATH_TRAVERSAL_IN: site-logo path validated for containment within DOCUMENT_DIR; fax-logo path from a trusted config property
    @SuppressFBWarnings(value = {"IMPROPER_UNICODE", "PATH_TRAVERSAL_IN"}, justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision; site-logo path validated for containment within DOCUMENT_DIR, fax-logo path from a trusted config property")
    @Deprecated
    private PdfPTable createLogoHeader() {

        PdfPTable infoTable = new PdfPTable(1);

        // The consultation form stores a site NAME, not its numeric database ID.
        // Resolve that explicit contract without changing this letter's existing
        // 50pt/padding-zero logo geometry to the fax cover's different layout.
        File imageFile = letter.images().logoFile();
        byte[] logo = letter.images().logo();
        if (imageFile != null) {
            addImage(infoTable, imageFile.getPath(), PageSize.LETTER.getWidth() * 0.5f, 50f);
        } else if (logo == null || !addImage(infoTable, logo, PageSize.LETTER.getWidth() * 0.5f, 50f)) {
            omissions.add(Omission.LOGO);
        }
        return infoTable;

    }

    /**
     * Adds supplied logo bytes as a scaled image cell, as {@link #addImage(PdfPTable, String,
     * float, float)} adds a logo file.
     *
     * @return false when the bytes are not an image OpenPDF can print (nothing is added)
     */
    private static boolean addImage(PdfPTable pdfPTable, byte[] bytes, float width, float height) {
        try {
            Image image = Image.getInstance(bytes);
            image.scaleToFit(width, height);
            image.setBorder(0);
            PdfPCell cell = new PdfPCell();
            cell.addElement(image);
            cell.setPadding(0);
            cell.setBorder(0);
            pdfPTable.addCell(cell);
            return true;
        } catch (IOException | RuntimeException e) {
            // BadElementException is unchecked in OpenPDF; any unreadable image is reported.
            logger.warn("Consultation letter logo could not be printed: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Reads an image file and adds it as a scaled element to the given PDF table.
     *
     * @param pdfPTable PdfPTable the table to add the image cell to
     * @param filename  String absolute path to the image file
     * @param width     float maximum width in points to scale the image to
     * @param height    float maximum height in points to scale the image to
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path derived from trusted configuration/constant/DB value, not user-controllable input")
    protected void addImage(PdfPTable pdfPTable, String filename, float width, float height) {

        File imageFile = PathValidationUtils.resolveTrustedPath(new File(filename));
        try (FileInputStream fileInputStream = new FileInputStream(imageFile)) {

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
            logger.error("Failed to locate file: {}", LogSafe.exceptionTrace(e));
        } catch (BadElementException e) {
            logger.error("Unexpected error.: {}", LogSafe.exceptionTrace(e));
        } catch (MalformedURLException e) {
            logger.error("This image location is malformed: {}", LogSafe.exceptionTrace(e));
        } catch (IOException e) {
            logger.error("Unexpected error.: {}", LogSafe.exceptionTrace(e));
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

        cell.setPhrase(PdfFonts.phrase(getResource("msgConsReq"), boldFontHeading));

        if ("1".equals(letter.request().patientWillBook())) {
            cell.setPhrase(PdfFonts.phrase(getResource("msgPleaseReplyPatient"), boldFontHeading));
        }

        // If not set to Patient Will Book then maybe a Custom Appointment Instruction is used.
        else if (letter.request().appointmentInstructionsLookup()) {
            cell.setPhrase(PdfFonts.phrase(letter.request().appointmentInstructionsLabel(), boldFontHeading));
        } else if (letter.request().multisites()) {
            cell.setPhrase(PdfFonts.phrase("Please reply", boldFontHeading));
        } else {
            cell.setPhrase(PdfFonts.phrase(
                    String.format("%s %s %s", getResource("msgPleaseReplyPart1"),
                            letter.letterhead().clinicName(),
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

        ConsultationLetterDto.Letterhead letterhead = letter.letterhead();
        String letterheadName = letterhead.name();

        PdfPTable infoTable = new PdfPTable(1);
        PdfPCell cell = new PdfPCell(PdfFonts.phrase(letterheadName, heading));
        cell.setBorder(0);
        cell.setPadding(0);
        infoTable.addCell(cell);

        // add the address details
        Phrase addressPhrase = new Phrase("", heading);
        if (letterhead.address() != null && letterhead.address().trim().length() > 0) {
            PdfFonts.addText(addressPhrase, letterhead.address(), heading);
        } else {
            PdfFonts.addText(addressPhrase, letterhead.clinicAddress(), heading);

            PdfFonts.addText(addressPhrase, String.format("%s, %s. %s", letterhead.clinicCity(),
                    letterhead.clinicProvince(),
                    letterhead.clinicPostal()), heading);
        }

        cell.setPhrase(addressPhrase);
        infoTable.addCell(cell);

        // add the telecom info
        Phrase telecomPhrase = new Phrase("", heading);
        if (letterhead.phone() != null && letterhead.phone().trim().length() > 0) {
            PdfFonts.addText(telecomPhrase, String.format("Phone: %s", letterhead.phone()), heading);
        } else {
            PdfFonts.addText(telecomPhrase, String.format("Phone: %s", letterhead.clinicPhone()), heading);
        }

        if (letterhead.fax() != null && letterhead.fax().trim().length() > 0) {
            PdfFonts.addText(telecomPhrase, String.format(" Fax: %s", letterhead.fax()), heading);
        } else {
            PdfFonts.addText(telecomPhrase, String.format(" Fax: %s", letterhead.clinicFax()), heading);
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
        ConsultationLetterDto.Specialist professionalSpecialist = letter.specialist();
        String urgency = letter.request().urgency();

        infoTable.addCell(setInfoCell(cell, getResource("msgConsultant")));

        // specialist name
        if (professionalSpecialist != null) {
            infoTable.addCell(setDataCell(cell, professionalSpecialist.name()));
        } else {
            infoTable.addCell(setDataCell(cell, ""));
        }

        infoTable.addCell(setInfoCell(cell, getResource("msgUrgency")));
        // urgency is nullable on consultationRequests (legacy/imported rows, partial
        // integration updates); a missing urgency must print blank, not NPE the whole
        // consultation PDF out of print/preview/fax.
        infoTable.addCell(setDataCell(cell, ("1".equals(urgency) ? getResource("msgUrgent") :
                ("2".equals(urgency) ? getResource("msgNUrgent") :
                        ("3".equals(urgency)) ? getResource("msgReturn")
                                : "  "))));

        infoTable.addCell(setInfoCell(cell, getResource("msgService")));
        infoTable.addCell(setDataCell(cell, letter.request().serviceName()));

        infoTable.addCell(setInfoCell(cell, getResource("msgPhone")));
        if (professionalSpecialist != null) {
            infoTable.addCell(setDataCell(cell, professionalSpecialist.phone()));
        } else {
            infoTable.addCell(setDataCell(cell, ""));
        }

        infoTable.addCell(setInfoCell(cell, getResource("msgFax")));

        if (professionalSpecialist != null) {
            infoTable.addCell(setDataCell(cell, professionalSpecialist.fax()));
        } else {
            infoTable.addCell(setDataCell(cell, ""));
        }

        infoTable.addCell(setInfoCell(cell, getResource("msgAddr")));

        if (professionalSpecialist != null) {
            infoTable.addCell(setDataCell(cell, divy(professionalSpecialist.address())));
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

        ConsultationLetterDto.Patient patient = letter.patient();
        String pwb = letter.request().patientWillBook();
        float[] tableWidths = new float[]{2, 2.5f};
        PdfPTable infoTable = new PdfPTable(tableWidths);
        PdfPCell cell = new PdfPCell();
        cell.setPadding(0);
        cell.setPaddingLeft(10f);

        infoTable.addCell(setInfoCell(cell, getResource("msgPat")));
        infoTable.addCell(setDataCell(cell, patient.name()));

        infoTable.addCell(setInfoCell(cell, getResource("msgAddr")));
        infoTable.addCell(setDataCell(cell, divy(patient.address())));

        infoTable.addCell(setInfoCell(cell, getResource("msgPhone")));
        infoTable.addCell(setDataCell(cell, patient.phone()));

        infoTable.addCell(setInfoCell(cell, getResource("msgCellPhone")));
        infoTable.addCell(setDataCell(cell, patient.cellPhone()));

        infoTable.addCell(setInfoCell(cell, getResource("msgWPhone")));
        infoTable.addCell(setDataCell(cell, patient.workPhone()));

        infoTable.addCell(setInfoCell(cell, getResource("msgEmail")));
        infoTable.addCell(setDataCell(cell, patient.email()));

        infoTable.addCell(setInfoCell(cell, getResource("msgBirth")));
        infoTable.addCell(setDataCell(cell, patient.dateOfBirth() + " (y/m/d)"));

        infoTable.addCell(setInfoCell(cell, getResource("msgSex")));
        infoTable.addCell(setDataCell(cell, patient.sex()));

        infoTable.addCell(setInfoCell(cell, getResource("msgCard")));
        infoTable.addCell(setDataCell(cell, String.format("(%s) %s %s", patient.healthCardType(),
                patient.healthNumber(),
                patient.healthCardVersion())));

        if (!pwb.equals("1")) {
            infoTable.addCell(setInfoCell(cell, getResource("msgappDate")));
            infoTable.addCell(setDataCell(cell, pwb.equals("1") ? getResource("pwb") : patient.appointmentDate()));
            infoTable.addCell(setInfoCell(cell, getResource("msgTime")));
            infoTable.addCell(setDataCell(cell, formatAppointmentTime(patient.appointmentHour(),
                    patient.appointmentMinute(), patient.appointmentPm())));
        }

        infoTable.addCell(setInfoCell(cell, getResource("msgChart")));
        infoTable.addCell(setDataCell(cell, patient.chartNumber()));

        return infoTable;
    }

    /**
     * Creates the table containing additional information
     * about the reason for the consultation request.
     *
     * @return the table produced
     */
    private PdfPTable createConsultDetailTable() {
        ConsultationLetterDto.Clinical clinical = letter.clinical();
        PdfPTable infoTable = new PdfPTable(1);
        PdfPCell cell = new PdfPCell();
        cell.setPadding(0);
        cell.setPaddingTop(10f);
        cell.setPaddingBottom(5f);

        infoTable.addCell(setDataCell(cell, clinical.reason()));

        if (getlen(clinical.clinicalInformation()) > 1) {
            infoTable.addCell(setInfoCell(cell, getResource("msgClinicalInfom"), boldFontHeading));
            infoTable.addCell(setDataCell(cell, divy(clinical.clinicalInformation())));
        }

        if (getlen(clinical.concurrentProblems()) > 0) {
            if (getlen(clinical.concurrentProblemsTitle()) > 1) {
                infoTable.addCell(setInfoCell(cell, clinical.concurrentProblemsTitle(), boldFontHeading));
            } else {
                infoTable.addCell(setInfoCell(cell, getResource("msgSigProb"), boldFontHeading));
            }
            infoTable.addCell(setDataCell(cell, divy(clinical.concurrentProblems())));
        }

        if (getlen(clinical.currentMedications()) > 1) {
            if (getlen(clinical.currentMedicationsTitle()) > 1) {
                infoTable.addCell(setInfoCell(cell, clinical.currentMedicationsTitle(), boldFontHeading));
            } else {
                infoTable.addCell(setInfoCell(cell, getResource("msgCurrMed"), boldFontHeading));
            }
            infoTable.addCell(setDataCell(cell, divy(clinical.currentMedications())));
        }

        if (getlen(clinical.allergies()) > 1) {
            infoTable.addCell(setInfoCell(cell, getResource("msgAllergies"), boldFontHeading));
            infoTable.addCell(setDataCell(cell, divy(clinical.allergies())));
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
        byte[] signatureImage = letter.images().signature();

        if (signatureImage != null && signatureImage.length > 0) {
            try {
                Image image = createScaledSignatureImage(signatureImage);
                addTable(pdfPTable, createRightAlignedSignatureTable(getResource("msgSignature"), image, font));
            } catch (BadElementException | IOException e) {
                omissions.add(Omission.SIGNATURE);
                logger.error("An error occurred while trying to create an image from the signature: {}", LogSafe.exceptionTrace(e));
            }
        } else {
            omissions.add(Omission.SIGNATURE);
        }
    }

    static Image createScaledSignatureImage(byte[] signatureImage) throws BadElementException, IOException {
        Image image = Image.getInstance(signatureImage);
        image.scalePercent(Math.min(SIGNATURE_IMAGE_SCALE_PERCENT, Math.min(
                scalePercentToFit(image.getPlainWidth(), SIGNATURE_IMAGE_MAX_WIDTH),
                scalePercentToFit(image.getPlainHeight(), SIGNATURE_IMAGE_MAX_HEIGHT))));
        image.setBorder(0);
        return image;
    }

    private static float scalePercentToFit(float sourceSize, float maxSize) {
        if (sourceSize <= 0f) {
            return SIGNATURE_IMAGE_SCALE_PERCENT;
        }
        return maxSize * 100f / sourceSize;
    }

    private static PdfPTable createRightAlignedSignatureTable(String signatureLabel, Image image, Font signatureFont) {
        PdfPTable signatureBlock = new PdfPTable(1);
        signatureBlock.setWidthPercentage(100f);

        PdfPCell labelCell = new PdfPCell(PdfFonts.phrase(signatureLabel, signatureFont));
        labelCell.setBorder(0);
        labelCell.setPadding(0);
        labelCell.setPaddingBottom(SIGNATURE_LABEL_BOTTOM_PADDING);
        signatureBlock.addCell(labelCell);

        PdfPCell imageCell = new PdfPCell(image);
        imageCell.setBorder(0);
        imageCell.setPadding(0);
        //imageCell.setHorizontalAlignment(PdfPCell.ALIGN_RIGHT);
        signatureBlock.addCell(imageCell);

        PdfPTable table = new PdfPTable(SIGNATURE_ROW_WIDTHS);
        table.setWidthPercentage(100f);
        table.addCell(createSignatureSpacerCell());

        PdfPCell signatureCell = new PdfPCell(signatureBlock);
        signatureCell.setBorder(0);
        signatureCell.setPadding(0);
        signatureCell.setPaddingTop(SIGNATURE_TOP_PADDING);
        table.addCell(signatureCell);
        return table;
    }

    private static PdfPCell createSignatureSpacerCell() {
        PdfPCell spacerCell = new PdfPCell(new Phrase(""));
        spacerCell.setBorder(0);
        spacerCell.setPadding(0);
        spacerCell.setPaddingTop(SIGNATURE_TOP_PADDING);
        return spacerCell;
    }

    /**
     * Resolves the signature image bytes to render, preferring a non-mutating preview override over the
     * persisted signature.
     *
     * <p>When {@code signatureImageOverride} is non-empty it is returned as-is and {@code mgr} is not
     * consulted (this is the print-preview path that renders freshly-selected stamp/manual bytes without
     * persisting a {@link DigitalSignature}). Otherwise the stored {@link DigitalSignature} is looked up
     * by id.</p>
     *
     * @param signatureImageOverride non-mutating preview bytes, or {@code null}/empty when none
     * @param signatureImageId       the persisted {@code DigitalSignature} id, or {@code null}/empty
     * @param mgr                    manager used to load the persisted signature when no override is present
     * @return the chosen signature bytes, or {@code null} when none are available, the id is blank/invalid
     *         after trimming, or the signature cannot be loaded
     */
    @SuppressWarnings("java:S1168")
    static byte[] resolveSignatureBytes(byte[] signatureImageOverride, String signatureImageId, DigitalSignatureManager mgr) {
        if (signatureImageOverride != null && signatureImageOverride.length > 0) {
            return signatureImageOverride;
        }
        String normalizedSignatureImageId = signatureImageId == null ? "" : signatureImageId.strip();
        if (normalizedSignatureImageId.isEmpty()) {
            return null;
        }
        /*
         *  This is not the preferred way to handle a potential NFE. Unfortunately
         *  this entire thread was not designed well from the beginning.
         *  Now maintainers are required to insert
         *  odd patches in order to save valuable time on a full refactor.
         */
        int parsedId;
        try {
            parsedId = Integer.parseInt(normalizedSignatureImageId);
        } catch (NumberFormatException e) {
            // Malformed id is benign (the field is upstream-validated); render unsigned.
            logger.debug("Consultation signature id {} is not a valid number", LogSafe.sanitize(normalizedSignatureImageId));
            return null;
        }
        try {
            DigitalSignature digitalSignature = mgr.getDigitalSignature(parsedId);
            return digitalSignature != null ? digitalSignature.getSignatureImage() : null;
        } catch (RuntimeException e) {
            // A real lookup fault (DB/infra) - log WITH the stack trace rather than mislabel it
            // "not found"; stay fail-soft so a transient blip renders unsigned instead of blanking the PDF.
            logger.error("Error loading consultation digital signature {} {}", parsedId, LogSafe.exceptionTrace(e));
            return null;
        }
    }

    /**
     * The appointment time as the referral prints it, from the 12-hour parts the form carries:
     * {@code 3}, {@code 5}, {@code PM} prints {@code 3:05 PM}.
     *
     * <p>Print previews the posted form ({@link ConsultationPreviewOverlay}), and the minute it posts
     * is not zero-padded: the reopened request fills it with {@code String.valueOf(minute)} and the
     * time box script with {@code String(minute)}. So the time is formatted from the parsed hour and
     * minute rather than joined as text. A missing hour prints nothing, and the AM/PM label is
     * printed as given. Parts that are not an hour of 1-12 and a minute of 0-59 are printed as given,
     * so an unexpected value is never hidden.</p>
     */
    static String formatAppointmentTime(String appointmentHour, String appointmentMinute, String appointmentPm) {
        String hour = StringUtils.trimToEmpty(appointmentHour);
        String minute = StringUtils.trimToEmpty(appointmentMinute);
        String half = StringUtils.trimToEmpty(appointmentPm);
        if (hour.isEmpty()) {
            return "";
        }
        String label = half.isEmpty() ? "" : " " + half;
        Integer parsedHour = numberInRange(hour, 1, 12);
        Integer parsedMinute = minute.isEmpty() ? null : numberInRange(minute, 0, 59);
        if (parsedHour != null && minute.isEmpty()) {
            return parsedHour + label;
        }
        if (parsedHour != null && parsedMinute != null) {
            return String.format(Locale.ROOT, "%d:%02d", parsedHour, parsedMinute) + label;
        }
        return hour + (minute.isEmpty() ? "" : ":" + minute) + label;
    }

    private static Integer numberInRange(String value, int min, int max) {
        if (!value.matches("\\d{1,2}")) {
            return null;
        }
        int number = Integer.parseInt(value);
        return number >= min && number <= max ? number : null;
    }

    /**
     * Determines whether the consultation PDF should render a signature image.
     *
     * <p>The explicit suppression flag wins over both freshly supplied signature bytes and stored
     * signature identifiers. Otherwise, either non-empty override bytes or a non-blank stored id is
     * enough to request signature rendering.
     */
    static boolean shouldRenderSignature(boolean suppressSignature, byte[] signatureImageOverride, String signatureImageId) {
        if (suppressSignature) {
            return false;
        }
        return (signatureImageOverride != null && signatureImageOverride.length > 0)
                || StringUtils.isNotBlank(signatureImageId);
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
    private PdfPTable createReferringPracAndMRPDetailTable() {
        ConsultationLetterDto.Footer footer = letter.footer();
        String ohipNo = footer.referringNumber();
        String famDocOhipNo = footer.familyDoctorNumber();

        PdfPTable table = new PdfPTable(new float[]{1.0f, 1.0f});
        PdfPCell cell = new PdfPCell();
        if (footer.printReferringPractitioner()) {
            table.addCell(setFooterCell(cell, getResource("msgAssociated2"), footer.referringPractitioner() + ((getlen(ohipNo) > 0) ? " (" + ohipNo + ")" : "")));
        } else {
            table.addCell(setFooterCell(cell, "", ""));
        }

        if (footer.printFamilyDoctor()) {
            table.addCell(setFooterCell(cell, getResource("msgFamilyDoc2"), footer.familyDoctor() + ((getlen(famDocOhipNo) > 0) ? " (" + famDocOhipNo + ")" : "")));
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

        // Record values can be in any script; addText draws characters DejaVu lacks (Chinese,
        // Japanese, Korean, emoji) in the bundled fallback faces instead of dropping them (#5046).
        cell.setPhrase(PdfFonts.phrase(phrase, font != null ? font : this.font));
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
        return oscarR.getString("encounter.oscarConsultationRequest.consultationFormPrint." + key);
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
