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
package io.github.carlos_emr.carlos.fax.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.openpdf.text.*;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfWriter;
import io.github.carlos_emr.carlos.fax.core.FaxAccount;
import io.github.carlos_emr.carlos.fax.core.FaxRecipient;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.clinic.ClinicData;

import static io.github.carlos_emr.carlos.utility.ClinicLogoUtility.createLogoHeader;


/**
 * Generates a single-page PDF fax cover page with clinic letterhead, sender/recipient
 * information, a confidentiality footer, and an optional memo note.
 *
 * <p>The cover page is a fixed-height document (US Letter, 792pt) composed of a nested table
 * layout: letterhead with optional logo, title line ("Fax Transmittal" + "CONFIDENTIAL"),
 * sender/recipient info, memo body, and a confidentiality statement footer. Long notes are
 * truncated to fit the fixed memo area (491pt).</p>
 *
 * <p>This class should be accessed through {@code FaxDocumentManager} rather than
 * instantiated directly, to ensure proper sender/recipient resolution.</p>
 *
 * <p>Uses OpenPDF ({@code org.openpdf.*}) for PDF generation.</p>
 *
 * @see io.github.carlos_emr.carlos.fax.core.FaxAccount
 * @see io.github.carlos_emr.carlos.fax.core.FaxRecipient
 * @see io.github.carlos_emr.carlos.utility.ClinicLogoUtility
 * @since 2014-08-29
 */
public class PdfCoverPageCreator {

    private String note;

    private static BaseFont basefont;
    private static Font body = new Font(basefont, 12, Font.NORMAL);
    private static Font heading_bold = new Font(basefont, 14, Font.BOLD);
    private ClinicData clinic;
    private Font footer;
    private Font LETTERHEAD;
    private FaxRecipient recipient;
    private FaxAccount sender;
    private int numberPages;

    /**
     * Always use a manager (FaxDocumentManager) to access this class.
     * Do not access directly.
     */
    public PdfCoverPageCreator(String note) {
        this.note = note;
        try {
            basefont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            body = new Font(basefont, 11, Font.NORMAL);
            footer = new Font(basefont, 10, Font.NORMAL);
            heading_bold = new Font(basefont, 11, Font.BOLD);
            LETTERHEAD = new Font(basefont, 11, Font.NORMAL);
        } catch (IOException | DocumentException e) {
            throw new RuntimeException("Cannot create PDF cover page fonts", e);
        }
    }

    /**
     * Creates a cover page creator with note text and page count.
     *
     * @param note String the memo/note text to display on the cover page
     * @param numberPages int the number of fax content pages (excluding the cover page itself)
     */
    public PdfCoverPageCreator(String note, int numberPages) {
        this(note);
        this.numberPages = numberPages;
    }

    /**
     * Creates a fully configured cover page creator with sender and recipient details.
     *
     * @param note String the memo/note text to display on the cover page
     * @param numberPages int the number of fax content pages (excluding the cover page itself)
     * @param recipient FaxRecipient the fax recipient with name and fax number
     * @param sender FaxAccount the sending fax account with letterhead and contact info
     */
    public PdfCoverPageCreator(String note, int numberPages, FaxRecipient recipient, FaxAccount sender) {
        this(note, numberPages);
        this.recipient = recipient;
        this.sender = sender;
    }

    /**
     * Generates the fax cover page as a PDF byte array.
     *
     * <p>The cover page has a fixed US Letter height (792pt). Long note information will be
     * truncated to fit the fixed memo area. Layout sections:</p>
     * <ul>
     *   <li>Letterhead with optional clinic logo (70pt)</li>
     *   <li>Title line ("Fax Transmittal" / "CONFIDENTIAL")</li>
     *   <li>Sender and recipient information with date and page count</li>
     *   <li>Memo body (491pt fixed height)</li>
     *   <li>Confidentiality statement footer</li>
     * </ul>
     *
     * @return byte[] the generated PDF cover page, or an empty array if generation fails
     */
    public byte[] createCoverPage() {
        byte[] bytearray = new byte[]{};
        float[] tableWidths = {1f, 1f};
        PdfPTable border1;

        PdfPCell cell;
        OscarProperties oscarProperties = OscarProperties.getInstance();

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, os);

