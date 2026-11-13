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
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.clinic.ClinicData;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.ConsultationSignatureService;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.DigitalSignatureManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Pins the consultation letter the request path prints today: page text, every page's drawing
 * instructions and every embedded image, for a clinic letterhead with logo and stored signature,
 * and for a program letterhead with letterhead contact details, patient-will-book and a
 * suppressed signature. A change to how the letter is assembled must leave these fingerprints
 * exactly as they are.
 */
@DisplayName("ConsultationPDFCreator request path output")
@Tag("unit")
class ConsultationPDFCreatorRequestPathUnitTest {

    @TempDir Path temp;

    private MockedStatic<SpringUtils> springUtils;
    private MockedConstruction<EctConsultationFormRequestUtil> forms;
    private MockedConstruction<ClinicData> clinics;
    private final List<String[]> restored = new ArrayList<>();
    private Scenario scenario;
    /** The consultant the form loaded; null for the pinned scenarios' directory specialist. */
    private ProfessionalSpecialist consultant;

    private record Scenario(
            String letterheadName,
            String letterheadAddress,
            String letterheadPhone,
            String letterheadFax,
            String pwb,
            boolean suppressSignature,
            boolean logo) {}

    @BeforeEach
    void setUp() {
        springUtils = mockStatic(SpringUtils.class);
        ProviderDao providers = mock(ProviderDao.class);
        when(providers.getProvider("101")).thenReturn(provider("1234567"));
        when(providers.getProvider("999")).thenReturn(provider("7654321"));
        DemographicManager demographics = mock(DemographicManager.class);
        Demographic demographic = new Demographic();
        demographic.setProviderNo("999");
        when(demographics.getDemographic(any(), eq(42))).thenReturn(demographic);
        DigitalSignatureManager signatures = mock(DigitalSignatureManager.class);
        DigitalSignature signature = new DigitalSignature();
        signature.setSignatureImage(png(300, 90, Color.BLUE));
        when(signatures.getDigitalSignature(7)).thenReturn(signature);
        ProgramDao programs = mock(ProgramDao.class);
        when(programs.getProgramName(5)).thenReturn("Synthetic Program");
        springUtils.when(() -> SpringUtils.getBean(ProviderDao.class)).thenReturn(providers);
        springUtils.when(() -> SpringUtils.getBean(DemographicManager.class)).thenReturn(demographics);
        springUtils.when(() -> SpringUtils.getBean(DigitalSignatureManager.class)).thenReturn(signatures);
        springUtils.when(() -> SpringUtils.getBean(ProgramDao.class)).thenReturn(programs);
        forms = mockConstruction(EctConsultationFormRequestUtil.class, (form, context) -> fill(form));
        clinics = mockConstruction(ClinicData.class, (clinic, context) -> {
            when(clinic.getClinicName()).thenReturn("Synthetic Clinic");
            when(clinic.getClinicAddress()).thenReturn("1 Test Street");
            when(clinic.getClinicCity()).thenReturn("Testville");
            when(clinic.getClinicProvince()).thenReturn("ON");
            when(clinic.getClinicPostal()).thenReturn("A1A 1A1");
            when(clinic.getClinicPhone()).thenReturn("555-0100");
            when(clinic.getClinicFax()).thenReturn("555-0101");
        });
    }

    @AfterEach
    void tearDown() {
        for (String[] entry : restored.reversed()) {
            if (entry[1] == null) CarlosProperties.getInstance().remove(entry[0]);
            else CarlosProperties.getInstance().setProperty(entry[0], entry[1]);
        }
        clinics.close();
        forms.close();
        springUtils.close();
    }

    @Test
    @DisplayName("prints the clinic letterhead with logo and stored signature exactly as pinned")
    void shouldPrintPinnedLetter_withClinicLetterheadLogoAndSignature() throws Exception {
        scenario = new Scenario("-1", "", "", "", "0", false, true);

        String fingerprint = fingerprint(print());

        assertThat(fingerprint).isEqualTo(CLINIC_FINGERPRINT);
    }

    @Test
    @DisplayName("prints the program letterhead, patient-will-book and no signature exactly as pinned")
    void shouldPrintPinnedLetter_withProgramLetterheadAndSuppressedSignature() throws Exception {
        scenario = new Scenario(
                "prog_5", "9 Program Road\nSuite 2", "555-0200", "555-0201", "1", true, false);

        String fingerprint = fingerprint(print());

        assertThat(fingerprint).isEqualTo(PROGRAM_FINGERPRINT);
    }

