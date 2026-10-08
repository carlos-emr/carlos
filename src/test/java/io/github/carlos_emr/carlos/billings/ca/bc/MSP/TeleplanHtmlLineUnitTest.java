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
package io.github.carlos_emr.carlos.billings.ca.bc.MSP;

import java.util.function.Function;
import java.util.stream.Stream;

import io.github.carlos_emr.carlos.billing.CA.BC.dao.LogTeleplanTxDao;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Wcb;
import io.github.carlos_emr.carlos.billings.ca.bc.Teleplan.WCBTeleplanSubmission;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The Teleplan summary table rows built by {@link ExtractBean#htmlLine}, {@link WcbSb#getHtmlLine(String,
 * String, String, String, String, String, String, String, String, String)} and
 * {@link WCBTeleplanSubmission#getHtmlLine(String, String, String, String, String, String, String, String,
 * String, String)}.
 * <p>
 * Ordinary claim values come out exactly as before, and a missing field leaves its cell blank.
 * Characters that mean something in HTML, such as {@code <}, {@code &} and quotes, are shown as text
 * in every cell and stay inside the invoice link's script call. Extends {@link CarlosUnitTestBase}
 * because {@code new ExtractBean()} looks up its DAOs through {@code SpringUtils.getBean}.
 *
 * @since 2026-10-08
 */
@Tag("unit")
@Tag("fast")
@DisplayName("Teleplan summary table rows")
class TeleplanHtmlLineUnitTest extends CarlosUnitTestBase {

    /** A builder under test: the ten row fields in, one table row out. */
    interface RowBuilder extends Function<String[], String> {
    }

    @BeforeEach
    void registerBeans() {
        registerMock(LogTeleplanTxDao.class, mock(LogTeleplanTxDao.class));
        registerMock(BillingDao.class, mock(BillingDao.class));
        registerMock(BillingmasterDAO.class, mock(BillingmasterDAO.class));
    }

    /** Each builder, and whether it keeps only the digits of the service date. */
    static Stream<Arguments> builders() {
        RowBuilder msp = f -> new ExtractBean().htmlLine(f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8], f[9]);
        RowBuilder wcbForm = f -> {
            // This row takes the health number from its form, not from the argument.
            Wcb form = new Wcb();
            form.setPhn(f[3]);
            return new WcbSb(form).getHtmlLine(f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8], f[9]);
        };
        RowBuilder wcb = f -> new WCBTeleplanSubmission().getHtmlLine(f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8], f[9]);
        return Stream.of(Arguments.of("MSP", msp, false), Arguments.of("WCB form", wcbForm, true),
                Arguments.of("WCB", wcb, true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("builders")
    @DisplayName("ordinary claim values are written as before")
    void shouldWriteValuesUnchanged_forOrdinaryClaim(String name, RowBuilder builder, boolean digitsOnlyDate) {
        String row = builder.apply(new String[] {"123", "45", "FAKE,PATIENT", "9876543217", "20261001", "00100",
                "25.50", "780", "", ""});

        assertThat(row)
                .startsWith("<tr><td class='bodytext'><a href='#' onClick=\"openBrWindow('")
                .contains("_no=0000123'")
                .contains(">45</a></td>")
                .contains("<td class='bodytext'>FAKE,PATIENT</td>")
                .contains("<td class='bodytext'>9876543217</td>")
                .contains("<td class='bodytext'>20261001</td>")
                .contains("<td class='bodytext'>00100</td>")
                .contains("<td align='right' class='bodytext'>25.50</td>")
                .contains("<td align='right' class='bodytext'>780  </td>")
                .contains("<td class='bodytext'>0000123</td>")
                .endsWith("<td class='bodytext'>&nbsp;</td></tr>");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("builders")
    @DisplayName("<, & and quotes in a field are shown as text")
    void shouldShowMarkupCharactersAsText_inEveryCell(String name, RowBuilder builder, boolean digitsOnlyDate) {
        String row = builder.apply(new String[] {"1'&\"<", "<b>4</b>", "O'Brien <Jr> & \"Co\"", "<9>", "<20261001>",
                "<c>", "&1", "<&\"'", "<", "&"});

        assertThat(row)
                .contains(">&lt;b&gt;4&lt;/b&gt;</a>")
                .contains("<td class='bodytext'>O&#39;Brien &lt;Jr&gt; &amp; &#34;Co&#34;</td>")
                .contains("<td class='bodytext'>&lt;9&gt;</td>")
                .contains("<td class='bodytext'>" + (digitsOnlyDate ? "20261001" : "&lt;20261001&gt;") + "</td>")
                .contains("<td class='bodytext'>&lt;c&gt;</td>")
                .contains("<td align='right' class='bodytext'>&amp;1</td>")
                .contains("<td align='right' class='bodytext'>&lt;&amp;&#34;&#39; </td>")
                .contains("<td align='right' class='bodytext'>&lt;    </td>")
                .contains("<td align='right' class='bodytext'>&amp;    </td>")
                .doesNotContain("<b>", "<Jr>", "<9>", "<c>", "<20261001>", "\"Co\"", ">&1<", "><    <", ">&    <");
        // The record number stays a single URL parameter inside the quoted script argument.
        assertThat(row).contains("_no=001%27%26%22%3C'")
                .contains("<td class='bodytext'>001&#39;&amp;&#34;&lt;</td>");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("builders")
    @DisplayName("a missing field leaves its cell blank")
    void shouldLeaveCellBlank_whenFieldMissing(String name, RowBuilder builder, boolean digitsOnlyDate) {
        String row = builder.apply(new String[] {"123", "45", "FAKE,PATIENT", null, "20261001", null, null, "780",
                "", ""});

        assertThat(row)
                .contains("<td class='bodytext'>FAKE,PATIENT</td><td class='bodytext'></td>")
                .contains("<td class='bodytext'>20261001</td><td class='bodytext'></td>")
                .contains("<td align='right' class='bodytext'></td>")
                .doesNotContain(">null<");
    }
}
