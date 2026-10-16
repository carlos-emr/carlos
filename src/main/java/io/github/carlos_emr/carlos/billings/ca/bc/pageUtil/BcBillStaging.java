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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.web.util.WebUtils;

/**
 * Holds each open BC bill under its own key while it moves from the bill form
 * ({@code billingBC.jsp}) through review ({@code billingCreated.jsp}) to the save (issue #4673).
 *
 * <p>The bill being built used to be one login-wide session attribute,
 * {@code billingSessionBean}. With two patients' bills open in two windows, the second bill
 * replaced the first, so reviewing or saving in the first window billed the second window's
 * patient and appointment with the first window's codes. Each bill is now staged under a random
 * key that its pages carry ({@code billKey}) and every step reads exactly that bill. A missing,
 * malformed or dropped key finds nothing, never another window's bill.</p>
 *
 * <p>The steps hand the bill to their JSP as the request attribute {@value #BEAN_ATTRIBUTE},
 * the name the pages have always looked up, together with the key as {@value #KEY_ATTRIBUTE}.
 * The session holds at most {@value #MAX_ENTRIES} bills; opening another drops the oldest, whose
 * window then refuses to continue. As in {@code EmailAttachmentStaging}, the map in the session is
 * replaced, never changed in place, and every change happens under the session mutex.</p>
 *
 * @since 2026-10-16
 */
public final class BcBillStaging {

    /** Form and query parameter carrying a bill's key. */
    public static final String KEY_PARAMETER = "billKey";

    /** Request attribute carrying the bill's key to its page (and through a forward). */
    public static final String KEY_ATTRIBUTE = "billKey";

    /** Request attribute under which the pages find the bill. */
    public static final String BEAN_ATTRIBUTE = "billingSessionBean";

    /** Open bills per session; opening another drops the oldest. */
    public static final int MAX_ENTRIES = 8;

    private static final String ATTRIBUTE = BcBillStaging.class.getName() + ".bills";

    /** 16 random bytes, URL-safe Base64 without padding. */
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]{22}");

    private static final SecureRandom RANDOM = new SecureRandom();

    private BcBillStaging() {
    }

    /**
     * Stages a newly opened bill and hands it to this request's page.
     *
     * @return the new bill's key
     */
    public static String open(HttpServletRequest request, BillingSessionBean bill) {
        String key = newKey();
        store(request.getSession(), key, bill);
        publish(request, key, bill);
        return key;
    }

    /**
     * The bill this request continues, named by its {@value #KEY_PARAMETER} parameter or, after a
     * forward, its {@value #KEY_ATTRIBUTE} request attribute. When found, it is also handed to
     * this request's page.
     *
     * @return the bill, or null when the request names none that is still open
     */
    public static BillingSessionBean resume(HttpServletRequest request) {
        String key = request.getParameter(KEY_PARAMETER);
        if (key == null && request.getAttribute(KEY_ATTRIBUTE) instanceof String forwarded) {
            key = forwarded;
        }
        if (!isKey(key)) {
            return null;
        }
        HttpSession session = request.getSession(false);
        BillingSessionBean bill = session == null ? null : bills(session).get(key);
        if (bill != null) {
            publish(request, key, bill);
        }
        return bill;
    }

    /**
     * Stores {@code bill} again under the key this request carries, after a step changed it, so a
     * container that persists or replicates sessions sees the change. Does nothing when the
     * request carries no open bill's key.
     */
    public static void save(HttpServletRequest request, BillingSessionBean bill) {
        Object key = request.getAttribute(KEY_ATTRIBUTE);
        HttpSession session = request.getSession(false);
        if (session != null && key instanceof String k && isKey(k) && bills(session).containsKey(k)) {
            store(session, k, bill);
        }
    }

    /** @return whether {@code key} has the shape of a bill key; says nothing about what is open */
    public static boolean isKey(String key) {
        return key != null && KEY.matcher(key).matches();
    }

    private static void publish(HttpServletRequest request, String key, BillingSessionBean bill) {
        request.setAttribute(KEY_ATTRIBUTE, key);
        request.setAttribute(BEAN_ATTRIBUTE, bill);
    }

    private static void store(HttpSession session, String key, BillingSessionBean bill) {
        synchronized (WebUtils.getSessionMutex(session)) {
            LinkedHashMap<String, BillingSessionBean> next = new LinkedHashMap<>(bills(session));
            next.remove(key);
            next.put(key, bill);
            Iterator<String> oldest = next.keySet().iterator();
            while (next.size() > MAX_ENTRIES) {
                oldest.next();
                oldest.remove();
            }
            session.setAttribute(ATTRIBUTE, Collections.unmodifiableMap(next));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, BillingSessionBean> bills(HttpSession session) {
        Object value = session.getAttribute(ATTRIBUTE);
        return value instanceof Map<?, ?> map ? (Map<String, BillingSessionBean>) map : Map.of();
    }

    private static String newKey() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
