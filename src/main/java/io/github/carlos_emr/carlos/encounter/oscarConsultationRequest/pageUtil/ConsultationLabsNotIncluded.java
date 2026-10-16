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
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import io.github.carlos_emr.carlos.commn.printing.PdfFonts;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import org.apache.logging.log4j.Logger;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfWriter;

/**
 * Names the lab attachments a consultation PDF cannot include, and draws the page that lists them
 * in the packet (issue #4693).
 *
 * <p><b>Why this exists.</b> The consultation packet can draw HL7 lab reports only
 * ({@code LabPDFCreator}). There is no PDF renderer for CML, MDS or PathNet (BCP) labs, and an
 * attachment whose lab source was never recorded cannot be drawn at all. Those labs are kept away
 * from the renderer on purpose: a legacy lab number can equal one of the patient's HL7 lab numbers,
 * which would print the wrong lab (see {@code AttachmentOwnershipService#renderableLabsOnly}). They
 * used to be left out with nothing said, so neither the sender nor the receiving clinician knew that
 * an attached lab was missing. Every such lab is now named: on a page right after the consultation
 * letter, in the Print warning, on the fax cover page and on the confirmation page.</p>
 *
 * <p><b>What a name contains.</b> A lab the consultation listing found is named by its source,
 * discipline and date, as the consultation form shows it. An attachment the listing could not
 * match (its source is not listed on this install, or the lab is gone or was re-routed) is named by
 * its recorded source alone: never by the placeholder's label, which is an instruction to the
 * sender, and never with details that could belong to another patient's lab. No ids are printed.</p>
 *
 * <p>Static and without side effects, apart from the discipline lookup a CML or PathNet lab does
 * in its own tables. That lookup is best effort: when it fails, the lab is still named, without
 * details, rather than failing the print or fax. Only the exception class is logged.</p>
 *
 * @since 2026-10-16
 */
public final class ConsultationLabsNotIncluded {

    /**
     * Request attribute holding the names ({@code List<String>}, possibly empty) for the pages that
     * show them; set by {@code DocumentAttachmentManager.renderConsultationFormWithAttachments} and
     * by the fax cover page's action. The JSPs read it as {@code consultLabsNotIncluded}.
     */
    public static final String REQUEST_ATTRIBUTE = "consultLabsNotIncluded";

    private static final Logger logger = MiscUtils.getLogger();

    private static final String BUNDLE = "oscarResources";
    private static final String PRINT_KEYS = "encounter.oscarConsultationRequest.consultationFormPrint.";
    private static final String PREVIEW_WARNING_KEY =
            "encounter.oscarConsultationRequest.ConsultationFormRequest.msgPreviewLabsNotIncluded";

    /**
     * Source names as staff know them, by the code {@code consultdocs.lab_type} stores. BCP is the
     * PathNet feed ({@code PATHNET_LABS}). Anything else, {@code UNRESOLVED} included, has no
     * confirmed source and is named without one.
     */
    private static final Map<String, String> SOURCE_NAMES = Map.of(
            LabResultData.CML, "CML",
            LabResultData.MDS, "MDS",
            LabResultData.EXCELLERIS, "PathNet");

    private ConsultationLabsNotIncluded() {
    }

    /**
     * Names each lab in the reader's language.
     *
     * @param labs the labs the PDF cannot draw, as {@code AttachmentOwnershipService.labsNotRenderable}
     *             returns them; {@code null} yields an empty list
     * @param locale the reader's locale ({@code LocaleUtils.resolveBundleLocale}); {@code null} is English
     * @return one name per lab, in order; never {@code null}
     */
    public static List<String> describe(List<LabResultData> labs, Locale locale) {
        List<String> names = new ArrayList<>();
        if (labs == null || labs.isEmpty()) {
            return names;
        }
        Locale readerLocale = locale == null ? Locale.ENGLISH : locale;
        ResourceBundle bundle = bundle(readerLocale);
        for (LabResultData lab : labs) {
            if (lab != null) {
                names.add(describe(lab, bundle, readerLocale));
            }
        }
        return names;
    }

    private static String describe(LabResultData lab, ResourceBundle bundle, Locale locale) {
        // Map.of rejects a null key, and a lab with no type has no source to name either.
        String labType = lab.getLabType();
        String source = labType == null ? null : SOURCE_NAMES.get(labType);
        if (source == null) {
            return bundle.getString(PRINT_KEYS + "msgLabNotIncludedUnknownSource");
        }
        // A placeholder's own fields describe nothing the receiver should read.
        String details = lab.isAttachmentUnavailable() ? "" : details(lab);
        if (details.isEmpty()) {
            return format(bundle, locale, PRINT_KEYS + "msgLabNotIncludedNoDetails", source);
        }
        return format(bundle, locale, PRINT_KEYS + "msgLabNotIncludedDetails", source, details);
    }

