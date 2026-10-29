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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit coverage for {@link HtmlPdfRenderRequest}: the validation that makes a request safe to hand
 * to the render service, its defensive copy of the allowed directories, and a {@code toString()}
 * that can never write PHI or a path to a log.
 *
 * @since 2026-10-29
 */
@DisplayName("HtmlPdfRenderRequest")
@Tag("unit")
@Tag("htmlpdf")
class HtmlPdfRenderRequestUnitTest {

    private static final HtmlPdfRenderSource FORM = HtmlPdfRenderSource.FORM;

    @TempDir
    Path tmp;

    @TempDir
    Path otherTmp;

    @Test
    void shouldRejectNullHtml_forConstructor() {
        assertThatNullPointerException()
                .isThrownBy(() -> new HtmlPdfRenderRequest(null, null, List.of(tmp), FORM));
    }

    @Test
    void shouldRejectNullSourceOrDirectories_forConstructor() {
        List<Path> withNullEntry = Arrays.asList(tmp, null);

        assertThatNullPointerException().as("null source")
                .isThrownBy(() -> new HtmlPdfRenderRequest("x", null, List.of(tmp), null));
        assertThatNullPointerException().as("null directory list")
                .isThrownBy(() -> new HtmlPdfRenderRequest("x", null, null, FORM));
        assertThatNullPointerException().as("null entry in the directory list")
                .isThrownBy(() -> new HtmlPdfRenderRequest("x", null, withNullEntry, FORM));
    }

    @Test
    void shouldRejectEmptyAllowedDirectories_forConstructor() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HtmlPdfRenderRequest("x", null, List.of(), FORM));
    }

    @Test
    void shouldRejectRelativeOrRootDirectory_forConstructor() {
        assertThatIllegalArgumentException().as("relative directory")
                .isThrownBy(() -> requestWith(null, Path.of("rel")));
        assertThatIllegalArgumentException().as("filesystem root")
                .isThrownBy(() -> requestWith(null, Path.of("/")));
        assertThatIllegalArgumentException().as("unnormalised directory")
                .isThrownBy(() -> requestWith(null, tmp.resolve("a/../b")));
    }

    @Test
    void shouldRejectBaseOutsideAllowed_forConstructor() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> requestWith(otherTmp, tmp));
    }

    @Test
    void shouldRejectNamePrefixSibling_asBase() {
        // Containment is by path elements, not by string prefix: "<tmp>-sibling" shares the
        // allowed directory's name as a prefix but is not inside it.
        Path sibling = tmp.resolveSibling(tmp.getFileName() + "-sibling");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> requestWith(sibling, tmp));
    }

    @Test
    void shouldRejectRelativeOrUnnormalisedBase_forConstructor() {
        // "<tmp>/sub/../../escape" starts with the allowed directory element by element, but
        // resolves outside it: a lexical startsWith() check alone would let it through.
        assertThatIllegalArgumentException().as("base that climbs out of the allowed directory")
                .isThrownBy(() -> requestWith(tmp.resolve("sub/../../escape"), tmp));
        assertThatIllegalArgumentException().as("relative base")
                .isThrownBy(() -> requestWith(Path.of("rel"), tmp));
    }

    @Test
    void shouldAcceptNullBase_forMissingWebappRoot() {
        HtmlPdfRenderRequest request = requestWith(null, tmp);

        assertThat(request.baseDirectory()).isNull();
        assertThat(request.allowedDirectories()).containsExactly(tmp);
    }

    @Test
    void shouldAcceptBaseUnderAnyAllowedDirectory_forConstructor() {
        Path nested = tmp.resolve("sub").resolve("deeper");

        assertThat(requestWith(tmp, tmp, otherTmp).baseDirectory())
                .as("base equal to an allowed directory").isEqualTo(tmp);
        assertThat(requestWith(nested, tmp, otherTmp).baseDirectory())
                .as("base nested in the first allowed directory").isEqualTo(nested);
        assertThat(requestWith(otherTmp.resolve("sub"), tmp, otherTmp).baseDirectory())
                .as("base nested in the second allowed directory").isEqualTo(otherTmp.resolve("sub"));
    }

    @Test
    void shouldAcceptEmptyHtml_forConstructor() {
        // Whitespace-only input is the service's decision (nothing to print), not a malformed
        // request, so the boundary type does not reject it.
        HtmlPdfRenderRequest request = new HtmlPdfRenderRequest(" \n", null, List.of(tmp), FORM);

        assertThat(request.html()).isEqualTo(" \n");
        assertThat(request.source()).isEqualTo(FORM);
    }

    @Test
    void shouldCopyAllowedDirectories_forDefensiveCopy() {
        List<Path> source = new ArrayList<>(List.of(tmp));
        HtmlPdfRenderRequest request = new HtmlPdfRenderRequest("x", null, source, FORM);

        source.add(otherTmp);
        source.remove(tmp);

        assertThat(request.allowedDirectories()).containsExactly(tmp);
        assertThatThrownBy(() -> request.allowedDirectories().add(otherTmp))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> request.allowedDirectories().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldOmitHtmlAndPaths_fromToString() throws IOException {
        Path base = Files.createDirectory(tmp.resolve("FAKE-dir"));
        String html = "FAKE-Patient <b>x</b>";
        HtmlPdfRenderRequest request = new HtmlPdfRenderRequest(html, base, List.of(tmp), FORM);

        String text = request.toString();

        assertThat(text).contains("source=FORM", "htmlLength=");
        assertThat(text).doesNotContain("FAKE-", "<b>", tmp.toString());
        assertThat(text).isEqualTo(
                "HtmlPdfRenderRequest[source=FORM, htmlLength=" + html.length() + ", allowedDirectories=1]");
    }

    @Test
    void shouldNameFailedCheckOnly_inValidationMessages() {
        // Facades catch the exception and may log its message, so it must never carry a path.
        Path fakeUnnormalised = tmp.resolve("FAKE-dir/../x");
        Path fakeOutside = otherTmp.resolve("FAKE-base");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> requestWith(null, fakeUnnormalised))
                .withMessageNotContaining("FAKE-")
                .withMessageNotContaining(tmp.toString());
        assertThatIllegalArgumentException()
                .isThrownBy(() -> requestWith(fakeOutside, tmp))
                .withMessageNotContaining("FAKE-")
                .withMessageNotContaining(otherTmp.toString());
    }

    private HtmlPdfRenderRequest requestWith(Path baseDirectory, Path... allowedDirectories) {
        return new HtmlPdfRenderRequest("x", baseDirectory, List.of(allowedDirectories), FORM);
    }
}
