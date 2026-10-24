/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.pageUtil;

import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@Tag("unit")
@Tag("encounter")
class EncounterPatientContextUnitTest {
    @ParameterizedTest
    @MethodSource("validPatients")
    void shouldResolveCanonicalPositiveDatabaseId(String name, String value, String expected) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(name, value);
        assertThat(EncounterPatientContext.requireDemographicNo(request)).isEqualTo(expected);
    }

    static Stream<Arguments> validPatients() {
        return Stream.of("demographicNo", "demographic_no").flatMap(name -> Stream.of(
                Arguments.of(name, "1", "1"), Arguments.of(name, "123", "123"),
                Arguments.of(name, "0000000123", "123"),
                Arguments.of(name, "2147483647", "2147483647")));
    }

    @Test
    void shouldAcceptRepeatedEquivalentValuesAcrossBothAliases() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("demographicNo", "123", "00123", "123");
        request.setParameter("demographic_no", "0123", "123");
        assertThat(EncounterPatientContext.requireDemographicNo(request)).isEqualTo("123");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "000", "-1", "+1", " 1", "1 ", "1\n", "1.0", "1e2",
            "abc", "1,2", "2147483648", "9999999999", "10000000000", "١", "１"})
    void shouldRejectInvalidValuesEvenWhenAnotherValueIsValid(String invalid) {
        for (String alias : new String[]{"demographicNo", "demographic_no"}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setParameter(alias, new String[]{"123", invalid});
            assertThatIllegalArgumentException()
                    .as("alias %s, invalid value <%s>", alias, invalid)
                    .isThrownBy(() -> EncounterPatientContext.requireDemographicNo(request));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"demographicNo", "demographic_no"})
    void shouldRejectConflictingRepeatedValues(String alias) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(alias, "123", "456");
        assertThatIllegalArgumentException().isThrownBy(() -> EncounterPatientContext.requireDemographicNo(request));
    }

    @Test
    void shouldRejectConflictingAliases() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("demographicNo", "123");
        request.setParameter("demographic_no", "456");
        assertThatIllegalArgumentException().isThrownBy(() -> EncounterPatientContext.requireDemographicNo(request));
    }

    @Test
    void shouldRejectMissingIdentityDespiteRequestAndSessionChartState() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        EctSessionBean lastChart = new EctSessionBean();
        lastChart.demographicNo = "456";
        request.getSession().setAttribute("EctSessionBean", lastChart);
        request.getSession().setAttribute("eChartID", "456");
        request.setAttribute("demographicNo", "123");
        assertThatIllegalArgumentException().isThrownBy(() -> EncounterPatientContext.requireDemographicNo(request));
    }

    @Test
    void shouldPreferExplicitRequestOverStaleSessionState() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("eChartID", "456");
        request.setParameter("demographic_no", "123");
        assertThat(EncounterPatientContext.requireDemographicNo(request)).isEqualTo("123");
        assertThat(request.getSession().getAttribute("eChartID")).isEqualTo("456");
    }

    @Test
    void shouldRetainIndependentSnapshotsAfterOriginalAndReturnedBeansAreMutated() {
        MockHttpServletRequest entry = new MockHttpServletRequest();
        EctSessionBean a = metadata("101", "A", "9001");
        EctSessionBean b = metadata("202", "B", "9002");
        EncounterPatientContext.rememberEncounter(entry, a);
        EncounterPatientContext.rememberEncounter(entry, b);
        assertThat(entry.getAttribute("EctSessionBean")).isSameAs(b);
        overwriteMetadata(a);
        overwriteMetadata(b);

        MockHttpServletRequest read = new MockHttpServletRequest();
        read.setSession(entry.getSession());
        EctSessionBean firstA = EncounterPatientContext.findEncounter(read, "000101");
        EctSessionBean firstB = EncounterPatientContext.findEncounter(read, "202");
        assertMetadata(firstA, "101", "A", "9001");
        assertMetadata(firstB, "202", "B", "9002");
        assertThat(firstA).isNotSameAs(a).isNotSameAs(firstB);
        assertThat(firstB).isNotSameAs(b);
        overwriteMetadata(firstA);
        overwriteMetadata(firstB);

        EctSessionBean nextA = EncounterPatientContext.findEncounter(read, "101");
        EctSessionBean nextB = EncounterPatientContext.findEncounter(read, "000202");
        assertMetadata(nextA, "101", "A", "9001");
        assertMetadata(nextB, "202", "B", "9002");
        assertThat(nextA).isNotSameAs(firstA);
        assertThat(nextB).isNotSameAs(firstB);
    }

    @Test
    void shouldRefreshOnlyTheRememberedPatientWithoutChangingPriorRequestCopies() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        EncounterPatientContext.rememberEncounter(request, metadata("00101", "A", "9001"));
        EncounterPatientContext.rememberEncounter(request, metadata("202", "B", "9002"));
        EctSessionBean olderA = EncounterPatientContext.findEncounter(request, "101");
        EncounterPatientContext.rememberEncounter(request, metadata("101", "Updated A", "9003"));
        assertMetadata(EncounterPatientContext.findEncounter(request, "101"), "101", "Updated A", "9003");
        assertMetadata(EncounterPatientContext.findEncounter(request, "202"), "202", "B", "9002");
        assertMetadata(olderA, "101", "A", "9001");
    }

    @Test
    void shouldReturnNullForUnrememberedPatientDespiteOtherCachedAndGlobalPatients() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        EctSessionBean b = metadata("202", "B", "9002");
        EncounterPatientContext.rememberEncounter(request, b);
        request.getSession().setAttribute("EctSessionBean", b);
        request.getSession().setAttribute("eChartID", "202");
        assertThat(EncounterPatientContext.findEncounter(request, "101")).isNull();
        assertMetadata(EncounterPatientContext.findEncounter(request, "202"), "202", "B", "9002");
    }

    @Test
    void shouldForgetOnlyAAndRetainBAndUnrelatedSessionState() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        EncounterPatientContext.rememberEncounter(request, metadata("101", "A", "9001"));
        EctSessionBean b = metadata("202", "B", "9002");
        EncounterPatientContext.rememberEncounter(request, b);
        request.getSession().setAttribute("EctSessionBean", b);
        request.getSession().setAttribute("login", "retained");
        EncounterPatientContext.forgetEncounter(request, "303");
        assertMetadata(EncounterPatientContext.findEncounter(request, "101"), "101", "A", "9001");
        EncounterPatientContext.forgetEncounter(request, "000101");
        EncounterPatientContext.forgetEncounter(request, "101");
        assertThat(EncounterPatientContext.findEncounter(request, "101")).isNull();
        assertMetadata(EncounterPatientContext.findEncounter(request, "202"), "202", "B", "9002");
        assertThat(request.getSession().getAttribute("EctSessionBean")).isSameAs(b);
        assertThat(request.getSession().getAttribute("login")).isEqualTo("retained");
    }

    @Test
    void shouldReadAndForgetWithoutCreatingASession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(request.getSession(false)).isNull();
        assertThat(EncounterPatientContext.findEncounter(request, "101")).isNull();
        assertThat(request.getSession(false)).isNull();
        EncounterPatientContext.forgetEncounter(request, "101");
        assertThat(request.getSession(false)).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "-1", "abc", "2147483648"})
    void shouldRejectInvalidCachePatientWithoutCreatingASession(String invalid) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThatIllegalArgumentException().isThrownBy(() -> EncounterPatientContext.findEncounter(request, invalid));
        assertThatIllegalArgumentException().isThrownBy(() -> EncounterPatientContext.forgetEncounter(request, invalid));
        EctSessionBean bean = metadata(invalid, "Invalid", "9001");
        assertThatIllegalArgumentException().isThrownBy(() -> EncounterPatientContext.rememberEncounter(request, bean));
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void shouldReturnOwnEntryCopy_whenThisPatientsChartWasEntered() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        EctSessionBean aEntry = metadata("123", "A", "700");
        EncounterPatientContext.rememberEncounter(request, aEntry);
        request.getSession().setAttribute("EctSessionBean", metadata("456", "B", "800"));

        EctSessionBean entry = EncounterPatientContext.entryOrLoggedIn(request, loggedIn("999998"), "00123");

        assertMetadata(entry, "123", "A", "700");
        assertThat(entry).isNotSameAs(aEntry);
        overwriteMetadata(entry);
        assertMetadata(EncounterPatientContext.findEncounter(request, "123"), "123", "A", "700");
    }

    @Test
    void shouldUseLoggedInIdentity_whenOnlyAnotherChartWasEntered() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        EncounterPatientContext.rememberEncounter(request, metadata("456", "B", "800"));
        request.getSession().setAttribute("userfirstname", "Casey");
        request.getSession().setAttribute("userlastname", "Clinician");

        EctSessionBean entry = EncounterPatientContext.entryOrLoggedIn(request, loggedIn("999998"), "123");

        assertThat(entry.demographicNo).isEqualTo("123");
        assertThat(entry.providerNo).isEqualTo("999998");
        assertThat(entry.curProviderNo).isEqualTo("999998");
        assertThat(entry.userName).isEqualTo("Casey Clinician");
        assertThat(entry.appointmentNo).isNull();
        assertThat(entry.reason).isNull();
        assertThat(entry.appointmentDate).matches("[0-9]{4}-[0-9]{2}-[0-9]{2}");
        assertThat(entry.currentDate).isNotNull();
    }

    @Test
    void shouldGiveEachChartItsOwnEntryProvider_whenAnotherChartWasEnteredLast() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        EncounterPatientContext.rememberEncounter(request, metadata("123", "A", "700"));
        // Chart B entered last in another window: the login-wide bean is B's.
        EctSessionBean b = metadata("456", "B", "800");
        EncounterPatientContext.rememberEncounter(request, b);
        request.getSession().setAttribute("EctSessionBean", b);

        assertThat(EncounterPatientContext.entryProvider(request, loggedIn("999998"), "123")).isEqualTo("A provider");
        assertThat(EncounterPatientContext.entryProvider(request, loggedIn("999998"), "456")).isEqualTo("B provider");
        // A chart this login has not entered, or no chart at all: the logged-in provider.
        assertThat(EncounterPatientContext.entryProvider(request, loggedIn("999998"), "789")).isEqualTo("999998");
        assertThat(EncounterPatientContext.entryProvider(request, loggedIn("999998"), null)).isEqualTo("999998");
    }

    @Test
    void shouldUseTheLoggedInProvider_whenTheChartsEntryNamesNone() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        EctSessionBean entry = metadata("123", "A", "700");
        entry.providerNo = "";
        EncounterPatientContext.rememberEncounter(request, entry);

        assertThat(EncounterPatientContext.entryProvider(request, loggedIn("999998"), "123")).isEqualTo("999998");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc"})
    void shouldRejectInvalidPatient_forEntryLookup(String invalid) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EncounterPatientContext.entryOrLoggedIn(request, loggedIn("999998"), invalid));
    }

    private static io.github.carlos_emr.carlos.utility.LoggedInInfo loggedIn(String providerNo) {
        io.github.carlos_emr.carlos.utility.LoggedInInfo login = org.mockito.Mockito.mock(
                io.github.carlos_emr.carlos.utility.LoggedInInfo.class);
        org.mockito.Mockito.when(login.getLoggedInProviderNo()).thenReturn(providerNo);
        return login;
    }

    private static EctSessionBean metadata(String patient, String name, String appointment) {
        EctSessionBean bean = new EctSessionBean();
        bean.demographicNo = patient;
        bean.providerNo = name + " provider";
        bean.curProviderNo = name + " current provider";
        bean.userName = name + " user";
        bean.appointmentNo = appointment;
        bean.appointmentDate = name + " date";
        bean.startTime = name + " time";
        bean.encType = name + " visit";
        bean.reason = name + " reason";
        bean.patientFirstName = name + " first";
        bean.patientLastName = name + " last";
        bean.status = name + " status";
        return bean;
    }

    private static void assertMetadata(EctSessionBean bean, String patient, String name, String appointment) {
        assertThat(bean).isNotNull();
        assertThat(bean.demographicNo).isEqualTo(patient);
        assertThat(bean.providerNo).isEqualTo(name + " provider");
        assertThat(bean.curProviderNo).isEqualTo(name + " current provider");
        assertThat(bean.userName).isEqualTo(name + " user");
        assertThat(bean.appointmentNo).isEqualTo(appointment);
        assertThat(bean.appointmentDate).isEqualTo(name + " date");
        assertThat(bean.startTime).isEqualTo(name + " time");
        assertThat(bean.encType).isEqualTo(name + " visit");
        assertThat(bean.reason).isEqualTo(name + " reason");
        assertThat(bean.patientFirstName).isEqualTo(name + " first");
        assertThat(bean.patientLastName).isEqualTo(name + " last");
        assertThat(bean.status).isEqualTo(name + " status");
    }

    private static void overwriteMetadata(EctSessionBean bean) {
        bean.demographicNo = "999";
        bean.providerNo = "changed";
        bean.curProviderNo = "changed";
        bean.userName = "changed";
        bean.appointmentNo = "9999";
        bean.appointmentDate = "changed";
        bean.startTime = "changed";
        bean.encType = "changed";
        bean.reason = "changed";
        bean.patientFirstName = "changed";
        bean.patientLastName = "changed";
        bean.status = "changed";
    }
}
