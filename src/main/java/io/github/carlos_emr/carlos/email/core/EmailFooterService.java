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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import jakarta.persistence.PersistenceException;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
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
 * <p>Saves run at READ COMMITTED, as {@code PatientConsentManagerImpl}'s do: under MariaDB's
 * default REPEATABLE READ with {@code innodb_snapshot_isolation} on (the default from 11.6), a save
 * that read rows another save then changed fails with error 1020 instead of reading the committed
 * rows. A clinic save that changes the footer locks the clinic row and the users' footers it
 * reads, so a second save waits and then sees what the first one committed (the very first clinic
 * save has no clinic row to lock yet, so two first saves can both go through). Saves that still
 * collide (a deadlock, or a row the other save removed, including a user's footer removed between
 * the clinic save's read and its lock) fail with a Spring {@code ConcurrencyFailureException},
 * whether the failure comes at commit or from a flush partway through, and the pages turn it into
 * "please try again".</p>
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

    /** Turns JPA and Hibernate lock and stale-row errors into Spring's, as a commit would. */
    private static final HibernateJpaDialect JPA_EXCEPTIONS = new HibernateJpaDialect();

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

    /** What saving the clinic default did. */
    public enum ClinicDefaultOutcome {
        /** The default changed and was applied to users' footers. */
        CHANGED,
        /** The text was the one the page showed, or the one in force: nothing changed. */
        UNCHANGED,
        /** Someone changed the default after the page was opened: nothing was saved. */
        CHANGED_SINCE_SHOWN
    }

    /**
     * The outcome of saving the clinic default.
     *
     * @param outcome what the save did
     * @param noticed how many own-footer rows differed from the new default and got (or kept) a notice
     */
    public record ClinicDefaultSaved(ClinicDefaultOutcome outcome, int noticed) {

        /** @return whether the default changed */
        public boolean changed() {
            return outcome == ClinicDefaultOutcome.CHANGED;
        }
    }

    /** @return the clinic default, empty when none is set */
    public String clinicDefault() {
        UserProperty property = firstClinicRow();
        return property == null || property.getValue() == null ? "" : property.getValue();
    }

    /**
     * A fingerprint of a footer, for a page to send back with its form: comparing it with the
     * stored footer's tells whether the footer changed after the page was opened, without putting
     * the text in a hidden field. Footers that differ only in line-break style, surrounding
     * whitespace or characters the page shows as spaces have the same fingerprint, as they save
     * the same.
     *
     * @param footer a footer, or null for none
     * @return the fingerprint, 64 hexadecimal characters
     */
    public static String fingerprint(String footer) {
        return DigestUtils.sha256Hex(normalise(footer));
    }

    /**
     * The footer a user's compose screen opens with when the eForm supplies none: the user's own
     * (even when they saved it empty, meaning no footer), otherwise the clinic default.
     *
     * @param providerNo the logged-in user, or null when there is none
     * @return the footer, or empty when the user has none and no clinic default is set
     */
    public Optional<String> composeFooter(String providerNo) {
        UserProperty own = firstRow(providerNo, USER_FOOTER);
        if (own != null) {
            return Optional.of(nullToEmpty(own.getValue()));
        }
        String clinic = clinicDefault();
        return clinic.isEmpty() ? Optional.empty() : Optional.of(clinic);
    }

    /**
     * @param providerNo the logged-in user, or null when there is none
     * @return the user's previous footer when a clinic change affected it, or null
     */
    public String clinicChangeNotice(String providerNo) {
        UserProperty notice = firstRow(providerNo, CLINIC_CHANGE_NOTICE);
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
        UserProperty own = firstRow(providerNo, USER_FOOTER);
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
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void saveOwnFooter(String providerNo, String footer) {
        String normalised = withinLimit(footer);
        translated(() -> {
            writeRow(providerNo, USER_FOOTER, normalised);
            deleteRows(providerNo, CLINIC_CHANGE_NOTICE);
            return null;
        });
    }

    /**
     * Removes the user's own footer, so they follow the clinic default, and clears any notice.
     *
     * @param providerNo the logged-in user
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void useClinicDefault(String providerNo) {
        translated(() -> {
            deleteRows(providerNo, USER_FOOTER);
            deleteRows(providerNo, CLINIC_CHANGE_NOTICE);
            return null;
        });
    }

    /**
     * Makes the footer a clinic change replaced the user's own footer again.
     *
     * @param providerNo the logged-in user
     * @return false when there was no notice to restore from
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean restorePreviousFooter(String providerNo) {
        return translated(() -> {
            UserProperty notice = firstRow(providerNo, CLINIC_CHANGE_NOTICE);
            if (notice == null) {
                return false;
            }
            writeRow(providerNo, USER_FOOTER, nullToEmpty(notice.getValue()));
            deleteRows(providerNo, CLINIC_CHANGE_NOTICE);
            return true;
        });
    }

    /**
     * Dismisses the clinic-change notice and keeps the footer the user now has.
     *
     * @param providerNo the logged-in user
     * @return false when there was no notice
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean dismissClinicChangeNotice(String providerNo) {
        return translated(() -> {
            boolean hadNotice = firstRow(providerNo, CLINIC_CHANGE_NOTICE) != null;
            deleteRows(providerNo, CLINIC_CHANGE_NOTICE);
            return hadNotice;
        });
    }

    /**
     * Saves the clinic default and applies it to users' own footers.
     *
     * <p>The page sends back the fingerprint of the footer it showed. Saving that text unedited
     * changes nothing, even when someone has changed the default since: an administrator who opens
     * the page and saves it as it is must not undo another's change, nor replace users' footers a
     * second time. Saving a different text when the default has changed since the page was opened
     * saves nothing either, so the administrator can see the current footer first.</p>
     *
     * <p>Otherwise, a user's own footer equal to the
     * new default is removed, so the user follows the default from now on. With own footers
     * replaced on a clinic change, every other own footer is removed too, and the user gets a
     * notice holding their previous text, unless it was the old default word for word (they were
     * following it in effect). With the rule switched off, own footers are kept and every user
     * whose footer differs from the new default gets the notice. An own footer saved empty means
     * "no footer", a choice of its own, so it always gets the notice.</p>
     *
     * @param footer the clinic default as typed; empty means no clinic default
     * @param shownFingerprint the {@link #fingerprint} of the footer the page showed
     * @return what the save did, and how many own footers got a notice
     * @throws FooterTooLongException when the footer is over the limit; nothing is saved
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ClinicDefaultSaved saveClinicDefault(String footer, String shownFingerprint) {
        Objects.requireNonNull(shownFingerprint, "shownFingerprint");
        String normalised = withinLimit(footer);
        // Unedited, the text the page showed, whatever is stored now: nothing to read or lock.
        if (fingerprint(normalised).equals(shownFingerprint)) {
            return new ClinicDefaultSaved(ClinicDefaultOutcome.UNCHANGED, 0);
        }
        return translated(() -> applyClinicDefault(normalised, shownFingerprint));
    }

    private ClinicDefaultSaved applyClinicDefault(String normalised, String shownFingerprint) {
        // Locked, so a second save waits here and then compares against what this one commits.
        List<UserProperty> clinicRows = userPropertyDao.lockClinicProperties(CLINIC_DEFAULT);
        UserProperty clinic = clinicRows.isEmpty() ? null : clinicRows.get(0);
        String previous = clinic == null ? "" : normalise(clinic.getValue());
        // Already the text in force, including an empty form when none is set.
        if (normalised.equals(previous)) {
            return new ClinicDefaultSaved(ClinicDefaultOutcome.UNCHANGED, 0);
        }
        if (!fingerprint(previous).equals(shownFingerprint)) {
            return new ClinicDefaultSaved(ClinicDefaultOutcome.CHANGED_SINCE_SHOWN, 0);
        }
        if (clinic == null) {
            clinic = new UserProperty();
            clinic.setName(CLINIC_DEFAULT);
        }
        clinic.setValue(normalised);
        userPropertyDao.saveProp(clinic);
        // Two first saves at the same moment can each add a row: keep the oldest only.
        clinicRows.stream().skip(1).forEach(userPropertyDao::delete);

        int noticed = 0;
        // Locked too: a user's own save waits for this one rather than losing to it unseen.
        for (UserProperty own : userPropertyDao.lockProviderProperties(USER_FOOTER)) {
            String text = normalise(own.getValue());
            if (text.equals(normalised)) {
                // Already the new default. An empty one ("no footer") stays the user's own
                // choice, so a later clinic footer still reaches them with a notice.
                if (!text.isEmpty()) {
                    userPropertyDao.delete(own);
                }
                continue;
            }
            boolean followedOldDefault = !text.isEmpty() && text.equals(previous);
            if (replaceOwnFooters) {
                userPropertyDao.delete(own);
                if (followedOldDefault) {
                    continue;
                }
            }
            // A notice from an earlier change keeps the text the user lost first.
            if (firstRow(own.getProviderNo(), CLINIC_CHANGE_NOTICE) == null) {
                userPropertyDao.saveProp(own.getProviderNo(), CLINIC_CHANGE_NOTICE, text);
            }
            noticed++;
        }
        return new ClinicDefaultSaved(ClinicDefaultOutcome.CHANGED, noticed);
    }

    /**
     * Runs a save's work, turning a JPA or Hibernate lock or stale-row error from a flush partway
     * through into Spring's {@code ConcurrencyFailureException} family, as a failure at commit
     * already is. Nothing registers Spring's repository exception translation for the DAOs.
     */
    private static <T> T translated(Supplier<T> work) {
        try {
            return work.get();
        } catch (PersistenceException e) {
            DataAccessException translated = JPA_EXCEPTIONS.translateExceptionIfPossible(e);
            throw translated != null ? translated : e;
        }
    }

    private UserProperty firstClinicRow() {
        return userPropertyDao.findClinicProperties(CLINIC_DEFAULT).stream().findFirst().orElse(null);
    }

    /** The user's oldest row with this name; a double submit can leave more than one. */
    private UserProperty firstRow(String providerNo, String name) {
        return providerNo == null ? null : rows(providerNo, name).stream().findFirst().orElse(null);
    }

    /** Writes the value to the oldest row, creating one if needed, and removes any duplicates. */
    private void writeRow(String providerNo, String name, String value) {
        List<UserProperty> rows = rows(providerNo, name);
        if (rows.isEmpty()) {
            userPropertyDao.saveProp(providerNo, name, value);
            return;
        }
        UserProperty kept = rows.get(0);
        kept.setValue(value);
        userPropertyDao.saveProp(kept);
        rows.stream().skip(1).forEach(userPropertyDao::delete);
    }

    private void deleteRows(String providerNo, String name) {
        rows(providerNo, name).forEach(userPropertyDao::delete);
    }

    private List<UserProperty> rows(String providerNo, String name) {
        List<UserProperty> rows = new ArrayList<>(userPropertyDao.getAllProperties(name, List.of(providerNo)));
        rows.sort(Comparator.comparing(UserProperty::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        return rows;
    }

    /**
     * @param footer a footer as typed
     * @return the footer with line breaks stored as one character each, as the send action counts,
     *         characters the page shows as spaces stored as spaces, and without surrounding
     *         whitespace
     * @throws FooterTooLongException when it is over the limit
     */
    static String withinLimit(String footer) {
        String normalised = normalise(footer);
        if (normalised.length() > EmailData.FOOTER_MAX_LENGTH) {
            throw new FooterTooLongException();
        }
        return normalised;
    }

    /**
     * Line breaks as one character each, as the send action counts them, characters the page
     * would show as a space stored as one, and no surrounding whitespace: sending drops it anyway,
     * and a browser drops a textarea's first line break. Each would otherwise make an unchanged
     * footer look changed on its next save.
     */
    static String normalise(String footer) {
        if (footer == null) {
            return "";
        }
        String lines = footer.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder shown = new StringBuilder(lines.length());
        lines.codePoints().forEach(cp -> {
            if (shownAsSpace(cp)) {
                shown.append(' ');
            } else {
                shown.appendCodePoint(cp);
            }
        });
        return shown.toString().strip();
    }

    /**
     * Whether the pages' encoder (OWASP {@code forHtmlContent}) shows this code point as a space:
     * control characters other than tab, line breaks and NEL, Unicode non-characters, and lone
     * surrogates (a pair arrives here as one code point).
     */
    private static boolean shownAsSpace(int cp) {
        if (cp == '\t' || cp == '\n' || cp == '\r' || cp == 0x85) {
            return false;
        }
        return Character.isISOControl(cp)
                || (cp >= 0xFDD0 && cp <= 0xFDEF)
                || (cp & 0xFFFE) == 0xFFFE
                || (cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
