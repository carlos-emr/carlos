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
 *
 */
package io.github.carlos_emr.carlos.admin.web;

import java.sql.SQLException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.model.SystemPreferences;
import io.github.carlos_emr.carlos.lab.service.LabPdfPreviewSettings;
import io.github.carlos_emr.carlos.lab.service.LabPdfPreviewSettingsService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.springframework.dao.PessimisticLockingFailureException;

/**
 * Lab Display Settings admin page ({@code admin/LabDisplaySettings}): whether PDFs embedded in
 * HL7 lab results are previewed inline, and the largest PDF previewed (#3977).
 *
 * <p>GET/HEAD render the page with {@code _admin} read; a save ({@code dboperation=Save}) must be
 * a POST and needs {@code _admin} write. A save intent on GET/HEAD, or any other method, is
 * refused with 405 before any privilege check or preference access, matching
 * {@link EchartDisplaySettings2Action}. The size is entered in whole MiB between 1 and 100; any
 * other value re-renders the page with an error, keeping the submitted toggle and size, and
 * saves nothing.</p>
 *
 * @since 2026-09-30
 */
public class LabDisplaySettings2Action extends ActionSupport {

    /**
     * Attempts at the transactional save before reporting a conflict. A first save on a table
     * with no row to lock can lose an InnoDB deadlock to a concurrent first save (see
     * {@code SystemPreferencesDao#upsertPreference}); a fresh transaction then finds the
     * winner's row and updates it.
     */
    static final int MAX_SAVE_ATTEMPTS = 3;

    private static final Logger logger = MiscUtils.getLogger();

    /** Form field for the size limit, in MiB. */
    static final String MAX_SIZE_MB_PARAMETER = "lab_pdf_max_size_mb";

    // transient: ActionSupport implements Serializable; Spring-managed beans are not serializable.
    private final transient SecurityInfoManager securityInfoManager;
    private final transient LabPdfPreviewSettingsService previewSettingsService;

