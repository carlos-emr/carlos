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
 * script its created date, start date and text linked to matched nothing and opened empty. On a Note row
 * those three cells are now plain text; every other row keeps its three links unchanged.
 */
@DisplayName("Rx drug profile row for a Note")
@Tag("unit")
@Tag("rx")
@Tag("regression")
class RxProfileNoteEntryJspRegressionTest {

    private static final Path LIST_DRUGS_JSP = Path.of("src/main/webapp/WEB-INF/jsp/rx/ListDrugs.jsp");
    private static final String NOTE_ROW = "boolean noteRow = Boolean.TRUE.equals(prescriptDrug.isCustomNote());";
    private static final String NOTE_BRANCH = "<% if (noteRow) { %>";
    private static final String OTHER_ROWS = "<% } else { %>";
    private static final String END = "<% } %>";
    private static final String STATIC_SCRIPT = "<%= request.getContextPath() %>/rx/ViewStaticScript2?demographicNo="
            + "<carlos:encode value='<%= String.valueOf(prescriptDrug.getDemographicId()) %>' context=\"uriComponent\"/>"
            + "&amp;regionalIdentifier=<carlos:encode value='<%= prescriptDrug.getRegionalIdentifier() %>' context=\"uriComponent\"/>"
            + "&amp;cn=<carlos:encode value='<%= prescriptDrug.getCustomName() %>' context=\"uriComponent\"/>"
            + "&amp;bn=<carlos:encode value='<%= bn %>' context=\"uriComponent\"/>";
    private static final String TEXT = "<carlos:encode value='<%= RxPrescriptionData.getFullOutLine(prescriptDrug.getSpecial()).replaceAll(\";\", \" \") %>'/>";

    /** The row's three cells that open the static script, as each was before Notes got their own branch. */
    enum Cell {
        CREATED("createDate_",
                "<td><a id=\"createDate_<%=prescriptIdInt%>\"   <%=styleColor%> href=\"" + STATIC_SCRIPT
                        + "&amp;atc=<carlos:encode value='<%= prescriptDrug.getAtc() %>' context=\"uriComponent\"/>\">"
                        + "<%=DateToString(prescriptDrug.getCreateDate())%></a></td>",
                "<td><span id=\"createDate_<%=prescriptIdInt%>\"   <%=styleColor%>><%=DateToString(prescriptDrug.getCreateDate())%></span></td>"),
        START("rxDate_",
                "<a id=\"rxDate_<%=prescriptIdInt%>\"   <%=styleColor%>\n                   href=\"" + STATIC_SCRIPT + "\"><%=startDate%>\n"
                        + "                </a>",
                "<span id=\"rxDate_<%=prescriptIdInt%>\"   <%=styleColor%>><%=startDate%></span>"),
        TEXT_CELL("prescrip_",
                "<td ><a id=\"prescrip_<%=prescriptIdInt%>\" <%=styleColor%> href=\"" + STATIC_SCRIPT
                        + "&amp;atc=<carlos:encode value='<%= prescriptDrug.getAtc() %>' context=\"uriComponent\"/>\"   <%=tComment%>   >"
                        + TEXT + "</a></td>",
                "<td ><span id=\"prescrip_<%=prescriptIdInt%>\" <%=styleColor%> <%=tComment%>>" + TEXT + "</span></td>");

        final String id;
        final String link;
        final String noteSpan;

        Cell(String id, String link, String noteSpan) {
            this.id = id;
            this.link = link;
            this.noteSpan = noteSpan;
        }
    }

    private static String source;

    @BeforeAll
    static void readPage() throws IOException {
        source = Files.readString(LIST_DRUGS_JSP, StandardCharsets.UTF_8);
    }

    /** The if/else that holds the cell's markup: [note branch, other rows]. */
    private static String[] branches(Cell cell) {
        int at = source.indexOf("id=\"" + cell.id + "<%=prescriptIdInt%>\"");
        int branch = source.lastIndexOf(NOTE_BRANCH, at);
        assertThat(branch).as("%s has a branch for Notes", cell).isPositive();
        int other = source.indexOf(OTHER_ROWS, branch);
        int end = source.indexOf(END, other);
        assertThat(other).isPositive();
        assertThat(end).isPositive();
        return new String[] {source.substring(branch, other), source.substring(other, end)};
    }

    @ParameterizedTest
    @EnumSource(Cell.class)
    @DisplayName("should show a Note row's cell as plain text with no link")
    void shouldShowCellWithoutLink_onNoteRow(Cell cell) {
        assertThat(source).contains(NOTE_ROW);
        String noteBranch = branches(cell)[0];

        // Same id and colour, so the page's scripts (Delete, Discontinue) still find and strike it.
        assertThat(noteBranch).contains(cell.noteSpan).doesNotContain("href").doesNotContain("ViewStaticScript2");
    }

    @ParameterizedTest
    @EnumSource(Cell.class)
    @DisplayName("should keep every other row's link to its drug's static script unchanged")
    void shouldKeepLinkUnchanged_onOtherRows(Cell cell) {
        assertThat(branches(cell)[1]).contains(cell.link);
    }
}
