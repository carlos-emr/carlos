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
}
