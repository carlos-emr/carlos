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
package io.github.carlos_emr.carlos.clinical.summary.web;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.clinical.summary.*;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.List;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiChartUpdatesActionUnitTest extends CarlosUnitTestBase {
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final ChartUpdateContext context = mock(ChartUpdateContext.class);
    private final ReviewedChartUpdateService writer = mock(ReviewedChartUpdateService.class);
    private final ChartUpdateProposals generator = mock(ChartUpdateProposals.class);
    private final ProviderDao providers = mock(ProviderDao.class);
    private final LoggedInInfo user = mock(LoggedInInfo.class);
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> sessions;
    private MockedStatic<CarlosProperties> settings;
    private CarlosProperties properties;
    private AiChartUpdates2Action action;
    private ChartUpdateContext.Snapshot snapshot;
    private ChartUpdateReview review;
    private ChartUpdateProposals.Proposal proposal;

    @BeforeEach void setUp() throws Exception {
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        sessions = mockStatic(LoggedInInfo.class);
        sessions.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(user);
        properties = mock(CarlosProperties.class);
        settings = mockStatic(CarlosProperties.class);
        settings.when(CarlosProperties::getInstance).thenReturn(properties);
        when(properties.getProperty(anyString(), eq("false"))).thenReturn("true");
        when(user.getLoggedInProviderNo()).thenReturn("101");
        request.setMethod("POST");
        request.setParameter("documentId", "42");
        snapshot = new ChartUpdateContext.Snapshot(42, 3001, "Synthetic patient", "Synthetic", "2026-09-28", "Review symptoms.",
                "source", "fresh", "10016", "1", List.of());
        when(context.load(user, 42)).thenReturn(snapshot);
        proposal = new ChartUpdateProposals.Proposal("tickler", "Review symptoms.");
        when(generator.generate(snapshot.source())).thenReturn(List.of(proposal));
        review = new ChartUpdateReview("101", snapshot, List.of(proposal));
        request.getSession().setAttribute(ChartUpdateReview.SESSION_KEY, review);
        request.setParameter("reviewToken", review.getToken());
        request.setParameter("proposalKey", proposal.key());
        action = new AiChartUpdates2Action(context, writer, providers, generator);
    }
    @AfterEach void tearDown() { settings.close(); sessions.close(); servlet.close(); }

    @Test void shouldAvoidGenerationAndWrites_whenPreviewing() throws Exception {
        request.setMethod("GET");
        assertThat(action.execute()).isEqualTo("success");
        assertThat(request.getAttribute("chartUpdateSource")).isEqualTo(snapshot.source());
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        verifyNoInteractions(generator, writer);
    }

    @Test void shouldCheckAvailability_withoutGeneratingOrChangingReviewState() throws Exception {
        request.setMethod("GET");
        request.addHeader("Accept", "application/json");
        assertThat(action.execute()).isEqualTo("none");
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).isEqualTo("{\"available\":true}");
        assertThat(request.getSession().getAttribute(ChartUpdateReview.SESSION_KEY)).isSameAs(review);
        assertThat(request.getAttribute("chartUpdateSource")).isNull();
        verify(context, times(1)).load(user, 42);
        verifyNoInteractions(generator, writer);
    }

    @Test void shouldReturnUnavailableMessage_withoutRenderingAnEmptyPatientPage() throws Exception {
        request.setMethod("GET");
        request.addHeader("Accept", "application/json");
        when(context.load(user, 42)).thenThrow(new IllegalStateException("Document text is unavailable. Reopen the original."));
        assertThat(action.execute()).isEqualTo("none");
        assertThat(response.getContentAsString()).contains("\"available\":false", "Document text is unavailable", "\"originalAvailable\":true");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        verify(context, times(1)).load(user, 42);
        verifyNoInteractions(generator, writer);
    }

    @Test void shouldHideOriginal_whenTheSourceFileIsMissing() throws Exception {
        request.setMethod("GET");
        request.addHeader("Accept", "application/json");
        when(context.load(user, 42)).thenThrow(new ChartUpdateContext.OriginalDocumentMissingException());
        assertThat(action.execute()).isEqualTo("none");
        assertThat(response.getContentAsString()).contains("\"available\":false", "\"originalAvailable\":false",
                "original document file is missing");
        verifyNoInteractions(generator, writer);
    }

    @Test void shouldKeepAuthorizationEnforced_forAvailabilityRequests() {
        request.setMethod("GET");
        request.addHeader("Accept", "application/json");
        when(context.load(user, 42)).thenThrow(new SecurityException("Denied"));
        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        assertThat(response.getContentAsByteArray()).isEmpty();
        verifyNoInteractions(generator, writer);
    }

    @Test void shouldRejectMutation_whenMethodIsGet() throws Exception {
        request.setMethod("GET");
        assertThat(action.apply()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(405);
        verifyNoInteractions(context, generator, writer);
    }

    @Test void shouldHideFeature_whenDisabled() throws Exception {
        when(properties.getProperty(ChartUpdateProposals.ENABLED, "false")).thenReturn("false");
        assertThat(action.generate()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(404);
        verifyNoInteractions(context, generator, writer);
    }

    @Test void shouldRejectRequest_whenSessionMissingOrDocumentDuplicated() throws Exception {
        request.setParameter("documentId", "42", "43");
        assertThat(action.generate()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(400);
        request.setParameter("documentId", "42");
        sessions.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(null);
        assertThatThrownBy(action::generate).isInstanceOf(SecurityException.class);
        verifyNoInteractions(context, generator, writer);
    }

    @Test void shouldRecheckSnapshot_beforeCreatingReview() throws Exception {
        action.generate();
        assertThat(request.getSession().getAttribute(ChartUpdateReview.SESSION_KEY)).isNotSameAs(review);
        assertThat(response.getStatus()).isEqualTo(303);
        assertThat(response.getHeader("Location")).isEqualTo("/documentManager/AiChartUpdates?documentId=42");
        verify(context, times(3)).load(user, 42);
        verifyNoInteractions(writer);
    }

    @Test void shouldSuggestAssignee_onlyWhenClinicianIsActive() throws Exception {
        var provider = new io.github.carlos_emr.carlos.commn.model.Provider();
        provider.setProviderNo("101");
        when(providers.getActiveProviders()).thenReturn(List.of(provider));
        action.generate();
        var active = (ChartUpdateReview) request.getSession().getAttribute(ChartUpdateReview.SESSION_KEY);
        assertThat(active.draft(proposal.key()).assignee()).isEqualTo("101");
        when(providers.getActiveProviders()).thenReturn(List.of());
        action.generate();
        var inactive = (ChartUpdateReview) request.getSession().getAttribute(ChartUpdateReview.SESSION_KEY);
        assertThat(inactive.draft(proposal.key()).assignee()).isEmpty();
        verifyNoInteractions(writer);
    }

    @Test void shouldKeepOtherDraftsWithoutApproval_whenSavingOneProposal() throws Exception {
        var other = new ChartUpdateProposals.Proposal("history", "Suspected asthma.");
        review = new ChartUpdateReview("101", snapshot, List.of(proposal, other));
        request.getSession().setAttribute(ChartUpdateReview.SESSION_KEY, review);
        request.setParameter("reviewToken", review.getToken());
        request.setParameter("entryText", "Edited reminder");
        request.setParameter("draft." + other.key() + ".entryText", "Clinician history edit");
        request.setParameter("draft." + other.key() + ".destination", "Concerns");
        request.setParameter("draft." + other.key() + ".confirmed", "true");
        action.dismiss();
        request.setMethod("GET");
        action.execute();
        assertThat(review.draft(other.key()).text()).isEqualTo("Clinician history edit");
        assertThat(review.draft(other.key()).destination()).isEqualTo("Concerns");
        assertThat(request.getAttribute("chartUpdateRemaining")).isEqualTo(1);
        verifyNoInteractions(writer);
    }

    @Test void shouldRejectOversizedDraftBeforeWriting_whenSubmittedWithApproval() throws Exception {
        request.setParameter("entryText", "x".repeat(2001));
        action.apply();
        assertThat(request.getAttribute("chartUpdateError")).isEqualTo("Invalid review draft.");
        verifyNoInteractions(writer);
    }

    @Test void shouldDiscardGeneration_whenSourceOrChartChanged() throws Exception {
        var changed = new ChartUpdateContext.Snapshot(42, 3001, "Synthetic patient", "Synthetic", "", snapshot.source(),
                "source", "changed", "10016", "1", List.of());
        when(context.load(user, 42)).thenReturn(snapshot, changed);
        action.generate();
        assertThat(request.getAttribute("chartUpdateError").toString()).contains("changed during generation");
        assertThat(request.getSession().getAttribute(ChartUpdateReview.SESSION_KEY)).isSameAs(review);
        verifyNoInteractions(writer);
    }

    @Test void shouldRequireValidToken_whenDismissingWithoutWrites() throws Exception {
        request.setParameter("reviewToken", "forged");
        assertThatThrownBy(action::dismiss).isInstanceOf(SecurityException.class);
        assertThat(review.getOutcomes()).isEmpty();
        request.setParameter("reviewToken", review.getToken());
        action.dismiss();
        assertThat(review.getOutcomes().get(proposal.key())).contains("Nothing saved");
        verifyNoInteractions(writer, generator);
    }

    @Test void shouldUseSessionProposalAndFingerprint_whenApprovingOnce() throws Exception {
        request.setParameter("entryText", "Clinician edit");
        request.setParameter("dueDate", "2026-10-12");
        request.setParameter("assignee", "101");
        request.setParameter("confirmed", "true");
        request.setParameter("chartFingerprint", "fresh");
        // These are untrusted extras, not authority to choose another chart or source.
        request.setParameter("patientId", "9999");
        request.setParameter("kind", "prescription");
        when(writer.apply(eq(user), eq(review), eq(review.getToken()), eq(proposal.key()), any()))
                .thenReturn(new ReviewedChartUpdateService.Result("tickler", 123, false));
        action.apply();
        action.apply();
        verify(writer, times(1)).apply(eq(user), eq(review), eq(review.getToken()), eq(proposal.key()),
                argThat(approval -> approval.text().equals("Clinician edit") && approval.confirmed()
                        && approval.fingerprint().equals("fresh")));
        assertThat(review.getOutcomes().get(proposal.key())).contains("Saved: tickler #123");
    }

    @Test void shouldAvoidWrites_whenReadAccessRevoked() {
        when(context.load(user, 42)).thenThrow(new SecurityException());
        assertThatThrownBy(action::apply).isInstanceOf(SecurityException.class);
        verifyNoInteractions(writer, generator);
    }
}
