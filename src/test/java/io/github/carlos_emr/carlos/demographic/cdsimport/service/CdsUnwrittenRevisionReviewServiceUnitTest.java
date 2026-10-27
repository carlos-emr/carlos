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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class CdsUnwrittenRevisionReviewServiceUnitTest {
    @TempDir Path root;
    private static final String RUN="19df2cb9-cc1a-40c6-898c-014242438e25", PRIOR="36a50e64-6591-4acb-86af-7b64a2e94acb";
    private static final String PATIENT="a".repeat(64), SOURCE="d".repeat(64);
    private final CdsImportLedgerService ledger=mock(CdsImportLedgerService.class);
    private final CdsImportJobService jobs=mock(CdsImportJobService.class);
    private final CdsImportConfigurationService config=mock(CdsImportConfigurationService.class);
    private final CdsSnapshotService snapshots=new CdsSnapshotService();
    private final CdsXmlParser parser=new CdsXmlParser();
    private final CdsFieldParser fields=new CdsFieldParser();
    private CdsUnwrittenRevisionReviewService service;
    private CdsImportJobDocumentDto document;
    private CdsSnapshotDto snapshot;
    private CdsImportRevisionDto proof;
    private Path context,attempt,original;

    @BeforeEach
    void fixture() throws Exception {
        var limits=CdsInputLimitsDto.defaults();when(config.limits()).thenReturn(limits);
        when(config.normalizationPolicyDigest(any())).thenReturn("6".repeat(64));
        service=new CdsUnwrittenRevisionReviewService(ledger,jobs,config,snapshots,parser,fields,mock(CdsCorrectionLookupService.class));
        Path input=Files.createDirectory(root.resolve("input")),job=Files.createDirectory(root.resolve("job"));
        String xml;try(var stream=getClass().getResourceAsStream("/demographic/cds-import-summary.xml")){xml=new String(stream.readAllBytes(),StandardCharsets.UTF_8);}
        original=Files.writeString(input.resolve("patient.xml"),xml);
        snapshot=snapshots.snapshot(original,input,job,"snapshot",limits,()->false);
        CdsSnapshotFileDto[] selected={null};snapshots.forEachXml(snapshot,value->selected[0]=value);var source=selected[0];
        Path parsed=Files.createDirectory(job.resolve("parsed"));Path folder=parsed.resolve(CdsImportKeys.hash(source.relativeName(),source.sha256()));
        String[] vendor={null};parser.parse(source.path(),folder,limits,()->false,record->{
            if("Demographics".equals(record.section())) try{vendor[0]=fields.firstToken(record,"UniqueVendorIdSequence",128);}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        });
        Path result=Files.writeString(folder.resolve("parse-result.json"),"{}");
        var command=new CdsImportCommand(original,"submission","999998",null,null,"test-source","pcon-2024-02",false,false,false,0,null,false);
        String options=CdsImportJobService.optionsDigest(command),rules=CdsImportJobService.rulesetDigest(command,config);
        var actor=new CdsImportActorDto("root","999998",null,null,null,null);
        document=new CdsImportJobDocumentDto(command,actor,snapshot,null,job,rules);
        when(ledger.status(RUN)).thenReturn(status(RUN,snapshot.sha256(),options,rules));
        proof=new CdsImportRevisionDto(RUN,PATIENT,PATIENT,"test-source",vendor[0],"clinical","0".repeat(64),"1".repeat(64),"2".repeat(64),
                SOURCE,options,rules,null,0,true,List.of(PRIOR),1,false);
        when(ledger.unwrittenRevision(RUN,7,vendor[0],"clinical",SOURCE)).thenReturn(proof);
        when(ledger.unwrittenRevisionPriorRuns(RUN,7,vendor[0],"clinical","",100)).thenReturn(List.of(PRIOR));
        when(ledger.unwrittenRevisionPriorRuns(RUN,7,vendor[0],"clinical",PRIOR,100)).thenReturn(List.of());
        var old=new CdsImportJobDocumentDto(command,actor,snapshot,null,root.resolve("old-job"),"2".repeat(64));
        when(jobs.load(PRIOR)).thenReturn(old);when(jobs.snapshot(PRIOR,old)).thenReturn(snapshot);
        when(ledger.status(PRIOR)).thenReturn(status(PRIOR,snapshot.sha256(),options,"2".repeat(64)));
        var json=new ObjectMapper();byte[] bytes=json.writeValueAsBytes(Map.of("version",1,"sourceMember",source.relativeName(),"sourceXmlSha256",source.sha256(),
                "recordXPath","/OmdCds[1]/PatientRecord[1]/Demographics[1]","parseResultFile",result.toString(),"patientKey",PATIENT,"newPatientSourceDigest",SOURCE,"revision",proof));
        context=Files.write(folder.resolve("revision-conflict-"+CdsPaths.hex(CdsPaths.sha256().digest(bytes))+".json"),bytes);
        attempt=Files.createDirectory(job.resolve("review-attempt"));
    }

    @Test
    void shouldValidateActualPinnedXmlAndArchiveEveryPriorRun_whenAnUnwrittenRevisionIsPrepared() throws Exception {
        byte[] before=Files.readAllBytes(original);
        var prepared=service.prepare(RUN,7,PATIENT,context.toString(),document,snapshot,attempt,()->false);
        assertThat(prepared.revision()).isEqualTo(proof);assertThat(prepared.artifacts()).containsOnlyKeys("revision-context.json","lineage-approval.json","prior-run-provenance.jsonl");
        assertThat(Files.readString(attempt.resolve("prior-run-provenance.jsonl"))).contains(PRIOR,"sourceManifestSha256","FOOTER");
        assertThat(Files.readAllBytes(original)).isEqualTo(before);
        verify(ledger,never()).approveUnwrittenRevision(anyString(),anyLong(),anyLong(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString());
    }

    @Test
    void shouldRejectChangedConflictBytesBeforeCanonicalLookup_whenTheImmutableContextHashNoLongerMatches() throws Exception {
        Files.writeString(context,"changed");
        assertThatThrownBy(()->service.prepare(RUN,7,PATIENT,context.toString(),document,snapshot,attempt,()->false)).hasMessage("REVISION_PROOF_HASH_MISMATCH");
        verify(ledger,never()).unwrittenRevision(anyString(),anyLong(),anyString(),anyString(),anyString());
    }

    @Test
    void shouldRefuseMissingPriorRunPages_whenAProvenanceArchiveWouldOtherwiseBeIncomplete() throws Exception {
        when(ledger.unwrittenRevisionPriorRuns(RUN,7,proof.sourcePatientId(),"clinical","",100)).thenReturn(List.of());
        assertThatThrownBy(()->service.prepare(RUN,7,PATIENT,context.toString(),document,snapshot,attempt,()->false)).hasMessage("REVISION_PROVENANCE_INCOMPLETE");
    }

    private static CdsImportJobDto status(String id,String source,String options,String rules) {
        return new CdsImportJobDto(id,"root","999998","test-source","pcon-2024-02","REVIEW_REQUIRED",source,options,rules,null,0,0,7,false,Instant.EPOCH,Instant.EPOCH,0,null,null);
    }
}