    /** The discipline and date the listing gives the lab, either of which may be missing. */
    private static String details(LabResultData lab) {
        String discipline;
        try {
            // CML and PathNet look the discipline up in their own report tables.
            discipline = lab.getDiscipline();
        } catch (RuntimeException e) {
            logger.warn("The details of a lab attachment the consultation PDF cannot include could not be read ({})",
                    e.getClass().getSimpleName());
            return "";
        }
        List<String> parts = new ArrayList<>(2);
        if (discipline != null && !discipline.isBlank()) {
            parts.add(discipline.trim());
        }
        String date = lab.getDateTime();
        if (date != null && !date.isBlank()) {
            parts.add(date.trim());
        }
        return String.join(", ", parts);
    }

    /**
     * Draws the page that goes right after the consultation letter: a heading, a sentence asking the
     * receiver to contact the sender, and one line per name. Text is drawn in the embedded Unicode
     * faces of {@link PdfFonts}, so accented and non-Latin names print as written.
     *
     * @param names the names from {@link #describe}; must not be empty, since a notice that names
     *              nothing would be faxed as a page that says an attachment is missing
     * @param locale the reader's locale; {@code null} is English
     * @return the page as PDF bytes
     * @throws DocumentException when OpenPDF cannot build the page
     * @throws IllegalArgumentException when there is nothing to name
     */
    public static byte[] noticePage(List<String> names, Locale locale) throws DocumentException {
        if (names == null || names.isEmpty()) {
            throw new IllegalArgumentException("A notice page needs at least one lab attachment to name");
        }
        ResourceBundle bundle = bundle(locale == null ? Locale.ENGLISH : locale);
        Font heading = PdfFonts.getFont(BaseFont.HELVETICA_BOLD, 14, Font.NORMAL, Color.BLACK);
        Font body = PdfFonts.getFont(BaseFont.HELVETICA, 11, Font.NORMAL, Color.BLACK);
        String title = bundle.getString(PRINT_KEYS + "titleLabsNotIncluded");

        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        Document document = new Document(PageSize.LETTER, 54, 54, 54, 54);
        PdfWriter.getInstance(document, pdf);
        document.addTitle(title);
        document.addCreator("CARLOS EMR");
        document.open();
        try {
            Paragraph titleLine = new Paragraph();
            PdfFonts.addText(titleLine, title, heading);
            titleLine.setSpacingAfter(10f);
            document.add(titleLine);

            Paragraph explanation = new Paragraph();
            PdfFonts.addText(explanation, bundle.getString(PRINT_KEYS + "msgLabsNotIncluded"), body);
            explanation.setSpacingAfter(10f);
            document.add(explanation);

            for (String name : names) {
                Paragraph item = new Paragraph();
                PdfFonts.addText(item, "\u2022 " + name, body);
                item.setIndentationLeft(24f);
                item.setFirstLineIndent(-12f);
                item.setSpacingAfter(4f);
                document.add(item);
            }
        } finally {
            document.close();
        }
        return pdf.toByteArray();
    }

    /**
     * The warning the Print reply carries, one name per line, for the alert the consultation form
     * raises after the download.
     *
     * @param names the names from {@link #describe}
     * @param locale the reader's locale; {@code null} is English
     * @return the warning, or {@code null} when there is nothing to name
     */
    public static String previewWarning(List<String> names, Locale locale) {
        if (names == null || names.isEmpty()) {
            return null;
        }
        Locale readerLocale = locale == null ? Locale.ENGLISH : locale;
        String list = names.stream().map(name -> "- " + name).collect(Collectors.joining("\n"));
        return format(bundle(readerLocale), readerLocale, PREVIEW_WARNING_KEY, list);
    }

    private static String format(ResourceBundle bundle, Locale locale, String key, Object... arguments) {
        return new MessageFormat(bundle.getString(key), locale).format(arguments);
    }

    /**
     * The message bundle for the locale. Callers pass a negotiated locale, which always has a bundle;
     * any other locale falls back to English rather than to the server's default locale.
     */
    private static ResourceBundle bundle(Locale locale) {
        try {
            return ResourceBundle.getBundle(BUNDLE, locale,
                    ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
        } catch (MissingResourceException e) {
            return ResourceBundle.getBundle(BUNDLE, Locale.ENGLISH);
        }
    }
}
