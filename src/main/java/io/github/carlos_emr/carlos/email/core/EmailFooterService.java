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
package io.github.carlos_emr.carlos.email.core;

import java.util.List;
import java.util.Optional;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The clinic's default email footer and each user's own footer (follow-up to issue #3981).
 *
 * <p>Every user, doctor or front desk, has a footer: their own, if they saved one, otherwise the
 * clinic default. When an administrator changes the clinic default, users' own footers are
 * replaced by it ({@link #REPLACE_OWN_FOOTERS_ON_CLINIC_CHANGE}); each user whose own text was
 * different is told on their next email, not the administrator, and can put their old text
 * back.</p>
 *
 * <p>All values live in the {@code property} table ({@link UserProperty}): the clinic default as a
 * row without a provider, users' footers and notices per provider. Its {@code value} column holds
 * 2000 characters, the footer limit, so line breaks are stored as one character each, as the send
 * action counts them. A longer footer is refused, never cut.</p>
 *
 * @since 2026-10-07
 */
@Service
public class EmailFooterService {

    /** A user's own footer; no row means the user follows the clinic default. */
    static final String USER_FOOTER = "email_footer";
    /** The clinic default, a row with no provider. */
    static final String CLINIC_DEFAULT = "email_footer_clinic_default";
    /**
     * Set on a user whose own footer differed from a new clinic default: their previous text, shown
     * on their next email until they restore it, keep the default, or save their footer.
     */
    static final String CLINIC_CHANGE_NOTICE = "email_footer_clinic_change";

    /**
     * Maintainer decision (6 Oct 2026): "Clinic sets a default, then docs can edit it, but if admin
     * changes it it will update all doctors", and the doctors are warned on their next send, not the
     * admin. Set to false to keep users' own footers on a clinic change and only tell them.
     */
    static final boolean REPLACE_OWN_FOOTERS_ON_CLINIC_CHANGE = true;

    private final UserPropertyDAO userPropertyDao;
    private final boolean replaceOwnFooters;

    @Autowired
    public EmailFooterService(UserPropertyDAO userPropertyDao) {
        this(userPropertyDao, REPLACE_OWN_FOOTERS_ON_CLINIC_CHANGE);
    }

    // Package-private so tests can cover both settings of the clinic-change rule.
    EmailFooterService(UserPropertyDAO userPropertyDao, boolean replaceOwnFooters) {
        this.userPropertyDao = userPropertyDao;
        this.replaceOwnFooters = replaceOwnFooters;
    }

    /** Thrown when a footer is longer than {@link EmailData#FOOTER_MAX_LENGTH}; nothing is saved. */
    public static final class FooterTooLongException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        FooterTooLongException() {
            super("Footer must not exceed " + EmailData.FOOTER_MAX_LENGTH + " characters");
        }
    }

    /**
     * What a user's footer page shows.
     *
     * @param ownFooter the user's own footer, or null when they follow the clinic default
     * @param clinicDefault the clinic default, empty when none is set
     * @param clinicChangeNotice the user's previous footer when a clinic change affected it, or null
     * @param ownFootersReplaced whether a clinic change replaces own footers (the notice wording)
     */
    public record UserFooterSettings(String ownFooter, String clinicDefault, String clinicChangeNotice,
            boolean ownFootersReplaced) {
    }

    /** @return the clinic default, empty when none is set */
    public String clinicDefault() {
        UserProperty property = userPropertyDao.getProp(CLINIC_DEFAULT);
        return property == null || property.getValue() == null ? "" : property.getValue();
    }

    /**
     * The footer a user's compose screen opens with when the eForm supplies none: the user's own
     * (even when they saved it empty, meaning no footer), otherwise the clinic default.
     *
     * @param providerNo the logged-in user
     * @return the footer, or empty when the user has none and no clinic default is set
     */
    public Optional<String> composeFooter(String providerNo) {
        UserProperty own = userPropertyDao.getProp(providerNo, USER_FOOTER);
        if (own != null) {
            return Optional.of(own.getValue() == null ? "" : own.getValue());
        }
        String clinic = clinicDefault();
        return clinic.isEmpty() ? Optional.empty() : Optional.of(clinic);
    }

    /**
     * @param providerNo the logged-in user
     * @return the user's previous footer when a clinic change affected it, or null
     */
    public String clinicChangeNotice(String providerNo) {
        UserProperty notice = userPropertyDao.getProp(providerNo, CLINIC_CHANGE_NOTICE);
        return notice == null ? null : nullToEmpty(notice.getValue());
    }

    /** @return whether a clinic change replaces users' own footers */
    public boolean ownFootersReplacedOnClinicChange() {
        return replaceOwnFooters;
    }

    /**
     * @param providerNo the logged-in user
     * @return what the user's footer page shows
     */
    public UserFooterSettings settingsFor(String providerNo) {
        UserProperty own = userPropertyDao.getProp(providerNo, USER_FOOTER);
        return new UserFooterSettings(own == null ? null : nullToEmpty(own.getValue()), clinicDefault(),
                clinicChangeNotice(providerNo), replaceOwnFooters);
    }

    /**
     * Saves the user's own footer (empty means no footer) and clears any clinic-change notice.
     *
     * @param providerNo the logged-in user
     * @param footer the footer as typed
     * @throws FooterTooLongException when the footer is over the limit; nothing is saved
     */
    @Transactional
    public void saveOwnFooter(String providerNo, String footer) {
        String normalised = withinLimit(footer);
        userPropertyDao.saveProp(providerNo, USER_FOOTER, normalised);
        clearNotice(providerNo);
    }

    /**
     * Removes the user's own footer, so they follow the clinic default, and clears any notice.
     *
     * @param providerNo the logged-in user
     */
    @Transactional
    public void useClinicDefault(String providerNo) {
        userPropertyDao.delete(userPropertyDao.getProp(providerNo, USER_FOOTER));
        clearNotice(providerNo);
    }

    /**
     * Makes the footer a clinic change replaced the user's own footer again.
     *
     * @param providerNo the logged-in user
     * @return false when there was no notice to restore from
     */
    @Transactional
    public boolean restorePreviousFooter(String providerNo) {
        UserProperty notice = userPropertyDao.getProp(providerNo, CLINIC_CHANGE_NOTICE);
        if (notice == null) {
            return false;
        }
        userPropertyDao.saveProp(providerNo, USER_FOOTER, nullToEmpty(notice.getValue()));
        userPropertyDao.delete(notice);
        return true;
    }

    /**
     * Dismisses the clinic-change notice and keeps the footer the user now has.
     *
     * @param providerNo the logged-in user
     */
    @Transactional
    public void dismissClinicChangeNotice(String providerNo) {
        clearNotice(providerNo);
    }

    /**
     * Saves the clinic default and applies it to users' own footers.
     *
     * <p>A user's own footer that equals the old or the new default is removed, so the user follows
     * the default from now on. Any other own footer is replaced (or, with the rule switched off,
     * kept), and the user gets a notice holding their previous text.</p>
     *
     * @param footer the clinic default as typed; empty means no clinic default
     * @return how many users get a notice
     * @throws FooterTooLongException when the footer is over the limit; nothing is saved
     */
    @Transactional
    public int saveClinicDefault(String footer) {
        String normalised = withinLimit(footer);
        String previous = normalise(clinicDefault());
        UserProperty clinic = userPropertyDao.getProp(CLINIC_DEFAULT);
        if (clinic == null) {
            clinic = new UserProperty();
            clinic.setName(CLINIC_DEFAULT);
        }
        clinic.setValue(normalised);
        userPropertyDao.saveProp(clinic);

        int noticed = 0;
        List<UserProperty> ownFooters = userPropertyDao.findProviderProperties(USER_FOOTER);
        for (UserProperty own : ownFooters) {
            String text = normalise(own.getValue());
            if (text.equals(previous) || text.equals(normalised)) {
                userPropertyDao.delete(own);
                continue;
            }
            // A notice from an earlier change keeps the text the user lost first.
            if (userPropertyDao.getProp(own.getProviderNo(), CLINIC_CHANGE_NOTICE) == null) {
                userPropertyDao.saveProp(own.getProviderNo(), CLINIC_CHANGE_NOTICE, text);
            }
            if (replaceOwnFooters) {
                userPropertyDao.delete(own);
            }
            noticed++;
        }
        return noticed;
    }

    /**
     * @param footer a footer as typed
     * @return the footer with line breaks stored as one character each, as the send action counts
     * @throws FooterTooLongException when it is over the limit
     */
    static String withinLimit(String footer) {
        String normalised = normalise(footer);
        if (normalised.length() > EmailData.FOOTER_MAX_LENGTH) {
            throw new FooterTooLongException();
        }
        return normalised;
    }

    private static String normalise(String footer) {
        return footer == null ? "" : footer.replace("\r\n", "\n").replace('\r', '\n');
    }

    private void clearNotice(String providerNo) {
        userPropertyDao.delete(userPropertyDao.getProp(providerNo, CLINIC_CHANGE_NOTICE));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
