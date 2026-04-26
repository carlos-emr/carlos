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
