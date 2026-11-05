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
package io.github.carlos_emr.carlos.eform;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpSession;

/**
 * The links an opener eForm's {@code oscarOPEN} buttons hand out, remembered for the session.
 *
 * <p>A button opens another eForm with {@code eform_link=<provider>_<patient>_<opener fid>_<button>}.
 * When that eForm is saved, its id is kept in the session under the link, and the opener's own save
 * writes it into the button as {@code link$eform}. {@link #issue} records each link a rendered button
 * carries, together with the eForm it opens; {@link #wasIssued} lets the save accept a link only if
 * this session issued it for the signed-in provider, the patient and the eForm being saved.</p>
 *
 * <p>The windows of one session send requests at the same time, so every read and write holds one
 * class-wide lock (a session object is not guaranteed to be the same instance on every request), and
 * the stored map is replaced, never changed in place. Links are not used up: a retried save of the
 * same linked eForm must still be accepted.</p>
 *
 * @since 2026-10-16
 */
public final class EFormOpenerLinks {
    /** {@code <provider>_<patient, or -1 in the admin preview>_<opener fid>_<button name>}. */
    public static final Pattern LINK_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9]{1,6}_(-1|\\d{1,10})_\\d{1,10}_[a-zA-Z0-9_.-]{1,50}$");

    /** Links kept per session; issuing one more drops the one issued longest ago. */
    static final int MAX_LINKS = 200;

    private static final String SESSION_KEY = EFormOpenerLinks.class.getName();
    private static final Pattern FID = Pattern.compile("\\d{1,10}");
    private static final Object LOCK = new Object();

    /** Each issued link and the fid of the eForm its button opens, in issue order. */
    private record Issued(LinkedHashMap<String, String> targets) implements Serializable { }

    private EFormOpenerLinks() { }

    /**
     * Records the link an opener's button carries and returns it. Issuing a link again (the opener
     * was rendered again) makes it the most recent.
     *
     * @param providerNo the signed-in provider, who will save the linked eForm
     * @param targetFid  the fid of the eForm the button opens
     * @return the link, or {@code null} when there is no session or the parts do not form a link
     */
    public static String issue(HttpSession session, String providerNo, String demographicNo, String openerFid,
                               String buttonName, String targetFid) {
        String link = providerNo + "_" + demographicNo + "_" + openerFid + "_" + buttonName;
        if (session == null || !LINK_PATTERN.matcher(link).matches()
                || targetFid == null || !FID.matcher(targetFid).matches()) {
            return null;
        }
        synchronized (LOCK) {
            LinkedHashMap<String, String> targets = stored(session);
            targets.remove(link);
            if (targets.size() >= MAX_LINKS) {
                targets.remove(targets.firstEntry().getKey());
            }
            targets.put(link, targetFid);
            session.setAttribute(SESSION_KEY, new Issued(targets));
        }
        return link;
    }

    /**
     * Whether this session issued {@code link} to a button that opens {@code savedFid}, for this
     * provider and this patient.
     */
    public static boolean wasIssued(HttpSession session, String link, String providerNo, String demographicNo,
                                    String savedFid) {
        if (session == null || link == null || savedFid == null
                || !link.startsWith(providerNo + "_" + demographicNo + "_")) {
            return false;
        }
        synchronized (LOCK) {
            return session.getAttribute(SESSION_KEY) instanceof Issued(var targets) && savedFid.equals(targets.get(link));
        }
    }

    private static LinkedHashMap<String, String> stored(HttpSession session) {
        return session.getAttribute(SESSION_KEY) instanceof Issued(var targets)
                ? new LinkedHashMap<>(targets) : new LinkedHashMap<>();
    }
}
