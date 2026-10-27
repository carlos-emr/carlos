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
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Bounded operational evidence without exception messages, SQL, values or filenames. */
@Service
public class CdsImportFailureEvidenceService {
    private final CdsImportJobService jobs;
    private final CdsImportFileService files;
    public CdsImportFailureEvidenceService(CdsImportJobService jobs, CdsImportFileService files) { this.jobs=jobs; this.files=files; }

    /** Evidence delivery is secondary: callers retain the original outcome if storage fails. */
    public String write(String runId, long epoch, Throwable failure) {
        CdsImportLedgerService.requireNoTransaction();
        try {
            Path root=jobs.root(runId), directory=root.resolve("diagnostics");
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                try { CdsPaths.privateDirectory(directory, root); }
                catch (java.nio.file.FileAlreadyExistsException concurrent) { /* Validate below. */ }
            }
            directory=CdsPaths.within(directory, root);
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)
                    || (((int) Files.getAttribute(directory, "unix:mode", LinkOption.NOFOLLOW_LINKS)) & 0077)!=0) return null;
            byte[] bytes=document(epoch, failure);
            Path file=directory.resolve("failure-"+epoch+"-"+UUID.randomUUID()+".json");
            try (var output=CdsPaths.newPrivateFile(file, directory)) { output.write(bytes); }
            CdsPaths.forceFile(file); CdsPaths.forceDirectory(directory);
            files.reconcilePublication(runId, epoch, file.toString(), CdsPaths.hex(CdsPaths.sha256().digest(bytes)), bytes.length);
            return file.toString();
        } catch (IOException | RuntimeException unavailable) { return null; }
    }

    /** Internal: the facade must authorize the operator and exact diagnostic/run association first. */
    public Map<String, Object> read(String runId, String location) throws IOException {
        CdsImportLedgerService.requireNoTransaction();
        Path path=CdsPaths.within(Path.of(location), jobs.root(runId).resolve("diagnostics"));
        var registered=files.findOwnedPublishedLocation(runId, path.toString(), 65536);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path)!=registered.byteLength()) throw invalid();
        byte[] bytes=CdsImportJobService.boundedFile(path, 65536);
        if (bytes.length!=registered.byteLength() || !CdsPaths.hex(CdsPaths.sha256().digest(bytes)).equals(registered.digest())) throw invalid();
        var factory=com.fasterxml.jackson.core.JsonFactory.builder()
                .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
        var document=com.fasterxml.jackson.databind.json.JsonMapper.builder(factory)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build().readTree(bytes);
        if (document==null || !document.isObject() || !only(document, Set.of("version", "ownerEpoch", "causes"))
                || !document.path("version").isInt() || document.path("version").intValue()!=1
                || !document.path("ownerEpoch").isIntegralNumber() || !document.path("ownerEpoch").canConvertToLong()
                || document.path("ownerEpoch").longValue()<0 || !document.path("causes").isArray() || document.path("causes").size()>16) throw invalid();
        List<Map<String, Object>> causes=new ArrayList<>(); int totalFrames=0;
        for (var source:document.path("causes")) {
            if (!source.isObject() || !only(source, Set.of("type", "sqlState", "vendorCode", "frames"))
                    || !source.path("type").isTextual() || !source.path("type").asText().equals(safe(source.path("type").asText(), 512))
                    || !source.path("frames").isArray()) throw invalid();
            Map<String, Object> cause=new LinkedHashMap<>(); cause.put("type", source.path("type").asText());
            if (source.has("sqlState")) {
                if (!source.path("sqlState").isTextual() || !source.path("sqlState").asText().matches("[A-Z0-9]{5}")) throw invalid();
                cause.put("sqlState", source.path("sqlState").asText());
            }
            if (source.has("vendorCode")) {
                if (!source.path("vendorCode").isIntegralNumber() || !source.path("vendorCode").canConvertToInt()) throw invalid();
                cause.put("vendorCode", source.path("vendorCode").intValue());
            }
            List<String> frames=new ArrayList<>();
            for (var frame:source.path("frames")) {
                if (++totalFrames>32 || !frame.isTextual() || frame.asText().length()>800
                        || !frame.asText().matches("[A-Za-z0-9_.$/<>-]+#[A-Za-z0-9_.$/<>-]+:-?[0-9]{1,10}")) throw invalid();
                frames.add(frame.asText());
            }
            cause.put("frames", List.copyOf(frames)); causes.add(cause);
        }
        return Map.of("version", 1, "ownerEpoch", document.path("ownerEpoch").longValue(), "causes", List.copyOf(causes));
    }
    private static boolean only(com.fasterxml.jackson.databind.JsonNode object, Set<String> allowed) {
        var names=object.fieldNames(); while (names.hasNext()) if (!allowed.contains(names.next())) return false; return true;
    }
    private static CdsImportException invalid() { return new CdsImportException("IMPORT_FAILURE_EVIDENCE_INVALID"); }

    static byte[] document(long epoch, Throwable failure) throws IOException {
        List<Map<String, Object>> causes=new ArrayList<>();
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        int framesRemaining=32;
        for (Throwable cursor=failure; cursor!=null && causes.size()<16 && seen.add(cursor); cursor=cursor.getCause()) {
            Map<String, Object> cause=new LinkedHashMap<>(); cause.put("type", safe(cursor.getClass().getName(), 512));
            if (cursor instanceof SQLException sql) {
                if (sql.getSQLState()!=null && sql.getSQLState().matches("[A-Z0-9]{5}")) cause.put("sqlState", sql.getSQLState());
                cause.put("vendorCode", sql.getErrorCode());
            }
            List<String> frames=new ArrayList<>();
            // Reserve frames for deeper causes instead of spending the budget
            // on a wrapping exception's entire framework stack.
            int localLimit=Math.min(4, framesRemaining);
            for (var frame:cursor.getStackTrace()) {
                if (frames.size()==localLimit) break;
                frames.add(safe(frame.getClassName(), 512)+"#"+safe(frame.getMethodName(), 256)+":"+frame.getLineNumber());
            }
            framesRemaining-=frames.size(); cause.put("frames", frames); causes.add(cause);
        }
        byte[] bytes=new ObjectMapper().writeValueAsBytes(Map.of("version", 1, "ownerEpoch", epoch, "causes", causes));
        if (bytes.length>65536) throw new IOException("IMPORT_FAILURE_EVIDENCE_CAPACITY");
        return bytes;
    }
    private static String safe(String value, int maximum) {
        return value.length()<=maximum && value.matches("[A-Za-z0-9_.$/<>-]+")?value:"_unavailable";
    }
}
