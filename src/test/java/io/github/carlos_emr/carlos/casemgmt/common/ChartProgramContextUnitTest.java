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
package io.github.carlos_emr.carlos.casemgmt.common;

import io.github.carlos_emr.carlos.encounter.data.EctProgram;
import io.github.carlos_emr.carlos.encounter.data.EctProgramManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.LabelValueBean;
import io.github.carlos_emr.carlos.utility.BoundedSessionAttributes;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
class ChartProgramContextUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldKeepEveryIssuedWindowContext_whenTheFirstChartsOpenConcurrently() throws Exception {
        MockHttpSession session = new MockHttpSession();
        int windows = 8;
        CountDownLatch ready = new CountDownLatch(windows);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MockHttpServletRequest>> issued = new ArrayList<>();
        try (var workers = Executors.newFixedThreadPool(windows)) {
            try {
                for (int i = 0; i < windows; i++) {
                    String program = Integer.toString(11 + i);
                    issued.add(workers.submit(() -> {
                        MockHttpServletRequest request = chart(session, "101");
                        ready.countDown();
                        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Chart start timed out");
                        ChartProgramContext.remember(request, program);
                        return request;
                    }));
                }
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            List<String> tokens = new ArrayList<>();
            List<String> formKeys = new ArrayList<>();
            for (int i = 0; i < windows; i++) {
                MockHttpServletRequest request = issued.get(i).get(10, TimeUnit.SECONDS);
                String token = ChartProgramContext.token(request);
                tokens.add(token);
                MockHttpServletRequest reopened = reload(session, "101", token);
                assertThat(ChartProgramContext.resolve(reopened)).isEqualTo(Integer.toString(11 + i));
                formKeys.add(ChartProgramContext.formKey(reopened, "101"));
            }
            assertThat(tokens).doesNotHaveDuplicates();
            assertThat(formKeys).doesNotHaveDuplicates();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"101:22", "202:11"})
    void shouldPreserveTheExistingWindowAndLegacyProgram_whenTheSignedPayloadIsReplaced(String payload) {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest initial = chart(session, "101");
        ChartProgramContext.remember(initial, "11");
        String token = ChartProgramContext.token(initial);
        String forged = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.US_ASCII)) + token.substring(token.indexOf('.'));
        MockHttpServletRequest attempt = reload(session, "101", forged);

        assertThatThrownBy(() -> ChartProgramContext.remember(attempt, "33")).isInstanceOf(SecurityException.class);

        assertThat(ChartProgramContext.resolve(reload(session, "101", token))).isEqualTo("11");
        assertThat(ChartProgramContext.resolve(chart(session, "101"))).isEqualTo("11");
        assertThat(ChartProgramContext.resolve(chart(session, "202"))).isNull();
    }

    @Test
    void shouldKeepEachChartsProgram_whenAnotherWindowChangesPmmSelection() {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest first = chart(session, "101");
        EctProgram programs = mock(EctProgram.class);
        EctProgramManager manager = mock(EctProgramManager.class);
        when(programs.getProgram("999998")).thenReturn("11", "22");
        when(programs.getEctProgramManager()).thenReturn(manager);
        when(manager.getProgramBeans("999998", null)).thenReturn(List.of(
                new LabelValueBean("First program", "11"), new LabelValueBean("Second program", "22")));
        session.setAttribute("case_program_id", "11");
        ChartProgramContext.initialize(first, programs, "999998");
        MockHttpServletRequest second = chart(session, "202");
        session.setAttribute("case_program_id", "22");
        ChartProgramContext.initialize(second, programs, "999998");
        session.setAttribute("case_program_id", "33");
        assertThat(ChartProgramContext.resolve(first)).isEqualTo("11");
        assertThat(ChartProgramContext.resolve(second)).isEqualTo("22");
        assertThat(ChartProgramContext.resolve(chart(session, "303"))).isNull();
    }

    @Test
    void shouldResolveNoProgram_whenPatientIsMissingOrSelectionInvalid() {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = chart(session, "101");
        ChartProgramContext.remember(request, "");
        assertThat(ChartProgramContext.resolve(request)).isNull();
        MockHttpServletRequest legacy = new MockHttpServletRequest();
        legacy.setSession(session);
        assertThat(ChartProgramContext.resolve(legacy)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1000000000", "2147483647"})
    void shouldCaptureProgram_whenIdentifierFitsTheChartDaoRange(String program) {
        MockHttpServletRequest request = chart(new MockHttpSession(), "101");
        ChartProgramContext.remember(request, program);
        assertThat(ChartProgramContext.resolve(request)).isEqualTo(program);
        assertThat(request.getAttribute("case_program_id")).isEqualTo(program);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "2147483648", "9223372036854775808"})
    void shouldClearCapturedProgram_whenIdentifierIsOutsideTheChartDaoRange(String program) {
        MockHttpServletRequest request = chart(new MockHttpSession(), "101");
        ChartProgramContext.remember(request, "11");
        ChartProgramContext.remember(request, program);
        assertThat(ChartProgramContext.resolve(request)).isNull();
        assertThat(request.getAttribute("case_program_id")).isNull();
    }

    @Test
    void shouldKeepIndependentPrograms_whenTwoWindowsOpenTheSamePatient() {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest first = chart(session, "101");
        MockHttpServletRequest second = chart(session, "101");
        ChartProgramContext.remember(first, "11");
        ChartProgramContext.remember(second, "22");
        String firstToken = ChartProgramContext.token(first);
        String secondToken = ChartProgramContext.token(second);

        assertThat(firstToken).isNotBlank().isNotEqualTo(secondToken);
        assertThat(ChartProgramContext.resolve(reload(session, "101", firstToken))).isEqualTo("11");
        assertThat(ChartProgramContext.resolve(reload(session, "101", secondToken))).isEqualTo("22");
        assertThat(ChartProgramContext.resolve(first)).isEqualTo("11");
        assertThat(ChartProgramContext.resolve(second)).isEqualTo("22");
        assertThat(ChartProgramContext.resolve(chart(session, "101"))).isEqualTo("22");
    }

    @Test
    void shouldRetainAnOpenWindowsProgram_whenOldLegacyPatientMetadataIsEvicted() {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest first = chart(session, "101");
        ChartProgramContext.remember(first, "11");
        String firstToken = ChartProgramContext.token(first);
        String contextPrefix = ChartProgramContext.class.getName() + ".";
        Object signingKey = session.getAttribute(contextPrefix + "signingKey");
        assertThat(signingKey).isInstanceOf(byte[].class);

        for (int i = 0; i <= BoundedSessionAttributes.MAX_ENTRIES; i++) {
            MockHttpServletRequest later = chart(session, Integer.toString(1000 + i));
            ChartProgramContext.remember(later, "22");
        }

        assertThat(ChartProgramContext.resolve(reload(session, "101", firstToken))).isEqualTo("11");
        assertThat(ChartProgramContext.resolve(chart(session, "101"))).isNull();
        assertThat(ChartProgramContext.resolve(chart(session,
                Integer.toString(1000 + BoundedSessionAttributes.MAX_ENTRIES)))).isEqualTo("22");
        assertThat(session.getAttribute(contextPrefix + "signingKey")).isSameAs(signingKey);
        assertThat(Collections.list(session.getAttributeNames()).stream()
                .filter(name -> name.matches(java.util.regex.Pattern.quote(contextPrefix) + "[0-9]+")))
                .hasSize(BoundedSessionAttributes.MAX_ENTRIES);
    }

    @Test
    void shouldRetainTheWindowProgram_whenPmmSelectionChangesBeforeReload() {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest first = chart(session, "101");
        ChartProgramContext.remember(first, "11");
        String token = ChartProgramContext.token(first);
        session.setAttribute("case_program_id", "22");
        MockHttpServletRequest reload = reload(session, "101", token);

        EctProgram programs = mock(EctProgram.class);
        ChartProgramContext.initialize(reload, programs, "999998");

        verifyNoInteractions(programs);
        assertThat(ChartProgramContext.token(reload)).isEqualTo(token);
        assertThat(ChartProgramContext.resolve(reload)).isEqualTo("11");
        assertThat(session.getAttribute("case_program_id")).isEqualTo("22");
    }

    @Test
    void shouldAcceptEquivalentPatientAliases_whenReloadCarriesAValidToken() {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest initial = chart(session, "101");
        ChartProgramContext.remember(initial, "11");
        String token = ChartProgramContext.token(initial);
        MockHttpServletRequest reload = reload(session, "000101", token);
        reload.setParameter("demographicNo", new String[]{"101", "000101"});
        reload.setParameter("demographic_no", "00101");
        reload.setParameter(ChartProgramContext.PARAMETER, new String[]{token, token});

        assertThat(ChartProgramContext.token(reload)).isEqualTo(token);
        assertThat(ChartProgramContext.resolve(reload)).isEqualTo("11");
    }

    @Test
    void shouldRejectAWindowToken_whenAnotherLoginReusesIt() {
        MockHttpServletRequest initial = chart(new MockHttpSession(), "101");
        ChartProgramContext.remember(initial, "11");
        MockHttpServletRequest otherLogin = reload(new MockHttpSession(), "101", ChartProgramContext.token(initial));

        assertThatThrownBy(() -> ChartProgramContext.resolve(otherLogin)).isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldRejectAWindowToken_whenTheExplicitPatientIsDifferent() {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest initial = chart(session, "101");
        ChartProgramContext.remember(initial, "11");
        MockHttpServletRequest otherPatient = reload(session, "202", ChartProgramContext.token(initial));

        assertThatThrownBy(() -> ChartProgramContext.resolve(otherPatient)).isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldRejectResolution_whenTheWindowPatientIsMissing() {
        MockHttpServletRequest initial = chart(new MockHttpSession(), "101");
        ChartProgramContext.remember(initial, "11");
        initial.removeParameter("demographicNo");

        assertThatThrownBy(() -> ChartProgramContext.resolve(initial)).isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldUseTheAuthenticatedProvidersDefault_whenNoProgramWasExplicitlySelected() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("case_program_id", "22");
        MockHttpServletRequest request = chart(session, "101");
        EctProgram programs = mock(EctProgram.class);
        EctProgramManager manager = mock(EctProgramManager.class);
        when(programs.getProgram("999998")).thenReturn("11");
        when(programs.getEctProgramManager()).thenReturn(manager);
        when(manager.getProgramBeans("999998", null)).thenReturn(List.of(new LabelValueBean("Default", "11")));

        ChartProgramContext.initialize(request, programs, "999998");

        assertThat(ChartProgramContext.resolve(request)).isEqualTo("11");
        assertThat(ChartProgramContext.token(request)).isNotBlank();
        assertThat(session.getAttribute("case_program_id")).isEqualTo("22");
        verify(programs).getProgram("999998");
        verify(manager).getProgramBeans("999998", null);
    }

    @Test
    void shouldPreserveTheChartOwnersDefault_whenTheAuthenticatedCallerBelongsToThatProgram() {
        MockHttpServletRequest request = chart(new MockHttpSession(), "101");
        EctProgram programs = mock(EctProgram.class);
        EctProgramManager manager = mock(EctProgramManager.class);
        when(programs.getProgram("doctor")).thenReturn("22");
        when(programs.getEctProgramManager()).thenReturn(manager);
        when(manager.getProgramBeans("nurse", null)).thenReturn(List.of(
                new LabelValueBean("Nurse default", "11"), new LabelValueBean("Doctor default", "22")));

        ChartProgramContext.initialize(request, programs, "nurse", "doctor");

        assertThat(ChartProgramContext.resolve(request)).isEqualTo("22");
        verify(programs).getProgram("doctor");
        verify(programs, never()).getProgram("nurse");
        verify(manager).getProgramBeans("nurse", null);
    }

    @Test
    void shouldRefuseTheChartOwnersDefault_whenTheAuthenticatedCallerLacksMembership() {
        MockHttpServletRequest request = chart(new MockHttpSession(), "101");
        EctProgram programs = mock(EctProgram.class);
        EctProgramManager manager = mock(EctProgramManager.class);
        when(programs.getProgram("doctor")).thenReturn("22");
        when(programs.getEctProgramManager()).thenReturn(manager);
        when(manager.getProgramBeans("nurse", null)).thenReturn(List.of(new LabelValueBean("Nurse default", "11")));

        assertThatThrownBy(() -> ChartProgramContext.initialize(request, programs, "nurse", "doctor"))
                .isInstanceOf(SecurityException.class);

        assertThat(ChartProgramContext.token(request)).isNull();
        assertThat(ChartProgramContext.resolve(request)).isNull();
        verify(programs, never()).getProgram("nurse");
    }

    @Test
    void shouldUseIndependentEntryFormKeys_whenTheSamePatientHasTwoProgramContexts() {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest first = chart(session, "101");
        MockHttpServletRequest second = chart(session, "101");
        ChartProgramContext.remember(first, "11");
        ChartProgramContext.remember(second, "22");
        String firstKey = ChartProgramContext.formKey(first, "101");
        String secondKey = ChartProgramContext.formKey(second, "101");

        assertThat(firstKey).isNotBlank().isNotEqualTo(secondKey).isNotEqualTo("caseManagementEntryForm101");
        assertThat(ChartProgramContext.formKey(reload(session, "101", ChartProgramContext.token(first)), "101"))
                .isEqualTo(firstKey);
        assertThat(ChartProgramContext.formKey(reload(session, "101", ChartProgramContext.token(second)), "101"))
                .isEqualTo(secondKey);
        assertThat(ChartProgramContext.formKey(chart(session, "101"), "101")).isEqualTo("caseManagementEntryForm101");
    }

    @Test
    void shouldRefuseEntryFormLookup_whenTheContextIsForgedOrBelongsToAnotherPatient() {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest initial = chart(session, "101");
        ChartProgramContext.remember(initial, "11");
        String token = ChartProgramContext.token(initial);
        MockHttpServletRequest mismatch = reload(session, "202", token);
        MockHttpServletRequest forged = reload(session, "101", "not-a-context");

        assertThatThrownBy(() -> ChartProgramContext.formKey(mismatch, "202")).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> ChartProgramContext.formKey(forged, "101")).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> ChartProgramContext.formKey(initial, "202")).isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldRetainTheSelectedProgram_whenAnExistingWindowReloads() {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest initial = chart(session, "101");
        ChartProgramContext.remember(initial, "11");
        String token = ChartProgramContext.token(initial);
        MockHttpServletRequest reload = reload(session, "101", token);
        reload.setParameter("programId", "22");
        EctProgram programs = mock(EctProgram.class);

        ChartProgramContext.initialize(reload, programs, "999998");

        assertThat(ChartProgramContext.resolve(reload)).isEqualTo("11");
        assertThat(ChartProgramContext.token(reload)).isEqualTo(token);
        verifyNoInteractions(programs);
    }

    @ParameterizedTest
    @ValueSource(strings = {"programId", "case_program_id"})
    void shouldUseAnExplicitProgram_whenItIsAvailableToTheAuthenticatedProvider(String alias) {
        MockHttpServletRequest request = chart(new MockHttpSession(), "101");
        request.setParameter(alias, "22");
        EctProgram programs = mock(EctProgram.class);
        EctProgramManager manager = mock(EctProgramManager.class);
        when(programs.getEctProgramManager()).thenReturn(manager);
        when(manager.getProgramBeans("999998", null)).thenReturn(List.of(
                new LabelValueBean("Default", "11"), new LabelValueBean("Chosen", "22")));

        ChartProgramContext.initialize(request, programs, "999998");

        assertThat(ChartProgramContext.resolve(request)).isEqualTo("22");
        verify(manager).getProgramBeans("999998", null);
        verify(programs, never()).getProgram("999998");
    }

    @Test
    void shouldRejectAnExplicitProgram_whenItIsUnavailableToTheAuthenticatedProvider() {
        MockHttpServletRequest request = chart(new MockHttpSession(), "101");
        request.setParameter("programId", "22");
        EctProgram programs = mock(EctProgram.class);
        EctProgramManager manager = mock(EctProgramManager.class);
        when(programs.getEctProgramManager()).thenReturn(manager);
        when(manager.getProgramBeans("999998", null)).thenReturn(List.of(new LabelValueBean("Default", "11")));

        assertThatThrownBy(() -> ChartProgramContext.initialize(request, programs, "999998"))
                .isInstanceOf(SecurityException.class);

        assertThat(ChartProgramContext.token(request)).isNull();
        assertThat(ChartProgramContext.resolve(request)).isNull();
    }

    @Test
    void shouldRejectAmbiguousProgramAliases_beforeLookingUpAvailablePrograms() {
        MockHttpServletRequest request = chart(new MockHttpSession(), "101");
        request.setParameter("programId", "11");
        request.setParameter("case_program_id", "22");
        EctProgram programs = mock(EctProgram.class);

        assertThatThrownBy(() -> ChartProgramContext.initialize(request, programs, "999998"))
                .isInstanceOf(SecurityException.class);

        verifyNoInteractions(programs);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "-1", "abc", "2147483648"})
    void shouldRejectInvalidExplicitPrograms_beforeLookingUpAvailablePrograms(String selected) {
        MockHttpServletRequest request = chart(new MockHttpSession(), "101");
        request.setParameter("programId", selected);
        EctProgram programs = mock(EctProgram.class);

        assertThatThrownBy(() -> ChartProgramContext.initialize(request, programs, "999998"))
                .isInstanceOf(SecurityException.class);

        verifyNoInteractions(programs);
    }

    private MockHttpServletRequest reload(MockHttpSession session, String patient, String token) {
        MockHttpServletRequest request = chart(session, patient);
        request.setParameter(ChartProgramContext.PARAMETER, token);
        return request;
    }

    private MockHttpServletRequest chart(MockHttpSession session, String patient) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        request.setParameter("demographicNo", patient);
        return request;
    }
}