    public LabDisplaySettings2Action(SecurityInfoManager securityInfoManager,
            LabPdfPreviewSettingsService previewSettingsService) {
        this.securityInfoManager = securityInfoManager;
        this.previewSettingsService = previewSettingsService;
    }

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        String method = request.getMethod();
        boolean saveIntent = "Save".equals(request.getParameter("dboperation"));
        if ((!"GET".equals(method) && !"HEAD".equals(method) && !"POST".equals(method))
                || (saveIntent && !"POST".equals(method))) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            response.setHeader("Allow", saveIntent ? "POST" : "GET, HEAD, POST");
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String requiredPrivilege = "POST".equals(method) ? "w" : "r";
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", requiredPrivilege, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        boolean saved = false;
        boolean invalidSize = false;
        boolean saveFailed = false;
        LabPdfPreviewSettings settings;
        Object displayedMaxSizeMb = null;
        if ("POST".equals(method) && saveIntent) {
            boolean enabled = "true".equals(request.getParameter(
                    SystemPreferences.LAB_DISPLAY_PREFERENCE_KEYS.lab_pdf_inline_preview.name()));
            String submittedSize = request.getParameter(MAX_SIZE_MB_PARAMETER);
            Long maxMegabytes = megabytes(submittedSize);
            if (maxMegabytes == null) {
                invalidSize = true;
                // Nothing is saved, but re-render what the administrator submitted (the toggle and
                // the rejected size) next to the error rather than reverting it to the stored
                // values. The JSP encodes the echoed size for the attribute context.
                settings = new LabPdfPreviewSettings(enabled, previewSettingsService.load().maxBytes());
                displayedMaxSizeMb = submittedSize == null ? "" : submittedSize.trim();
            } else {
                settings = new LabPdfPreviewSettings(enabled, maxMegabytes * 1024 * 1024);
                saved = saveWithRetry(settings);
                // On a persistent conflict the submitted values stay on the page with an error.
                saveFailed = !saved;
            }
        } else {
            settings = previewSettingsService.load();
        }
        request.setAttribute("labPdfInlinePreview", settings.inlinePreviewEnabled());
        request.setAttribute("labPdfMaxSizeMb",
                displayedMaxSizeMb != null ? displayedMaxSizeMb : settings.maxMegabytes());
        request.setAttribute("labPdfMaxSizeMbLimit", LabPdfPreviewSettings.MAX_ALLOWED_BYTES / (1024 * 1024));
        request.setAttribute("saved", saved);
        request.setAttribute("invalidSize", invalidSize);
        request.setAttribute("saveFailed", saveFailed);
        return SUCCESS;
    }

    /**
     * Saves in a new transaction per attempt, retrying only a deadlock or serialization failure
     * (which InnoDB reports immediately), at most {@link #MAX_SAVE_ATTEMPTS} times. A lock-wait
     * timeout is a lock conflict too, but it is terminal: it is reported without a retry. This runs outside the service's
     * transaction on purpose: the deadlock victim's transaction is already rolled back, so only
     * a fresh call can succeed. Any other failure propagates unchanged.
     *
     * @return {@code true} when saved; {@code false} when every attempt hit a lock conflict
     */
    private boolean saveWithRetry(LabPdfPreviewSettings settings) {
        for (int attempt = 1; ; attempt++) {
            try {
                previewSettingsService.save(settings);
                return true;
            } catch (RuntimeException e) {
                if (!isLockConflict(e)) {
                    throw e;
                }
                if (isLockWaitTimeout(e)) {
                    // A lock-wait timeout (1205) arrives only after a full innodb_lock_wait_timeout;
                    // retrying would hold this request for several more. Report it at once.
                    logger.warn("Lab display settings not saved: lock wait timeout on attempt {}", attempt, e);
                    return false;
                }
                if (attempt >= MAX_SAVE_ATTEMPTS) {
                    logger.warn("Lab display settings not saved: lock conflict on all {} attempts", attempt, e);
                    return false;
                }
                logger.info("Lab display settings save hit a lock conflict; retrying (attempt {} of {})",
                        attempt, MAX_SAVE_ATTEMPTS);
            }
        }
    }

    /**
     * Whether a save failed because of a lock conflict. The DAO is not behind Spring's exception
     * translation, so this recognises the translated, JPA, Hibernate and JDBC forms anywhere in
     * the cause chain: SQLState {@code 40001} (serialization failure / deadlock) or MySQL/MariaDB
     * error 1213 (deadlock) or 1205 (lock wait timeout).
     */
    static boolean isLockConflict(Throwable failure) {
        int depth = 0;
        for (Throwable t = failure; t != null && depth < 16; t = t.getCause(), depth++) {
            if (t instanceof PessimisticLockingFailureException
                    || t instanceof jakarta.persistence.PessimisticLockException
                    || t instanceof jakarta.persistence.LockTimeoutException
                    || t instanceof org.hibernate.exception.LockAcquisitionException
                    || t instanceof org.hibernate.PessimisticLockException) {
                return true;
            }
            if (t instanceof SQLException sql
                    && ("40001".equals(sql.getSQLState()) || sql.getErrorCode() == 1213 || sql.getErrorCode() == 1205)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a lock conflict is a lock-wait timeout rather than a deadlock: MySQL/MariaDB error
     * 1205, or the JPA/Hibernate timeout types, anywhere in the cause chain. Checked before the
     * deadlock signals because Hibernate's {@code LockTimeoutException} extends
     * {@code LockAcquisitionException}, and the JDBC error code is the most specific signal.
     */
    static boolean isLockWaitTimeout(Throwable failure) {
        int depth = 0;
        for (Throwable t = failure; t != null && depth < 16; t = t.getCause(), depth++) {
            if (t instanceof jakarta.persistence.LockTimeoutException
                    || t instanceof org.hibernate.exception.LockTimeoutException) {
                return true;
            }
            if (t instanceof SQLException sql && sql.getErrorCode() == 1205) {
                return true;
            }
        }
        return false;
    }

    /** Whole MiB from 1 to the allowed maximum, or {@code null}. */
    private static Long megabytes(String value) {
        // At most four digits, so Long.parseLong cannot overflow; longer input is invalid.
        if (value == null || !value.trim().matches("\\d{1,4}")) {
            return null;
        }
        long megabytes = Long.parseLong(value.trim());
        long limit = LabPdfPreviewSettings.MAX_ALLOWED_BYTES / (1024 * 1024);
        return megabytes >= 1 && megabytes <= limit ? megabytes : null;
    }
}
