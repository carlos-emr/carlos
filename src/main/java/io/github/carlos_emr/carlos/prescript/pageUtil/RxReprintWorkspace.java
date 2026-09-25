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
package io.github.carlos_emr.carlos.prescript.pageUtil;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpSession;

import org.springframework.web.util.WebUtils;

/**
 * Per-patient reprint workspace: the saved script a reprint loaded, and its script comment,
 * keyed by the patient's demographic number.
 * <p>
 * Reprint used to keep one session-wide {@code tmpBeanRX} bean, a session-wide {@code rePrint}
 * flag and a session-wide {@code comment}. With two patients' Rx windows open, a reprint in one
 * window put the other window into reprint mode too, and ViewScript2 / Preview2 / the fax header
 * then rendered the first patient's prescription under the second patient's chart (#3908). The
 * workspace replaces all three: a patient is "reprinting" exactly when an entry exists for that
 * patient, and readers look the entry up by the patient their own request resolved to, so one
 * patient's reprint can never be rendered for another.
 * <p>
 * Writers are {@code RxRePrescribe2Action.reprint} / {@code reprint2}; the save paths of
 * {@code RxWriteScript2Action} and {@code RxViewScript2Action} clear the patient's entry; readers
 * are {@code RxViewScript2Action}, ViewScript2.jsp, Preview2.jsp and the fax header binding in
 * {@code FrmCustomedPDFServlet}.
 *
 * @since 2026-09-24
 */
public final class RxReprintWorkspace {

    /** Session attribute holding the per-patient map. Never read it directly; use this class. */
    static final String SESSION_ATTRIBUTE = "rxReprintByPatient";

    /**
     * One patient's reprint: the script loaded for reprinting and its stored script comment.
     *
     * @param bean    the reprinted script's items, for the patient this entry is keyed by
     * @param comment the script's comment, or an empty string
     */
    public record Entry(RxSessionBean bean, String comment) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    private RxReprintWorkspace() {
    }

    /**
     * Records a reprint for the patient {@code reprintBean} belongs to, replacing only that
     * patient's earlier reprint.
     *
     * @param session     the prescriber's session
     * @param reprintBean the reprinted script; its demographic number is the key
     * @param comment     the script comment, or {@code null}
     */
    public static void store(HttpSession session, RxSessionBean reprintBean, String comment) {
        // Lookup and put under the same session mutex, so a save that clears this patient's reprint
        // in another window cannot interleave between them and leave a stale entry behind.
        synchronized (WebUtils.getSessionMutex(session)) {
            map(session, true).put(reprintBean.getDemographicNo(), new Entry(reprintBean, comment == null ? "" : comment));
        }
    }

    /**
     * The reprint pending for this patient, or {@code null} when the patient is not reprinting.
     *
     * @param session       the prescriber's session, may be {@code null}
     * @param demographicNo the patient the caller's request resolved to
     * @return the patient's reprint entry, or {@code null}
     */
    public static Entry find(HttpSession session, Integer demographicNo) {
        if (session == null || demographicNo == null) {
            return null;
        }
        return map(session, false).get(demographicNo);
    }

    /**
     * Whether this patient has a reprint pending.
     *
     * @param session       the prescriber's session, may be {@code null}
     * @param demographicNo the patient the caller's request resolved to
     * @return {@code true} only when a reprint was loaded for this same patient
     */
    public static boolean isReprinting(HttpSession session, Integer demographicNo) {
        return find(session, demographicNo) != null;
    }

    /**
     * Ends this patient's reprint mode, leaving other patients' reprints alone.
     *
     * @param session       the prescriber's session, may be {@code null}
     * @param demographicNo the patient whose reprint is finished
     */
    public static void clear(HttpSession session, Integer demographicNo) {
        if (session == null || demographicNo == null) {
            return;
        }
        synchronized (WebUtils.getSessionMutex(session)) {
            map(session, false).remove(demographicNo);
        }
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<Integer, Entry> map(HttpSession session, boolean create) {
        // Two windows of one session can reprint at the same moment; create the map once under the
        // session mutex so neither request replaces the other's map.
        synchronized (WebUtils.getSessionMutex(session)) {
            Object existing = session.getAttribute(SESSION_ATTRIBUTE);
            if (existing instanceof ConcurrentHashMap<?, ?> found) {
                return (ConcurrentHashMap<Integer, Entry>) found;
            }
            if (!create) {
                // Not stored: an empty view for readers, so callers never see null.
                return new ConcurrentHashMap<>();
            }
            ConcurrentHashMap<Integer, Entry> created = new ConcurrentHashMap<>();
            session.setAttribute(SESSION_ATTRIBUTE, created);
            return created;
        }
    }
}
