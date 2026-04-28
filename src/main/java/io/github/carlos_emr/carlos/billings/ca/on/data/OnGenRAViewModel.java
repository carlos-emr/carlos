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
package io.github.carlos_emr.carlos.billings.ca.on.data;

import java.util.Collections;
import java.util.List;

/**
 * Immutable view model for {@code billing/CA/ON/onGenRA.jsp}, the
 * Billing Reconciliation list page. Built by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.assembler.OnGenRADataAssembler}
 * after the action runs the optional RA file import + Rahd list lookup.
 *
 * <p>Captures the rows the JSP renders (one per RA header) plus the action
 * status that selects which "Settle / S35 / Processed" links the row shows.</p>
 *
 * @since 2026-04-25
 */
public final class OnGenRAViewModel {

    /** One row of the RA-header list rendered by onGenRA.jsp. */
    public record Row(
            String raNo,
            String readDate,
            String paymentDate,
            String payable,
            String claimsCount,
            String recordsCount,
            String total,
            String status) {}

    private final List<Row> rows;

    private OnGenRAViewModel(Builder b) {
        this.rows = b.rows == null ? Collections.emptyList() : List.copyOf(b.rows);
    }

    public List<Row> getRows() { return rows; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private List<Row> rows;

        public Builder rows(List<Row> v) { this.rows = v; return this; }

        public OnGenRAViewModel build() { return new OnGenRAViewModel(this); }
    }
}
