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

import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;

import java.security.SecureRandom;

/**
 * Stash keys ({@code randomId}) for staged Rx cards.
 *
 * <p>A staged card is identified by this key when the prescriber closes, edits or saves it
 * (#3871, #3908), so it comes from one shared {@link SecureRandom} rather than
 * {@code Math.random()}. The values keep the ranges the Rx pages have always used: several code
 * paths parse the key back with {@code Integer.parseInt}, so it must stay a small non-negative
 * number.</p>
 *
 * @since 2026-09-24
 */
public final class RxStashIds {

    /** The range most staging paths use: 0 to 1,000,000 inclusive. */
    public static final int DEFAULT_BOUND = 1_000_000;

    private static final SecureRandom RANDOM = new SecureRandom();

    private RxStashIds() {
    }

    /**
     * A new stash key between 0 and {@code bound} inclusive, the same range
     * {@code Math.round(Math.random() * bound)} produced.
     *
     * @param bound the largest key, must not be negative
     * @return the key
     * @throws IllegalArgumentException if {@code bound} is negative
     */
    public static long next(int bound) {
        if (bound < 0) {
            throw new IllegalArgumentException("bound must not be negative");
        }
        return RANDOM.nextInt(bound + 1);
    }

    /**
     * A new stash key that no card in {@code bean}'s stash already uses. A key identifies the card
     * the prescriber closes, edits or saves, so two cards must never share one (#3908).
     *
     * @param bean  the patient's Rx bean whose stash the key must be unique in
     * @param bound the largest key, must not be negative
     * @return an unused key between 0 and {@code bound}, or, if the range is exhausted, one above
     *         every key in use
     */
    public static long nextUnique(RxSessionBean bean, int bound) {
        // Two windows of the same patient share one bean, so the stash is read under the bean's
        // monitor; RxSessionBean#addStashItem re-keys under the same monitor, which is what makes a
        // draw here and the later insertion safe against each other (#3908). The bean takes its
        // own monitor rather than this method locking on its parameter.
        return bean.nextUniqueStashKey(bound);
    }

    /** {@link #nextUnique} for a caller that already holds {@code bean}'s monitor. */
    static long nextUniqueLocked(RxSessionBean bean, int bound) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            long key = next(bound);
            if (!inUse(bean, key)) {
                return key;
            }
        }
        long highest = -1;
        for (int i = 0; i < bean.getStashSize(); i++) {
            highest = Math.max(highest, bean.getStashItem(i).getRandomId());
        }
        return highest + 1;
    }

    /**
     * The key a client proposed for a card it is about to render, when it is a well-formed
     * non-negative {@code int} that no staged card uses; otherwise a fresh
     * {@link #nextUnique unique} key. Kept for the pages that name the card's key before the
     * server answers; the server's reply renders the card with the key returned here.
     *
     * @param bean      the patient's Rx bean
     * @param clientKey the proposed key, may be {@code null} or malformed
     * @param bound     the range for a fresh key
     * @return a key no other staged card uses
     */
    public static long acceptOrNext(RxSessionBean bean, String clientKey, int bound) {
        return bean.acceptOrNextStashKey(clientKey, bound);
    }

    /** {@link #acceptOrNext} for a caller that already holds {@code bean}'s monitor. */
    static long acceptOrNextLocked(RxSessionBean bean, String clientKey, int bound) {
        if (clientKey != null && clientKey.matches("\\d{1,9}")) {
            long key = Long.parseLong(clientKey);
            if (!inUse(bean, key)) {
                return key;
            }
        }
        return nextUniqueLocked(bean, bound);
    }

    /**
     * Whether a card other than {@code item} already carries {@code key} in {@code bean}'s stash.
     * Called by {@link RxSessionBean#addStashItem} under the bean's monitor, so an allocation that
     * raced with another window's insertion is caught at the moment the card is added.
     */
    static boolean inUseByAnother(RxSessionBean bean, RxPrescriptionData.Prescription item, long key) {
        for (int i = 0; i < bean.getStashSize(); i++) {
            RxPrescriptionData.Prescription other = bean.getStashItem(i);
            if (other != null && other != item && other.getRandomId() == key) {
                return true;
            }
        }
        return false;
    }

    private static boolean inUse(RxSessionBean bean, long key) {
        for (int i = 0; i < bean.getStashSize(); i++) {
            RxPrescriptionData.Prescription item = bean.getStashItem(i);
            if (item != null && item.getRandomId() == key) {
                return true;
            }
        }
        return false;
    }

    private static final int MAX_ATTEMPTS = 1000;
}
