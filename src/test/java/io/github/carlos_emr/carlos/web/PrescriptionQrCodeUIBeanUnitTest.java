/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

import ca.uhn.hl7v2.model.v26.message.OMP_O09;
import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.util.idgenerator.InMemoryIDGenerator;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.dao.PrescriptionDao;
import io.github.carlos_emr.carlos.commn.hl7.v2.Hl7ParserUtils;
import io.github.carlos_emr.carlos.commn.hl7.v2.OmpO09Builder;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.QrCodeUtils;
import io.github.carlos_emr.carlos.utility.QrCodeUtils.QrCodesOrientation;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Verifies that QR diagnostics never serialize clinical payloads or exception chains. */
@DisplayName("Prescription QR code diagnostic privacy")
public class PrescriptionQrCodeUIBeanUnitTest extends CarlosUnitTestBase {
    private static final int PRESCRIPTION_ID = 5322;
    private static final String PATIENT = "SYNTHETIC5322PATIENT";
    private static final String MEDICATION = "SYNTHETIC5322MEDICATION";
    private final Map<Field, Object> originalDaos = new HashMap<>();
    private final Clinic clinic = new Clinic();
    private final Provider provider = new Provider();
    private final Demographic demographic = new Demographic();
    private final Prescription prescription = new Prescription();
    private final List<Drug> drugs = List.of(new Drug());
    private DefaultHapiContext hapiContext;

    @BeforeEach
    void setUp() throws Exception {
        hapiContext = new DefaultHapiContext();
        // HAPI's default generator writes a shared ./id_file. Each synthetic
        // message owns its test context and must not persist state in the checkout.
        hapiContext.getParserConfiguration().setIdGenerator(new InMemoryIDGenerator());
        ClinicDAO clinicDao = createAndRegisterMock(ClinicDAO.class);
        ProviderDao providerDao = createAndRegisterMock(ProviderDao.class);
        DemographicDao demographicDao = createAndRegisterMock(DemographicDao.class);
        PrescriptionDao prescriptionDao = createAndRegisterMock(PrescriptionDao.class);
        DrugDao drugDao = createAndRegisterMock(DrugDao.class);
        // The utility caches static DAOs; restore the previous values after each test.
        for (String name : List.of("clinicDAO", "providerDao", "demographicDao", "prescriptionDao", "drugDao")) {
            Field field = PrescriptionQrCodeUIBean.class.getDeclaredField(name);
            field.setAccessible(true);
            originalDaos.put(field, field.get(null));
            field.set(null, mockedBeans.get(field.getType()));
        }
        injectDependency(prescription, "id", PRESCRIPTION_ID);
        prescription.setProviderNo("999990");
        prescription.setDemographicId(1);
        when(clinicDao.getClinic()).thenReturn(clinic);
        when(prescriptionDao.find(PRESCRIPTION_ID)).thenReturn(prescription);
        when(providerDao.getProvider("999990")).thenReturn(provider);
        when(demographicDao.getDemographicById(1)).thenReturn(demographic);
        when(drugDao.findByPrescriptionId(PRESCRIPTION_ID)).thenReturn(drugs);
    }

    @AfterEach
    void restoreDaos() throws Exception {
        try {
            for (var entry : originalDaos.entrySet()) {
                entry.getKey().set(null, entry.getValue());
            }
        } finally {
            if (hapiContext != null) hapiContext.close();
        }
    }

    private OMP_O09 syntheticMessage() throws Exception {
        OMP_O09 message = new OMP_O09();
        message.setParser(hapiContext.getPipeParser());
        message.initQuickstart("OMP", "O09", "P");
        return message;
    }

