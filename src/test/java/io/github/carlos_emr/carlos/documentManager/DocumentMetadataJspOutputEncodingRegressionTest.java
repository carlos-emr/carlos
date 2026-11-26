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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the output encoding of stored document metadata on the document list, the document editor
 * and the multi-file upload page (issue #3057, leaf #2656).
 *
 * <ul>
 *   <li>Document sub-classes ({@code ctl_doc_class}) feed the sub-class autocomplete as a JavaScript
 *       array literal inside a {@code <script>} block on three pages. A raw print let a stored
 *       sub-class close the string, or the script element, and run as script on every page that
 *       offers document filing. All three use the JavaScript-string encoder, as the HTML-document
 *       editor already did.</li>
 *   <li>The multi-file upload page listed queue names and provider names raw in its two
 *       selects, unlike its sibling {@code documentUploader.jsp}; the document editor printed the
 *       provider number of its responsible-provider options raw.</li>
 * </ul>
 *
 * <p>The behavioural proof is the installed {@code xss-poison-documents-inbox} browser check;
 * these source contracts keep a raw print from coming back between package runs.</p>
 *
 * @since 2026-11-26
 */
@DisplayName("Document metadata JSP output encoding")
@Tag("unit")
@Tag("document")
@Tag("security")
class DocumentMetadataJspOutputEncodingRegressionTest {

    private static final String JSP_DIR = "src/main/webapp/WEB-INF/jsp/documentManager/";
    private static final String CARLOS_TAGLIB = "<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>";

    private static String read(String name) throws IOException {
        return Files.readString(Path.of(JSP_DIR + name), StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("Sub-class autocomplete array")
    class SubClassAutocomplete {

        private static final String ENCODED_ELEMENT =
                "\"<carlos:encode value='<%= subClasses.get(i) %>' context='javaScript'/>\"";

        /**
         * Any {@code subClasses.get(i)} printed by a scriptlet expression that is not the value of a
         * {@code <carlos:encode>} tag.
         */
        private static final Pattern RAW_ELEMENT =
                Pattern.compile("(?<!<carlos:encode value=')<%=\\s*+(?:\\(String\\)\\s*+|\\(\\s*+)?subClasses\\.get\\(");

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"documentReport.jsp", "editDocument.jsp", "addedithtmldocument.jsp"})
        @DisplayName("should JavaScript-string encode every stored sub-class in the autocomplete array")
        void shouldEncodeSubClasses_asJavaScriptStrings(String jsp) throws IOException {
            String page = read(jsp);

            assertThat(page).contains(CARLOS_TAGLIB).contains(ENCODED_ELEMENT);
            assertThat(RAW_ELEMENT.matcher(page).find())
                    .as("%s prints a stored document sub-class without encoding", jsp)
                    .isFalse();
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "\"<%=subClasses.get(i)%>\",",
                "<input value='<%= subClasses.get(i) %>'>",
                "\"<%= (String) subClasses.get(i) %>\","
        })
        @DisplayName("should detect a raw sub-class print, also inside a single-quoted attribute")
        void shouldDetectRawPrint_forUnencodedFixture(String fixture) {
            assertThat(RAW_ELEMENT.matcher(fixture).find()).isTrue();
        }

        @Test
        @DisplayName("should not count the expression handed to the CARLOS encoder as a raw print")
        void shouldAcceptEncodedPrint_forCarlosEncodeFixture() {
            assertThat(RAW_ELEMENT.matcher(ENCODED_ELEMENT).find()).isFalse();
        }
    }

    @Nested
    @DisplayName("Document list form state")
    class DocumentListFormState {

        @Test
        @DisplayName("should attribute-encode the session user carried by the combine-PDF form")
        void shouldEncodeCurrentUser_inCombinePdfForm() throws IOException {
            String page = read("documentReport.jsp");

            assertThat(page)
                    .contains("<input type=\"hidden\" name=\"curUser\" value=\"<carlos:encode value='<%= curUser %>' context=\"htmlAttribute\"/>\">")
                    .doesNotContain("value=\"<%=curUser%>\"");
        }
    }

    @Nested
    @DisplayName("Document editor responsible-provider select")
    class EditorProviderSelect {

        @Test
        @DisplayName("should attribute-encode the stored provider number of each responsible-provider option")
        void shouldEncodeProviderNumber_inResponsibleProviderOption() throws IOException {
            String page = read("editDocument.jsp");

            assertThat(page)
                    .contains("<option value=\"<carlos:encode value='<%= pd.get(\"providerNo\") %>' context=\"htmlAttribute\"/>\"")
                    .doesNotContain("<option value=\"<%=pd.get(\"providerNo\")%>\"");
        }
    }

    @Nested
    @DisplayName("Multi-file upload page")
    class MultiFileUpload {

        @Test
        @DisplayName("should declare the CARLOS encoder taglib")
        void shouldDeclareCarlosTaglib_forUploadPage() throws IOException {
            assertThat(read("html5AddDocuments.jsp")).contains(CARLOS_TAGLIB);
        }

        @Test
        @DisplayName("should HTML-encode stored queue names in the queue select")
        void shouldEncodeQueueNames_inQueueSelect() throws IOException {
            String page = read("html5AddDocuments.jsp");

            assertThat(page)
                    .contains("<carlos:encode value='<%= qName %>' context=\"html\"/>")
                    .doesNotContain("<%= qName%>")
                    .doesNotContain("<%=qName%>");
        }

        @Test
        @DisplayName("should encode provider numbers as attributes and provider names as text in the provider select")
        void shouldEncodeProviderOptions_inProviderSelect() throws IOException {
            String page = read("html5AddDocuments.jsp");

            assertThat(page)
                    .contains("<option value=\"<carlos:encode value='<%= optionProviderNo %>' context=\"htmlAttribute\"/>\"")
                    .contains("<carlos:encode value='<%= (String) h.get(\"lastName\") %>' context=\"html\"/>")
                    .contains("<carlos:encode value='<%= (String) h.get(\"firstName\") %>' context=\"html\"/>")
                    .doesNotContain("<%= h.get(\"providerNo\")%>")
                    .doesNotContain("<%= h.get(\"lastName\")%>")
                    .doesNotContain("<%= h.get(\"firstName\")%>");
        }

        @Test
        @DisplayName("should URI-encode the selected queue and provider when building the upload URL")
        void shouldUriEncodeSelection_inUploadUrl() throws IOException {
            String page = read("html5AddDocuments.jsp");

            assertThat(page)
                    .contains("encodeURIComponent(document.getElementById('queue').value)")
                    .contains("encodeURIComponent(document.getElementById('provider').value)")
                    .contains("&providers=<carlos:encode value='<%= provider %>' context=\"uriComponent\"/>")
                    .doesNotContain("&providers=<%=provider%>");
        }
    }
}
