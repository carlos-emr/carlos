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

import java.util.Map;

/**
 * The {@code FileExtensionAndVersion} declared for a native report's stored file.
 *
 * <p>The declaration comes from what CARLOS recorded: the stored file name's extension when it
 * names a recognised format, otherwise the stored {@code contenttype} (a scanner or fax file
 * such as {@code 20260305101600scan0001} has no extension but a content type). Each is kept only
 * when the file's leading bytes agree with it. Bytes with a recognisable signature that
 * contradict it are declared as what they are, and anything else is declared as the opaque
 * {@value #OPAQUE}: the export never claims a format the bytes do not have. The CDS guide and
 * the exporter's own XML validator require a declaration for every Binary report, and an
 * unrecognised file is still exported rather than holding the patient. The original file name
 * and content type stay in the report's Notes source context.
 */
final class CdsExportReportFileFormats {
    /** The declaration for a file whose format cannot be named from its bytes. */
    static final String OPAQUE = "bin";

    /** Leading bytes the decision reads. */
    static final int PREFIX = 16;

    /** What a format's leading bytes must be; TEXT formats have no signature of their own. */
    private enum Signature {
        PDF,
        PNG,
        JPEG,
        TIFF,
        GIF,
        BMP,
        OLE2,
        ZIP,
        RTF,
        TEXT
    }

    /** Recognised declarations, spelled as a stored file name or content type gives them. */
    private static final Map<String, Signature> EXTENSIONS =
            Map.ofEntries(
                    Map.entry("pdf", Signature.PDF),
                    Map.entry("png", Signature.PNG),
                    Map.entry("jpg", Signature.JPEG),
                    Map.entry("jpeg", Signature.JPEG),
                    Map.entry("tif", Signature.TIFF),
                    Map.entry("tiff", Signature.TIFF),
                    Map.entry("gif", Signature.GIF),
                    Map.entry("bmp", Signature.BMP),
                    Map.entry("doc", Signature.OLE2),
                    Map.entry("docx", Signature.ZIP),
                    Map.entry("rtf", Signature.RTF),
                    Map.entry("txt", Signature.TEXT),
                    Map.entry("html", Signature.TEXT),
                    Map.entry("htm", Signature.TEXT));

    /** Stored content types (parameters removed) and the declaration each stands for. */
    private static final Map<String, String> MEDIA_TYPES =
            Map.ofEntries(
                    Map.entry("application/pdf", "pdf"),
                    Map.entry("image/png", "png"),
                    Map.entry("image/jpeg", "jpg"),
                    Map.entry("image/jpg", "jpg"),
                    Map.entry("image/pjpeg", "jpg"),
                    Map.entry("image/tiff", "tif"),
                    Map.entry("image/gif", "gif"),
                    Map.entry("image/bmp", "bmp"),
                    Map.entry("image/x-bmp", "bmp"),
                    Map.entry("image/x-ms-bmp", "bmp"),
                    Map.entry("application/msword", "doc"),
                    Map.entry(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "docx"),
                    Map.entry("application/rtf", "rtf"),
                    Map.entry("text/rtf", "rtf"),
                    Map.entry("text/plain", "txt"),
                    Map.entry("text/html", "html"));

    private CdsExportReportFileFormats() {}

    /**
     * @param filename the stored {@code document.docfilename}
     * @param contentType the stored {@code document.contenttype}, possibly empty or null
     * @param prefix the file's first bytes (up to {@link #PREFIX}; fewer for a short file)
     * @return a lower-case declaration of 1 to 12 letters or digits
     */
    static String extension(String filename, String contentType, byte[] prefix) {
        Signature actual = recognised(prefix);
        // The stored name first, then the stored content type: the first that the bytes bear out.
        for (String declared :
                new String[] {fromFilename(filename), fromContentType(contentType)}) {
            if (declared == null) continue;
            Signature expected = EXTENSIONS.get(declared);
            // A text declaration stands unless the bytes are a recognisable binary format.
            if (expected == Signature.TEXT ? actual == null : matches(expected, prefix))
                return declared;
        }
        return actual == null ? OPAQUE : name(actual);
    }

    private static String fromFilename(String filename) {
        if (filename == null) return null;
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return null;
        String extension = ascii(filename.substring(dot + 1));
        return extension != null && EXTENSIONS.containsKey(extension) ? extension : null;
    }

    private static String fromContentType(String contentType) {
        if (contentType == null) return null;
        int parameters = contentType.indexOf(';');
        String type = (parameters < 0 ? contentType : contentType.substring(0, parameters)).strip();
        if (!type.matches("[A-Za-z0-9.+-]{1,64}/[A-Za-z0-9.+-]{1,96}")) return null;
        return MEDIA_TYPES.get(ascii(type));
    }

    /** ASCII-only lower case of an ASCII token; anything else is not a recognised name. */
    private static String ascii(String value) {
        if (value.isEmpty() || value.length() > 160) return null;
        var lower = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 'A' && c <= 'Z') lower.append((char) (c + ('a' - 'A')));
            else if (c < 0x80) lower.append(c);
            else return null;
        }
        return lower.toString();
    }

    /** Signatures strong enough to name a format on their own. */
    private static Signature recognised(byte[] prefix) {
        for (Signature signature :
                new Signature[] {
                    Signature.PDF,
                    Signature.PNG,
                    Signature.JPEG,
                    Signature.TIFF,
                    Signature.GIF,
                    Signature.RTF
                }) if (matches(signature, prefix)) return signature;
        return null;
    }

    private static boolean matches(Signature signature, byte[] prefix) {
        return switch (signature) {
            case PDF -> starts(prefix, 0, "%PDF-") || bom(prefix) && starts(prefix, 3, "%PDF-");
            case PNG -> starts(prefix, 0, 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a);
            case JPEG -> starts(prefix, 0, 0xff, 0xd8, 0xff);
            case TIFF -> starts(prefix, 0, 'I', 'I', 0x2a, 0) || starts(prefix, 0, 'M', 'M', 0, 0x2a);
            case GIF -> starts(prefix, 0, "GIF87a") || starts(prefix, 0, "GIF89a");
            // "BM" alone is ordinary text; the header's reserved bytes must also be zero.
            case BMP -> starts(prefix, 0, "BM") && starts(prefix, 6, 0, 0, 0, 0);
            case OLE2 -> starts(prefix, 0, 0xd0, 0xcf, 0x11, 0xe0, 0xa1, 0xb1, 0x1a, 0xe1);
            case ZIP -> starts(prefix, 0, 'P', 'K', 3, 4);
            case RTF -> starts(prefix, 0, "{\\rtf");
            case TEXT -> false;
        };
    }

    private static boolean bom(byte[] prefix) {
        return starts(prefix, 0, 0xef, 0xbb, 0xbf);
    }

    private static boolean starts(byte[] prefix, int offset, String ascii) {
        int[] expected = new int[ascii.length()];
        for (int i = 0; i < expected.length; i++) expected[i] = ascii.charAt(i);
        return starts(prefix, offset, expected);
    }

    private static boolean starts(byte[] prefix, int offset, int... expected) {
        if (prefix == null || prefix.length < offset + expected.length) return false;
        for (int i = 0; i < expected.length; i++)
            if ((prefix[offset + i] & 0xff) != expected[i]) return false;
        return true;
    }

    private static String name(Signature signature) {
        return switch (signature) {
            case PDF -> "pdf";
            case PNG -> "png";
            case JPEG -> "jpg";
            case TIFF -> "tif";
            case GIF -> "gif";
            case RTF -> "rtf";
            case BMP, OLE2, ZIP, TEXT -> OPAQUE;
        };
    }
}
