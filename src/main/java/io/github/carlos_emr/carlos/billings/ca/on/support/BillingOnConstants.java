/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
package io.github.carlos_emr.carlos.billings.ca.on.support;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Legacy Ontario billing constants and lookup tables shared by disk, claim,
 * save, and correction flows.
 */

public final class BillingOnConstants {
    public static final int FIELD_SERVICE_NUM = 10;
    public static final int FIELD_MAX_SERVICE_NUM = 20;

    public static final String BATCHHEADER_TRANSACTIONIDENTIFIER = "HE";
    public static final String BATCHHEADER_REORDIDENTIFICATION = "B";
    public static final String BATCHHEADER_SPECID = "V03";
    public static final String CLAIMHEADER1_TRANSACTIONIDENTIFIER = "HE";
    public static final String CLAIMHEADER1_REORDIDENTIFICATION = "H";
    public static final String CLAIMHEADER1_PAYMENTPROGRAM_PRIVATE = "PAT";
    public static final String CLAIMHEADER1_PAYEE = "P";

    public static final String ITEM_TRANSACTIONIDENTIFIER = "HE";
    public static final String ITEM_REORDIDENTIFICATION = "T";

    public static final String BILLINGFILE_STATUS_UNCERT = "U";
    public static final String BILLINGFILE_STATUS_DELETED = "D";
    public static final String BILLINGFILE_STATUS_BILLED = "B";

    public static final String BILLINGACTION_CREATE = "create";
    public static final String BILLINGACTION_UPDATE = "update";

    public static final String BILLINGMATCHSTRING_3RDPARTY = "PAT|OCF|ODS|CPP|STD|IFH";

    // UH: update billing_on_cheader1, refer to issue#233 https://github.com/oscaremr/oscar/issues/233
    public enum ACTION_TYPE {C, R, U, D, UH}


    /**
     * Properties-compatible lookup table sealed after static initialization.
     * Existing callers can keep using {@link Properties#getProperty(String)},
     * but mutation of the shared constants is rejected.
     */
    private static final class FrozenProperties extends Properties {
        private static final long serialVersionUID = 1L;
        private boolean sealed = false;

        private void buildEntry(String key, String value) {
            super.put(key, value);
        }

        private void seal() {
            this.sealed = true;
        }

        private static UnsupportedOperationException immutable() {
            return new UnsupportedOperationException(
                    "BillingOnConstants lookup tables are immutable");
        }

        @Override
        public synchronized Object setProperty(String key, String value) {
            throw immutable();
        }

        @Override
        public synchronized Object put(Object key, Object value) {
            if (sealed) {
                throw immutable();
            }
            return super.put(key, value);
        }

        @Override
        public synchronized Object remove(Object key) {
            throw immutable();
        }

        @Override
        public synchronized boolean remove(Object key, Object value) {
            throw immutable();
        }

        @Override
        public synchronized void clear() {
            throw immutable();
        }

        @Override
        public synchronized void putAll(java.util.Map<?, ?> t) {
            throw immutable();
        }

        @Override
        public synchronized Object putIfAbsent(Object key, Object value) {
            throw immutable();
        }

        @Override
        public synchronized Object replace(Object key, Object value) {
            throw immutable();
        }

        @Override
        public synchronized boolean replace(Object key, Object oldValue, Object newValue) {
            throw immutable();
        }

        @Override
        public synchronized void replaceAll(
                BiFunction<? super Object, ? super Object, ? extends Object> function) {
            throw immutable();
        }

        @Override
        public synchronized Object computeIfAbsent(
                Object key, Function<? super Object, ? extends Object> mappingFunction) {
            throw immutable();
        }

        @Override
        public synchronized Object computeIfPresent(
                Object key, BiFunction<? super Object, ? super Object, ? extends Object> remappingFunction) {
            throw immutable();
        }

        @Override
        public synchronized Object compute(
                Object key, BiFunction<? super Object, ? super Object, ? extends Object> remappingFunction) {
            throw immutable();
        }

        @Override
        public synchronized Object merge(
                Object key, Object value, BiFunction<? super Object, ? super Object, ? extends Object> remappingFunction) {
            throw immutable();
        }

        @Override
        public synchronized Set<Map.Entry<Object, Object>> entrySet() {
            Set<Map.Entry<Object, Object>> entries = new HashSet<>();
            for (Map.Entry<Object, Object> entry : super.entrySet()) {
                entries.add(new AbstractMap.SimpleImmutableEntry<>(entry));
            }
            return Collections.unmodifiableSet(entries);
        }

        @Override
        public synchronized Set<Object> keySet() {
            return Collections.unmodifiableSet(new HashSet<>(super.keySet()));
        }

        @Override
        public synchronized Collection<Object> values() {
            return Collections.unmodifiableList(new ArrayList<>(super.values()));
        }
    }

    public static final Properties propMonthCode = new FrozenProperties();
    public static final Properties propBillingCenter = new FrozenProperties();
    public static final Properties propBillingType = new FrozenProperties();
    public static final List<String> vecPaymentType = Collections.unmodifiableList(Arrays.asList(
            "HCP",
            "Bill OHIP",
            "RMB",
            "Reciprocal Medical Billing",
            "WCB",
            "Worker's Compensation Board",
            "PAT",
            "Bill Patient",
            "OCF",
            "OCF",
            "ODS",
            "ODS",
            "CPP",
            "Canada Pension Plan",
            "STD",
            "Short/Long Term Disability",
            "IFH",
            "Interm Federal Health",
            "NOT",
            "Do Not Bill"
    ));

    static {
        FrozenProperties pmc = (FrozenProperties) propMonthCode;
        pmc.buildEntry("1", "A");
        pmc.buildEntry("2", "B");
        pmc.buildEntry("3", "C");
        pmc.buildEntry("4", "D");
        pmc.buildEntry("5", "E");
        pmc.buildEntry("6", "F");
        pmc.buildEntry("7", "G");
        pmc.buildEntry("8", "H");
        pmc.buildEntry("9", "I");
        pmc.buildEntry("10", "J");
        pmc.buildEntry("11", "K");
        pmc.buildEntry("12", "L");
        pmc.seal();

        FrozenProperties pbc = (FrozenProperties) propBillingCenter;
        pbc.buildEntry("G", "Hamilton");
        pbc.buildEntry("J", "Kingston");
        pbc.buildEntry("P", "London");
        pbc.buildEntry("E", "Mississauga");
        pbc.buildEntry("F", "Oshawa");
        pbc.buildEntry("D", "Ottawa");
        pbc.buildEntry("R", "Sudbury");
        pbc.buildEntry("U", "Thunder Bay");
        pbc.buildEntry("N", "Toronto");
        pbc.seal();

        FrozenProperties pbt = (FrozenProperties) propBillingType;
        pbt.buildEntry("O", "Bill OHIP");
        pbt.buildEntry("B", "Submitted OHIP");
        pbt.buildEntry("N", "Do Not Bill");
        pbt.buildEntry("P", "Bill Patient");
        pbt.buildEntry("W", "Bill WCB");
        pbt.buildEntry("H", "Capitated");
        pbt.buildEntry("S", "Settled");
        pbt.buildEntry("D", "Deleted");
        pbt.buildEntry("X", "Bad Debt");
        pbt.seal();
    }
}
