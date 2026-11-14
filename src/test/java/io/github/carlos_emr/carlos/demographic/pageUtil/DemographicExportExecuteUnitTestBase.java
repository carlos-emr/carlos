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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.xml.bind.JAXBContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.clinic.ClinicData;
import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.ContactDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicPharmacyDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDocsDao;
import io.github.carlos_emr.carlos.commn.dao.PharmacyInfoDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionExtDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.exception.PatientDirectiveException;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsLegacyProjectionService;
import io.github.carlos_emr.carlos.demographic.data.DemographicData;
import io.github.carlos_emr.carlos.demographic.data.DemographicRelationship;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToProviderDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.OmdCds;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PatientRecord;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.ReportClass;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.ReportContent;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.ReportFormat;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.ReportsReceived;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.prevention.PreventionData;
import io.github.carlos_emr.carlos.providers.data.ProviderData;
import io.github.carlos_emr.carlos.report.data.DemographicSets;

/**
 * Runs {@link DemographicExportAction42Action#execute()} for whole charts: the real per-patient
 * loop, the real XML, ReadMe, export log, archive and download, over a temporary export directory
 * and document store.
 *
 * <p>Only the sources the loop reads are stand-ins: the charts ({@link #chart}), each chart's
 * document list ({@link #document}) and the prevention table ({@link #preventionDao}, behind the
 * real {@code PreventionData} readers). The request asks for Reports Received only, which keeps
 * every other clinical section out of the way. The prevention read is not optional: the loop
 * makes it for every chart whatever the request selects.
 */
abstract class DemographicExportExecuteUnitTestBase extends DemographicExportActionUnitTestBase {

    /** The most responsible provider of every fixture chart; names each chart's archive folder. */
    protected static final String PROVIDER_FOLDER = "FAKE_Doctor_123456";

    private static final String PROVIDER_NO = "999";
    private static final String PATIENT_SET = "FAKEset";

    @TempDir
    protected Path root;

    /** TMP_DIR for the run; the action makes its own random directory inside it. */
    protected Path tmpDir;
    /** DOCUMENT_DIR for the run. */
    protected Path documentDir;
    /** The prevention table behind the real prevention readers; empty for every chart by default. */
    protected final PreventionDao preventionDao = mock(PreventionDao.class);

    /** Provider lookups; {@code 999}, every fixture chart's MRP, resolves to FAKE Doctor. */
    protected final ProviderDao providerDao = mock(ProviderDao.class);
    /**
     * When set to zero or more, the response stream fails once that many bytes were written, as
     * a client that goes away part-way through the download does.
     */
    protected int failStreamAfterBytes = -1;

    /** Something a test does to the export directory while one chart is being exported. */
    @FunctionalInterface
    protected interface DuringExport {
        void accept(Path exportDirectory) throws Exception;
    }

    /** Chart numbers whose patient directive refuses the export. */
    protected final java.util.Set<String> refusedByDirective = new java.util.HashSet<>();

    private final Map<String, DuringExport> duringExport = new HashMap<>();
    private final Map<String, List<HRMDocumentToDemographic>> chartHrmDocuments = new HashMap<>();
    private final Map<String, Demographic> charts = new LinkedHashMap<>();
    private final Map<String, ArrayList<EDoc>> chartDocuments = new HashMap<>();
    private final Map<String, String> savedProperties = new HashMap<>();
    private final ByteArrayOutputStream streamed = new ByteArrayOutputStream();
    private final List<AutoCloseable> scopedMocks = new ArrayList<>();
    private Object savedPreventionDao;
    private Object savedPartialDateDao;

