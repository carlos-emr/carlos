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
package io.github.carlos_emr.carlos.billings.ca.on.validator;

import java.util.List;

/**
 * Generate OHIP File was refused before any disk was allocated because a
 * provider of the run has an OHIP billing number or specialty code that does
 * not fill its batch-header field (see
 * {@link io.github.carlos_emr.carlos.billings.ca.on.support.OhipProviderFields}).
 *
 * <p>Each problem is a fixed sentence naming the provider number and the
 * field. The stored values are not repeated, so the problems can be shown to
 * the billing user as they are.</p>
 *
 * @since 2026-10-15
 */
public class InvalidOhipProviderFieldsException extends BillingValidationException {
    private static final long serialVersionUID = 1L;

    private final List<String> problems;

    public InvalidOhipProviderFieldsException(List<String> problems) {
        super("A provider's OHIP billing number or specialty code does not fit the OHIP batch header.");
        this.problems = List.copyOf(problems);
    }

    /** One sentence per provider field to correct, in run order. */
    public List<String> getProblems() {
        return problems;
    }
}
