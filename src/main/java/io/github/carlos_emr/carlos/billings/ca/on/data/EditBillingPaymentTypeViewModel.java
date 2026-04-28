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

/**
 * Immutable view model for {@code editBillingPaymentType.jsp}.
 *
 * <p>The JSP has two modes — create vs. modify — selected by whether
 * {@code id} and {@code type} are present on the request. Both flow through
 * the same form; the model captures the per-mode title, AJAX method name,
 * and the round-tripped id / current payment-type value.</p>
 *
 * @since 2026-04-25
 */
public final class EditBillingPaymentTypeViewModel {

    private final boolean modify;
    private final String id;
    private final String type;
    private final String title;
    private final String method;

    public EditBillingPaymentTypeViewModel(boolean modify, String id, String type,
                                           String title, String method) {
        this.modify = modify;
        this.id = id == null ? "" : id;
        this.type = type == null ? "" : type;
        this.title = title == null ? "" : title;
        this.method = method == null ? "" : method;
    }

    public boolean isModify() { return modify; }
    public String getId() { return id; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getMethod() { return method; }
}
