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

    @Test void previewNeverGeneratesOrWrites() throws Exception {
        request.setMethod("GET");
        assertThat(action.execute()).isEqualTo("success");
        assertThat(request.getAttribute("chartUpdateSource")).isEqualTo(snapshot.source());
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        verifyNoInteractions(generator, writer);
    }

    @Test void rejectsGetMutations() throws Exception {
        request.setMethod("GET");
        assertThat(action.apply()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(405);
        verifyNoInteractions(context, generator, writer);
    }

    @Test void hidesDisabledFeature() throws Exception {
        when(properties.getProperty(ChartUpdateProposals.ENABLED, "false")).thenReturn("false");
        assertThat(action.generate()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(404);
        verifyNoInteractions(context, generator, writer);
    }

    @Test void rejectsMissingSessionOrDuplicateDocumentParameter() throws Exception {
        request.setParameter("documentId", "42", "43");
        assertThat(action.generate()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(400);
        request.setParameter("documentId", "42");
        sessions.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(null);
        assertThatThrownBy(action::generate).isInstanceOf(SecurityException.class);
        verifyNoInteractions(context, generator, writer);
    }

    @Test void generationRechecksSnapshotBeforeCreatingReview() throws Exception {
        action.generate();
        assertThat(request.getSession().getAttribute(ChartUpdateReview.SESSION_KEY)).isNotSameAs(review);
        verify(context, times(3)).load(user, 42);
        verifyNoInteractions(writer);
    }

    @Test void discardsGeneratedResultsIfSourceOrChartChanged() throws Exception {
        var changed = new ChartUpdateContext.Snapshot(42, 3001, "Synthetic patient", "Synthetic", "", snapshot.source(),
                "source", "changed", "10016", "1", List.of());
        when(context.load(user, 42)).thenReturn(snapshot, changed);
        action.generate();
        assertThat(request.getAttribute("chartUpdateError").toString()).contains("changed during generation");
        assertThat(request.getSession().getAttribute(ChartUpdateReview.SESSION_KEY)).isSameAs(review);
        verifyNoInteractions(writer);
    }

    @Test void dismissDoesNotWriteAndUnknownTokenCannotDismiss() throws Exception {
        request.setParameter("reviewToken", "forged");
        action.dismiss();
        assertThat(review.getOutcomes()).isEmpty();
        request.setParameter("reviewToken", review.getToken());
        action.dismiss();
        assertThat(review.getOutcomes().get(proposal.key())).contains("Nothing saved");
        verifyNoInteractions(writer, generator);
    }

    @Test void approvalUsesSessionProposalAndSubmittedFingerprintAndCannotRepeat() throws Exception {
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

    @Test void revokedReadAccessNeverCallsWriter() {
        when(context.load(user, 42)).thenThrow(new SecurityException());
        assertThatThrownBy(action::apply).isInstanceOf(SecurityException.class);
        verifyNoInteractions(writer, generator);
    }
}
