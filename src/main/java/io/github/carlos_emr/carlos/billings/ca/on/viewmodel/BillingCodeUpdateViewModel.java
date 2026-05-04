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
package io.github.carlos_emr.carlos.billings.ca.on.viewmodel;

/**
 * Immutable view model for {@code billingCodeUpdate.jsp}, the popup that
 * either confirms a multi-code selection (forwards to the parent window)
 * or persists a description edit on a {@code BillingService} row.
 *
 * <p>The legacy JSP did the persist mid-render. The action layer now does
 * the persist before forwarding here, and this view-model carries the
 * resulting state so the JSP only renders.</p>
 *
 * @since 2026-04-26
 */
public final class BillingCodeUpdateViewModel {

    /** Which client-side script the JSP should emit at the end of render. */
    public enum Mode {
        /** Confirm-mode: emit {@code CodeAttach(p0, p1, p2)} or "No input selected" stub. */
        CONFIRM_SELECTION,
        /** Update-mode: emit history.go(-1) + opener.refresh() to close the popup. */
        UPDATE_DESCRIPTION
    }

    private final Mode mode;
    private final boolean noSelection;
    private final String selected0;
    private final String selected1;
    private final String selected2;
    private final String nameFSafe;

    private BillingCodeUpdateViewModel(Builder b) {
        this.mode = b.mode == null ? Mode.CONFIRM_SELECTION : b.mode;
        this.noSelection = b.noSelection;
        this.selected0 = b.selected0 == null ? "" : b.selected0;
        this.selected1 = b.selected1 == null ? "" : b.selected1;
        this.selected2 = b.selected2 == null ? "" : b.selected2;
        this.nameFSafe = b.nameFSafe == null ? "" : b.nameFSafe;
    }

    public static Builder builder() { return new Builder(); }

    public Mode getMode() { return mode; }
    public boolean isNoSelection() { return noSelection; }
    public String getSelected0() { return selected0; }
    public String getSelected1() { return selected1; }
    public String getSelected2() { return selected2; }

    /**
     * The {@code nameF} request parameter validated against
     * {@code [a-zA-Z_][a-zA-Z0-9_.]*}. Empty string when the param is missing,
     * malformed, or null. JSP uses this to decide between targeted
     * {@code self.opener.<name>} assignment and the legacy three-field
     * fallback. Because validation strictly limits the value to JS-identifier
     * characters and dots, the JSP can splice it directly into a JS
     * identifier path.
     *
     * @return validated identifier or empty string (never null)
     */
    public String getNameFSafe() { return nameFSafe; }

    /**
     * @return {@code true} when {@link #getNameFSafe()} is non-empty (i.e.
     *         the JSP should emit the targeted opener assignment).
     */
    public boolean isHasNameF() { return !nameFSafe.isEmpty(); }

    /**
     * @return {@code true} when {@link #getMode()} is
     *         {@link Mode#CONFIRM_SELECTION}. EL-friendly accessor used by
     *         the JSP to branch on render mode.
     */
    public boolean isConfirmMode() { return mode == Mode.CONFIRM_SELECTION; }

    public static final class Builder {
        private Mode mode;
        private boolean noSelection;
        private String selected0;
        private String selected1;
        private String selected2;
        private String nameFSafe;

        public Builder mode(Mode v) { this.mode = v; return this; }
        public Builder noSelection(boolean v) { this.noSelection = v; return this; }
        public Builder selected0(String v) { this.selected0 = v; return this; }
        public Builder selected1(String v) { this.selected1 = v; return this; }
        public Builder selected2(String v) { this.selected2 = v; return this; }
        public Builder nameFSafe(String v) { this.nameFSafe = v; return this; }

        public BillingCodeUpdateViewModel build() { return new BillingCodeUpdateViewModel(this); }
    }
}
