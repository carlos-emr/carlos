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
package io.github.carlos_emr.carlos.casemgmt.service;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.DefaultRoleAccessDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramAccessDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.PMmodule.utility.ProgramAccessCache;
import io.github.carlos_emr.carlos.PMmodule.utility.RoleCache;
import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.clinic.ClinicData;
import io.github.carlos_emr.carlos.commn.dao.CaisiAccessTypeDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.model.security.Secrole;
import io.github.carlos_emr.carlos.daos.security.SecroleDao;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.commons.collections4.map.MultiValueMap;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockConstruction;

/**
 * Real H2 role permissions and actual PDF assembly must agree with the originating chart.
 * Only clinic-header metadata is isolated; notes, authorization, renderer and merger are real.
 *
 * @since 2026-10-25
 */
@Tag("integration")
@Tag("security")
class CaseManagementPrintVisibilityIntegrationTest extends CarlosTestBase {
    private static final String READER = "991001";
    private static final String WRITER = "991002";
    private static final long PROGRAM = 991001L;
    private static final String VISIBLE = "FAKE visible shared-program note café 中文";
    private static final String HIDDEN = "FAKE reporter-role hidden note café 中文";
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager entityManager;
    @TempDir Path documents;
    private CaseManagementManager manager;
    private LoggedInInfo login;
    private int patient;
    private CaseManagementNote visible;
    private CaseManagementNote hidden;
    private String facilitySetting;
    private String documentSetting;
    private final List<CacheField> originalCacheFields = new ArrayList<>();

