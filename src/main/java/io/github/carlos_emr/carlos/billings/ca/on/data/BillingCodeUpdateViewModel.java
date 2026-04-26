/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.data;

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

    private BillingCodeUpdateViewModel(Builder b) {
        this.mode = b.mode == null ? Mode.CONFIRM_SELECTION : b.mode;
        this.noSelection = b.noSelection;
        this.selected0 = b.selected0 == null ? "" : b.selected0;
        this.selected1 = b.selected1 == null ? "" : b.selected1;
        this.selected2 = b.selected2 == null ? "" : b.selected2;
    }

    public static Builder builder() { return new Builder(); }

    public Mode getMode() { return mode; }
    public boolean isNoSelection() { return noSelection; }
    public String getSelected0() { return selected0; }
    public String getSelected1() { return selected1; }
    public String getSelected2() { return selected2; }

    public static final class Builder {
        private Mode mode;
        private boolean noSelection;
        private String selected0;
        private String selected1;
        private String selected2;

        public Builder mode(Mode v) { this.mode = v; return this; }
        public Builder noSelection(boolean v) { this.noSelection = v; return this; }
        public Builder selected0(String v) { this.selected0 = v; return this; }
        public Builder selected1(String v) { this.selected1 = v; return this; }
        public Builder selected2(String v) { this.selected2 = v; return this; }

        public BillingCodeUpdateViewModel build() { return new BillingCodeUpdateViewModel(this); }
    }
}