    @Test
    @DisplayName("prints a health-care-team contact with its own values, without a borrowed title or the text null")
    void shouldPrintAHealthCareTeamContact_withItsOwnValuesOnly() throws Exception {
        scenario = new Scenario("-1", "", "", "", "0", false, false);
        // As a CDS import creates a team contact: no work phone, fax or CPSO number. Its id is
        // also a directory specialist's, which the letter must not borrow from.
        var contact = new io.github.carlos_emr.carlos.commn.model.ProfessionalContact();
        contact.setId(9001);
        contact.setFirstName("Nora");
        contact.setLastName("NURSE");
        contact.setCity("Testville");
        contact.setProvince("ON");
        consultant = ProfessionalSpecialist.healthCareTeamConsultant(contact);

        try (PDDocument pdf = Loader.loadPDF(print())) {
            String text = new PDFTextStripper().getText(pdf).replace("\r", "");
            assertThat(text).contains("Consultant: NURSE, Nora\n", "Address: Testville, ON\n")
                    .doesNotContain("null", "Dr.", "Specialist, Sam");
        }
    }

    private byte[] print() throws Exception {
        if (scenario.logo()) {
            Path logo = temp.resolve("logo.png");
            Files.write(logo, png(400, 120, Color.RED));
            override("faxLogoInConsultation", logo.toString());
            // The packaged default leaves the referring practitioner off; print it here.
            override("printPDF_referring_prac", "yes");
        }
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setPreferredLocales(List.of(Locale.ENGLISH));
        request.setAttribute("reqId", "77");
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        request.getSession().setAttribute(loggedInInfo.LOGGED_IN_INFO_KEY, loggedInInfo);
        if (scenario.suppressSignature())
            request.setAttribute(ConsultationSignatureService.SUPPRESS_SIGNATURE_ATTRIBUTE, Boolean.TRUE);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new ConsultationPDFCreator(request, output).printPdf(loggedInInfo);
        return output.toByteArray();
    }

    private void override(String key, String value) {
        String previous = CarlosProperties.getInstance().getProperty(key);
        CarlosProperties.getInstance().setProperty(key, value);
        restored.add(new String[]{key, previous});
    }

    private void fill(EctConsultationFormRequestUtil form) {
        form.demoNo = "42";
        form.pwb = scenario.pwb();
        form.referalDate = "2026/01/15";
        form.urgency = "2";
        form.service = "3";
        form.providerNo = "101";
        form.siteName = null;
        form.signatureImg = "7";
        form.letterheadName = scenario.letterheadName();
        form.letterheadTitle = "";
        form.letterheadAddress = scenario.letterheadAddress();
        form.letterheadPhone = scenario.letterheadPhone();
        form.letterheadFax = scenario.letterheadFax();
        form.patientName = "SYNTHETIC, PATIENT";
        form.patientAddress = "5 Sample Lane\nTestville,ON\nA1A 1A1";
        form.patientPhone = "555-0300";
        form.patientCellPhone = "555-0301";
        form.patientWPhone = "555-0302";
        form.patientEmail = "patient@example.invalid";
        form.patientDOB = "2001-02-03";
        form.patientSex = "F";
        form.patientHealthCardType = "ON";
        form.patientHealthNum = "0000000000";
        form.patientHealthCardVersionCode = "AB";
        form.patientChartNo = "C-42";
        form.appointmentDate = "2026/02/01";
        form.appointmentHour = "3";
        form.appointmentMinute = "05";
        form.appointmentPm = "PM";
        form.reasonForConsultation = "Synthetic reason for referral";
        form.clinicalInformation = "Clinical line one<br>Clinical line two";
        form.concurrentProblems = "Concurrent problem";
        form.currentMedications = "Medication one&nbsp;daily";
        form.allergies = "Allergy one";
        ProfessionalSpecialist specialist = new ProfessionalSpecialist();
        specialist.setFirstName("Sam");
        specialist.setLastName("Specialist");
        specialist.setProfessionalLetters("MD");
        specialist.setPhoneNumber("555-0400");
        specialist.setFaxNumber("555-0401");
        specialist.setStreetAddress("7 Consult Way<br>Testville");
        when(form.getProfessionalSpecialist()).thenReturn(consultant != null ? consultant : specialist);
        when(form.getServiceName("3")).thenReturn("Synthetic Service");
        when(form.getAppointmentInstructionsLabel()).thenReturn("Please reply with an appointment");
        when(form.getLetterheadAddress()).thenReturn(scenario.letterheadAddress());
        when(form.getLetterheadPhone()).thenReturn(scenario.letterheadPhone());
        when(form.getLetterheadFax()).thenReturn(scenario.letterheadFax());
        when(form.getSignatureImg()).thenReturn("7");
        when(form.getProviderName(anyString())).thenReturn("DOCTOR, REFERRING");
        when(form.getFamilyDoctor()).thenReturn("DOCTOR, FAMILY");
    }

    private static Provider provider(String ohip) {
        Provider provider = new Provider();
        provider.setOhipNo(ohip);
        return provider;
    }

