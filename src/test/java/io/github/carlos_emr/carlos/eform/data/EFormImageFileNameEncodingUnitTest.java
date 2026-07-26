/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

package io.github.carlos_emr.carlos.eform.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins percent-encoding of image filenames at {@code ${oscar_image_path}} substitution.
 *
 * <p>Real OSCAR Galaxy packages ship images whose names contain characters that are legal on disk
 * but illegal unencoded in a request target — {@code fe00622c-1[1].png}, a Windows duplicate-download
 * artifact. The ZIP importer stores the name verbatim, so the form emits
 * {@code …/displayImage?imagefile=fe00622c-1[1].png} and Tomcat rejects the whole request at the
 * HTTP parser ({@code Invalid character found in the request target}, HTTP 400) before any CARLOS
 * code runs. Two of five packages in one corpus batch failed this way, on both the viewer and the
 * render paths.</p>
 *
 * <p>Encoding at substitution time is preferred over renaming on disk because it also repairs forms
 * that are already imported.</p>
 */
@DisplayName("EForm image filename URL encoding")
@Tag("unit")
@Tag("fast")
@Tag("eform")
class EFormImageFileNameEncodingUnitTest {

    private static String substituted(String bodyHtml) {
        EForm eform = new EForm();
        eform.setFormHtml("<html><body>" + bodyHtml + "</body></html>");
        eform.setImagePath("/carlos");
        return eform.getFormHtml();
    }

    @Test
    @DisplayName("should percent-encode square brackets so Tomcat accepts the request target")
    void shouldPercentEncodeSquareBrackets_inImageFileName() {
        String html = substituted("<img src=\"${oscar_image_path}fe00622c-1[1].png\">");

        assertThat(html).contains("/carlos/eform/displayImage?imagefile=fe00622c-1%5B1%5D.png");
        assertThat(html).doesNotContain("[1].png");
    }

    @Test
    @DisplayName("should encode a space inside a quoted filename rather than stopping at it")
    void shouldEncodeSpace_insideQuotedFileName() {
        // Inside a quoted attribute the space belongs to the filename; only the closing quote ends
        // the value.
        String html = substituted("<img src=\"${oscar_image_path}my scan.png\">");

        assertThat(html).contains("imagefile=my%20scan.png");
    }

    @Test
    @DisplayName("should leave URL-structural characters untouched")
    void shouldLeaveUrlStructuralCharacters_untouched() {
        // Encoding '/', '?', '&' or '=' would break references that work today, so the encoded set
        // is deliberately narrow rather than a general URL encode.
        String html = substituted("<img src=\"${oscar_image_path}sub/dir/bg.png?v=2&amp;x=1\">");

        assertThat(html).contains("imagefile=sub/dir/bg.png?v=2");
        assertThat(html).doesNotContain("%2F");
        assertThat(html).doesNotContain("%3F");
    }

    @Test
    @DisplayName("should stop at the closing paren of a CSS url() rather than encoding it")
    void shouldStopAtClosingParen_ofCssUrl() {
        String html = substituted(
                "<div style=\"background-image:url(${oscar_image_path}bg[1].png)\"></div>");

        assertThat(html).contains("bg%5B1%5D.png)");
        // The CSS must still terminate; encoding the paren would corrupt the declaration.
        assertThat(html).doesNotContain("%29");
    }

    @Test
    @DisplayName("should leave a JavaScript concatenation of the marker alone")
    void shouldLeaveJavaScriptConcatenation_alone() {
        // Forms using the off-line authoring idiom build the URL at runtime:
        //   var prefix = (…) ? '${oscar_image_path}' : '';
        // The closing quote immediately follows the prefix, so there is no filename to encode and
        // the pass must not run on into the surrounding script.
        String html = substituted("<script>var p = '${oscar_image_path}' + name;</script>");

        assertThat(html).contains("'/carlos/eform/displayImage?imagefile=' + name");
    }

    @Test
    @DisplayName("should encode every occurrence, not only the first")
    void shouldEncodeEveryOccurrence_notOnlyTheFirst() {
        String html = substituted(
                "<img src=\"${oscar_image_path}a[1].png\"><img src=\"${oscar_image_path}b[2].png\">");

        assertThat(html).contains("imagefile=a%5B1%5D.png");
        assertThat(html).contains("imagefile=b%5B2%5D.png");
    }
}
