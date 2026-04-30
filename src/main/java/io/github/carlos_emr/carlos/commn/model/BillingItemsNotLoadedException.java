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
package io.github.carlos_emr.carlos.commn.model;

/**
 * Thrown when {@link BillingONCHeader1#recomputeTotalFromItems()} or
 * {@link BillingONCHeader1#getBillingItems()} is invoked on a header whose
 * {@code billingItems} collection is a LAZY Hibernate proxy that has not yet
 * been initialized and no open session is available to trigger lazy-loading.
 *
 * <p>Distinguishing this case from "no items" lets callers re-fetch through
 * {@code BillingONCHeader1Dao.findWithItems} rather than silently computing
 * a zero total from an unloaded proxy.</p>
 *
 * @since 2026-04-29
 */
public final class BillingItemsNotLoadedException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public BillingItemsNotLoadedException(String message) {
        super(message);
    }
}
