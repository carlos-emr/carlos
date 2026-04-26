/**
 * Assembler tier for the Ontario billing module.
 *
 * <p>This package builds {@code *ViewModel} instances from request parameters
 * and DAO lookups. The web tier ({@code billings.ca.on.web}) calls into
 * exactly one assembler per page; the assembler does the read-side work and
 * returns an immutable view model that the JSP renders via EL.</p>
 *
 * <h2>Naming contract</h2>
 *
 * Three suffixes are reserved with strict semantics. Pick the one that
 * matches the component's role:
 *
 * <dl>
 *   <dt>{@code *Assembler} (public final)</dt>
 *   <dd>Top-level orchestrator. Builds <em>one</em> {@code *ViewModel} from
 *       primitive inputs (request, model, DAOs). Pure read; no side effects.
 *       Constructed by web-tier actions; {@code public} surface = no-arg
 *       production ctor + {@code assemble(...)} method. Mocking ctor is
 *       package-private.</dd>
 *
 *   <dt>{@code *Composer} / {@code *Loader} (package-private final)</dt>
 *   <dd><em>Reusable</em> inner step shared by ≥2 assemblers. Mutates a
 *       supplied {@code Builder} or returns a partial result. Owned by the
 *       assemblers that consume it; never instantiated from {@code web/}.
 *       Example: {@link BillingONFormSiteContextComposer} — used by both
 *       {@link BillingONFormDataAssembler} and the legacy correction flow.</dd>
 *
 *   <dt>{@code *Step} (package-private final)</dt>
 *   <dd>Inner step of <em>one</em> assembler that's been extracted to its
 *       own file purely for size/readability — not for reuse. The suffix
 *       signals "implementation detail of one assembler" so future readers
 *       don't mistake it for a sharing seam.</dd>
 * </dl>
 *
 * <p>For side effects (DAO writes, file I/O, audit, mutation), see the
 * sibling {@code billings.ca.on.service} package — those are
 * {@code *Service} classes, not assemblers.</p>
 *
 * <h2>Anti-pattern guard rails</h2>
 *
 * <ul>
 *   <li>Don't extract a {@code *Step} unless its parent assembler is
 *       genuinely too long without it. The empty default is "inline as a
 *       private method on the assembler".</li>
 *   <li>Don't extract a {@code *Composer}/{@code *Loader} for a single
 *       caller. Wait until a second assembler actually needs the same step.
 *       Premature reuse abstractions hide intent.</li>
 *   <li>Side-effect logic does not belong in this package. If a class
 *       writes to the database, writes a file, or sends a notification,
 *       it's a {@code *Service} and lives next door.</li>
 * </ul>
 *
 * @since 2026-04-26
 */
package io.github.carlos_emr.carlos.billings.ca.on.assembler;
