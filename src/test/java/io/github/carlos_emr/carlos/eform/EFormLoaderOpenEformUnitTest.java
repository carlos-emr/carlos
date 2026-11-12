/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpSession;

import io.github.carlos_emr.carlos.eform.data.EForm;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * The {@code onclick} an {@code oscarOPEN} button gets ({@link EFormLoader#getOpenEform}): which eForm
 * route it opens, and the link it carries.
 */
@Tag("unit")
@Tag("eform")
@DisplayName("eForm oscarOPEN button target")
class EFormLoaderOpenEformUnitTest extends CarlosUnitTestBase {

    private static final String ADD = "/carlos/eform/efmformadd_data";
    private static final String SHOW = "/carlos/eform/efmshowform_data";

    private final MockHttpSession session = new MockHttpSession();
    private MockedStatic<EFormUtil> eFormUtil;
    private EForm opener;

    @BeforeEach
    void setUp() {
        eFormUtil = mockStatic(EFormUtil.class);
        eFormUtil.when(() -> EFormUtil.getEFormIdByName("Linked eForm")).thenReturn("12");
        opener = mock(EForm.class);
        when(opener.getDemographicNo()).thenReturn("123");
        when(opener.getFid()).thenReturn("13");
        when(opener.getAppointmentNo()).thenReturn("0");
        // A saved opener keeps its author; the button must still link for whoever is signed in.
        when(opener.getProviderNo()).thenReturn("doc2");
    }

    @AfterEach
    void tearDown() {
        eFormUtil.close();
    }

    private String onclick(String route, String fdid) {
        return EFormLoader.getOpenEform(route, fdid, "Linked eForm", "openLinked", opener, "doc1", session);
    }

    @Test
    @DisplayName("should open a new copy of the linked eForm from a new opener")
    void shouldOpenNewLinkedCopy_fromNewOpener() {
        assertThat(onclick(ADD, null)).isEqualTo("window.open('" + ADD
                + "?fid=12&demographic_no=123&appointment=0&eform_link=doc1_123_13_openLinked');");
    }

    @Test
    @DisplayName("should open the saved copy a saved opener links to")
    void shouldOpenLinkedSavedCopy_fromSavedOpener() {
        assertThat(onclick(SHOW, "55")).isEqualTo("window.open('" + SHOW
                + "?fdid=55&appointment=0&eform_link=doc1_123_13_openLinked');");
    }

    @Test
    @DisplayName("should open a new copy from a saved opener that links to nothing yet")
    void shouldOpenNewLinkedCopy_fromUnlinkedSavedOpener() {
        assertThat(onclick(SHOW, "")).isEqualTo("window.open('" + ADD
                + "?fid=12&demographic_no=123&appointment=0&eform_link=doc1_123_13_openLinked');");
    }

    @Test
    @DisplayName("should issue the button's link for the signed-in provider and the eForm it opens")
    void shouldIssueLink_forSignedInProviderAndTarget() {
        onclick(SHOW, "");

        assertThat(EFormOpenerLinks.wasIssued(session, "doc1_123_13_openLinked", "doc1", "123", "12")).isTrue();
        assertThat(EFormOpenerLinks.wasIssued(session, "doc2_123_13_openLinked", "doc2", "123", "12")).isFalse();
    }

    @Test
    @DisplayName("should open the linked eForm without a link when there is no session to issue it in")
    void shouldOmitLink_withoutSession() {
        assertThat(EFormLoader.getOpenEform(ADD, null, "Linked eForm", "openLinked", opener, "doc1", null))
                .isEqualTo("window.open('" + ADD + "?fid=12&demographic_no=123&appointment=0');");
    }
}