    @BeforeEach
    void configureRealReporterRoleRestrictions() {
        facilitySetting = CarlosProperties.getInstance().getProperty("FILTER_ON_FACILITY");
        documentSetting = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("FILTER_ON_FACILITY", "false");
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documents.toString());
        bindRealAuthorizationCaches();
        manager = SpringUtils.getBean(CaseManagementManager.class);
        for (String provider : List.of(READER, WRITER)) entityManager.createNativeQuery(
                "MERGE INTO provider(provider_no,first_name,last_name,provider_type,sex,specialty,status) KEY(provider_no)"
                        + " VALUES (?1,'FAKE','PdfVisibility','doctor','U','GP','1')")
                .setParameter(1, provider).executeUpdate();
        entityManager.createNativeQuery("MERGE INTO program(id,name,type) KEY(id)"
                        + " VALUES (?1,'FAKE-PDF-VISIBILITY','community')")
                .setParameter(1, PROGRAM).executeUpdate();
        entityManager.createNativeQuery("UPDATE program SET userDefined=1,holdingTank=0,allowBatchAdmission=0,"
                        + "allowBatchDischarge=0,hic=0,transgender=0,firstNation=0,alcohol=0,physicalHealth=0,"
                        + "mentalHealth=0,housing=0,enableEncounterTime=0,enableEncounterTransportationTime=0 WHERE id=?1")
                .setParameter(1, PROGRAM).executeUpdate();
        Secrole readerRole = role("FAKE-PDF-READER");
        Secrole hiddenRole = role("FAKE-PDF-REPORTER");
        ProgramProvider membership = new ProgramProvider();
        membership.setProgramId(PROGRAM);
        membership.setProviderNo(READER);
        membership.setRoleId(readerRole.getId());
        // Use the real DAO writer so its provider/program cache is invalidated
        // between independently rolled-back parameterized invocations.
        ((ProgramProviderDAO) SpringUtils.getBean("programProviderDAO")).saveProgramProvider(membership);
        entityManager.flush();
        entityManager.clear();
        Demographic demographic = new Demographic();
        demographic.setFirstName("FAKE");
        demographic.setLastName("PdfVisibility");
        demographic.setYearOfBirth("1980");
        demographic.setMonthOfBirth("01");
        demographic.setDateOfBirth("02");
        demographic.setProviderNo("");
        demographic.setSex("U");
        entityManager.persist(demographic);
        entityManager.flush();
        patient = demographic.getDemographicNo();
        login = new LoggedInInfo();
        login.setLoggedInProvider(entityManager.find(Provider.class, READER));
        visible = note(readerRole.getId(), VISIBLE, PROGRAM + 1);
        hidden = note(hiddenRole.getId(), HIDDEN, PROGRAM);
        hibernateTemplate.flush();
        entityManager.flush();
        RoleCache.reload();
        ProgramAccessCache.setAccessMap(PROGRAM);
        List<CaseManagementNote> notes = manager.getNotes(Integer.toString(patient));
        assertThat(notes).extracting(CaseManagementNote::getId).contains(visible.getId(), hidden.getId());
        assertThat(manager.filterNotes(login, READER, notes, Long.toString(PROGRAM)))
                .extracting(CaseManagementNote::getId).containsExactly(visible.getId());
    }

    @AfterEach
    void restoreProcessSettings() {
        try {
            restore("FILTER_ON_FACILITY", facilitySetting);
            restore("DOCUMENT_DIR", documentSetting);
        } finally {
            for (CacheField original : originalCacheFields.reversed()) {
                ReflectionTestUtils.setField(original.owner(), original.name(), original.value());
                assertThat(ReflectionTestUtils.getField(original.owner(), original.name())).isSameAs(original.value());
            }
            originalCacheFields.clear();
        }
    }

    /**
     * These legacy caches capture Spring beans once per JVM. A preceding unit test may
     * initialize them from its mocks, so bind this transaction's real DAOs and isolated
     * maps. Restore the original objects rather than clearing or reloading another test's
     * cache; authorization below continues through the real manager and real H2 data.
     */
    private void bindRealAuthorizationCaches() {
        bindCacheField(RoleCache.class, "secroleDao", applicationContext.getBean(SecroleDao.class));
        bindCacheField(RoleCache.class, "defaultRoleAccessDAO", applicationContext.getBean(DefaultRoleAccessDAO.class));
        bindCacheField(RoleCache.class, "accessTypeDao", applicationContext.getBean(CaisiAccessTypeDao.class));
        bindCacheField(RoleCache.class, "roleMap", new HashMap<>());
        bindCacheField(RoleCache.class, "accessTypeMap", new HashMap<>());
        bindCacheField(RoleCache.class, "defaultRoleAccessMap", new MultiValueMap<>());
        bindCacheField(ProgramAccessCache.class, "programAccessDAO", applicationContext.getBean("programAccessDAO", ProgramAccessDAO.class));
        bindCacheField(ProgramAccessCache.class, "accessMaps", new HashMap<>());
    }

    private void bindCacheField(Class<?> owner, String name, Object replacement) {
        originalCacheFields.add(new CacheField(owner, name, ReflectionTestUtils.getField(owner, name)));
        ReflectionTestUtils.setField(owner, name, replacement);
    }

    private record CacheField(Class<?> owner, String name, Object value) { }

    @ParameterizedTest
    @ValueSource(strings = {"all", "date-range", "cpp", "other-meds"})
    void shouldOmitChartHiddenBodiesFromActualPdf_whenPrintingBulkScopes(String mode) throws Exception {
        boolean cpp = "cpp".equals(mode);
        boolean otherMeds = "other-meds".equals(mode);
        if (cpp || otherMeds) linkToCpp(otherMeds ? "OMeds" : "MedHistory");
        MockHttpServletRequest request = request(Long.toString(PROGRAM));
        if (otherMeds) request.setParameter("printRx", "true");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        print(request, bytes, !cpp && !otherMeds,
                cpp || otherMeds ? new String[0] : new String[]{visible.getId().toString(), hidden.getId().toString()},
                cpp, "date-range".equals(mode));
        assertThat(pdfText(bytes)).contains(VISIBLE).doesNotContain(HIDDEN);
    }

    @Test
    void shouldRefuseBeforeAnyOutput_whenAnExplicitSelectionContainsAHiddenNote() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertThatThrownBy(() -> print(request(Long.toString(PROGRAM)), bytes, false,
                new String[]{visible.getId().toString(), hidden.getId().toString()}, false, false))
                .isInstanceOf(CaseManagementPrint.NoteAccessDeniedException.class);
        assertThat(bytes.size()).isZero();
    }

    @Test
    void shouldRetainReadableSharedProgramNote_whenItsStoredProgramDiffersFromTheChart() throws Exception {
        assertThat(visible.getProgram_no()).isNotEqualTo(Long.toString(PROGRAM));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        print(request(Long.toString(PROGRAM)), bytes, false, new String[]{visible.getId().toString()}, false, false);
        assertThat(pdfText(bytes)).contains(VISIBLE).doesNotContain(HIDDEN);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0"})
    void shouldPreserveLegacyPrintVisibility_whenNoProgramRestrictionIsCaptured(String program) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        print(request(program), bytes, false,
                new String[]{visible.getId().toString(), hidden.getId().toString()}, false, false);
        assertThat(pdfText(bytes)).contains(VISIBLE, HIDDEN);
    }

    @Test
    void shouldRetainTheCapturedPolicy_whenALaterRestRequestContainsAConflictingPatientAlias() throws Exception {
        MockHttpServletRequest captured = request(Long.toString(PROGRAM));
        MockHttpServletRequest later = new MockHttpServletRequest();
        later.setSession(captured.getSession());
        later.setParameter("demographicNo", Integer.toString(patient));
        assertThat(ChartProgramContext.resolve(later)).isEqualTo(Long.toString(PROGRAM));
        // A later REST call carries a path patient and its legacy session capture,
        // without remember()'s request attribute or an explicit signed token.
        later.setParameter("demographic_no", Integer.toString(patient + 1));
        ByteArrayOutputStream bulk = new ByteArrayOutputStream();
        print(later, bulk, true, new String[0], false, false);
        assertThat(pdfText(bulk)).contains(VISIBLE).doesNotContain(HIDDEN);
        assertThat(later.getParameter("demographic_no")).isEqualTo(Integer.toString(patient + 1));
        ByteArrayOutputStream selected = new ByteArrayOutputStream();
        assertThatThrownBy(() -> print(later, selected, false,
                new String[]{visible.getId().toString(), hidden.getId().toString()}, false, false))
                .isInstanceOf(CaseManagementPrint.NoteAccessDeniedException.class);
        assertThat(selected.size()).isZero();
    }

    private void print(MockHttpServletRequest request, ByteArrayOutputStream bytes, boolean all,
                       String[] ids, boolean cpp, boolean dates) throws Exception {
        try (var clinic = mockConstruction(ClinicData.class)) {
            Calendar day = new GregorianCalendar(2026, Calendar.OCTOBER, 25);
            new CaseManagementPrint().doPrint(login, patient, all, ids, cpp,
                    Boolean.parseBoolean(request.getParameter("printRx")), false, false, false, dates, day, day, request, bytes);
        }
    }

    private MockHttpServletRequest request(String program) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("demographicNo", Integer.toString(patient));
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        if (program != null) ChartProgramContext.remember(request, program);
        return request;
    }

    private String pdfText(ByteArrayOutputStream bytes) throws Exception {
        assertThat(new String(bytes.toByteArray(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try (var pdf = Loader.loadPDF(bytes.toByteArray())) {
            assertThat(pdf.getNumberOfPages()).isPositive();
            return new PDFTextStripper().getText(pdf);
        }
    }

    private Secrole role(String name) {
        Secrole role = new Secrole();
        role.setRoleName(name);
        role.setDescription("FAKE PDF visibility fixture");
        entityManager.persist(role);
        entityManager.flush();
        return role;
    }

    private CaseManagementNote note(long role, String text, long program) {
        CaseManagementNote note = new CaseManagementNote();
        note.setDemographic_no(Integer.toString(patient));
        note.setProviderNo(WRITER);
        note.setSigning_provider_no(WRITER);
        note.setReporter_caisi_role(Long.toString(role));
        note.setReporter_program_team("0");
        note.setProgram_no(Long.toString(program));
        note.setSigned(true);
        note.setLocked(false);
        note.setArchived(false);
        note.setObservation_date(new GregorianCalendar(2026, Calendar.OCTOBER, 25, 12, 0, 0).getTime());
        note.setUpdate_date(new Date());
        note.setUuid(UUID.randomUUID().toString());
        note.setHistory(text);
        note.setNote(text);
        manager.saveNoteSimple(note);
        return note;
    }

    private void linkToCpp(String code) {
        Issue issue = new Issue();
        issue.setCode(code);
        issue.setDescription("FAKE CPP visibility");
        issue.setRole("FAKE-PDF-READER");
        issue.setType("system");
        entityManager.persist(issue);
        entityManager.flush();
        entityManager.createNativeQuery("INSERT INTO casemgmt_issue"
                        + "(demographic_no,issue_id,acute,certain,major,resolved,program_id,type,update_date)"
                        + " VALUES (?1,?2,0,1,0,0,?3,'',CURRENT_TIMESTAMP)")
                .setParameter(1, patient).setParameter(2, issue.getId()).setParameter(3, PROGRAM).executeUpdate();
        Number caseIssue = (Number) entityManager.createNativeQuery("SELECT id FROM casemgmt_issue"
                        + " WHERE demographic_no=?1 AND issue_id=?2")
                .setParameter(1, patient).setParameter(2, issue.getId()).getSingleResult();
        for (CaseManagementNote note : List.of(visible, hidden)) entityManager.createNativeQuery(
                "INSERT INTO casemgmt_issue_notes(id,note_id) VALUES (?1,?2)")
                .setParameter(1, caseIssue).setParameter(2, note.getId()).executeUpdate();
        assertThat(manager.getNotes(Integer.toString(patient), new String[]{issue.getId().toString()}))
                .extracting(CaseManagementNote::getId).contains(visible.getId(), hidden.getId());
    }

    private void restore(String key, String value) {
        if (value == null) CarlosProperties.getInstance().remove(key);
        else CarlosProperties.getInstance().setProperty(key, value);
    }
}
