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

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfWriter;
import io.github.carlos_emr.carlos.fax.core.FaxAccount;
import io.github.carlos_emr.carlos.fax.core.FaxRecipient;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.clinic.ClinicData;

import static io.github.carlos_emr.carlos.utility.ClinicLogoUtility.createLogoHeader;


public class PdfCoverPageCreator {

    private String note;

    private static BaseFont basefont;
    private static Font body = new Font(basefont, 12, Font.NORMAL);
    private static Font heading = new Font(basefont, 13, Font.NORMAL);
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
        } catch (IOException e) {
            MiscUtils.getLogger().error("PDF COVER PAGE ERROR", e);
        } catch (DocumentException e) {
            MiscUtils.getLogger().error("PDF COVER PAGE ERROR", e);
        }
    }

    public PdfCoverPageCreator(String note, int numberPages) {
        this(note);
        this.numberPages = numberPages;
    }

    public PdfCoverPageCreator(String note, int numberPages, FaxRecipient recipient, FaxAccount sender) {
        this(note, numberPages);
        this.recipient = recipient;
        this.sender = sender;
    }

    /**
     * This cover page has a fixed height.  Long note information will be truncated.
     * Cover page height: 792f
     * Letterhead height: 70f
     * Heading height:
     * Sender - Recipient info height:
     * Body(note) height:
     * Footer Height:
     */
    public byte[] createCoverPage() {
        byte[] bytearray = new byte[]{};
        float[] tableWidths = {1f, 1f};
        PdfPTable border1;

        PdfPCell cell;
        CarlosProperties oscarProperties = CarlosProperties.getInstance();

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, os);

            document.setPageSize(PageSize.LETTER);
            document.addTitle("Fax Cover Page");

            if (sender != null) {
                document.addCreator(sender.getLetterheadName());
            }

            document.addAuthor("OSCAR EMR");
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
            cell = new PdfPCell(new Phrase(CarlosProperties.getConfidentialityStatement(), footer));
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

}