    @Test
    void shouldPreserveQrPayload_withoutLoggingClinicalData() throws Exception {
        OMP_O09 message = syntheticMessage();
        message.getPATIENT().getPID().getPatientName(0).getFamilyName().getSurname().setValue(PATIENT);
        message.getORDER(0).getRXO().getRequestedGiveCode().getText().setValue(MEDICATION);
        String payload = Hl7ParserUtils.pipeParser.encode(message);
        assertThat(payload).contains(PATIENT, MEDICATION);
        byte[] expectedImage = {1, 2, 3};
        CarlosProperties properties = mock(CarlosProperties.class);
        when(properties.getProperty("QR_CODE_IMAGE_SCALE_FACTOR")).thenReturn("2");
        when(properties.getProperty("QR_CODE_ERROR_CORRECTION_LEVEL")).thenReturn("M");
        try (var builder = mockStatic(OmpO09Builder.class);
             var renderer = mockStatic(QrCodeUtils.class);
             var configuration = mockStatic(CarlosProperties.class);
             LogCapture logs = LogCapture.forLogger(PrescriptionQrCodeUIBean.class)) {
            builder.when(() -> OmpO09Builder.makeOmpO09(clinic, provider, demographic, prescription, drugs))
                    .thenReturn(message);
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);
            renderer.when(() -> QrCodeUtils.toMultipleQrCodePngs(anyString(), any(), any(), anyInt()))
                    .thenReturn(expectedImage);

            assertThat(PrescriptionQrCodeUIBean.getPrescriptionHl7QrCodeImage(PRESCRIPTION_ID))
                    .isEqualTo(expectedImage);
            renderer.verify(() -> QrCodeUtils.toMultipleQrCodePngs(eq(payload), eq(ErrorCorrectionLevel.M),
                    eq(QrCodesOrientation.VERTICAL), eq(2)));
            assertThat(logs.events()).anyMatch(event -> event.getLevel() == Level.DEBUG);
            assertThat(render(logs)).contains("prescriptionId=5322", "drugCount=1", "payloadLength=" + payload.length())
                    .doesNotContain(PATIENT, MEDICATION, payload);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"F", "SYNTHETIC5322GENDER"})
    void shouldRenderDecodablePrescription_withoutLoggingClinicalDataInAnyLayer(String gender) throws Exception {
        Date date = Date.from(Instant.parse("2000-01-02T00:00:00Z"));
        clinic.setClinicName("SYNTHETIC5322CLINIC");
        provider.setProviderNo("999990");
        demographic.setDemographicNo(1);
        demographic.setLastName(PATIENT);
        demographic.setFirstName("FAKE");
        demographic.setYearOfBirth("1980");
        demographic.setMonthOfBirth("01");
        demographic.setDateOfBirth("02");
        demographic.setSex(gender);
        prescription.setDatePrescribed(date);
        Drug drug = drugs.get(0);
        drug.setGenericName(MEDICATION);
        drug.setRxDate(date);
        drug.setEndDate(date);
        CarlosProperties properties = mock(CarlosProperties.class);
        when(properties.getProperty("QR_CODE_IMAGE_SCALE_FACTOR")).thenReturn("4");
        when(properties.getProperty("QR_CODE_ERROR_CORRECTION_LEVEL")).thenReturn("M");

        try (var configuration = mockStatic(CarlosProperties.class);
             LogCapture uiLogs = LogCapture.forLogger(PrescriptionQrCodeUIBean.class);
             LogCapture rendererLogs = LogCapture.forLogger(QrCodeUtils.class);
             LogCapture builderLogs = LogCapture.forLogger(OmpO09Builder.class)) {
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);
            configuration.when(CarlosProperties::getBuildTag).thenReturn("synthetic-build");
            configuration.when(CarlosProperties::getBuildDate).thenReturn("synthetic-date");
            byte[] png = PrescriptionQrCodeUIBean.getPrescriptionHl7QrCodeImage(PRESCRIPTION_ID);
            assertThat(png).isNotNull();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
            var source = new com.google.zxing.RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels);
            var bitmap = new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(source));
            String decoded = new com.google.zxing.qrcode.QRCodeReader().decode(bitmap).getText();
            assertThat(decoded).contains(PATIENT, MEDICATION);
            OMP_O09 parsed = (OMP_O09) Hl7ParserUtils.pipeParser.parse(decoded);
            assertThat(parsed.getPATIENT().getPID().getAdministrativeSex().getValue())
                    .isEqualTo("F".equals(gender) ? "F" : "U");
            assertThat(rendererLogs.events()).anyMatch(event -> event.getLevel() == Level.DEBUG);
            assertThat(render(rendererLogs)).contains("chunkIndex=0", "chunkLength=");
            assertThat(render(uiLogs) + render(rendererLogs) + render(builderLogs))
                    .doesNotContain(PATIENT, MEDICATION, "SYNTHETIC5322GENDER", decoded);
            if (!"F".equals(gender)) {
                assertThat(render(builderLogs)).contains("using unknown gender", "demographicNo=1");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldKeepFailureCorrelation_withoutLoggingExceptionPayload(boolean rendererFailure) throws Exception {
        IllegalArgumentException failure = new IllegalArgumentException(PATIENT,
                new IllegalStateException(MEDICATION));
        failure.addSuppressed(new IllegalStateException("SYNTHETIC5322SUPPRESSED"));
        CarlosProperties properties = mock(CarlosProperties.class);
        when(properties.getProperty("QR_CODE_IMAGE_SCALE_FACTOR")).thenReturn("2");
        when(properties.getProperty("QR_CODE_ERROR_CORRECTION_LEVEL")).thenReturn("M");
        try (var builder = mockStatic(OmpO09Builder.class);
             var renderer = mockStatic(QrCodeUtils.class);
             var configuration = mockStatic(CarlosProperties.class);
             LogCapture logs = LogCapture.forLogger(PrescriptionQrCodeUIBean.class)) {
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);
            if (rendererFailure) {
                OMP_O09 message = syntheticMessage();
                builder.when(() -> OmpO09Builder.makeOmpO09(any(), any(), any(), any(), anyList()))
                        .thenReturn(message);
                renderer.when(() -> QrCodeUtils.toMultipleQrCodePngs(anyString(), any(), any(), anyInt()))
                        .thenThrow(failure);
            } else {
                builder.when(() -> OmpO09Builder.makeOmpO09(any(), any(), any(), any(), anyList()))
                        .thenThrow(failure);
            }
            assertThat(PrescriptionQrCodeUIBean.getPrescriptionHl7QrCodeImage(PRESCRIPTION_ID)).isNull();
            assertThat(logs.events()).anyMatch(event -> event.getLevel() == Level.ERROR);
            assertThat(render(logs)).contains("prescriptionId=5322", "errorType=java.lang.IllegalArgumentException")
                    .contains("Caused by: java.lang.IllegalStateException", "Suppressed: java.lang.IllegalStateException",
                            "PrescriptionQrCodeUIBeanUnitTest.shouldKeepFailureCorrelation_withoutLoggingExceptionPayload")
                    .doesNotContain(PATIENT, MEDICATION, "SYNTHETIC5322SUPPRESSED");
            assertThat(logs.events()).allMatch(event -> event.getThrown() == null);
        }
    }

    private static String render(LogCapture logs) {
        PatternLayout layout = PatternLayout.newBuilder().withPattern("%m%n%ex").build();
        return logs.events().stream().map(layout::toSerializable).collect(Collectors.joining());
    }
}
