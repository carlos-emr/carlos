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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportKeys;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.springframework.stereotype.Service;

/** File proof for an explicit zero-write canonical revision; publication remains a ledger transaction participant. */
@Service
public class CdsUnwrittenRevisionReviewService {
    private static final ObjectMapper JSON=com.fasterxml.jackson.databind.json.JsonMapper.builder(
            com.fasterxml.jackson.core.JsonFactory.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final CdsImportLedgerService ledger;
    private final CdsImportJobService jobs;
    private final CdsImportConfigurationService configuration;
    private final CdsSnapshotService snapshots;
    private final CdsXmlParser parser;
    private final CdsFieldParser fields;
    private final CdsCorrectionLookupService corrections;

    public CdsUnwrittenRevisionReviewService(CdsImportLedgerService ledger,CdsImportJobService jobs,
            CdsImportConfigurationService configuration,CdsSnapshotService snapshots,CdsXmlParser parser,
            CdsFieldParser fields,CdsCorrectionLookupService corrections) {
        this.ledger=ledger;this.jobs=jobs;this.configuration=configuration;this.snapshots=snapshots;
        this.parser=parser;this.fields=fields;this.corrections=corrections;
    }

    public Prepared prepare(String run,long epoch,String requestedPatient,String protectedLocation,
            CdsImportJobDocumentDto document,CdsSnapshotDto snapshot,Path attempt,BooleanSupplier cancelled) throws IOException {
        CdsImportLedgerService.requireNoTransaction();
        Path contextFile=CdsPaths.within(Path.of(protectedLocation),document.jobRoot().resolve("parsed"));
        byte[] bytes=CdsImportJobService.boundedFile(contextFile,65536);
        String digest=hash(bytes);
        if (!contextFile.getFileName().toString().equals("revision-conflict-"+digest+".json")) throw failure("REVISION_PROOF_HASH_MISMATCH");
        JsonNode context=JSON.readTree(bytes);
        if (context==null || !context.isObject() || context.path("version").asInt(-1)!=1) throw failure("REVISION_PROOF_INVALID");
        var supplied=JSON.treeToValue(context.path("revision"),CdsImportRevisionDto.class);
        String member=text(context,"sourceMember",2048),xmlSha=text(context,"sourceXmlSha256",64);
        String patient=text(context,"patientKey",64),newSource=text(context,"newPatientSourceDigest",64);
        String recordPath=text(context,"recordXPath",2048);
        if (!"/OmdCds[1]/PatientRecord[1]/Demographics[1]".equals(recordPath)
                || !run.equals(supplied.runId()) || !patient.equals(supplied.patientKey())
                || !patient.equals(requestedPatient) || !newSource.equals(supplied.newSourceDigest())
                || !document.command().sourceNamespace().equals(supplied.sourceNamespace())) throw failure("REVISION_PROOF_IDENTITY_MISMATCH");
        Path parseResult=CdsPaths.within(Path.of(text(context,"parseResultFile",4096)),document.jobRoot().resolve("parsed"));
        if (!parseResult.getParent().equals(contextFile.getParent()) || !Files.isRegularFile(parseResult,LinkOption.NOFOLLOW_LINKS)) throw failure("REVISION_PARSE_PROOF_MISSING");
        String folder=contextFile.getParent().getFileName().toString(),base=CdsImportKeys.hash(member,xmlSha);
        if (!folder.equals(base) && !folder.matches(java.util.regex.Pattern.quote(base)+"-attempt-[0-9a-f-]{36}")) throw failure("REVISION_ACCEPTED_SOURCE_MISMATCH");
        var status=ledger.status(run);
        if (!status.optionsDigest().equals(CdsImportJobService.optionsDigest(document.command()))
                || !status.rulesetDigest().equals(CdsImportJobService.rulesetDigest(document.command(),configuration))) throw failure("REVIEW_RUNTIME_REVISION_CHANGED");
        var fresh=ledger.unwrittenRevision(run,epoch,supplied.sourcePatientId(),supplied.copyKey(),newSource);
        if (!fresh.eligible()) throw failure("UNWRITTEN_REVISION_AFTER_WRITE_OR_UNCERTAINTY_REFUSED");
        if (fresh.priorRunCount()<1) throw failure("REVISION_PRIOR_SOURCE_PROOF_MISSING");
        if (!fresh.equals(supplied)) throw failure("CANONICAL_REVISION_CONFLICT");
        CdsSnapshotFileDto[] selected={null};
        snapshots.forEachXml(snapshot,xml -> {
            if (xml.relativeName().equals(member) && xml.sha256().equals(xmlSha)) {
                if (selected[0]!=null) throw failure("REVISION_SOURCE_AMBIGUOUS");selected[0]=xml;
            }
        });
        if (selected[0]==null) throw failure("REVISION_ACCEPTED_SOURCE_MISMATCH");
        String[] vendor={null};
        var parsed=parser.parse(selected[0].path(),attempt.resolve("revision-validated-source"),configuration.limits(),cancelled,record -> {
            if ("Demographics".equals(record.section())) {
                try {vendor[0]=fields.firstToken(record,"UniqueVendorIdSequence",128);}
                catch (IOException failure) {throw new java.io.UncheckedIOException(failure);}
            }
        },corrections.find(run,member,xmlSha,parseResult.getParent()));
        if (!xmlSha.equals(parsed.sourceSha256()) || !fresh.sourcePatientId().equals(vendor[0])) throw failure("REVISION_ACCEPTED_SOURCE_CHANGED");
        write(attempt.resolve("revision-context.json"),attempt,bytes);
        write(attempt.resolve("lineage-approval.json"),attempt,JSON.writeValueAsBytes(fresh));
        Path prior=attempt.resolve("prior-run-provenance.jsonl");long count=0;String cursor="";
        try (OutputStream output=CdsPaths.newPrivateFile(prior,attempt)) {
            line(output,Map.of("type","HEADER","expectedPriorRuns",fresh.priorRunCount(),"lineageKey",fresh.lineageKey()));
            while (true) {
                check(cancelled);List<String> page=ledger.unwrittenRevisionPriorRuns(run,epoch,fresh.sourcePatientId(),fresh.copyKey(),cursor,100);
                if (page.isEmpty()) break;
                for (String previous:page) {
                    if (previous.compareTo(cursor)<=0) throw failure("REVISION_PROVENANCE_PAGE_INVALID");
                    check(cancelled);var old=ledger.status(previous);var accepted=jobs.load(previous);var oldSnapshot=jobs.snapshot(previous,accepted);
                    if (!old.sourceDigest().equals(oldSnapshot.sha256()) || !old.optionsDigest().equals(CdsImportJobService.optionsDigest(accepted.command()))
                            || accepted.acceptedRulesetDigest()==null || !old.rulesetDigest().equals(accepted.acceptedRulesetDigest())) throw failure("REVISION_PRIOR_SOURCE_PROOF_CHANGED");
                    Map<String,Object> row=new LinkedHashMap<>();row.put("type","PRIOR_RUN");row.put("runId",previous);
                    row.put("sourceDigest",old.sourceDigest());row.put("optionsDigest",old.optionsDigest());row.put("rulesetDigest",old.rulesetDigest());
                    row.put("state",old.state());row.put("acceptance",accepted);row.put("snapshot",oldSnapshot);
                    row.put("sourceManifestBytes",Files.size(oldSnapshot.filesManifestFile()));row.put("sourceManifestSha256",hash(oldSnapshot.filesManifestFile(),cancelled));
                    line(output,row);count++;cursor=previous;
                }
            }
            if (count!=fresh.priorRunCount()) throw failure("REVISION_PROVENANCE_INCOMPLETE");
            line(output,Map.of("type","FOOTER","priorRuns",count));
        }
        CdsPaths.forceFile(prior);CdsPaths.forceDirectory(attempt);
        Map<String,Map<String,Object>> artifacts=new LinkedHashMap<>();
        for (String name:List.of("revision-context.json","lineage-approval.json","prior-run-provenance.jsonl")) {
            Path artifact=attempt.resolve(name);artifacts.put(name,Map.of("sha256",hash(artifact),"bytes",Files.size(artifact)));
        }
        return new Prepared(fresh,selected[0],recordPath,Map.copyOf(artifacts));
    }

    /** Participant only: exact current zero-write state and old digests are checked atomically by the ledger. */
    public void approve(String run,long epoch,long version,Prepared selected) {
        var proof=selected.revision();ledger.approveUnwrittenRevision(run,epoch,version,proof.sourcePatientId(),proof.copyKey(),
                proof.oldSourceDigest(),proof.oldOptionsDigest(),proof.oldRulesetDigest(),proof.newSourceDigest());
    }

    private static String text(JsonNode object,String key,int maximum) {
        var value=object.path(key);if (!value.isTextual() || value.asText().isBlank() || value.asText().length()>maximum) throw failure("REVISION_PROOF_INVALID");return value.asText();
    }
    private static void check(BooleanSupplier cancelled) {if (cancelled.getAsBoolean()) throw failure("REVIEW_CANCELLED");}
    private static void write(Path path,Path root,byte[] bytes) throws IOException {
        try (OutputStream output=CdsPaths.newPrivateFile(path,root)) {output.write(bytes);}CdsPaths.forceFile(path);CdsPaths.forceDirectory(root);
    }
    private static void line(OutputStream output,Object value) throws IOException {output.write(JSON.writeValueAsBytes(value));output.write('\n');}
    private static String hash(byte[] bytes) {return CdsPaths.hex(CdsPaths.sha256().digest(bytes));}
    private static String hash(Path path) throws IOException {
        return hash(path,() -> false);
    }
    private static String hash(Path path,BooleanSupplier cancelled) throws IOException {
        var digest=CdsPaths.sha256();try (var input=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)) {byte[] buffer=new byte[65536];int count;while ((count=input.read(buffer))!=-1) {check(cancelled);digest.update(buffer,0,count);}}return CdsPaths.hex(digest.digest());
    }
    private static CdsImportException failure(String code) {return new CdsImportException(code);}
    public record Prepared(CdsImportRevisionDto revision,CdsSnapshotFileDto source,String recordXPath,Map<String,Map<String,Object>> artifacts) {
        @Override public String toString() {return "UnwrittenRevisionProof[protected]";}
    }
}
