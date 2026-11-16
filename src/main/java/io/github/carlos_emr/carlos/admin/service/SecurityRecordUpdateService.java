/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.admin.service;

import java.util.Date;
import java.util.Objects;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.MyDateFormat;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.SecurityManager;
import io.github.carlos_emr.carlos.www.admin.SecurityEditVersion;
import io.github.carlos_emr.carlos.www.admin.SecurityUpdatePasswordValidator;
import io.github.carlos_emr.carlos.www.admin.SecurityUpdatePinHandler;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves the Security Records edit form (Administration, Search/Edit/Delete Security Records,
 * Update Record) onto one login, unless another save changed the login after the form was opened.
 *
 * <p>The form posts every control it shows, and the save used to live in a JSP scriptlet that
 * copied all of them onto the row. Administrator A, on a page opened before administrator B saved,
 * therefore put B's change back without a warning to either: a Force password reset, an expiry
 * flag or date, a lock flag or the MFA switch was silently undone (ledger finding 355). The form
 * now carries the version it was rendered from ({@link SecurityEditVersion}). This method locks
 * the row ({@link SecurityDao#findForUpdate}), compares, and writes in one transaction, so no other
 * save can land between the comparison and the write.</p>
 *
 * <ul>
 *   <li>Current version: the form is written exactly as the scriptlet wrote it (the password is
 *       hashed only when it is not the unchanged marker, the PIN goes through
 *       {@link SecurityUpdatePinHandler}, {@code lastUpdateDate} is stamped). No archive row is
 *       written, as before.</li>
 *   <li>Stale version: nothing is written, the new password and PIN included. When the login
 *       already holds every value the form posts, and the form changes neither credential, there
 *       is nothing to undo or lose (most often the same administrator's second click after the
 *       first one saved), and the outcome is {@link Outcome#ALREADY_STORED}. Otherwise it is
 *       {@link Outcome#STALE}. A posted new password or PIN always makes a stale form
 *       {@code STALE}: deciding whether the stored hash already is that password would mean
 *       testing a candidate password against it, which this save must never offer.</li>
 *   <li>No version (an older page, a hand-built request): written as before, without the check.</li>
 * </ul>
 *
 * <p>Authorization, the POST check and the user name and password validation stay with
 * {@code SecurityUpdate2Action}, which also writes the audit row after a save.</p>
 *
 * @since 2026-11-13
 */
@Service
@Transactional
public class SecurityRecordUpdateService {

    /** What became of one submission. */
    public enum Outcome {
        /** The form was written to the login. */
        SAVED,
        /** No login has the posted {@code security_no}; nothing was written. */
        NOT_FOUND,
        /** The login already holds everything the stale form posts; nothing was written. */
        ALREADY_STORED,
        /** The form was rendered before another save changed the login; nothing was written. */
        STALE
    }

    private final SecurityDao securityDao;
    private final SecurityManager securityManager;

    public SecurityRecordUpdateService(SecurityDao securityDao, SecurityManager securityManager) {
        this.securityDao = securityDao;
        this.securityManager = securityManager;
    }

    /**
     * Writes the submitted form to the login under its row lock, or refuses a stale form.
     *
     * @param edit the validated submission
     * @return the outcome; only {@link Outcome#SAVED} wrote anything
     * @throws NumberFormatException when a submitted lock flag is not a number (nothing is written)
     */
    public Outcome update(SecurityRecordUpdateCommand edit) {
        // Locked even without a version, so an unversioned save cannot land between another
        // request's comparison and its write.
        Security security = securityDao.findForUpdate(edit.securityNo());
        if (security == null) {
            return Outcome.NOT_FOUND;
        }
        if (SecurityEditVersion.isPresent(edit.renderedVersion())
                && !SecurityEditVersion.matches(edit.renderedVersion(), security)) {
            // Nothing has been changed on the managed row, so the transaction writes nothing.
            return alreadyStored(security, edit) ? Outcome.ALREADY_STORED : Outcome.STALE;
        }
        applyFormFields(security, edit);
        if (!keepsStoredPassword(edit)) {
            security.setPassword(securityManager.encodePassword(edit.password()));
            security.setPasswordUpdateDate(new Date());
        }
        SecurityUpdatePinHandler.apply(security, edit.pin(), CarlosProperties.getInstance().isPINEncripted());
        security.setLastUpdateDate(new Date());
        securityDao.saveEntity(security);
        return Outcome.SAVED;
    }

    /** Copies the non-credential controls onto {@code security}, as the save always has. */
    private static void applyFormFields(Security security, SecurityRecordUpdateCommand edit) {
        security.setUserName(edit.userName());
        security.setProviderNo(edit.providerNo());
        security.setBExpireset(edit.expireSet());
        security.setDateExpiredate(MyDateFormat.getSysDate(edit.expireDate()));
        SecurityUpdatePinHandler.applyLockFlags(security, edit.lockFlagsEditable(),
                edit.localLockSet(), edit.remoteLockSet());
        security.setForcePasswordReset(edit.forcePasswordReset());
        security.setUsingMfa(edit.usingMfa());
    }

    private static boolean keepsStoredPassword(SecurityRecordUpdateCommand edit) {
        return SecurityUpdatePasswordValidator.UNCHANGED_PASSWORD.equals(edit.password());
    }

    /**
     * Whether writing the form would change nothing on the stored login: it keeps both credentials
     * and every other control equals the stored value as {@link #applyFormFields} would write it.
     */
    private static boolean alreadyStored(Security stored, SecurityRecordUpdateCommand edit) {
        if (!keepsStoredPassword(edit) || !SecurityUpdatePinHandler.keepsStoredPin(edit.pin())) {
            return false;
        }
        Security probe = new Security();
        probe.setBLocallockset(stored.getBLocallockset());
        probe.setBRemotelockset(stored.getBRemotelockset());
        applyFormFields(probe, edit);
        return Objects.equals(probe.getUserName(), stored.getUserName())
                && Objects.equals(probe.getProviderNo(), stored.getProviderNo())
                && Objects.equals(probe.getBExpireset(), stored.getBExpireset())
                && Objects.equals(day(probe.getDateExpiredate()), day(stored.getDateExpiredate()))
                && Objects.equals(probe.getBLocallockset(), stored.getBLocallockset())
                && Objects.equals(probe.getBRemotelockset(), stored.getBRemotelockset())
                && Objects.equals(probe.isForcePasswordReset(), stored.isForcePasswordReset())
                && probe.isUsingMfa() == stored.isUsingMfa();
    }

    /** The calendar day of a {@code date_ExpireDate} value, which the column stores without a time. */
    private static String day(Date date) {
        return date == null ? null : new java.sql.Date(date.getTime()).toString();
    }
}
