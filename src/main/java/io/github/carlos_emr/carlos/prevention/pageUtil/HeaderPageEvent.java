package io.github.carlos_emr.carlos.prevention.pageUtil;

import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Reusable page event handler for rendering headers on PDFs.
 * This replaces the deprecated HeaderFooter approach from older PDF library versions.
 */
public class HeaderPageEvent extends PdfPageEventHelper {
    private Phrase headerPhrase;
    private boolean hasBorder;
    private float headerPadding;
    private float borderSpacing;
    private static final Logger logger = MiscUtils.getLogger();
    
    /**
     * Constructor for HeaderPageEvent.
     * @param headerPhrase The header text as a Phrase.
     * @param hasBorder Whether to draw a border line below the header.
     * @param headerPadding Padding space between the header text and the top of the page.
     * @param borderSpacing Spacing between the bottom of the header text and the border line.
     */
    public HeaderPageEvent(Phrase headerPhrase, boolean hasBorder, float headerPadding, float borderSpacing) {
        this.headerPhrase = headerPhrase;
        this.hasBorder = hasBorder;
        this.headerPadding = headerPadding;
        this.borderSpacing = borderSpacing;
    }
    
    
    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        if (headerPhrase != null) {
            PdfContentByte contentByte = writer.getDirectContent();
            ColumnText columnText = new ColumnText(contentByte);

            Rectangle headerRect = calculateHeaderSize(document);
            columnText.setSimpleColumn(headerRect.getLeft(), headerRect.getBottom(),
                    headerRect.getRight(), headerRect.getTop());
            columnText.setAlignment(Element.ALIGN_RIGHT);
            columnText.addText(headerPhrase);

            try {
                columnText.go();
            } catch (DocumentException e) {
                logger.error("Document exception error: " + e);
            }
            
            if (hasBorder) {
                float textBottom = calculateBorderYPosition(columnText);

                // Draw border line at the bottom of the header
                contentByte.saveState();
                contentByte.setLineWidth(0.90f);
                contentByte.moveTo(document.left(), textBottom);
                contentByte.lineTo(document.right(), textBottom);
                contentByte.stroke();
                contentByte.restoreState();
            }
        } else {
            logger.error("Header phrase is null");
            return;
        }
    }

    /**
     * Calculate the size and position of the header rectangle.
     * @param document The PDF document.
     * @return The Rectangle defining the header area.
     */
    private Rectangle calculateHeaderSize (Document document) {
        // Calculate header height from top margin
        float pageHeight = document.getPageSize().getHeight();
        float topMargin = pageHeight - document.top();

        // Set bounds of header rectangle
        float x1 = document.left();
        float x2 = document.right();
        float y1 = document.top();
        float y2 = document.top() + (topMargin - headerPadding);

        return new Rectangle(x1, y1, x2, y2);
    }

    /**
     * Calculate the Y position for the border line below the header.
     * @param columnText The ColumnText containing the header text.
     * @return The Y position for the border line.
     */
    private float calculateBorderYPosition (ColumnText columnText) {
        // Get the Y position as to where the header text ended
        float textBottom = columnText.getYLine();

        // Move the text bottom down a bit to create space for the border line
        textBottom -= borderSpacing;

        return textBottom;
    }
}