            document.setPageSize(PageSize.LETTER);
            document.addTitle("Fax Cover Page");

            if (sender != null) {
                document.addCreator(sender.getLetterheadName());
            }

            document.addAuthor("CARLOS EMR");
            document.open();

            PdfPTable maintable = new PdfPTable(1);
            maintable.setWidthPercentage(100f);
            maintable.setSplitLate(true);

            // Set the letterhead
            if (oscarProperties.getProperty("faxLogoInCoverPage") != null) {
                border1 = new PdfPTable(tableWidths);
                addToTable(border1, createLogoHeader(), false);

                // Adding clinic information to the border.
                addToTable(border1, createClinicInfoHeader(), false);
                // add the new header table to the main table.
                addTable(maintable, border1);
            } else {
                // clinic address
                cell = new PdfPCell(createClinicInfoHeader());
                cell.setBorder(0);
                maintable.addCell(cell);
            }

            // title line
            addTable(maintable, createTitleLine());

            // info line
            addToTable(maintable, createInfoLine(), new int[]{PdfPCell.TOP, PdfPCell.BOTTOM}, null);

            // memo
            PdfPTable memoTable = new PdfPTable(1);
            cell = new PdfPCell(new Phrase(note, body));
            cell.setPaddingTop(10);
            cell.setPaddingBottom(10);
            cell.setBorder(0);
            cell.setColspan(1);
            cell.setFixedHeight(491f);
            memoTable.addCell(cell);
            addTable(maintable, memoTable);

            // footer
            PdfPTable footerTable = new PdfPTable(1);
            cell = new PdfPCell(new Phrase(OscarProperties.getConfidentialityStatement(), footer));
            cell.setPaddingTop(0);
            cell.setBorder(0);
            cell.setColspan(1);
            cell.enableBorderSide(PdfPCell.TOP);
            footerTable.addCell(cell);

            addTable(maintable, footerTable);

            document.add(maintable);
            document.close();