    @BeforeEach
    void setUpExecuteFixture() throws Exception {
        tmpDir = Files.createDirectory(root.resolve("tmp"));
        documentDir = Files.createDirectory(root.resolve("documents"));
        property("TMP_DIR", tmpDir.toString());
        property("DOCUMENT_DIR", documentDir.toString());
        property("Vendor_Product", "CARLOS EMR test");
        property("Support_Contact", "support@example.invalid");
        property("demographic.export.encryptedOnly", "false");

        registerMock(CdsLegacyProjectionService.class, mock(CdsLegacyProjectionService.class));
        registerMock(DemographicPharmacyDao.class, mock(DemographicPharmacyDao.class));
        registerMock(PharmacyInfoDao.class, mock(PharmacyInfoDao.class));
        registerMock(ProfessionalSpecialistDao.class, mock(ProfessionalSpecialistDao.class));
        registerMock(ContactDao.class, mock(ContactDao.class));
        registerMock(DemographicManager.class, mock(DemographicManager.class));
        // In case this test is the first to load PreventionData or EDocUtil, which resolve
        // their DAOs when the class loads.
        registerMock(PreventionDao.class, preventionDao);
        registerMock(PreventionExtDao.class, mock(PreventionExtDao.class));
        registerMock(ConsultDocsDao.class, mock(ConsultDocsDao.class));
        registerMock(DocumentDao.class, mock(DocumentDao.class));
        registerMock(ProgramManager2.class, mock(ProgramManager2.class));
        registerMock(EFormDocsDao.class, mock(EFormDocsDao.class));
        registerMock(HRMDocumentToProviderDao.class, mock(HRMDocumentToProviderDao.class));
        when(hrmDocumentToDemographicDao.findByDemographicNo(anyString()))
                .thenAnswer(read -> new ArrayList<>(chartHrmDocuments.getOrDefault(read.getArgument(0), List.of())));
        Provider provider = new Provider();
        provider.setFirstName("FAKE");
        provider.setLastName("Doctor");
        provider.setOhipNo("123456");
        when(providerDao.getProvider(PROVIDER_NO)).thenReturn(provider);
        registerMock(ProviderDao.class, providerDao);
        savedPreventionDao = swapPreventionDataField("preventionDao", preventionDao);
        savedPartialDateDao = swapPreventionDataField("partialDateDao", partialDateDao);

        scopedMocks.add(mockConstruction(DemographicData.class, (data, context) ->
                when(data.getDemographic(any(), anyString())).thenAnswer(read -> {
                    if (refusedByDirective.contains(read.<String>getArgument(1))) {
                        throw new PatientDirectiveException("synthetic patient directive");
                    }
                    return charts.get(read.getArgument(1));
                })));
        scopedMocks.add(mockConstruction(DemographicRelationship.class));
        scopedMocks.add(mockConstruction(DemographicSets.class, (sets, context) ->
                when(sets.getDemographicSet(PATIENT_SET)).thenAnswer(read -> new ArrayList<>(charts.keySet()))));
        scopedMocks.add(mockConstruction(ProviderData.class, (data, context) -> {
            when(data.getFirst_name()).thenReturn("FAKE");
            when(data.getLast_name()).thenReturn("Doctor");
            when(data.getOhip_no()).thenReturn("123456");
        }));
        scopedMocks.add(mockConstruction(ClinicData.class, (clinic, context) -> {
            when(clinic.getClinicName()).thenReturn("FAKE Clinic");
            when(clinic.getClinicAddress()).thenReturn("1 FAKE Street");
            when(clinic.getClinicCity()).thenReturn("FAKE City");
            when(clinic.getClinicProvince()).thenReturn("ON");
            when(clinic.getClinicPostal()).thenReturn("A1A 1A1");
            when(clinic.getClinicPhone()).thenReturn("555-555-0100");
        }));
        var documentLists = mockStatic(EDocUtil.class);
        documentLists.when(() -> EDocUtil.listDemoDocs(any(), anyString())).thenAnswer(read -> {
            DuringExport hook = duringExport.get(read.getArgument(1));
            if (hook != null) {
                hook.accept(exportDirectory());
            }
            return new ArrayList<>(chartDocuments.getOrDefault(read.getArgument(1), new ArrayList<>()));
        });
        scopedMocks.add(documentLists);

        when(request.getMethod()).thenReturn("POST");
        when(request.getParameter("exReportsReceived")).thenReturn("on");
        when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
            @Override
            public void write(int b) throws java.io.IOException {
                if (failStreamAfterBytes >= 0 && streamed.size() >= failStreamAfterBytes) {
                    throw new java.io.IOException("synthetic client abort");
                }
                streamed.write(b);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener listener) {
                // synchronous test stream
            }
        });
        action.setTemplate(String.valueOf(DemographicExportAction42Action.CMS4));
        action.setPatientSet(PATIENT_SET);
        action.setProviderNo("-1");
        action.setPgpReady("No");
    }

    @AfterEach
    void tearDownExecuteFixture() throws Exception {
        for (int i = scopedMocks.size() - 1; i >= 0; i--) {
            scopedMocks.get(i).close();
        }
        swapPreventionDataField("preventionDao", savedPreventionDao);
        swapPreventionDataField("partialDateDao", savedPartialDateDao);
        CarlosProperties properties = CarlosProperties.getInstance();
        for (Map.Entry<String, String> saved : savedProperties.entrySet()) {
            if (saved.getValue() == null) {
                properties.remove(saved.getKey());
            } else {
                properties.setProperty(saved.getKey(), saved.getValue());
            }
        }
    }

    /** Sets a property for this test only. */
    protected void property(String key, String value) {
        CarlosProperties properties = CarlosProperties.getInstance();
        Object previous = properties.setProperty(key, value);
        savedProperties.putIfAbsent(key, previous == null ? null : previous.toString());
    }

    /** Adds a chart to the patient set the request exports, in order. */
    protected Demographic chart(int demographicNo, String firstName) {
        Demographic chart = new Demographic();
        chart.setDemographicNo(demographicNo);
        chart.setFirstName(firstName);
        chart.setLastName("FAKE-Patient");
        chart.setSex("F");
        chart.setYearOfBirth("1980");
        chart.setMonthOfBirth("03");
        chart.setDateOfBirth("15");
        chart.setPatientStatus("AC");
        chart.setRosterStatus("");
        chart.setOfficialLanguage("English");
        chart.setProviderNo(PROVIDER_NO);
        charts.put(String.valueOf(demographicNo), chart);
        return chart;
    }

    /**
     * Stores a document file and lists it in a chart.
     *
     * @param documentId the document id as the chart lists it; may be {@code null} or not numeric
     * @return the listed document, for a test that needs to change it
     */
    protected EDoc document(int demographicNo, String documentId, String fileName, byte[] content) throws Exception {
        Files.write(documentDir.resolve(fileName), content);
        EDoc document = new EDoc();
        document.setDocId(documentId);
        document.setFileName(fileName);
        document.setContentType("application/pdf");
        document.setType("lab");
        // Complete enough that a document exported without trouble adds nothing to the export log.
        document.setDocClass("Other Letter");
        document.setObservationDate("2026/01/15");
        document.setDateTimeStamp("2026-01-15 10:00:00");
        chartDocuments.computeIfAbsent(String.valueOf(demographicNo), chart -> new ArrayList<>()).add(document);
        return document;
    }

    /**
     * Runs {@code hook} with the request's export directory while the given chart is being
     * exported (when the loop lists that chart's documents, before its file is written).
     */
    protected void duringExportOf(int demographicNo, DuringExport hook) {
        duringExport.put(String.valueOf(demographicNo), hook);
    }

    /** The request's own directory under TMP_DIR, while the export is running. */
    protected Path exportDirectory() throws Exception {
        try (var children = Files.list(tmpDir)) {
            return children.filter(Files::isDirectory).findFirst().orElseThrow();
        }
    }

    /**
     * Stores an HRM report file and links it to a chart. Each content becomes one report of the
     * file, in order: a {@code byte[]} is Base64 media of a PDF report, a {@code String} is the
     * text of a text report.
     */
    protected void hrmDocument(int demographicNo, int hrmDocumentId, String fileName, Object... contents) throws Exception {
        writeHrmFile(documentDir.resolve(fileName), contents);
        linkHrmDocument(demographicNo, hrmDocumentId, fileName);
    }

    /** Links a stored HRM file, whatever it holds, to a chart. */
    protected void linkHrmDocument(int demographicNo, int hrmDocumentId, String fileName) {
        HRMDocumentToDemographic link = new HRMDocumentToDemographic();
        link.setHrmDocumentId(hrmDocumentId);
        chartHrmDocuments.computeIfAbsent(String.valueOf(demographicNo), chart -> new ArrayList<>()).add(link);
        HRMDocument document = new HRMDocument();
        document.setReportFile(fileName);
        document.setSourceFacility("FAKE Hospital");
        when(hrmDocumentDao.findById(hrmDocumentId)).thenReturn(List.of(document));
    }

    /** Writes an HRM report file the way the hospital report manager stores one. */
    protected static void writeHrmFile(Path file, Object... contents) throws Exception {
        PatientRecord record = new PatientRecord();
        for (Object content : contents) {
            ReportsReceived report = new ReportsReceived();
            ReportContent reportContent = new ReportContent();
            if (content instanceof byte[] media) {
                reportContent.setMedia(media);
                report.setFormat(ReportFormat.BINARY);
                report.setFileExtensionAndVersion(".pdf");
            } else {
                reportContent.setTextContent((String) content);
                report.setFormat(ReportFormat.TEXT);
                report.setFileExtensionAndVersion("txt");
            }
            report.setContent(reportContent);
            report.setClazz(ReportClass.CONSULTANT_REPORT);
            report.setSendingFacility("4001");
            record.getReportsReceived().add(report);
        }
        OmdCds root = new OmdCds();
        root.setPatientRecord(record);
        JAXBContext.newInstance("io.github.carlos_emr.carlos.hospitalReportManager.xsd")
                .createMarshaller().marshal(root, file.toFile());
    }

    /** The name of a chart's XML inside the archive. */
    protected static String patientXml(int demographicNo, String firstName) {
        return PROVIDER_FOLDER + "/" + firstName + "_FAKE-Patient_" + demographicNo + "_15031980.xml";
    }

    /** @return whether anything was written to the response body */
    protected boolean nothingStreamed() {
        return streamed.size() == 0;
    }

    /** The streamed archive's files by entry name; folders are left out. */
    protected Map<String, byte[]> streamedArchive() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(streamed.toByteArray()))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), zip.readAllBytes());
                }
            }
        }
        return entries;
    }

    /** @return the directories the action left under TMP_DIR; none after any outcome */
    protected List<Path> exportDirectoriesLeft() throws Exception {
        try (var left = Files.list(tmpDir)) {
            return left.toList();
        }
    }

    private static Object swapPreventionDataField(String name, Object value) throws Exception {
        Field field = PreventionData.class.getDeclaredField(name);
        field.setAccessible(true);
        Object previous = field.get(null);
        field.set(null, value);
        return previous;
    }
}
