/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.util.Arrays;
import java.util.Locale;

/**
 * Records what the JDK itself resolves for the logical "SansSerif" font, for the report font
 * matrix (scripts/check-report-fonts.sh, issue #5046).
 *
 * <p>fc-match only shows fontconfig's answer. The JDK makes its own choice and caches it under
 * {@code ${user.home}/.java/fonts}, so a scenario that only changes FONTCONFIG_FILE can silently
 * keep the previous host's mapping. Run with the same environment and {@code -Duser.home} as the
 * tests, this prints the families the JDK can see and the 12pt SansSerif line height, which is
 * what JasperReports compares against a field's box height.
 *
 * <p>Usage: {@code java -Duser.home=<dir> scripts/fonts/JavaFontProbe.java}
 *
 * @since 2026-10-23
 */
public final class JavaFontProbe {
    private JavaFontProbe() { }

    /**
     * Prints {@code sansserif.line12=<points>} and {@code families=<a|b|...>} to standard output.
     *
     * @param args String[] command-line arguments; none are read
     */
    public static void main(String[] args) {
        String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ROOT);
        LineMetrics metrics = new Font(Font.SANS_SERIF, Font.PLAIN, 12)
                .getLineMetrics("Hg", new FontRenderContext(null, true, true));
        float line = metrics.getAscent() + metrics.getDescent() + metrics.getLeading();
        System.out.printf(Locale.ROOT, "sansserif.line12=%.2f%n", line);
        System.out.println("families=" + String.join("|", Arrays.stream(families).sorted().toList()));
    }
}
