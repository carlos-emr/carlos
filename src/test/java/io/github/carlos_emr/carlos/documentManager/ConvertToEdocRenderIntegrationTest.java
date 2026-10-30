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
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.TestImages;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.TestPdfs;
import io.github.carlos_emr.carlos.form.util.FormTransportContainer;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ConvertToEdoc} through the real HTML-to-PDF service, with the webapp somewhere other than the working
 * directory (as on a Debian install, where Tomcat runs in one place and the application lives in another). The
 * wkhtmltopdf step is made to fail, which is when the service renders. The web root holds a linked style sheet, a
 * CSS {@code url()} background image and an image, the eForm image directory holds the style sheet a hidden input
 * names: all of them must load, and none may print as the "[image unavailable]" box.
 */
@Tag("integration")
@Tag("documentManager")
@DisplayName("ConvertToEdoc rendering integration tests")
class ConvertToEdocRenderIntegrationTest extends CarlosTestBase {

    @TempDir
    Path directory;

    private Object originalRealPath;
    private String originalEformImagesDir;

    @BeforeEach
    void rememberSettings() {
        originalRealPath = ReflectionTestUtils.getField(ConvertToEdoc.class, "realPath");
        originalEformImagesDir = CarlosProperties.getInstance().getProperty("EFORM_IMAGES_DIR");
    }

    @AfterEach
    void restoreSettings() {
        synchronized (ConvertToEdoc.class) {
            ReflectionTestUtils.setField(ConvertToEdoc.class, "realPath", originalRealPath);
        }
        if (originalEformImagesDir == null) {
            CarlosProperties.getInstance().remove("EFORM_IMAGES_DIR");
        } else {
            CarlosProperties.getInstance().setProperty("EFORM_IMAGES_DIR", originalEformImagesDir);
        }
    }

    @Test
    @DisplayName("should load the webapp's style sheet, background image, image and the custom style sheet when the working directory is elsewhere")
    void shouldLoadWebappResources_whenWorkingDirectoryIsElsewhere() throws Exception {
        Path webapp = Files.createDirectories(directory.resolve("deployed webapp"));
        Path images = Files.createDirectories(directory.resolve("eform images"));
        Files.writeString(Files.createDirectories(webapp.resolve("css")).resolve("page.css"), ".big { font-size: 40px }");
        TestImages.write(webapp, "img/background.png", TestImages.png(60, 40));
        TestImages.write(webapp, "img/logo.png", TestImages.png(50, 30));
        Files.writeString(images.resolve("media.css"), ".media { font-size: 25px }");
        CarlosProperties.getInstance().setProperty("EFORM_IMAGES_DIR", images.toString());
        assertThat(Path.of(System.getProperty("user.dir")).toAbsolutePath().startsWith(webapp.toAbsolutePath()))
                .as("the working directory is not under the webapp").isFalse();

        String html = "<html><head><link rel=\"stylesheet\" href=\"css/page.css\">"
                + "<style>.bg { background-image: url(img/background.png); background-repeat: no-repeat; width: 120px; height: 80px }</style></head><body>"
                + "<input type=\"hidden\" id=\"" + ConvertToEdoc.CUSTOM_STYLESHEET_ID + "\" name=\"s\" value=\"media.css\">"
                + "<p class=\"big\">FAKE-Big text</p><p class=\"media\">FAKE-Media text</p>"
                + "<div class=\"bg\">FAKE-On background</div><img src=\"img/logo.png\"><img src=\"img/missing.png\" width=\"40\" height=\"20\">"
                + "</body></html>";
        FormTransportContainer form = mock(FormTransportContainer.class);
        when(form.getHTML()).thenReturn(html);
        when(form.getRealPath()).thenReturn(webapp.toString());
        when(form.getFormName()).thenReturn("FAKE-form");
        when(form.getDemographicNo()).thenReturn("1");

        Path pdf;
        try (MockedConstruction<InternalEDocConverter> converter = Mockito.mockConstruction(InternalEDocConverter.class, (mock, context) ->
                doThrow(new IOException("wkhtmltopdf is not available")).when(mock).convert(any(), any()))) {
            pdf = ConvertToEdoc.saveAsTempPDF(form);
        }

        assertThat(pdf).isNotNull();
        byte[] bytes = Files.readAllBytes(pdf);
        String text = String.join("\n", TestPdfs.pageTexts(bytes)).replaceAll("\\s+", " ");
        assertThat(text).contains("FAKE-Big text", "FAKE-Media text", "FAKE-On background");
        // The linked style sheet made the text 40 px, the custom one 25 px: 0.75 pt a pixel, at the page zoom of 0.8.
        List<TextPosition> glyphs = TestPdfs.glyphs(bytes);
        assertThat(sizeOf(glyphs, "FAKE-Big")).isCloseTo(40f * 0.75f * 0.8f, within(0.1f));
        assertThat(sizeOf(glyphs, "FAKE-Media")).isCloseTo(25f * 0.75f * 0.8f, within(0.1f));
        // The picture and the background image are drawn once each; the picture that is not there is a box.
        assertThat(TestPdfs.imageDraws(bytes)).isEqualTo(2);
        assertThat(text.split("\\[image unavailable\\]", -1)).hasSize(2);
        Files.deleteIfExists(pdf);
    }

    private static float sizeOf(List<TextPosition> glyphs, String phrase) {
        StringBuilder all = new StringBuilder();
        for (TextPosition glyph : glyphs) {
            all.append(glyph.getUnicode());
        }
        int at = all.indexOf(phrase);
        assertThat(at).as(phrase).isGreaterThanOrEqualTo(0);
        return TestPdfs.sizeOnPage(glyphs.get(at));
    }
}
