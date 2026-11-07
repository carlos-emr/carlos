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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportJobService;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit") @Tag("cds-export")
class CdsExportRetainedImportOptionsReaderUnitTest {
    @TempDir Path directory;
    private static final String RUN="11111111-1111-4111-8111-111111111111", RULES="a".repeat(64), SOURCE="b".repeat(64);
    private final ObjectMapper json=new ObjectMapper();
    private CdsImportJobDocumentDto document;
    private byte[] bytes;
    private final Map<String, String> run=new LinkedHashMap<>();
    @BeforeEach void setup()throws Exception{setDocument(false, 0, RULES); }
    @Test void shouldVerifyOrdinaryOptions_withoutReturningPrivatePathsOrTheRequest()throws Exception{
        var options=verify(); assertThat(options.synthetic()).isFalse(); assertThat(options.timeshiftDays()).isZero(); assertThat(options.sourceProfile()).isEqualTo("omd-5.2"); assertThat(options.toString()).doesNotContain(directory.toString(), "source.xml", RUN);
    }
    @Test void shouldReadPositiveAndNegativeShifts_fromTheOriginalSealedAcceptance()throws Exception{
        for (int days:new int[]{365, -365, 0}){setDocument(true, days, RULES); var options=verify(); assertThat(options.synthetic()).isTrue(); assertThat(options.timeshiftDays()).isEqualTo(days); }
    }
    @Test void shouldRefusePreviewDocuments_forAppliedClinicalEvidence()throws Exception{
        run.put("dry_run", "1"); assertThatThrownBy(this::verify).hasMessage("CDS_EXPORT_RETAINED_IMPORT_OPTIONS_SCOPE_INVALID");
    }
    @Test void shouldDetectChangedBytesOrPointers_beforeParsingTheOriginalDocument()throws Exception{
        bytes=Arrays.copyOf(bytes, bytes.length+1); bytes[bytes.length-1]=' '; assertThatThrownBy(this::verify).hasMessage("CDS_EXPORT_RETAINED_IMPORT_DOCUMENT_HASH_MISMATCH"); setup(); run.put("snapshot_location", directory.resolve("job-other.json").toString()); assertThatThrownBy(this::verify).hasMessage("CDS_EXPORT_RETAINED_IMPORT_DOCUMENT_HASH_MISMATCH");
    }
    @Test void shouldBindHashNamedDocuments_toRunOptionsAndSnapshotEvidence()throws Exception{
        for (String field:List.of("operator", "provider_no", "source_namespace", "profile", "options_digest", "ruleset_digest", "source_digest")){setup(); run.put(field, "different"); assertThatThrownBy(this::verify).hasMessage("CDS_EXPORT_RETAINED_IMPORT_DOCUMENT_BINDING_MISMATCH"); }
    }
    @Test void shouldRefuseAnotherProtectedDirectory_forTheSameOriginalRun()throws Exception{
        run.put("snapshot_location", directory.resolve("another").resolve("job-"+sha(bytes)+".json").toString()); assertThatThrownBy(this::verify).hasMessage("CDS_EXPORT_RETAINED_IMPORT_DOCUMENT_BINDING_MISMATCH");
    }
    @Test void shouldVerifyTheStoredOptionsDigest_forLegacyDocumentsWithoutAnAcceptedRuleset()throws Exception{
        setDocument(false, 0, null); assertThat(verify().rulesetSha256()).isEqualTo(RULES); run.put("options_digest", "c".repeat(64)); assertThatThrownBy(this::verify).hasMessage("CDS_EXPORT_RETAINED_IMPORT_DOCUMENT_BINDING_MISMATCH");
    }
    @Test void shouldRefuseDuplicateKeysTrailingDocumentsAndCoercion_whenDecodingTheOriginalJob()throws Exception{
        String text=new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        for (String changed:List.of(text+"{}", text.replace("\"uploadId\":null", "\"uploadId\":null,\"uploadId\":null"), text.replace("\"timeshiftDays\":0", "\"timeshiftDays\":\"0\""))){setBytes(changed.getBytes(java.nio.charset.StandardCharsets.UTF_8)); assertThatThrownBy(this::verify).hasMessage("CDS_EXPORT_RETAINED_IMPORT_DOCUMENT_INVALID"); }
    }
    @Test void shouldRefuseOversizedAndNonJsonInput_beforeDecoding()throws Exception{
        bytes=new byte[65537]; assertThatThrownBy(this::verify).hasMessage("CDS_EXPORT_RETAINED_IMPORT_OPTIONS_SCOPE_INVALID"); bytes=new byte[]{'['}; assertThatThrownBy(this::verify).hasMessage("CDS_EXPORT_RETAINED_IMPORT_OPTIONS_SCOPE_INVALID");
    }
    @Test void shouldPropagateCancellation_beforeAndAfterTheBoundedDecode()throws Exception{
        assertThatThrownBy(()-> new CdsExportRetainedImportOptionsReader().verify(row(), bytes, ()-> {throw new IllegalStateException("CANCELLED"); })).hasMessage("CANCELLED"); var count=new AtomicInteger(); assertThatThrownBy(()-> new CdsExportRetainedImportOptionsReader().verify(row(), bytes, ()-> {if (count.incrementAndGet()==2)throw new IllegalArgumentException("CANCELLED_AFTER_READ"); })).hasMessage("CANCELLED_AFTER_READ");
    }
    @Test void shouldRequireApprovedRootsAndPrivateRegularFiles_forProtectedReads()throws Exception{
        var permissions=java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")); Path root=java.nio.file.Files.createTempDirectory(Path.of("target").toAbsolutePath(), "owned-options-", permissions); Path previous=directory;
        try (var ancestors=org.mockito.Mockito.mockStatic(io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportProtectedFiles.class, org.mockito.Mockito.CALLS_REAL_METHODS)){directory=root; setup(); java.nio.file.Files.createDirectory(document.jobRoot(), permissions);
            // The sandbox root has a foreign UID; real ancestor checks run in the installed container proof.
            ancestors.when(()-> io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportProtectedFiles.dataDirectory(root)).thenReturn(root); ancestors.when(()-> io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportProtectedFiles.dataDirectory(document.jobRoot())).thenReturn(document.jobRoot()); Path file=Path.of(run.get("snapshot_location")); java.nio.file.Files.write(file, bytes, java.nio.file.StandardOpenOption.CREATE_NEW); java.nio.file.Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")); assertThat(new CdsExportRetainedImportOptionsReader().readProtected(root, row(), ()-> {}).timeshiftDays()).isZero();
            java.nio.file.Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--")); assertThatThrownBy(()-> new CdsExportRetainedImportOptionsReader().readProtected(root, row(), ()-> {})).hasMessage("CDS_EXPORT_RETAINED_IMPORT_DOCUMENT_UNAVAILABLE");
            java.nio.file.Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")); java.nio.file.Files.delete(file); java.nio.file.Files.createSymbolicLink(file, root.resolve("missing")); assertThatThrownBy(()-> new CdsExportRetainedImportOptionsReader().readProtected(root, row(), ()-> {})).hasMessage("CDS_EXPORT_RETAINED_IMPORT_DOCUMENT_UNAVAILABLE");
        }finally {directory=previous; try (var paths=java.nio.file.Files.walk(root)){for (Path path:paths.sorted(Comparator.reverseOrder()).toList())java.nio.file.Files.delete(path); }}
    }
    @Test void shouldVerifyContentAndContainment_forProtectedOriginalFiles()throws Exception{
        var permissions=java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")); Path root=java.nio.file.Files.createTempDirectory(Path.of("target").toAbsolutePath(), "owned-options-", permissions); Path previous=directory;
        try (var ancestors=org.mockito.Mockito.mockStatic(io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportProtectedFiles.class, org.mockito.Mockito.CALLS_REAL_METHODS)){directory=root; setup(); java.nio.file.Files.createDirectory(document.jobRoot(), permissions);
            // The sandbox root has a foreign UID; real ancestor checks run in the installed container proof.
            ancestors.when(()-> io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportProtectedFiles.dataDirectory(root)).thenReturn(root); ancestors.when(()-> io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportProtectedFiles.dataDirectory(document.jobRoot())).thenReturn(document.jobRoot()); Path file=Path.of(run.get("snapshot_location")); java.nio.file.Files.write(file, Arrays.copyOf(bytes, bytes.length+1), java.nio.file.StandardOpenOption.CREATE_NEW); java.nio.file.Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")); assertThatThrownBy(()-> new CdsExportRetainedImportOptionsReader().readProtected(root, row(), ()-> {})).hasMessage("CDS_EXPORT_RETAINED_IMPORT_DOCUMENT_HASH_MISMATCH"); Path another=java.nio.file.Files.createDirectory(root.resolve("another"), permissions); ancestors.when(()-> io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportProtectedFiles.dataDirectory(another)).thenReturn(another); assertThatThrownBy(()-> new CdsExportRetainedImportOptionsReader().readProtected(another, row(), ()-> {})).hasMessage("CDS_EXPORT_RETAINED_IMPORT_DOCUMENT_UNAVAILABLE");
        }finally {directory=previous; try (var paths=java.nio.file.Files.walk(root)){for (Path path:paths.sorted(Comparator.reverseOrder()).toList())java.nio.file.Files.delete(path); }}
    }
    @Test void shouldDeriveTheExactCopyOptions_fromTheAuthenticatedOriginalCommandAndFrozenPlan()throws Exception {
        setDocument(true, 365, RULES); byte[] plan=copyPlan("course-7-student-123456-program-11", "123456", 11, 2); var options=verifyCopies(plan);
        var c=document.command(); var effective=new CdsImportCommand(c.input(), c.submissionKey(), c.providerNo(), 11, 2, c.sourceNamespace(), c.sourceProfile(), c.mergeExisting(), c.dryRun(), c.matchProviderNames(), c.timeshiftDays(), c.courseId(), c.synthetic());
        assertThat(options.copyOptionsSha256()).containsExactlyEntriesOf(Map.of("course-7-student-123456-program-11", CdsImportJobService.optionsDigest(effective)));
        assertThat(options.optionsSha256()).isEqualTo(CdsImportJobService.optionsDigest(c)).isNotEqualTo(CdsImportJobService.optionsDigest(effective));
        assertThat(options.copiesSha256()).isEqualTo(sha(plan)); assertThatThrownBy(()-> options.copyOptionsSha256().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void shouldRefuseChangedCopyBytes_whenTheOriginalCopySealNoLongerMatches()throws Exception {
        setDocument(true, 365, RULES); byte[] plan=copyPlan("course-7-student-123456-program-11", "123456", 11, 2), seal=(sha(plan)+"\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII); plan[plan.length-2]^=1;
        assertThatThrownBy(()-> new CdsExportRetainedImportOptionsReader().verifyCopies(row(), bytes, plan, seal, ()-> {})).hasMessage("CDS_EXPORT_RETAINED_COPY_PLAN_HASH_MISMATCH");
    }
    @Test void shouldRefuseMismatchedCourseStudentProgramOrFacility_withAValidCopyFileHash()throws Exception {
        setDocument(true, 365, RULES);
        for (byte[] plan:List.of(copyPlan("course-8-student-123456-program-11", "123456", 11, 2), copyPlan("course-7-student-123457-program-11", "123456", 11, 2), copyPlan("course-7-student-123456-program-12", "123456", 11, 2), copyPlan("course-7-student-123456-program-11", "123456", 11, null)))
            assertThatThrownBy(()-> verifyCopies(plan)).hasMessage("CDS_EXPORT_RETAINED_COPY_PLAN_INVALID");
    }
    @Test void shouldRefuseDuplicateAndUnterminatedCopyRows_beforeProducingCopyBindings()throws Exception {
        setDocument(true, 365, RULES); byte[] plan=copyPlan("course-7-student-123456-program-11", "123456", 11, 2); byte[] duplicate=new byte[plan.length*2]; System.arraycopy(plan, 0, duplicate, 0, plan.length); System.arraycopy(plan, 0, duplicate, plan.length, plan.length);
        for (byte[] invalid:List.of(duplicate, Arrays.copyOf(plan, plan.length-1), new byte[]{'\n'}))assertThatThrownBy(()-> verifyCopies(invalid)).hasMessage("CDS_EXPORT_RETAINED_COPY_PLAN_INVALID");
    }
    @Test void shouldKeepClinicalImportsIndependent_fromCourseCopyFiles()throws Exception {
        byte[] plan=copyPlan("course-7-student-123456-program-11", "123456", 11, 2); assertThatThrownBy(()-> verifyCopies(plan)).hasMessage("CDS_EXPORT_RETAINED_COPY_PLAN_INVALID"); assertThat(verify().copyOptionsSha256()).containsEntry("clinical", run.get("options_digest"));
    }
    @Test void shouldPropagateCancellation_afterVerifyingTheCopyPlan()throws Exception {
        setDocument(true, 365, RULES); byte[] plan=copyPlan("course-7-student-123456-program-11", "123456", 11, 2); var count=new AtomicInteger(); assertThatThrownBy(()-> new CdsExportRetainedImportOptionsReader().verifyCopies(row(), bytes, plan, (sha(plan)+"\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII), ()-> {if (count.incrementAndGet()==4)throw new IllegalStateException("CANCELLED_COPY_READ"); })).hasMessage("CANCELLED_COPY_READ");
    }
    private byte[] copyPlan(String key, String student, Integer program, Integer facility)throws Exception {
        return (json.writeValueAsString(new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsCourseCopyDto(key, student, program, facility))+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    private CdsExportRetainedImportOptionsReader.Options verifyCopies(byte[] plan)throws Exception {
        return new CdsExportRetainedImportOptionsReader().verifyCopies(row(), bytes, plan, (sha(plan)+"\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII), ()-> {});
    }
    private void setDocument(boolean synthetic, int days, String acceptedRules)throws Exception{
        var command=new CdsImportCommand(directory.resolve("source.xml"), "submission", "12", null, null, "fixture", "omd-5.2", false, false, false, days, synthetic?7:null, synthetic); var actor=new CdsImportActorDto("agent", "12", "12", null, null, null); var root=directory.resolve("job-root"); var snapshot=new CdsSnapshotDto(root.resolve("snapshot"), root.resolve("files"), SOURCE, 1, 1, 100); document=new CdsImportJobDocumentDto(command, actor, snapshot, null, root, acceptedRules);
        run.clear(); Plan.IMPORT_RUNS.columns().forEach(column-> run.put(column, null)); run.put("id", RUN); run.put("dry_run", "0"); run.put("operator", "agent"); run.put("provider_no", "12"); run.put("source_namespace", "fixture"); run.put("profile", "omd-5.2"); run.put("options_digest", CdsImportJobService.optionsDigest(command)); run.put("ruleset_digest", RULES); run.put("source_digest", SOURCE); setBytes(json.writeValueAsBytes(document));
    }
    private void setBytes(byte[] value){bytes=value; run.put("snapshot_location", document.jobRoot().resolve("job-"+sha(bytes)+".json").toString()); }
    private static String sha(byte[] bytes){return HexFormat.of().formatHex(CdsImportKeys.digest().digest(bytes)); }
    private CdsExportCapturedTextRowDto row(){return new CdsExportCapturedTextRowDto("cds_import_run", RUN, run); }
    private CdsExportRetainedImportOptionsReader.Options verify()throws Exception{return new CdsExportRetainedImportOptionsReader().verify(row(), bytes, ()-> {}); }
}