            bytearray = os.toByteArray();

        } catch (DocumentException | IOException e) {
            MiscUtils.getLogger().error("PDF COVER PAGE ERROR", e);
        }

        return bytearray;
    }

    /**
     * Creates a table and populates it with the clinic information for the header.
     *
     * @return the table produced
     */
    private PdfPTable createClinicInfoHeader() {

        PdfPTable infoTable = new PdfPTable(1);

        if (sender == null) {
            return infoTable;
        }

        PdfPCell cell = new PdfPCell(new Phrase(sender.getFaxNumberOwner(), LETTERHEAD));
        cell.setBorder(0);
        cell.setPadding(0);
        cell.setIndent(10);
        cell.setHorizontalAlignment(PdfPCell.ALIGN_LEFT);
        cell.setVerticalAlignment(PdfPCell.ALIGN_TOP);
        infoTable.addCell(cell);

        // add the address details
        Phrase addressPhrase = new Phrase("", LETTERHEAD);
        if (sender.getAddress() != null && sender.getAddress().length() > 0) {
            addressPhrase.add(String.format("%s", sender.getAddress()));
        }

        cell.setPhrase(addressPhrase);
        infoTable.addCell(cell);

        // add the telecom info
        Phrase telecomPhonePhrase = new Phrase("", LETTERHEAD);
        if (sender.getPhone() != null && sender.getPhone().trim().length() > 0) {
            telecomPhonePhrase.add(String.format("Phone: %s", sender.getPhone()));
        }

        cell.setPhrase(telecomPhonePhrase);
        infoTable.addCell(cell);

        Phrase telecomFaxPhrase = new Phrase("", LETTERHEAD);
        if (sender.getFax() != null && sender.getFax().trim().length() > 0) {
            telecomFaxPhrase.add(String.format("Fax: %s", sender.getFax()));
        }

        cell.setPhrase(telecomFaxPhrase);
        infoTable.addCell(cell);

        return infoTable;
    }

    /**
     * Creates the sender/recipient info section with To, From, Date, Fax number, and page count.
     *
     * @return PdfPTable a two-column table with sender/recipient details
     */
    private PdfPTable createInfoLine() {
        float[] tableWidths = {2f, 1f};
        PdfPTable infolineborder = new PdfPTable(tableWidths);
        infolineborder.setWidthPercentage(100f);

        // column 1
        PdfPTable column1Table = new PdfPTable(1);
        PdfPCell column1Cell = new PdfPCell();
        column1Cell.setBorder(0);
        column1Cell.setFixedHeight(25f);
        column1Cell.setHorizontalAlignment(PdfPCell.ALIGN_LEFT);
        column1Cell.setVerticalAlignment(PdfPCell.ALIGN_TOP);

        column1Cell.setPhrase(new Phrase("To: " + recipient.getName(), body));
        column1Table.addCell(column1Cell);
        column1Cell.setPhrase(new Phrase("From: " + sender.getLetterheadName(), body));
        column1Table.addCell(column1Cell);
        Date today = new Date();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd");
        column1Cell.setFixedHeight(0f);
        column1Cell.setPaddingBottom(5f);
        column1Cell.setPhrase(new Phrase("Date: " + simpleDateFormat.format(today), body));
        column1Table.addCell(column1Cell);

        addTable(infolineborder, column1Table);

        // column 2
        PdfPTable column2Table = new PdfPTable(1);
        PdfPCell column2Cell = new PdfPCell();
        column2Cell.setBorder(0);
        column2Cell.setFixedHeight(50f);
        column2Cell.enableBorderSide(PdfPCell.LEFT);
        column2Cell.setPhrase(new Phrase("Fax: \n" + recipient.getFax(), body));
        column2Table.addCell(column2Cell);
        column2Cell.setFixedHeight(0f);
        column2Cell.setPaddingBottom(5f);
        column2Cell.setPhrase(new Phrase("Total pages including fax cover: " + (numberPages + 1), body));
        column2Table.addCell(column2Cell);
        addTable(infolineborder, column2Table);

        return infolineborder;
    }

    /**
     * Creates the title section with "Fax Transmittal", "CONFIDENTIAL" label, and sender sub-text.
     *
     * @return PdfPTable a three-column table with the title row
     */
    private PdfPTable createTitleLine() {
        float[] tableWidths = {1f, 1f, 1f};
        PdfPTable titlelineborder = new PdfPTable(tableWidths);
        titlelineborder.setWidthPercentage(100f);

        // column 1
        PdfPTable column1Table = new PdfPTable(1);
        PdfPCell column1Cell = new PdfPCell();
        column1Cell.setPaddingTop(0);
        column1Cell.setBorder(0);
        column1Cell.setPhrase(new Phrase("Fax Transmittal:", heading_bold));
        column1Table.addCell(column1Cell);

        column1Cell.setFixedHeight(25f);
        column1Cell.setPhrase(new Phrase(sender.getSubText(), body));
        column1Table.addCell(column1Cell);

        addTable(titlelineborder, column1Table);

        // column 2
        PdfPCell column2Cell = new PdfPCell();
        column2Cell.setPaddingTop(0);
        column2Cell.setBorder(0);
        column2Cell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
        column2Cell.setVerticalAlignment(PdfPCell.ALIGN_TOP);
        column2Cell.setPhrase(new Phrase("CONFIDENTIAL", heading_bold));
        titlelineborder.addCell(column2Cell);

        // column 3
        column2Cell.setPhrase(new Phrase("", body));

        titlelineborder.addCell(column2Cell);

        return titlelineborder;
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
     * Adds a table to a parent table cell with configurable borders and padding.
     *
     * <p>Unlike {@link #addToTable(PdfPTable, PdfPTable, boolean)} which sets a full border
     * based on a boolean, this method allows individual border sides to be enabled selectively.</p>
     *
     * @param main PdfPTable the host table to add into
     * @param add PdfPTable the table being added as a nested cell
     * @param borderarray int[] array of {@link PdfPCell} border constants (e.g., {@code PdfPCell.TOP})
     * @param paddingarray int[] padding values as {left, top, right, bottom}, or null for defaults
     * @return PdfPCell the cell containing the nested table
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

}
