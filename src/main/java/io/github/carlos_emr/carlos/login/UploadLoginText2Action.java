/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.login;

import org.apache.struts2.ActionSupport;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.action.UploadedFilesAware;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.commn.service.AcceptableUseAgreementManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

public class UploadLoginText2Action extends ActionSupport implements UploadedFilesAware {

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger _logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        String validDurationNumber = request.getParameter("validDurationNumber");
        String validDurationPeriod = request.getParameter("validDurationPeriod");
        String validForever = request.getParameter("validForever");
        String foreverFrom = request.getParameter("foreverFrom");
        boolean mutation = importFile != null || validDurationNumber != null
                || validDurationPeriod != null || validForever != null || foreverFrom != null;
        if (!"POST".equals(request.getMethod())) {
            if ("GET".equals(request.getMethod()) && !mutation) {
                return SUCCESS;
            }
            response.setHeader("Allow", "POST");
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        Property prop = new Property();
        try {
            if ("forever".equals(validForever)) {
                if (foreverFrom == null) throw new IllegalArgumentException("Missing agreement date");
                LocalDateTime.parse(foreverFrom, DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
                        .withResolverStyle(ResolverStyle.STRICT));
                prop.setName("aua_valid_from");
                prop.setValue(foreverFrom);
            } else {
                int duration = Integer.parseInt(validDurationNumber);
                if (validForever != null || duration < 1 || duration > 18
                        || validDurationPeriod == null
                        || !List.of("year", "month", "weeks", "days").contains(validDurationPeriod)) {
                    throw new IllegalArgumentException("Invalid agreement duration");
                }
                prop.setName("aua_valid_duration");
                prop.setValue(duration + " " + validDurationPeriod);
            }
        } catch (IllegalArgumentException | java.time.DateTimeException invalid) {
            addActionError("Choose a valid agreement duration or a date in yyyy-MM-dd HH:mm:ss format.");
            request.setAttribute("error", true);
            return SUCCESS;
        }

        boolean error = false;
        PropertyDao propertyDao = SpringUtils.getBean(PropertyDao.class);

        // The agreement readers synchronize on this class while loading text.
        // Keep them from seeing a replacement before its validity property commits.
        synchronized (AcceptableUseAgreementManager.class) {
            PublishedLoginText published = null;
            boolean restored = false;
            try {
                if (importFile == null) {
                    _logger.warn("No file uploaded; skipping login text write");
                } else if (!importFile.getName().isEmpty()) {
                    published = writeLoginTextFile();
                }
                Property latestProperty = AcceptableUseAgreementManager.findLatestProperty();
                if (latestProperty == null || !prop.getName().equals(latestProperty.getName())
                        || !prop.getValue().equals(latestProperty.getValue())) {
                    propertyDao.persist(prop);
                    AcceptableUseAgreementManager.invalidateCache();
                }
            } catch (Exception e) {
                if (published != null) {
                    try {
                        restoreLoginTextFile(published);
                        restored = true;
                    } catch (IOException restoreError) {
                        e.addSuppressed(restoreError);
                    }
                }
                MiscUtils.getLogger().error("Error", e);
                error = true;
            } finally {
                if (published != null && (!error || restored) && published.backup() != null) {
                    try {
                        Files.deleteIfExists(published.backup());
                    } catch (IOException cleanupError) {
                        _logger.warn("Could not remove agreement backup after update", cleanupError);
                    }
                }
            }
        }

        request.setAttribute("error", error);
        return SUCCESS;
    }

    private record PublishedLoginText(Path destination, Path backup) { }

    private PublishedLoginText writeLoginTextFile() throws IOException {
        File saveFile = AcceptableUseAgreementManager.getAgreementFile();
        Path directory = saveFile.getParentFile().toPath();
        Files.createDirectories(directory);
        Path tempFile = Files.createTempFile(directory, "agreement-upload-", ".tmp");
        Path backup = null;
        boolean published = false;
        try {
            try (InputStream fis = Files.newInputStream(importFile.toPath());
                 OutputStream fos = Files.newOutputStream(tempFile)) {
                byte[] buf = new byte[128 * 1024];
                int i;
                while ((i = fis.read(buf)) != -1) {
                    fos.write(buf, 0, i);
                }
            }
            validateUtf8(tempFile);
            if (Files.exists(saveFile.toPath())) {
                backup = Files.createTempFile(directory, "agreement-backup-", ".tmp");
                Files.copy(saveFile.toPath(), backup,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
            moveLoginTextFile(tempFile, saveFile.toPath());
            published = true;
            AcceptableUseAgreementManager.invalidateCache();
            return new PublishedLoginText(saveFile.toPath(), backup);
        } finally {
            if (!published) {
                Files.deleteIfExists(tempFile);
                if (backup != null) Files.deleteIfExists(backup);
            }
        }
    }

    private void validateUtf8(Path file) throws IOException {
        try (Reader reader = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8
                .newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT))) {
            char[] chars = new char[8192];
            while (reader.read(chars) != -1) {
                // Validate the entire staged file before publishing any of it.
            }
        }
    }

    private void restoreLoginTextFile(PublishedLoginText published) throws IOException {
        if (published.backup() == null) {
            Files.deleteIfExists(published.destination());
        } else {
            moveLoginTextFile(published.backup(), published.destination());
        }
        AcceptableUseAgreementManager.invalidateCache();
    }

    private void moveLoginTextFile(Path tempFile, Path saveFile) throws IOException {
        try {
            Files.move(tempFile, saveFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempFile, saveFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private File importFile;

    @Override
    public void withUploadedFiles(List<UploadedFile> uploadedFiles) {
        if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
            UploadedFile uploaded = uploadedFiles.get(0);
            this.importFile = PathValidationUtils.validateUploadContent(uploaded.getContent());
        }
    }

    public File getImportFile() {
        return importFile;
    }

    public void setImportFile(File importFile) {
        if (importFile != null) {
            this.importFile = PathValidationUtils.validateUpload(importFile);
        }
        else {
            this.importFile = null;
        }
    }
}
