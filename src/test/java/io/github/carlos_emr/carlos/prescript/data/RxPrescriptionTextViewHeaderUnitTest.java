/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prescript.data;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.dao.PrescriptionDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderExtDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBean;
import io.github.carlos_emr.carlos.providers.data.ProSignatureData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.GregorianCalendar;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The prescriber, clinic and patient lines that open a saved prescription's stored text (prescription.textView). A
 * clinic or patient without an address, city, postal code or phone used to get the word "null" on that line. A missing
 * value is now an empty line, and every line keeps its place.
 */
@DisplayName("Stored prescription text: prescriber, clinic and patient lines")
@Tag("unit")
@Tag("prescript")
class RxPrescriptionTextViewHeaderUnitTest extends CarlosUnitTestBase {

    private static final java.util.Date TODAY = new GregorianCalendar(2026, 9, 17).getTime();
    private static final List<String> COMPLETE = List.of(
            "Dr. FAKE Signature", "FAKE Clinic", "1 FAKE Street", "Toronto", "M5V 1A1", "416-555-0100", "416-555-0101",
            "FAKE-Pat FAKE-Patient", "2 FAKE Avenue", "Hamilton L8P 4R5", "905-555-0102", "October 17, 2026");

    private RxProviderData providerData;

    @BeforeEach
    void registerBeans() {
        // RxProviderData and RxPatientData.Patient look these up when they are built (AllergyDao in Patient's static
        // initializer, so it must be there before the first Patient); the header only reads the objects' own fields.
        registerMock(AllergyDao.class, mock(AllergyDao.class));
        registerMock(PartialDateDao.class, mock(PartialDateDao.class));
        registerMock(ProviderDao.class, mock(ProviderDao.class));
        registerMock(UserPropertyDAO.class, mock(UserPropertyDAO.class));
        registerMock(ClinicDAO.class, mock(ClinicDAO.class));
        providerData = new RxProviderData();
    }

    private RxProviderData.Provider clinic(String name, String address, String city, String postal, String phone, String fax) {
        return providerData.new Provider("999998", "FAKE-Surname", "FAKE-First", name, address, city, postal, phone, fax,
                "", "");
    }

    private static RxPatientData.Patient patient(String first, String last, String address, String city, String postal,
                                                 String phone) {
        Demographic demographic = new Demographic();
        demographic.setFirstName(first);
        demographic.setLastName(last);
        demographic.setAddress(address);
        demographic.setCity(city);
        demographic.setPostal(postal);
        demographic.setPhone(phone);
        return new RxPatientData.Patient(demographic);
    }

    private static List<String> lines(String header) {
        assertThat(header).endsWith("\n");
        return List.of(header.substring(0, header.length() - 1).split("\n", -1));
    }

    private RxProviderData.Provider completeClinic() {
        return clinic("FAKE Clinic", "1 FAKE Street", "Toronto", "M5V 1A1", "416-555-0100", "416-555-0101");
    }

    private static RxPatientData.Patient completePatient() {
        return patient("FAKE-Pat", "FAKE-Patient", "2 FAKE Avenue", "Hamilton", "L8P 4R5", "905-555-0102");
    }

    @Test
    @DisplayName("should store no \"null\" line when the Rx page saves for a patient and clinic with no address or phone")
    void shouldStoreNoNullLine_whenSavingScriptWithMissingValues() throws Exception {
        PrescriptionDao prescriptionDao = mock(PrescriptionDao.class);
        registerMock(PrescriptionDao.class, prescriptionDao);
        registerMock(ProviderExtDao.class, mock(ProviderExtDao.class));
        // persist() gives the row its id, which saveScript returns.
        doAnswer(call -> {
            FieldUtils.writeField((Object) call.getArgument(0), "id", 1, true);
            return null;
        }).when(prescriptionDao).persist(any(Prescription.class));
        RxProviderData.Provider bare = clinic("FAKE Clinic", null, null, null, null, null);
        RxPatientData.Patient noAddress = patient("FAKE-Pat", "FAKE-Patient", null, null, null, null);
        RxSessionBean bean = new RxSessionBean();
        bean.setProviderNo("999998");
        bean.setDemographicNo(1001);

        try (MockedStatic<RxPatientData> patients = mockStatic(RxPatientData.class);
             MockedConstruction<RxProviderData> providers = mockConstruction(RxProviderData.class,
                     (mock, context) -> when(mock.getProvider(anyString())).thenReturn(bare));
             MockedConstruction<ProSignatureData> signatures = mockConstruction(ProSignatureData.class,
                     (mock, context) -> when(mock.hasSignature(anyString())).thenReturn(false))) {
            patients.when(() -> RxPatientData.getPatient(any(LoggedInInfo.class), anyInt())).thenReturn(noAddress);
            new RxPrescriptionData().saveScript(mock(LoggedInInfo.class), bean);
        }

        ArgumentCaptor<Prescription> saved = ArgumentCaptor.forClass(Prescription.class);
        verify(prescriptionDao).persist(saved.capture());
        String textView = saved.getValue().getTextView();
        assertThat(textView).doesNotContain("null").startsWith("FAKE-First FAKE-Surname\nFAKE Clinic\n");
        assertThat(textView.split("\n", -1)).hasSizeGreaterThanOrEqualTo(COMPLETE.size());
    }

