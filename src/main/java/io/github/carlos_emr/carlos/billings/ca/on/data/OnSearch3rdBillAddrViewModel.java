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
 * Immutable view model for {@code billing/CA/ON/onSearch3rdBillAddr.jsp},
 * the 3rd-party billing-address search popup.
 *
 * <p>The legacy JSP scriptlet drove direct {@link
 * io.github.carlos_emr.carlos.commn.dao.Billing3rdPartyAddressDao#findAddresses}
 * calls and computed the per-row JS click handlers inline. The action layer
 * now does all of that and hands the JSP this read-only model.</p>
 *
 * @since 2026-04-25
 */
public final class OnSearch3rdBillAddrViewModel {

    /** One row in the search-results table. */
    public record AddressEntry(
            String id,
            String attention,
            String companyName,
            String companyNameDisplay,
            String addressDisplay,
            String city,
            String province,
            String postcode,
            String telephone,
            String fax,
            /** Pre-built JS click handler — already encoded by the assembler. */
            String onClickHandler) { }

    /** Caller-supplied JS path for opener.document.forms[N].elements[X].value. */
    private final String param;
    /** Optional second JS path for paired-field updates. */
    private final String param2;
    /** The user-typed search keyword (echoed back into the form). */
    private final String keyword;
    /** Search mode echo for the radio buttons (search_name / postcode / telephone). */
    private final String searchMode;
    /** Order-by column echo. */
    private final String orderBy;
    private final String limit1;
    private final String limit2;
    /** Resolved list of address rows (already encoded into {@link AddressEntry#onClickHandler}). */
    private final List<AddressEntry> addresses;
    /** True when the page should render "No results found" hint. */
    private final boolean showNoResults;
    /** True when the previous-page button should be enabled. */
    private final boolean showPrevPage;
    /** True when the next-page button should be enabled. */
    private final boolean showNextPage;
    private final int nextPageLimit1;
    private final int prevPageLimit1;

    private OnSearch3rdBillAddrViewModel(Builder b) {
        this.param = b.param == null ? "" : b.param;
        this.param2 = b.param2 == null ? "" : b.param2;
        this.keyword = b.keyword == null ? "" : b.keyword;
        this.searchMode = b.searchMode == null ? "" : b.searchMode;
        this.orderBy = b.orderBy == null ? "" : b.orderBy;
        this.limit1 = b.limit1 == null ? "1" : b.limit1;
        this.limit2 = b.limit2 == null ? "25" : b.limit2;
        this.addresses = b.addresses == null ? Collections.emptyList() : List.copyOf(b.addresses);
        this.showNoResults = b.showNoResults;
        this.showPrevPage = b.showPrevPage;
        this.showNextPage = b.showNextPage;
        this.nextPageLimit1 = b.nextPageLimit1;
        this.prevPageLimit1 = b.prevPageLimit1;
    }

    public static Builder builder() { return new Builder(); }

    public String getParam() { return param; }
    public String getParam2() { return param2; }
    /** True iff the legacy template should render typeInData1. */
    public boolean isParamProvided() { return !param.isEmpty(); }
    /** True iff the legacy template should also render typeInData2. */
    public boolean isParam2Provided() { return !param2.isEmpty(); }
    public String getKeyword() { return keyword; }
    public String getSearchMode() { return searchMode; }
    public String getOrderBy() { return orderBy; }
    public String getLimit1() { return limit1; }
    public String getLimit2() { return limit2; }
    public List<AddressEntry> getAddresses() { return addresses; }
    public boolean isShowNoResults() { return showNoResults; }
    public boolean isShowPrevPage() { return showPrevPage; }
    public boolean isShowNextPage() { return showNextPage; }
    public int getNextPageLimit1() { return nextPageLimit1; }
    public int getPrevPageLimit1() { return prevPageLimit1; }

    public static final class Builder {
        private String param;
        private String param2;
        private String keyword;
        private String searchMode;
        private String orderBy;
        private String limit1;
        private String limit2;
        private List<AddressEntry> addresses;
        private boolean showNoResults;
        private boolean showPrevPage;
        private boolean showNextPage;
        private int nextPageLimit1;
        private int prevPageLimit1;

        public Builder param(String v) { this.param = v; return this; }
        public Builder param2(String v) { this.param2 = v; return this; }
        public Builder keyword(String v) { this.keyword = v; return this; }
        public Builder searchMode(String v) { this.searchMode = v; return this; }
        public Builder orderBy(String v) { this.orderBy = v; return this; }
        public Builder limit1(String v) { this.limit1 = v; return this; }
        public Builder limit2(String v) { this.limit2 = v; return this; }
        public Builder addresses(List<AddressEntry> v) { this.addresses = v == null ? null : List.copyOf(v); return this; }
        public Builder showNoResults(boolean v) { this.showNoResults = v; return this; }
        public Builder showPrevPage(boolean v) { this.showPrevPage = v; return this; }
        public Builder showNextPage(boolean v) { this.showNextPage = v; return this; }
        public Builder nextPageLimit1(int v) { this.nextPageLimit1 = v; return this; }
        public Builder prevPageLimit1(int v) { this.prevPageLimit1 = v; return this; }

        public OnSearch3rdBillAddrViewModel build() { return new OnSearch3rdBillAddrViewModel(this); }
    }
}
