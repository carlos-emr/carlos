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
    public CdsImportFailureEvidenceService(CdsImportJobService jobs) { this.jobs=jobs; }

    /** Evidence delivery is secondary: callers retain the original outcome if storage fails. */
    public String write(String runId,long epoch,Throwable failure) {
        CdsImportLedgerService.requireNoTransaction();
        try {
            Path root=jobs.root(runId),directory=root.resolve("diagnostics");
            if (!Files.exists(directory,LinkOption.NOFOLLOW_LINKS)) {
                try { CdsPaths.privateDirectory(directory,root); }
                catch (java.nio.file.FileAlreadyExistsException concurrent) { /* Validate below. */ }
            }
            directory=CdsPaths.within(directory,root);
            if (!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)
                    || (((int)Files.getAttribute(directory,"unix:mode",LinkOption.NOFOLLOW_LINKS)) & 0077)!=0) return null;
            byte[] bytes=document(epoch,failure);
            Path file=directory.resolve("failure-"+epoch+"-"+UUID.randomUUID()+".json");
            try (var output=CdsPaths.newPrivateFile(file,directory)) { output.write(bytes); }
            CdsPaths.forceFile(file);CdsPaths.forceDirectory(directory);return file.toString();
        } catch (IOException | RuntimeException unavailable) { return null; }
    }

    static byte[] document(long epoch,Throwable failure) throws IOException {
        List<Map<String,Object>> causes=new ArrayList<>();
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        int framesRemaining=32;
        for (Throwable cursor=failure;cursor!=null && causes.size()<16 && seen.add(cursor);cursor=cursor.getCause()) {
            Map<String,Object> cause=new LinkedHashMap<>();cause.put("type",safe(cursor.getClass().getName(),512));
            if (cursor instanceof SQLException sql) {
                if (sql.getSQLState()!=null && sql.getSQLState().matches("[A-Z0-9]{5}")) cause.put("sqlState",sql.getSQLState());
                cause.put("vendorCode",sql.getErrorCode());
            }
            List<String> frames=new ArrayList<>();
            // Reserve frames for deeper causes instead of spending the budget
            // on a wrapping exception's entire framework stack.
            int localLimit=Math.min(4,framesRemaining);
            for (var frame:cursor.getStackTrace()) {
                if (frames.size()==localLimit) break;
                frames.add(safe(frame.getClassName(),512)+"#"+safe(frame.getMethodName(),256)+":"+frame.getLineNumber());
            }
            framesRemaining-=frames.size();cause.put("frames",frames);causes.add(cause);
        }
        byte[] bytes=new ObjectMapper().writeValueAsBytes(Map.of("version",1,"ownerEpoch",epoch,"causes",causes));
        if (bytes.length>65536) throw new IOException("IMPORT_FAILURE_EVIDENCE_CAPACITY");
        return bytes;
    }
    private static String safe(String value,int maximum) {
        return value.length()<=maximum && value.matches("[A-Za-z0-9_.$/<>-]+")?value:"_unavailable";
    }
}
