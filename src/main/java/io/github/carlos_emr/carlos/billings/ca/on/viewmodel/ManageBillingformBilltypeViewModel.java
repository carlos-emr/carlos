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
 * Immutable view model for {@code manageBillingform_billtype.jsp}, the
 * fragment loaded via {@code fetch()} from the parent
 * {@code manageBillingform.jsp} when the user clicks "Manage Billing Form".
 *
 * <p>Captures the three values the legacy scriptlet computed:
 * the service-type id, the service-type display name, and the current
 * default bill type (one of {@code no/ODP/WCB/NOT/IFH/PAT/OCF/ODS/CPP/STD},
 * defaulting to {@code "no"} if no row exists in
 * {@code ctl_billing_type}).</p>
 *
 * @since 2026-04-25
 */
public final class ManageBillingformBilltypeViewModel {

    private final String typeId;
    private final String typeName;
    private final String billType;

    public ManageBillingformBilltypeViewModel(String typeId, String typeName, String billType) {
        this.typeId = typeId == null ? "" : typeId;
        this.typeName = typeName == null ? "" : typeName;
        this.billType = billType == null ? "no" : billType;
    }

    public String getTypeId() { return typeId; }
    public String getTypeName() { return typeName; }
    public String getBillType() { return billType; }
}
