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

import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSString;

/**
 * Makes a printed form's PDF reproducible: the same record gives the same bytes. OpenPDF (used
 * directly and by JasperReports) writes the current time as the creation and modification dates,
 * a time-based file identifier and a random six-letter tag on each embedded font subset. This
 * removes the dates, derives the identifier from the record and renames each subset tag by its
 * order in the file. Nothing drawn on a page changes.
 *
 * @since 2026-11-13
 */
final class CdsExportPrintedPdf {
    private CdsExportPrintedPdf() {}

    /**
     * The reproducible form of a PDF.
     *
     * @param pdf the rendered PDF
     * @param seed 64 hexadecimal characters naming the record (its identifier is derived from it)
     * @throws CdsExportMappingReviewException {@code CDS_EXPORT_DATA_SUMMARY_RENDER_FAILED} when the
     *     bytes are not a PDF PDFBox can load and write
     */
    static byte[] reproducible(byte[] pdf, String seed) throws IOException {
        if (seed == null || !seed.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("CDS_EXPORT_PRINTED_FORM_SEED_INVALID");
        try (var document = Loader.loadPDF(pdf)) {
            var information = document.getDocumentInformation();
            information.setCreationDate(null);
            information.setModificationDate(null);
            var cos = document.getDocument();
            var objects = new ArrayList<COSObject>(cos.getObjectsByType(COSName.FONT, COSName.FONT_DESC));
            objects.sort(Comparator.comparing(COSObject::getKey));
            var tags = new HashMap<String, String>();
            for (var object : objects)
                if (object.getObject() instanceof COSDictionary font) {
                    rename(font, COSName.BASE_FONT, tags);
                    rename(font, COSName.FONT_NAME, tags);
                }
            byte[] id = HexFormat.of().parseHex(seed.substring(0, 32));
            var identifier = new COSArray();
            identifier.add(new COSString(id));
            identifier.add(new COSString(id));
            cos.getTrailer().setItem(COSName.ID, identifier);
            var output = new ByteArrayOutputStream(Math.max(1024, pdf.length));
            document.save(output);
            return output.toByteArray();
        } catch (IOException | RuntimeException unreadable) {
            throw new CdsExportMappingReviewException(CdsExportDataSummaryPdfService.FAILED);
        }
    }

    /** Replaces a random subset tag ("ABCDEF+") with the next tag in file order. */
    private static void rename(COSDictionary font, COSName key, Map<String, String> tags) {
        var name = font.getCOSName(key);
        if (name == null) return;
        String value = name.getName();
        if (!value.matches("[A-Z]{6}\\+.+")) return;
        String tag = tags.computeIfAbsent(value.substring(0, 6), ignored -> tag(tags.size()));
        font.setName(key, tag + value.substring(6));
    }

    /** The n-th tag: six capital letters, "AAAAAA", "AAAAAB", ... */
    static String tag(int index) {
        var letters = new char[6];
        int value = index;
        for (int position = 5; position >= 0; position--) {
            letters[position] = (char) ('A' + value % 26);
            value /= 26;
        }
        return new String(letters);
    }

    /** For tests: the subset tags of a PDF in file order. */
    static List<String> tags(byte[] pdf) throws IOException {
        var result = new ArrayList<String>();
        try (var document = Loader.loadPDF(pdf)) {
            var objects = new ArrayList<COSObject>(
                    document.getDocument().getObjectsByType(COSName.FONT, COSName.FONT_DESC));
            objects.sort(Comparator.comparing(COSObject::getKey));
            for (var object : objects)
                if (object.getObject() instanceof COSDictionary font)
                    for (var key : List.of(COSName.BASE_FONT, COSName.FONT_NAME)) {
                        var name = font.getCOSName(key);
                        if (name != null && name.getName().matches("[A-Z]{6}\\+.+"))
                            result.add(name.getName().substring(0, 6));
                    }
        }
        return result;
    }
}
