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

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportFileDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Physical reconciliation is streaming and outside clinical transactions, including resumed/no-op references. */
@Service
public class CdsImportFileVerificationService {
    private final CdsImportFileService files;
    private final CdsImportLedgerService ledger;
    private final CdsImportConfigurationService configuration;

    public CdsImportFileVerificationService(CdsImportFileService files, CdsImportLedgerService ledger,
            CdsImportConfigurationService configuration) {
        this.files=files; this.ledger=ledger; this.configuration=configuration;
    }

    public void verify(String run, long epoch) throws IOException {
        CdsImportLedgerService.requireNoTransaction();
        String after="";
        while (true) {
            requireOwner(run, epoch);
            List<CdsImportFileDto> page=files.referencedFiles(run, after, 100);
            if (page.isEmpty()) return;
            for (CdsImportFileDto file:page) {
                requireOwner(run, epoch); verify(run, epoch, file); after=file.id();
            }
        }
    }

    private void verify(String run, long epoch, CdsImportFileDto file) throws IOException {
        if (!"PUBLISHED".equals(file.state())) throw new CdsImportException("REFERENCED_FILE_NOT_PUBLISHED");
        Path path=Path.of(file.location()).toAbsolutePath().normalize();
        Path staging=configuration.stagingRoot();
        Path document=PathValidationUtils.getRequiredDocumentDirectory().toPath().toAbsolutePath().normalize();
        Path allowed=path.startsWith(staging)?staging:path.startsWith(document)?document:null;
        if (allowed==null) throw new CdsImportException("REFERENCED_FILE_LOCATION_INVALID");
        path=CdsPaths.within(path, allowed);
        BasicFileAttributes before=Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile() || before.size()!=file.byteLength()) throw new CdsImportException("REFERENCED_FILE_LENGTH_MISMATCH");
        MessageDigest hash=CdsPaths.sha256();
        long bytes=0;
        long nextCheck=16L<<20;
        try (InputStream input=Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer=new byte[65536];
            int count;
            while ((count=input.read(buffer))!=-1) {
                bytes=Math.addExact(bytes, count); hash.update(buffer, 0, count);
                if (bytes>file.byteLength()) throw new CdsImportException("REFERENCED_FILE_LENGTH_MISMATCH");
                if (bytes>=nextCheck) { requireOwner(run, epoch); nextCheck=Math.addExact(bytes, 16L<<20); }
            }
        }
        BasicFileAttributes after=Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (bytes!=file.byteLength() || !CdsPaths.hex(hash.digest()).equals(file.digest())
                || !Objects.equals(before.fileKey(), after.fileKey()) || before.size()!=after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())) {
            throw new CdsImportException("REFERENCED_FILE_DIGEST_MISMATCH");
        }
        requireOwner(run, epoch);
        files.verifiedIfOwned(run, epoch, file.id(), file.digest(), file.byteLength());
    }

    private void requireOwner(String run, long epoch) {
        if (Thread.currentThread().isInterrupted()) throw new CdsImportException("IMPORT_CANCELLED");
        var job=ledger.status(run);
        if (job.ownerEpoch()!=epoch) throw new CdsImportException("OWNER_FENCED");
        if (job.cancelRequested()) throw new CdsImportException("IMPORT_CANCELLED");
    }
}
