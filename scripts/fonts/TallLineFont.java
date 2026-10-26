/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes a copy of a TrueType font whose line metrics are much taller than usual, for the report
 * font matrix (scripts/check-report-fonts.sh, issue #5046).
 *
 * <p>A host default sans-serif face with a tall line (Noto Sans, WenQuanYi Zen Hei, Thai faces)
 * is what made JasperReports fit zero lines into short field boxes and leave patient text out of
 * a valid PDF. CI images do not ship such a face, so the matrix derives one: the glyphs are
 * unchanged and only the vertical metrics in the hhea and OS/2 tables are raised, to an ascent of
 * 1.25 em and a descent of 0.40 em (a 1.65 em line). Table checksums are not recomputed; neither
 * fontconfig, FreeType nor the JDK verifies them.
 *
 * <p>Usage: {@code java scripts/fonts/TallLineFont.java <input.ttf> <output.ttf>}
 *
 * @since 2026-10-23
 */
public final class TallLineFont {
    private TallLineFont() { }

    /**
     * Writes the tall-line copy of a TrueType font.
     *
     * @param args String[] the input font path, then the output font path
     * @throws IOException if the input cannot be read or the output cannot be written
     * @throws IllegalArgumentException if there are not exactly two arguments, or the font lacks
     *         a head, hhea or OS/2 table
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: TallLineFont <input.ttf> <output.ttf>");
        }
        ByteBuffer font = ByteBuffer.wrap(Files.readAllBytes(Path.of(args[0])));
        int head = table(font, "head");
        int hhea = table(font, "hhea");
        int os2 = table(font, "OS/2");
        int unitsPerEm = font.getShort(head + 18) & 0xFFFF;
        short ascent = (short) Math.round(unitsPerEm * 1.25);
        short descent = (short) Math.round(unitsPerEm * 0.40);
        font.putShort(hhea + 4, ascent);
        font.putShort(hhea + 6, (short) -descent);
        font.putShort(hhea + 8, (short) 0);
        font.putShort(os2 + 68, ascent);
        font.putShort(os2 + 70, (short) -descent);
        font.putShort(os2 + 72, (short) 0);
        font.putShort(os2 + 74, ascent);
        font.putShort(os2 + 76, descent);
        Files.write(Path.of(args[1]), font.array());
    }

    /** Returns the offset of a table, from the font's table directory. */
    private static int table(ByteBuffer font, String tag) {
        int tables = font.getShort(4) & 0xFFFF;
        for (int i = 0; i < tables; i++) {
            int record = 12 + i * 16;
            byte[] name = new byte[4];
            font.get(record, name);
            if (tag.equals(new String(name, java.nio.charset.StandardCharsets.US_ASCII))) {
                return font.getInt(record + 8);
            }
        }
        throw new IllegalArgumentException("font has no " + tag + " table");
    }
}
