/**
 * Service tier for the Ontario billing module.
 *
 * <p>Houses domain operations that have <em>side effects</em> — the
 * complement of the read-side {@code billings.ca.on.assembler} package:</p>
 *
 * <ul>
 *   <li>Persistence mutations (DAO writes / merges / deletes)</li>
 *   <li>File I/O (OHIP claim file generation, MOH disk creation)</li>
 *   <li>Audit-trail emission ({@code BillActivity} rows)</li>
 *   <li>External-system invocation (HRM, MCEDT, EDT)</li>
 * </ul>
 *
 * <p>If a class only <em>reads</em> from DAOs to build a view model, it
 * belongs in {@code billings.ca.on.assembler}, not here.</p>
 *
 * <h2>Naming</h2>
 *
 * <p>Class names end in {@code *Service}. Methods are verb-phrase
 * imperatives (e.g. {@code generateReport}, {@code settle},
 * {@code regenerateDisk}) — not pseudo-getters. Each service is
 * stateless and instantiated on demand by the web tier.</p>
 *
 * @since 2026-04-26
 */
package io.github.carlos_emr.carlos.billings.ca.on.service;
