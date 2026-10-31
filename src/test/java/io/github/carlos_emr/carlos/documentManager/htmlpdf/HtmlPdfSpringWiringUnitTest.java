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
package io.github.carlos_emr.carlos.documentManager.htmlpdf;

import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderRequest;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderResult;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderSource;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.service.HtmlPdfRenderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that the module wires itself from component scanning of its own package alone, as the
 * application's broad scan finds it: the five package-private beans, the public entry point with its
 * two constructors, and a real render through the production wiring (the real font-file service, image
 * step and layout). Nothing from the rest of CARLOS is needed, which is also what keeps the module a
 * leaf of the dependency graph.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("HTML-to-PDF module Spring wiring")
class HtmlPdfSpringWiringUnitTest {

    private static final String MODULE_PACKAGE = "io.github.carlos_emr.carlos.documentManager.htmlpdf";

    @TempDir
    Path webRoot;

    @Test
    @DisplayName("should wire the render service from a scan of the module alone and render a PDF with it")
    void shouldWireRenderService_fromModuleScanAlone() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MODULE_PACKAGE)) {
            HtmlPdfRenderService service = context.getBean(HtmlPdfRenderService.class);

            HtmlPdfRenderResult result = service.render(new HtmlPdfRenderRequest(
                    "<html><body><p>FAKE-Body</p></body></html>", webRoot, List.of(webRoot), HtmlPdfRenderSource.FORM));

            assertThat(result.pdf()).startsWith("%PDF-".getBytes(StandardCharsets.US_ASCII));
            assertThat(result.degradations()).isEmpty();
        }
    }

    @Test
    @DisplayName("should register exactly the six beans of the design as singletons under their expected names")
    void shouldRegisterTheSixBeans_asSingletons() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MODULE_PACKAGE)) {
            String[] names = Arrays.stream(context.getBeanDefinitionNames())
                    .filter(name -> !name.startsWith("org.springframework.")).sorted().toArray(String[]::new);

            assertThat(names).containsExactly("bundledFontFileService", "htmlPdfRenderService", "printDocumentComposer",
                    "printImageService", "printLayoutService", "renderedPdfValidator");
            for (String name : names) {
                assertThat(context.isSingleton(name)).as(name).isTrue();
            }
        }
    }
}
