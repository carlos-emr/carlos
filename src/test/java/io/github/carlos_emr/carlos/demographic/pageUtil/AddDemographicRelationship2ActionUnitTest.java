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
package io.github.carlos_emr.carlos.demographic.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.CtlRelationshipsDao;
import io.github.carlos_emr.carlos.commn.dao.RelationshipsDao;
import io.github.carlos_emr.carlos.commn.model.CtlRelationships;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Relationships;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AddDemographicRelationship2Action}'s GET/POST mutation gate.
 *
 * <p>The "Add Relation" popup (edit-view.jsp) opens this action with a plain GET carrying only
 * {@code demo} to render the contact-search form. {@code linkingDemo}/{@code relation} are only
 * present once the form is actually submitted. The action must render on the parameter-less GET
 * without persisting anything, must reject a GET that carries save data (linkingDemo + relation)
 * with 405, must not treat blank linkingDemo/relation as save data, and must persist only on a
 * genuine POST save. See issue #3352.</p>
 */
@Tag("unit")
@Tag("security")
@DisplayName("AddDemographicRelationship2Action GET/POST mutation gate")
class AddDemographicRelationship2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;

    @Mock private SecurityInfoManager securityInfoManager;
    @Mock private RelationshipsDao relationshipsDao;
    @Mock private CtlRelationshipsDao ctlRelationshipsDao;
    @Mock private DemographicManager demographicManager;
    @Mock private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(RelationshipsDao.class, relationshipsDao);
        registerMock(CtlRelationshipsDao.class, ctlRelationshipsDao);
        registerMock(DemographicManager.class, demographicManager);

        when(securityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), eq("_demographic"), eq("w"), nullable(String.class)))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mocks != null) mocks.close();
    }

    @Test
    @DisplayName("should render contact-search form and never persist when GET has no mutation params")
    void shouldRenderContactSearchForm_whenGetWithoutMutationParams() throws Exception {
        request.setMethod("GET");
        request.setParameter("demo", "1373");
        request.setParameter("origDemo", "1373");

        String result = new AddDemographicRelationship2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        // The "demo" request attribute must populate on every render path (not just after a
        // save) since AddAlternateContact.jsp falls back to it for creatorDemo when the demo/
        // remarks query params are absent, e.g. the intermediate "select a contact" step.
        assertThat(request.getAttribute("demo")).isEqualTo("1373");
        verifyNoInteractions(relationshipsDao);
        verifyNoInteractions(ctlRelationshipsDao);
    }

    @Test
    @DisplayName("should reject GET carrying linkingDemo+relation mutation intent with 405 and never persist")
    void shouldReject405_whenGetCarriesLinkingDemoAndRelationMutationIntent() throws Exception {
        request.setMethod("GET");
        // A GET carrying save data is the CSRF-via-GET attempt the gate must stop.
        request.setParameter("origDemo", "1373");
        request.setParameter("linkingDemo", "1374");
        request.setParameter("relation", "Spouse");

        String result = new AddDemographicRelationship2Action().execute();

        // Rejected with 405 before any persist — the method gate runs ahead of the DAO calls.
        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(relationshipsDao);
        verifyNoInteractions(ctlRelationshipsDao);
    }

    // Blank (present-but-empty) values must not count as mutation intent: fromIntString("")
    // coerces to 0 the same as fromIntString(null), so this is the same garbage-row risk the
    // null-value gate exists to stop. The AND (not OR) semantics of the gate are guarded by
    // covering both-blank and each single-field-blank case.
    @ParameterizedTest(name = "should never persist when POST carries linkingDemo=\"{0}\" relation=\"{1}\"")
    @MethodSource("blankMutationParams")
    void shouldNotPersist_whenPostCarriesBlankMutationParam(String linkingDemo, String relation) throws Exception {
        request.setMethod("POST");
        request.setParameter("origDemo", "1373");
        request.setParameter("linkingDemo", linkingDemo);
        request.setParameter("relation", relation);

        String result = new AddDemographicRelationship2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        verifyNoInteractions(relationshipsDao);
        verifyNoInteractions(ctlRelationshipsDao);
    }

    private static Stream<Arguments> blankMutationParams() {
        return Stream.of(
                Arguments.of("   ", ""),      // blank linkingDemo and relation
                Arguments.of("   ", "Spouse"), // blank linkingDemo only
                Arguments.of("1374", ""));     // blank relation only
    }

    @Test
    @DisplayName("should reject POST with missing origDemo with 400 and never persist")
    void shouldReject400_whenPostHasMissingOrigDemo() throws Exception {
        request.setMethod("POST");
        // origDemo intentionally omitted — fromIntString(null) would coerce to demographic 0.
        request.setParameter("linkingDemo", "1374");
        request.setParameter("relation", "Spouse");
        request.getSession().setAttribute("user", "999998");

        String result = new AddDemographicRelationship2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(relationshipsDao);
        verifyNoInteractions(ctlRelationshipsDao);
    }

    @Test
    @DisplayName("should reject POST with non-numeric linkingDemo with 400 and never persist")
    void shouldReject400_whenPostHasNonNumericLinkingDemo() throws Exception {
        request.setMethod("POST");
        request.setParameter("origDemo", "1373");
        // Non-blank but non-numeric — fromIntString("abc") would also coerce to demographic 0.
        request.setParameter("linkingDemo", "abc");
        request.setParameter("relation", "Spouse");
        request.getSession().setAttribute("user", "999998");

        String result = new AddDemographicRelationship2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(relationshipsDao);
        verifyNoInteractions(ctlRelationshipsDao);
    }

    @Test
    @DisplayName("should reject POST with linkingDemo outside the int range with 400 and never persist")
    void shouldReject400_whenPostHasLinkingDemoOutsideIntRange() throws Exception {
        request.setMethod("POST");
        request.setParameter("origDemo", "1373");
        // Digit-only but overflows int — fromIntString would still coerce this to demographic 0
        // via its caught NumberFormatException, so the digit-only regex alone isn't enough.
        request.setParameter("linkingDemo", "2147483648");
        request.setParameter("relation", "Spouse");
        request.getSession().setAttribute("user", "999998");

        String result = new AddDemographicRelationship2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(relationshipsDao);
        verifyNoInteractions(ctlRelationshipsDao);
    }

    @Test
    @DisplayName("should reject POST with linkingDemo=0 with 400 and never persist")
    void shouldReject400_whenPostHasZeroLinkingDemo() throws Exception {
        request.setMethod("POST");
        request.setParameter("origDemo", "1373");
        // "0" is digit-only and parses as a valid int, but demographic_no is an auto-increment
        // PK starting at 1 -- 0 is the exact placeholder fromIntString(null/blank) coerces to.
        request.setParameter("linkingDemo", "0");
        request.setParameter("relation", "Spouse");
        request.getSession().setAttribute("user", "999998");

        String result = new AddDemographicRelationship2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(relationshipsDao);
        verifyNoInteractions(ctlRelationshipsDao);
    }

    @Test
    @DisplayName("should return pmmClient and never persist when GET carries the Finished param")
    void shouldReturnPmmClient_whenGetCarriesFinishedParam() throws Exception {
        request.setMethod("GET");
        request.setParameter("origDemo", "1373");
        request.setParameter("pmmClient", "Finished");

        String result = new AddDemographicRelationship2Action().execute();

        assertThat(result).isEqualTo("pmmClient");
        verifyNoInteractions(relationshipsDao);
        verifyNoInteractions(ctlRelationshipsDao);
    }

    @Test
    @DisplayName("should reject GET carrying pmmClient=Finished with mutation intent with 405, not pmmClient")
    void shouldReject405_whenGetCarriesPmmClientFinishedWithMutationIntent() throws Exception {
        // The method gate must run before the pmmClient short-circuit, or pmmClient=Finished
        // could be used to slip a non-POST save attempt past the 405 check.
        request.setMethod("GET");
        request.setParameter("origDemo", "1373");
        request.setParameter("linkingDemo", "1374");
        request.setParameter("relation", "Spouse");
        request.setParameter("pmmClient", "Finished");

        String result = new AddDemographicRelationship2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(relationshipsDao);
        verifyNoInteractions(ctlRelationshipsDao);
    }

    @Test
    @DisplayName("should persist the relationship when POST carries linkingDemo and relation")
    void shouldPersistRelationship_whenPostCarriesLinkingDemoAndRelation() throws Exception {
        request.setMethod("POST");
        request.setParameter("origDemo", "1373");
        request.setParameter("linkingDemo", "1374");
        request.setParameter("relation", "Spouse");
        request.getSession().setAttribute("user", "999998");
        // No configured inverse relation: the inverse-linking branch is skipped cleanly.
        when(ctlRelationshipsDao.findByValue("Spouse")).thenReturn(null);

        String result = new AddDemographicRelationship2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        verify(relationshipsDao).persist(any());
    }

    @Test
    @DisplayName("should persist a flipped inverse row when the relation has a sex-specific inverse")
    void shouldPersistInverseRelationship_whenRelationHasMaleInverseAndOrigDemoIsMale() throws Exception {
        request.setMethod("POST");
        request.setParameter("origDemo", "1373");
        request.setParameter("linkingDemo", "1374");
        request.setParameter("relation", "Parent");
        request.getSession().setAttribute("user", "999998");

        CtlRelationships parentRelation = new CtlRelationships();
        parentRelation.setValue("Parent");
        parentRelation.setMaleInverse("Son");
        parentRelation.setFemaleInverse("Daughter");
        when(ctlRelationshipsDao.findByValue("Parent")).thenReturn(parentRelation);

        Demographic origDemographic = new Demographic();
        origDemographic.setSex("M");
        when(demographicManager.getDemographic(eq(mockLoggedInInfo), eq("1373"))).thenReturn(origDemographic);

        String result = new AddDemographicRelationship2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);

        ArgumentCaptor<Relationships> captor = ArgumentCaptor.forClass(Relationships.class);
        verify(relationshipsDao, times(2)).persist(captor.capture());
        List<Relationships> persisted = captor.getAllValues();

        // Forward row: origDemo -> linkingDemo, the relation as submitted.
        assertThat(persisted.get(0).getDemographicNo()).isEqualTo(1373);
        assertThat(persisted.get(0).getRelationDemographicNo()).isEqualTo(1374);
        assertThat(persisted.get(0).getRelation()).isEqualTo("Parent");

        // Inverse row: flipped linkingDemo -> origDemo, using the male inverse since origDemo's
        // sex is "M" (computeInverseRelation resolves the inverse relative to the ORIGINAL
        // demographic's sex before flipping which side each demographic is on).
        assertThat(persisted.get(1).getDemographicNo()).isEqualTo(1374);
        assertThat(persisted.get(1).getRelationDemographicNo()).isEqualTo(1373);
        assertThat(persisted.get(1).getRelation()).isEqualTo("Son");
    }
}