    static byte[] png(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(color);
        graphics.fillRect(width / 4, height / 4, width / 2, height / 2);
        graphics.dispose();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The page text, then each page's decoded drawing instructions and image streams as SHA-256.
     * Creation dates and the file identifier are excluded: OpenPDF writes the current time there.
     */
    static String fingerprint(byte[] pdf) throws IOException, NoSuchAlgorithmException {
        StringBuilder result = new StringBuilder();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            result.append(new PDFTextStripper().getText(document).replace("\r", ""));
            int index = 0;
            for (PDPage page : document.getPages()) {
                try (InputStream contents = page.getContents()) {
                    result.append("\npage ").append(++index).append(' ').append(sha256(contents.readAllBytes()));
                }
                for (COSName name : page.getResources().getXObjectNames()) {
                    if (page.getResources().getXObject(name) instanceof PDImageXObject image) {
                        try (InputStream raw = image.getCOSObject().createRawInputStream()) {
                            result.append("\nimage ").append(image.getWidth()).append('x')
                                    .append(image.getHeight()).append(' ').append(sha256(raw.readAllBytes()));
                        }
                    }
                }
            }
        }
        return result.toString();
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static final String CLINIC_FINGERPRINT =
            "Synthetic Clinic\n"
            + "1 Test StreetTestville, ON. A1A 1A1\n"
            + "Phone: 555-0100 Fax: 555-0101\n"
            + "Consultation Request\n"
            + "Date: 2026/01/15\n"
            + "Please reply with an appointment\n"
            + "Consultant: Specialist, Sam MD\n"
            + "Urgency Non-Urgent\n"
            + "Service: Synthetic Service\n"
            + "Phone: 555-0400\n"
            + "Fax: 555-0401\n"
            + "Address: 7 Consult Way\n"
            + "Testville\n"
            + "Patient: SYNTHETIC, PATIENT\n"
            + "Address: 5 Sample Lane\n"
            + "Testville,ON\n"
            + "A1A 1A1\n"
            + "Phone: 555-0300\n"
            + "Cell Phone: 555-0301\n"
            + "Work Phone: 555-0302\n"
            + "Email: patient@example.invalid\n"
            + "Birthdate: 2001-02-03 (y/m/d)\n"
            + "Sex: F\n"
            + "Health Card No.: (ON) 0000000000 AB\n"
            + "Appointment date: 2026/02/01\n"
            + "Time: 3:05 PM\n"
            + "Chart No.: C-42\n"
            + "Synthetic reason for referral\n"
            + "Pertinent Clinical Information:\n"
            + "Clinical line one\n"
            + "Clinical line two\n"
            + "Significant Concurrent Problems:\n"
            + "Concurrent problem\n"
            + "Current Medications:\n"
            + "Medication one daily\n"
            + "Allergies:\n"
            + "Allergy one\n"
            + "Signature:\n"
            + "Referring Practitioner: DOCTOR, REFERRING\n"
            + "(1234567)\n"
            + "MRP: DOCTOR, FAMILY (7654321)\n"
            + "\n"
            + "page 1 158a0093ba591cde1118853e0e5bb71fd2962b47ab9ecede8b4406d338786419\n"
            + "image 300x90 b1bc58e59b0a1ed4aee6a3e6ac20292ac7953edc79cdababf55a8ff23d12dcb7\n"
            + "image 400x120 596af90cbde8158193e0ba9fc5cceea0d78f20ea74436c30557e72a5b6a59e27";
    private static final String PROGRAM_FINGERPRINT =
            "Synthetic Program\n"
            + "9 Program Road\n"
            + "Suite 2\n"
            + "Phone: 555-0200 Fax: 555-0201\n"
            + "Consultation Request\n"
            + "Date: Patient Will Book\n"
            + "Please reply to patient\n"
            + "Consultant: Specialist, Sam MD\n"
            + "Urgency Non-Urgent\n"
            + "Service: Synthetic Service\n"
            + "Phone: 555-0400\n"
            + "Fax: 555-0401\n"
            + "Address: 7 Consult Way\n"
            + "Testville\n"
            + "Patient: SYNTHETIC, PATIENT\n"
            + "Address: 5 Sample Lane\n"
            + "Testville,ON\n"
            + "A1A 1A1\n"
            + "Phone: 555-0300\n"
            + "Cell Phone: 555-0301\n"
            + "Work Phone: 555-0302\n"
            + "Email: patient@example.invalid\n"
            + "Birthdate: 2001-02-03 (y/m/d)\n"
            + "Sex: F\n"
            + "Health Card No.: (ON) 0000000000 AB\n"
            + "Chart No.: C-42\n"
            + "Synthetic reason for referral\n"
            + "Pertinent Clinical Information:\n"
            + "Clinical line one\n"
            + "Clinical line two\n"
            + "Significant Concurrent Problems:\n"
            + "Concurrent problem\n"
            + "Current Medications:\n"
            + "Medication one daily\n"
            + "Allergies:\n"
            + "Allergy one\n"
            + "MRP: DOCTOR, FAMILY (7654321)\n"
            + "\n"
            + "page 1 a954a9a800e0fde3c9aa87adf513a06bfc978c8028de42569e5e29647b1a524a";
}
