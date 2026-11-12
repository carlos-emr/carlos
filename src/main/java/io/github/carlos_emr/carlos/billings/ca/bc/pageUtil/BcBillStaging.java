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
 * The session holds at most {@value #MAX_ENTRIES} bills; opening another drops the one that has
 * gone longest unused. Every step that finds a bill counts as use, and a saved bill
 * ({@link #release}) is the first to go, so the bills dropped are the ones finished with or left
 * idle longest. A window whose bill was dropped is told its billing session expired. As in
 * {@code EmailAttachmentStaging}, the map in the session is replaced, never changed in place, and
 * every change happens under the session mutex.</p>
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

    /** Open bills per session; opening another drops the one unused longest. */
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
     * Hands a bill to this request's page without staging it, for a request that only re-renders
     * part of an open bill form. The form reloads its patient bar and code table in place when the
     * provider, bill type or form changes; it keeps posting the key of the bill it opened with, so
     * staging each reload would only push that bill, and other windows' bills, out of the session.
     */
    public static void render(HttpServletRequest request, BillingSessionBean bill) {
        request.removeAttribute(KEY_ATTRIBUTE);
        request.setAttribute(BEAN_ATTRIBUTE, bill);
    }

    /**
     * The bill this request continues, named by its {@value #KEY_PARAMETER} parameter or, after a
     * forward, its {@value #KEY_ATTRIBUTE} request attribute. When found, it is also handed to
     * this request's page, and counts as in use: it becomes the last bill to be dropped.
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
            moveToNewest(session, key);
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
        if (session == null || !(key instanceof String k) || !isKey(k)) {
            return;
        }
        synchronized (WebUtils.getSessionMutex(session)) {
            // Looked up and stored under one lock, with the session's own key: a bill another
            // request dropped in the meantime stays dropped instead of coming back.
            String held = heldKey(bills(session), k);
            if (held != null) {
                store(session, held, bill);
            }
        }
    }

    /**
     * Marks the bill this request carries as saved. It stays open, so a repeated submit of the
     * same review still finds it, but it becomes the first to be dropped when another bill needs
     * room, ahead of bills still open in other windows. Does nothing when the request carries no
     * open bill's key.
     */
    public static void release(HttpServletRequest request) {
        Object key = request.getAttribute(KEY_ATTRIBUTE);
        HttpSession session = request.getSession(false);
        if (session == null || !(key instanceof String k) || !isKey(k)) {
            return;
        }
        synchronized (WebUtils.getSessionMutex(session)) {
            Map<String, BillingSessionBean> current = bills(session);
            String held = heldKey(current, k);
            if (held == null) {
                return;
            }
            LinkedHashMap<String, BillingSessionBean> next = new LinkedHashMap<>();
            next.put(held, current.get(held));
            current.forEach(next::putIfAbsent);
            session.setAttribute(ATTRIBUTE, Collections.unmodifiableMap(next));
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

    /** Moves the bill held under {@code key} to the end that is dropped last. */
    private static void moveToNewest(HttpSession session, String key) {
        synchronized (WebUtils.getSessionMutex(session)) {
            Map<String, BillingSessionBean> current = bills(session);
            String held = heldKey(current, key);
            String newest = null;
            for (String k : current.keySet()) {
                newest = k;
            }
            if (held == null || held.equals(newest)) {
                return;
            }
            LinkedHashMap<String, BillingSessionBean> next = new LinkedHashMap<>(current);
            BillingSessionBean bill = next.remove(held);
            next.put(held, bill);
            session.setAttribute(ATTRIBUTE, Collections.unmodifiableMap(next));
        }
    }

    /**
     * The session's own copy of {@code key}, or null when no bill is held under it. The session
     * is rewritten with the key it issued, never with the request's copy, which only selects it.
     */
    private static String heldKey(Map<String, BillingSessionBean> bills, String key) {
        for (String held : bills.keySet()) {
            if (held.equals(key)) {
                return held;
            }
        }
        return null;
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
