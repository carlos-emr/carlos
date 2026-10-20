/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.casemgmt.service;

import io.github.carlos_emr.carlos.commn.printing.FontSettings;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;

/** Wrapped footer text for flowing reports, with space reserved before opening.
 * Page size and horizontal margins must be final when this stamper is created.
 * The first 24 points below the body are reserved for the existing page-number
 * stamper; wrapped branding and the full confidentiality notice follow below.
 */
public final class WrappedFooterStamper extends FooterSupport {
    private static final float TOP_GAP = 24;
    private static final float PAPER_MARGIN = 18;
    private final PdfPTable table;
    private final float width;

    public WrappedFooterStamper(Document document, FontSettings settings,
                                String confidentiality, String branding) {
        if (document.isOpen()) {
            throw new IllegalArgumentException("Reserve footer space before opening the document");
        }
        applyFont(settings);
        width = document.right() - document.left();
        if (width <= 0) throw new IllegalArgumentException("Footer needs positive printable width");
        table = new PdfPTable(1);
        table.setTotalWidth(width);
        table.setLockedWidth(true);
        addText(branding);
        addText(confidentiality);
        float bottomMargin = Math.max(document.bottomMargin(), TOP_GAP + table.getTotalHeight() + PAPER_MARGIN);
        if (bottomMargin + document.topMargin() >= document.getPageSize().getHeight()) {
            throw new IllegalArgumentException("Footer leaves no room for report content");
        }
        document.setMargins(document.leftMargin(), document.rightMargin(), document.topMargin(), bottomMargin);
    }

    private void addText(String text) {
        if (text == null || text.isBlank()) return;
        PdfPCell cell = new PdfPCell(new Phrase(text, new Font(getFont(), getFontSize())));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(0);
        cell.setPaddingBottom(4);
        cell.setLeading(0, 1.2f);
        table.addCell(cell);
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        if (Math.abs(document.right() - document.left() - width) > 0.1f
                || document.bottomMargin() < TOP_GAP + table.getTotalHeight() + PAPER_MARGIN) {
            throw new IllegalStateException("Report page geometry changed after reserving its footer");
        }
        if (table.size() > 0) {
            table.writeSelectedRows(0, -1, document.left(), document.bottom() - TOP_GAP, writer.getDirectContent());
        }
    }
}
