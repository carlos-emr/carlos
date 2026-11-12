/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.rx;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The drug profile row of a Note (Rx page ▸ Note). A note has no name, brand or identifier, so the static
 * script its created date, start date and text linked to matched nothing and opened empty. Each of those three
 * cells is now one {@code <a>} with the same id and colour on every row; only rows other than a Note's get its
 * href, which is unchanged. One element per cell keeps the page's ids unique, and the page's scripts (Delete,
 * Discontinue) still find each cell by id and strike or read it.
 */
@DisplayName("Rx drug profile row for a Note")
@Tag("unit")
@Tag("rx")
@Tag("regression")
class RxProfileNoteEntryJspRegressionTest {

    private static final Path LIST_DRUGS_JSP = Path.of("src/main/webapp/WEB-INF/jsp/rx/ListDrugs.jsp");
    private static final String NOTE_ROW = "boolean noteRow = Boolean.TRUE.equals(prescriptDrug.isCustomNote());";
    private static final String HREF_GUARD = "<% if (!noteRow) { %>";
    private static final String GUARD_END = "<% } %>";
    private static final String STATIC_SCRIPT = "<%= request.getContextPath() %>/rx/ViewStaticScript2?demographicNo="
            + "<carlos:encode value='<%= String.valueOf(prescriptDrug.getDemographicId()) %>' context=\"uriComponent\"/>"
            + "&amp;regionalIdentifier=<carlos:encode value='<%= prescriptDrug.getRegionalIdentifier() %>' context=\"uriComponent\"/>"
            + "&amp;cn=<carlos:encode value='<%= prescriptDrug.getCustomName() %>' context=\"uriComponent\"/>"
            + "&amp;bn=<carlos:encode value='<%= bn %>' context=\"uriComponent\"/>";
    private static final String ATC = "&amp;atc=<carlos:encode value='<%= prescriptDrug.getAtc() %>' context=\"uriComponent\"/>";
    private static final String TEXT = "<carlos:encode value='<%= RxPrescriptionData.getFullOutLine(prescriptDrug.getSpecial()).replaceAll(\";\", \" \") %>'/>";

    /** The row's three cells that open the static script: the href each had before, and the element it is now. */
    enum Cell {
        CREATED("createDate_", " href=\"" + STATIC_SCRIPT + ATC + "\"",
                "<a id=\"createDate_<%=prescriptIdInt%>\"   <%=styleColor%>" + HREF_GUARD + " href=\"" + STATIC_SCRIPT + ATC
                        + "\"" + GUARD_END + "><%=DateToString(prescriptDrug.getCreateDate())%></a>"),
        START("rxDate_", "\n                   href=\"" + STATIC_SCRIPT + "\"",
                "<a id=\"rxDate_<%=prescriptIdInt%>\"   <%=styleColor%>" + HREF_GUARD + "\n                   href=\""
                        + STATIC_SCRIPT + "\"" + GUARD_END + "><%=startDate%>\n                </a>"),
        TEXT_CELL("prescrip_", " href=\"" + STATIC_SCRIPT + ATC + "\"",
                "<a id=\"prescrip_<%=prescriptIdInt%>\" <%=styleColor%>" + HREF_GUARD + " href=\"" + STATIC_SCRIPT + ATC
                        + "\"" + GUARD_END + "   <%=tComment%>   >" + TEXT + "</a>");

        final String id;
        final String href;
        final String element;

        Cell(String id, String href, String element) {
            this.id = id;
            this.href = href;
            this.element = element;
        }

    }

    /** The cell's element as the page writes it: from its {@code <a id=...>} to its closing tag. */
    private static String rendered(Cell cell) {
        int start = source.indexOf("<a id=\"" + cell.id + "<%=prescriptIdInt%>\"");
        assertThat(start).as("%s is an <a> element", cell).isPositive();
        return source.substring(start, source.indexOf("</a>", start) + "</a>".length());
    }

    /** The part of the element that only rows other than a Note's render. */
    private static String guardedPart(String element) {
        int guard = element.indexOf(HREF_GUARD);
        assertThat(guard).as("the href is rendered only for rows other than a Note's").isPositive();
        int from = guard + HREF_GUARD.length();
        return element.substring(from, element.indexOf(GUARD_END, from));
    }

    private static String source;

    @BeforeAll
    static void readPage() throws IOException {
        source = Files.readString(LIST_DRUGS_JSP, StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @EnumSource(Cell.class)
    @DisplayName("should render each cell as one element, whatever the row")
    void shouldRenderOneElement_forEveryRow(Cell cell) {
        assertThat(source).contains(NOTE_ROW);
        assertThat(source.split("id=\"" + cell.id + "<%=prescriptIdInt%>\"", -1))
                .as("%s's id appears once in the page", cell).hasSize(2);
        assertThat(rendered(cell)).isEqualTo(cell.element);
    }

    @ParameterizedTest
    @EnumSource(Cell.class)
    @DisplayName("should keep every other row's link to its drug's static script unchanged")
    void shouldKeepHrefUnchanged_onOtherRows(Cell cell) {
        assertThat(guardedPart(rendered(cell))).isEqualTo(cell.href);
    }

    @ParameterizedTest
    @EnumSource(Cell.class)
    @DisplayName("should give a Note row's cell no href, keeping its id and colour")
    void shouldOmitHref_onNoteRow(Cell cell) {
        String element = rendered(cell);
        String noteRow = element.replace(HREF_GUARD + guardedPart(element) + GUARD_END, "");

        // Same id and colour, so the page's scripts (Delete, Discontinue) still find and strike it.
        assertThat(noteRow).contains("<a id=\"" + cell.id + "<%=prescriptIdInt%>\"").contains("<%=styleColor%>")
                .doesNotContain("href").doesNotContain("ViewStaticScript2");
    }
}
