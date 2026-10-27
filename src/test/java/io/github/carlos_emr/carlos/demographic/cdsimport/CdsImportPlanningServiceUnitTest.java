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
package io.github.carlos_emr.carlos.demographic.cdsimport;

import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsClinicalException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportKeys;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class CdsImportPlanningServiceUnitTest {
    @TempDir Path temp;

    @Test void shouldAccountIdenticalSourcePatientOnce_whenSeparateXmlFilesRepeatIt() throws Exception {
        Fixture fixture=fixture(false,true);
        fixture.planner.plan("run",1,fixture.document,fixture.snapshot);
        verify(fixture.ledger).seal(eq("run"),eq(1L),matches("[0-9a-f]{64}"),eq(1L),eq(8L));
        verify(fixture.ledger,times(2)).planPatient(eq("run"),eq(1L),anyString(),eq("source-id"),eq("clinical"),anyString());
        verify(fixture.ledger,times(8)).planUnit(eq("run"),eq(1L),anyString(),anyString(),anyString(),anyLong(),anyString(),anyString());
        verify(fixture.ledger).diagnostic(eq("run"),eq(1L),anyString(),isNull(),eq("WARNING"),eq("DUPLICATE_IDENTICAL_SOURCE_PATIENT"),
                eq("PREFLIGHT"),anyString(),anyString(),anyString(),eq(false));
    }

    @Test void shouldWithholdSealAndRetainPreciseClinicalFailure_whenNormalizationRefusesSource() throws Exception {
        Fixture fixture=fixture(true,false);
        fixture.planner.plan("run",1,fixture.document,fixture.snapshot);
        verify(fixture.ledger).stage("run",1,"REVIEW_REQUIRED");
        verify(fixture.ledger,never()).seal(anyString(),anyLong(),anyString(),anyLong(),anyLong());
        verify(fixture.ledger).diagnostic(eq("run"),eq(1L),eq(CdsImportKeys.hash("test-source","source-id","clinical")),isNull(),eq("ERROR"),eq("DATE_PRECISION_UNSUPPORTED"),
                eq("PREFLIGHT"),endsWith("/StartDate[1]/FullDate[1]"),anyString(),anyString(),eq(true));
    }

    @Test void shouldWithholdDiscoveryCompleteness_whenAnyXmlCannotParse() throws Exception {
        Fixture fixture=fixture(false,false);
        Files.writeString(fixture.source,"<OmdCds xmlns='cds'><broken>");
        var changed=new CdsSnapshotService().snapshot(fixture.source,fixture.source.getParent(),fixture.document.jobRoot(),"changed",CdsInputLimitsDto.defaults(),()->false);
        fixture.planner.plan("run",1,fixture.document,changed);
        verify(fixture.ledger).stage("run",1,"REVIEW_REQUIRED");
        verify(fixture.ledger,never()).discovery(anyString(),anyLong(),anyLong(),anyLong(),anyLong());
        verify(fixture.ledger,never()).seal(anyString(),anyLong(),anyString(),anyLong(),anyLong());
    }

    @Test void shouldRequireExactCompletedCanonicalCoverage_insteadOfRefusingAnUnchangedNoOp() throws Exception {
        Fixture fixture=fixture(false,false);canonical(fixture);
        fixture.planner.plan("run",1,fixture.document,fixture.snapshot);
        verify(fixture.ledger).requireCompleteCanonicalReplay(eq("run"),eq(1L),anyString());
        verify(fixture.identity,never()).resolve(any(),any());
        verify(fixture.ledger).seal(eq("run"),eq(1L),anyString(),eq(1L),eq(8L));
    }

    @Test void shouldRequireOriginalRunResume_whenCanonicalCheckpointCoverageIsPartial() throws Exception {
        Fixture fixture=fixture(false,false);canonical(fixture);
        doThrow(new CdsImportException("PRIOR_IMPORT_MUST_RESUME")).when(fixture.ledger)
                .requireCompleteCanonicalReplay(anyString(),anyLong(),anyString());
        fixture.planner.plan("run",1,fixture.document,fixture.snapshot);
        verify(fixture.ledger).stage("run",1,"REVIEW_REQUIRED");
        verify(fixture.ledger,never()).seal(anyString(),anyLong(),anyString(),anyLong(),anyLong());
    }

    @Test void shouldAccountExplicitlyDeclinedPatient_whenValidSchemaHasTypedClinicalFailure() throws Exception {
        var review=mock(CdsCorrectionLookupService.class);
        when(review.version("run")).thenReturn(1L);when(review.declined(eq("run"),anyString())).thenReturn(true);
        Fixture fixture=fixture(true,false,review);
        fixture.planner.plan("run",1,fixture.document,fixture.snapshot);
        verify(fixture.ledger).refusePlannedPatient(eq("run"),eq(1L),anyString(),eq("REFUSED"));
        verify(fixture.ledger,never()).planUnit(anyString(),anyLong(),anyString(),anyString(),anyString(),anyLong(),anyString(),anyString());
        verify(fixture.ledger).seal(eq("run"),eq(1L),anyString(),eq(1L),eq(0L));
        verify(review).revalidated(eq("run"),eq(1L),eq(1L),matches("[0-9a-f]{64}"));
    }

    private static void canonical(Fixture fixture) {
        when(fixture.ledger.planPatient(anyString(),anyLong(),anyString(),anyString(),anyString(),anyString())).thenAnswer(call->
                new CdsImportPatientDto(call.getArgument(2),call.getArgument(3),call.getArgument(4),
                        CdsImportKeys.hash("test-source",call.getArgument(3),call.getArgument(4)),99,"PLANNED",false,0));
        when(fixture.identity.matchesCanonical(any(),eq(99),any())).thenReturn(true);
    }

    private Fixture fixture(boolean clinicalFailure,boolean duplicate) throws Exception {
        return fixture(clinicalFailure,duplicate,null);
    }
    private Fixture fixture(boolean clinicalFailure,boolean duplicate,CdsCorrectionLookupService reviews) throws Exception {
        Path sources=Files.createDirectory(temp.resolve("sources"));
        Path source=Files.writeString(sources.resolve("one.xml"),CdsXmlParserUnitTest.fixture());
        if(duplicate) Files.writeString(sources.resolve("two.xml"),CdsXmlParserUnitTest.fixture());
        Path root=Files.createDirectory(temp.resolve("job"));
        var snapshot=new CdsSnapshotService().snapshot(sources,sources,root,"snapshot",CdsInputLimitsDto.defaults(),()->false);
        var command=new CdsImportCommand(source,"submission","1",null,null,"test-source","omd-5.2",false,false,false,0,null,false);
        var actor=new CdsImportActorDto("operator","1","1",null,null,null);
        var configuration=mock(CdsImportConfigurationService.class);when(configuration.limits()).thenReturn(CdsInputLimitsDto.defaults());
        var policy=new CdsImportConfigurationService(new java.util.Properties());
        when(configuration.normalizationPolicyDigest(any())).thenAnswer(call->policy.normalizationPolicyDigest(call.getArgument(0)));
        String ruleset=CdsImportJobService.rulesetDigest(command,configuration);
        var document=new CdsImportJobDocumentDto(command,actor,snapshot,null,root,ruleset);
        var ledger=mock(CdsImportLedgerService.class);
        when(ledger.status("run")).thenReturn(new CdsImportJobDto("run","operator","1","test-source","omd-5.2","PREFLIGHTING",
                snapshot.sha256(),CdsImportJobService.optionsDigest(command),ruleset,null,0,0,1,false,
                Instant.EPOCH,Instant.EPOCH,0,null,null));
        when(ledger.planPatient(anyString(),anyLong(),anyString(),anyString(),anyString(),anyString())).thenAnswer(call->
                new CdsImportPatientDto(call.getArgument(2),call.getArgument(3),call.getArgument(4),
                        CdsImportKeys.hash("test-source",call.getArgument(3),call.getArgument(4)),null,"PLANNED",false,0));
        var identity=mock(CdsPatientIdentityResolver.class);
        when(identity.source(any(),any())).thenReturn(new CdsPatientIdentityDto("source-id","First","Last",LocalDate.of(1971,3,4),"123","ON","F","AC"));
        when(identity.resolve(any(),any())).thenReturn(new CdsIdentityDecisionDto(CdsIdentityDecisionDto.Outcome.NO_MATCH,null));
        var nativeRecords=mock(CdsImportNativeService.class);
        if(clinicalFailure) doAnswer(call->{
            if(((CdsRecordDto)call.getArgument(1)).section().equals("MedicationsAndTreatments")) {
                throw new CdsClinicalException("DATE_PRECISION_UNSUPPORTED","StartDate");
            }
            return null;
        }).when(nativeRecords).validate(any(),any(),any(),anyString());
        var identityRegistry=mock(CdsPreflightIdentityService.class);java.util.Set<String> seen=new java.util.HashSet<>();
        when(identityRegistry.register(anyString(),anyLong(),anyString(),anyString(),any(),anyBoolean())).thenAnswer(call->seen.add(call.getArgument(2)));
        var planner=new CdsImportPlanningService(ledger,configuration,new CdsSnapshotService(),new CdsXmlParser(),new CdsFieldParser(),identity,
                nativeRecords,mock(CdsCourseService.class),mock(CdsImportFileService.class),mock(CdsImportJobService.class),identityRegistry,reviews);
        return new Fixture(ledger,planner,document,snapshot,source,identity);
    }
    private record Fixture(CdsImportLedgerService ledger,CdsImportPlanningService planner,CdsImportJobDocumentDto document,CdsSnapshotDto snapshot,Path source,CdsPatientIdentityResolver identity) { }
}
