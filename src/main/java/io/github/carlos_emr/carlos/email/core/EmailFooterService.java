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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.persistence.PersistenceException;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Provider;
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
 * clinic default. A blank own footer is not a choice of "no footer": saving one, or clearing the
 * box, means the user follows the clinic default. When an administrator changes the clinic
 * default, users' own footers are replaced by it ({@link #REPLACE_OWN_FOOTERS_ON_CLINIC_CHANGE}),
 * and every active user is told on their next email, not the administrator, with the footer they
 * had until then, which they can put back.</p>
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
     * Set on every active user when the clinic default changes: the footer they had until then
     * (their own, or the previous clinic default), shown on their next email until they restore it,
     * keep the new footer, or save their own.
     */
    static final String CLINIC_CHANGE_NOTICE = "email_footer_clinic_change";

    /**
     * Maintainer decision (6 Oct 2026): "Clinic sets a default, then docs can edit it, but if admin
     * changes it it will update all doctors", and the doctors are warned on their next send, not the
     * admin. Set to false to keep users' own footers on a clinic change and only tell them.
     * Maintainer decisions (8 Oct 2026): a blank own footer means the clinic default, never "no
     * footer", and a clinic change tells every user, not only those who had their own footer.
     */
    static final boolean REPLACE_OWN_FOOTERS_ON_CLINIC_CHANGE = true;

    /** Turns JPA and Hibernate lock and stale-row errors into Spring's, as a commit would. */
    private static final HibernateJpaDialect JPA_EXCEPTIONS = new HibernateJpaDialect();

    private final UserPropertyDAO userPropertyDao;
    private final ProviderDao providerDao;
    private final boolean replaceOwnFooters;

    @Autowired
    public EmailFooterService(UserPropertyDAO userPropertyDao, ProviderDao providerDao) {
        this(userPropertyDao, providerDao, REPLACE_OWN_FOOTERS_ON_CLINIC_CHANGE);
    }

    // Package-private so tests can cover both settings of the clinic-change rule.
    EmailFooterService(UserPropertyDAO userPropertyDao, ProviderDao providerDao, boolean replaceOwnFooters) {
        this.userPropertyDao = userPropertyDao;
        this.providerDao = providerDao;
        this.replaceOwnFooters = replaceOwnFooters;
    }

    /**
     * Thrown when a footer is longer than {@link EmailData#FOOTER_MAX_LENGTH} characters of plain
     * text or {@link EmailFooterHtml#MAX_HTML_LENGTH} of HTML; nothing is saved.
     */
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
     * @param clinicChangeNotice the footer the user had before a clinic change, or null
     * @param keptOwnFooter whether the clinic change kept the user's own footer (the notice wording);
     *        false when the user now uses the clinic footer
     */
    public record UserFooterSettings(String ownFooter, String clinicDefault, String clinicChangeNotice,
            boolean keptOwnFooter) {
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
     * @param noticed how many users got (or kept) a notice: every active user, and any other user
     *        who has their own footer
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
     * The footer a user's compose screen opens with when the eForm supplies none: the user's own,
     * otherwise the clinic default. A blank own footer (saved before a blank came to mean the
     * clinic default) also gives the clinic default.
     *
     * @param providerNo the logged-in user, or null when there is none
     * @return the footer, or empty when the user has none and no clinic default is set
     */
    public Optional<String> composeFooter(String providerNo) {
        String own = ownFooter(providerNo);
        if (own != null) {
            return Optional.of(own);
        }
        String clinic = clinicDefault();
        return clinic.isEmpty() ? Optional.empty() : Optional.of(clinic);
    }

    /**
     * @param providerNo the logged-in user, or null when there is none
     * @return the footer the user had before a clinic change they have not yet answered, or null
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
     * Which notice wording applies to the user: with the replace rule off, a user who still has their
     * own footer was told it was kept; everyone else now uses the clinic footer.
     *
     * @param providerNo the logged-in user, or null when there is none
     * @return whether the last clinic change kept the user's own footer
     */
    public boolean clinicChangeKeptOwnFooter(String providerNo) {
        return !replaceOwnFooters && ownFooter(providerNo) != null;
    }

    /**
     * @param providerNo the logged-in user
     * @return what the user's footer page shows
     */
    public UserFooterSettings settingsFor(String providerNo) {
        return new UserFooterSettings(ownFooter(providerNo), clinicDefault(), clinicChangeNotice(providerNo),
                clinicChangeKeptOwnFooter(providerNo));
    }

    /**
     * Saves the user's own footer and clears any clinic-change notice. A blank footer is not "no
     * footer": it removes the user's own, so they follow the clinic default.
     *
     * @param providerNo the logged-in user
     * @param footer the footer as typed
     * @throws FooterTooLongException when the footer is over the limit; nothing is saved
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void saveOwnFooter(String providerNo, String footer) {
        String normalised = withinLimit(footer);
        translated(() -> {
            waitForClinicChange();
            if (normalised.isEmpty()) {
                deleteRows(providerNo, USER_FOOTER);
            } else {
                writeRow(providerNo, USER_FOOTER, normalised);
            }
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
            waitForClinicChange();
            deleteRows(providerNo, USER_FOOTER);
            deleteRows(providerNo, CLINIC_CHANGE_NOTICE);
            return null;
        });
    }

    /**
     * Makes the footer the user had before a clinic change their own footer. When they had none
     * (no clinic default was set), there is nothing to put back and they keep following the clinic
     * default.
     *
     * @param providerNo the logged-in user
     * @return false when there was no notice to restore from
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean restorePreviousFooter(String providerNo) {
        return translated(() -> {
            waitForClinicChange();
            UserProperty notice = firstRow(providerNo, CLINIC_CHANGE_NOTICE);
            if (notice == null) {
                return false;
            }
            String previous = normalise(notice.getValue());
            if (previous.isEmpty()) {
                deleteRows(providerNo, USER_FOOTER);
            } else {
                writeRow(providerNo, USER_FOOTER, previous);
            }
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
            waitForClinicChange();
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
     * <p>Otherwise, a user's own footer equal to the new default, or blank, is removed, so the
     * user follows the default from now on. With own footers replaced on a clinic change, every
     * other own footer is removed too; with the rule switched off, those are kept. Either way every
     * active user, and any other user who has their own footer, gets a notice holding the footer
     * they had until then: their own, or the previous clinic default.</p>
     *
     * @param footer the clinic default as typed; empty means no clinic default
     * @param shownFingerprint the {@link #fingerprint} of the footer the page showed
     * @return what the save did, and how many users got (or kept) a notice
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

        // Every active user is told, with the footer they had until now: the previous clinic
        // default unless their own footer below says otherwise.
        Map<String, String> previousFooters = new LinkedHashMap<>();
        for (String providerNo : activeUsers()) {
            previousFooters.put(providerNo, previous);
        }
        Set<String> ownInEffect = new HashSet<>();
        // Locked too: a user's own save waits for this one rather than losing to it unseen.
        for (UserProperty own : userPropertyDao.lockProviderProperties(USER_FOOTER)) {
            String text = normalise(own.getValue());
            // Oldest first: a double submit's later row is not the footer in effect.
            if (ownInEffect.add(own.getProviderNo())) {
                previousFooters.put(own.getProviderNo(), text.isEmpty() ? previous : text);
            }
            // Blank, or already the new default: the user follows the default from now on.
            if (replaceOwnFooters || text.isEmpty() || text.equals(normalised)) {
                userPropertyDao.delete(own);
            }
        }
        // A notice from an earlier change keeps the footer the user had before that one.
        Set<String> alreadyNoticed = providersWith(CLINIC_CHANGE_NOTICE, previousFooters.keySet());
        previousFooters.forEach((providerNo, footer) -> {
            if (!alreadyNoticed.contains(providerNo)) {
                // A new row, as alreadyNoticed shows: no per-user lookup, which would scan the
                // property table (it has no index on name) once per user while the locks are held.
                userPropertyDao.saveProp(newRow(providerNo, CLINIC_CHANGE_NOTICE, footer));
            }
        });
        return new ClinicDefaultSaved(ClinicDefaultOutcome.CHANGED, previousFooters.size());
    }

    /**
     * Provider numbers of active users, without CARLOS's own system providers. A provider without
     * email rights gets a notice row too; only the email pages show it.
     */
    private List<String> activeUsers() {
        return providerDao.getActiveProviders().stream().map(Provider::getProviderNo)
                .filter(Objects::nonNull).toList();
    }

    /** @return which of these users have a row with this name */
    private Set<String> providersWith(String name, Collection<String> providerNos) {
        if (providerNos.isEmpty()) {
            return Set.of();
        }
        return userPropertyDao.getAllProperties(name, new ArrayList<>(providerNos)).stream()
                .map(UserProperty::getProviderNo).collect(Collectors.toSet());
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

    /** @return the user's own footer, or null when they follow the clinic default (no row, or blank) */
    private String ownFooter(String providerNo) {
        UserProperty own = firstRow(providerNo, USER_FOOTER);
        String text = own == null ? "" : nullToEmpty(own.getValue());
        // Blank as saves judge it, so a row the save would have removed reads as following.
        return normalise(text).isEmpty() ? null : text;
    }

    /**
     * Takes the clinic footer's lock first, in the order a clinic save takes its locks, so a user's
     * save, restore or dismiss waits for a clinic change in progress and then sees its notice:
     * otherwise a user with no footer of their own could save one unseen and be told afterwards that
     * the clinic footer now applies. Before the first clinic footer is saved there is nothing to
     * lock, the gap the class description notes.
     */
    private void waitForClinicChange() {
        userPropertyDao.lockClinicProperties(CLINIC_DEFAULT);
    }

    private static UserProperty newRow(String providerNo, String name, String value) {
        UserProperty row = new UserProperty();
        row.setProviderNo(providerNo);
        row.setName(name);
        row.setValue(value);
        return row;
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
     * @param footer a footer as posted by the Edit footer window (formatted HTML)
     * @return the footer as stored: cleaned against the footer's allow-list
     * @throws FooterTooLongException when it is over a limit: {@link EmailData#FOOTER_MAX_LENGTH}
     *         characters of plain text, or {@link EmailFooterHtml#MAX_HTML_LENGTH} of HTML
     */
    static String withinLimit(String footer) {
        // Refused before it is parsed, as the send action does: no footer within the limits is
        // posted longer than this.
        if (footer != null && footer.length() > 4 * EmailFooterHtml.MAX_HTML_LENGTH) {
            throw new FooterTooLongException();
        }
        String normalised = normalise(footer);
        if (EmailFooterHtml.visibleLength(normalised) > EmailData.FOOTER_MAX_LENGTH
                || normalised.length() > EmailFooterHtml.MAX_HTML_LENGTH) {
            throw new FooterTooLongException();
        }
        return normalised;
    }

    /**
     * The footer as stored and compared: cleaned against the footer's allow-list
     * ({@link EmailFooterHtml#clean}), which also drops surrounding whitespace and an editor's
     * trailing empty lines, and empty when it has no visible text. Cleaning twice gives the same
     * result, so a footer saved again unchanged compares equal.
     */
    static String normalise(String footer) {
        return EmailFooterHtml.clean(footer);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
