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
/**
 * Input-validation tier for the Ontario billing module.
 *
 * <p>Houses {@code *Validator} classes that inspect HTTP request input
 * and emit structured {@code Result(messages, ...)} records the action
 * layer translates to action errors. Distinct from
 * {@code billings.ca.on.assembler} (which builds view models from
 * already-validated input) and {@code billings.ca.on.service} (which
 * mutates state on validated input).</p>
 *
 * <p>Companion exceptions ({@code BillingValidationException}) live here
 * so the validation contract stays in one package.</p>
 *
 * <h2>Naming</h2>
 *
 * <p>Class names end in {@code *Validator}. Methods are verb-phrase
 * imperatives (e.g. {@code validate}, {@code persistIfRequested}) and
 * return value-class results — never throw on validation failure
 * (callers compose multiple validators), only on programmer-error
 * preconditions.</p>
 *
 * @since 2026-04-26
 */
package io.github.carlos_emr.carlos.billings.ca.on.validator;
