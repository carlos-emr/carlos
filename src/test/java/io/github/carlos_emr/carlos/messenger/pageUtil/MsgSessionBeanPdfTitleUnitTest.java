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
package io.github.carlos_emr.carlos.messenger.pageUtil;

import org.apache.commons.text.StringEscapeUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.util.Doc2PDF;

import static org.assertj.core.api.Assertions.assertThat;

/** Regression tests for XML injection through the PDF attachment title (issue #2668). */
@Tag("unit")
@Tag("messenger")
@DisplayName("MsgSessionBean PDF title escaping")
class MsgSessionBeanPdfTitleUnitTest {

    private static final String INJECTION = "</TITLE><INJECT>payload</INJECT><TITLE>";

    @Test
    void shouldEscapeMarkup_inTitleTag() {
        String tag = new MsgSessionBean().getPDFTitleTag(INJECTION);

        assertThat(tag).isEqualTo("<TITLE>&lt;/TITLE&gt;&lt;INJECT&gt;payload&lt;/INJECT&gt;&lt;TITLE&gt;</TITLE>");
        assertThat(tag).doesNotContain("<INJECT>");
    }

    @Test
    void shouldNotForgeElements_inAppendedAttachment() throws Exception {
        MsgSessionBean bean = new MsgSessionBean();
        bean.setAppendPDFAttachment("JVBERi0xLjQ=", INJECTION);

        String stored = bean.getPDFAttachment();
        assertThat(stored).doesNotContain("<INJECT>");
        assertThat(Doc2PDF.getXMLTagValue(stored, "TITLE")).hasSize(1);
        assertThat(Doc2PDF.getXMLTagValue(stored, "CONTENT")).hasSize(1);
    }

    @Test
    void shouldRoundTripTitle_afterUnescape() throws Exception {
        String title = "Smith & Jones <\"note\"> 'x'";
        MsgSessionBean bean = new MsgSessionBean();
        bean.setAppendPDFAttachment("JVBERi0xLjQ=", title);

        String stored = (String) Doc2PDF.getXMLTagValue(bean.getPDFAttachment(), "TITLE").get(0);
        assertThat(StringEscapeUtils.unescapeXml(stored)).isEqualTo(title);
    }

    @Test
    void shouldReturnEmptyTitle_forNullTitle() {
        assertThat(new MsgSessionBean().getPDFTitleTag(null)).isEqualTo("<TITLE></TITLE>");
    }
}
