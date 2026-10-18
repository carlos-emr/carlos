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
package io.github.carlos_emr.carlos.form;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The legacy form pages carry {@code <base href="<context>/">}, so an address of another form page written without
 * {@code form/}, in a link or in a pop-up call, resolves to {@code /<context>/formX}, which does not exist, instead of
 * the page's route {@code /<context>/form/formX}. The page links and pop-ups of BC-NewBorn 2008, BC-AR 2007,
 * BC-AR 2012 and Rourke, and the physician-name picker on seven BC form pages, answered 404 that way
 * (issue #4930).
 *
 * @since 2026-10-18
 */
@DisplayName("Links between form pages")
@Tag("unit")
@Tag("form")
class FormPageLinksUnitTest {

    private static final Path FORM_JSPS = Path.of("src/main/webapp/WEB-INF/jsp/form");
    /** The base every legacy form page writes, in its three spellings: the context root. */
    private static final Pattern CONTEXT_ROOT_BASE = Pattern.compile("<base href=[^>]*getContextPath\\(\\)\\s*\\+\\s*\"/\"");
    /**
     * A quoted address that starts with a form page's name, with a query or on its own ({@code 'formX?…'} or
     * {@code 'formX'}): written relative to the base, without form/. Only names of real form pages count, so a quoted
     * {@code "formId"} is not one.
     */
    private static final Pattern BARE_PAGE_ADDRESS = Pattern.compile("([\"'])(form[A-Za-z0-9_]*)(?:\\?|\\1)");
    /** HTML and JSP comments: a commented-out link is never followed. */
    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->|<%--.*?--%>", Pattern.DOTALL);

    /** The page's source without its comments. */
    private static String live(String source) {
        return COMMENT.matcher(source).replaceAll("");
    }

    private static String read(String page) throws IOException {
        return live(Files.readString(FORM_JSPS.resolve(page + ".jsp"), StandardCharsets.UTF_8));
    }

    @Test
    void shouldAddressOtherFormPagesUnderForm_whenThePageHasAContextRootBase() throws IOException {
        List<String> bare = new ArrayList<>();
        int pagesWithBase = 0;
        try (Stream<Path> files = Files.list(FORM_JSPS)) {
            for (Path jsp : files.filter(path -> path.toString().endsWith(".jsp")).sorted().toList()) {
                String source = live(Files.readString(jsp, StandardCharsets.UTF_8));
                if (!CONTEXT_ROOT_BASE.matcher(source).find()) {
                    continue;
                }
                pagesWithBase++;
                Matcher links = BARE_PAGE_ADDRESS.matcher(source);
                while (links.find()) {
                    if (Files.exists(FORM_JSPS.resolve(links.group(2) + ".jsp"))) {
                        bare.add(jsp.getFileName() + " -> " + links.group(2));
                    }
                }
            }
        }
        assertThat(pagesWithBase).as("form pages with the context-root base").isPositive();
        assertThat(bare).as("addresses that resolve to /<context>/formX instead of /<context>/form/formX").isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            // Page links
            "formBCNewBorn2008pg1, formBCNewBorn2008pg2 formBCNewBorn2008pg3",
            "formBCNewBorn2008pg2, formBCNewBorn2008pg1 formBCNewBorn2008pg3",
            "formBCNewBorn2008pg3, formBCNewBorn2008pg1 formBCNewBorn2008pg2",
            "formbcar2007pg1, formbcar2007pg2 formbcar2007pg3",
            "formbcar2012pg1, formbcar2012pg2 formbcar2012pg3",
            "formbcar2012pg2, formbcar2012pg1 formbcar2012pg3",
            "formbcar2012pg3, formbcar2012pg1 formbcar2012pg2",
            "formrourke1, formrourke2 formrourke3",
            "formrourke2, formrourke1 formrourke3",
            "formrourke3, formrourke1 formrourke2",
            // The physician-name picker, opened in a pop-up
            "formBCNewBorn2008pg1, formbcarpg1namepopup",
            "formbcnewbornpg1, formbcarpg1namepopup",
            "formbcbirthsummo, formbcarpg1namepopup",
            "formbcbirthsummo2008, formbcarpg1namepopup",
            "formbcarpg1, formbcarpg1namepopup",
            "formbcar2007pg1, formbcarpg1namepopup",
            "formbcar2012pg1, formbcarpg1namepopup",
    })
    void shouldAddressOtherPages_underForm(String page, String otherPages) throws IOException {
        String source = read(page);
        for (String other : otherPages.split(" ")) {
            assertThat(source).as("%s opens %s under form/", page, other)
                    .containsPattern("form/" + other + "[?\"']")
                    .doesNotContainPattern("[\"']" + other + "[?\"']");
        }
    }
}