    @Test
    @DisplayName("should write every line as before when the clinic and patient have every value")
    void shouldWriteEveryLine_whenAllValuesPresent() {
        String header = RxPrescriptionData.textViewHeader("Dr. FAKE Signature", completeClinic(), completePatient(), TODAY);

        assertThat(lines(header)).containsExactlyElementsOf(COMPLETE);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"clinic address", "clinic city", "clinic postal", "clinic phone", "clinic fax", "clinic name"})
    @DisplayName("should leave the clinic's line empty, not \"null\", when the clinic has no value for it")
    void shouldLeaveLineEmpty_whenClinicValueMissing(String field) {
        RxProviderData.Provider provider = clinic(
                field.equals("clinic name") ? null : "FAKE Clinic",
                field.equals("clinic address") ? null : "1 FAKE Street",
                field.equals("clinic city") ? null : "Toronto",
                field.equals("clinic postal") ? null : "M5V 1A1",
                field.equals("clinic phone") ? null : "416-555-0100",
                field.equals("clinic fax") ? null : "416-555-0101");
        int line = List.of("", "clinic name", "clinic address", "clinic city", "clinic postal", "clinic phone", "clinic fax")
                .indexOf(field);

        List<String> written = lines(RxPrescriptionData.textViewHeader("Dr. FAKE Signature", provider, completePatient(), TODAY));

        assertThat(written).hasSize(COMPLETE.size()).doesNotContain("null");
        assertThat(written.get(line)).isEmpty();
        for (int i = 0; i < COMPLETE.size(); i++) {
            if (i != line) {
                assertThat(written.get(i)).as("line %d", i).isEqualTo(COMPLETE.get(i));
            }
        }
    }

    @Test
    @DisplayName("should leave the patient's address and phone lines empty when the patient has neither")
    void shouldLeaveLinesEmpty_whenPatientHasNoAddressOrPhone() {
        List<String> written = lines(RxPrescriptionData.textViewHeader("Dr. FAKE Signature", completeClinic(),
                patient("FAKE-Pat", "FAKE-Patient", null, null, null, null), TODAY));

        assertThat(written).hasSize(COMPLETE.size());
        assertThat(String.join("\n", written)).doesNotContain("null");
        assertThat(written.subList(8, 11)).containsExactly("", "", "");
        assertThat(written.get(7)).isEqualTo("FAKE-Pat FAKE-Patient");
    }

    @Test
    @DisplayName("should write only the city or only the postal code when the patient has one of them")
    void shouldWritePresentPart_whenPatientHasOnlyCityOrPostal() {
        String cityOnly = lines(RxPrescriptionData.textViewHeader("Dr. FAKE Signature", completeClinic(),
                patient("FAKE-Pat", "FAKE-Patient", "2 FAKE Avenue", "Hamilton", null, "905-555-0102"), TODAY)).get(9);
        String postalOnly = lines(RxPrescriptionData.textViewHeader("Dr. FAKE Signature", completeClinic(),
                patient("FAKE-Pat", "FAKE-Patient", "2 FAKE Avenue", null, "L8P 4R5", "905-555-0102"), TODAY)).get(9);

        assertThat(cityOnly).isEqualTo("Hamilton");
        assertThat(postalOnly).isEqualTo("L8P 4R5");
    }

    @Test
    @DisplayName("should name the prescriber from the provider's names when there is no signature")
    void shouldUseProviderNames_whenNoSignature() {
        assertThat(lines(RxPrescriptionData.textViewHeader(null, completeClinic(), completePatient(), TODAY)).get(0))
                .isEqualTo("FAKE-First FAKE-Surname");
        RxProviderData.Provider noFirstName = providerData.new Provider("999998", "FAKE-Surname", null, "FAKE Clinic",
                "1 FAKE Street", "Toronto", "M5V 1A1", "416-555-0100", "416-555-0101", "", "");
        assertThat(lines(RxPrescriptionData.textViewHeader(null, noFirstName, completePatient(), TODAY)).get(0))
                .isEqualTo("FAKE-Surname");
    }
}
