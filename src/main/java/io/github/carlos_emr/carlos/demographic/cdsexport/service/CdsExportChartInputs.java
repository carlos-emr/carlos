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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

/**
 * Whether the chart being built on this thread read anything its capture does not hold.
 *
 * <p>A finished chart may be adopted by a later attempt only when it is a function of its capture
 * and of the attempt's build parameters ({@link CdsExportChartFingerprints}). The printed forms are
 * not: a consultation letter and a Rourke chart print the clinic letterhead, the specialist
 * directory, provider names and billing numbers, program names, the configured logo file and
 * clinic settings as they are when the form is printed ({@link
 * CdsExportConsultationLetterComposer}, {@link CdsExportRourkeChartComposer}). Each printer marks
 * the chart here, so a chart with a printed form is never adopted: it is built again, as a fresh
 * attempt would build it.
 *
 * @since 2026-11-14
 */
final class CdsExportChartInputs {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private CdsExportChartInputs() {}

    /** Records one chart's build on this thread until the scope is closed. */
    static Scope open() {
        var scope = new Scope(CURRENT.get());
        CURRENT.set(scope);
        return scope;
    }

    /** The chart being built read an input its capture does not hold. */
    static void uncaptured() {
        var scope = CURRENT.get();
        if (scope != null) scope.uncaptured = true;
    }

    /** One chart's build. */
    static final class Scope implements AutoCloseable {
        private final Scope outer;
        private boolean uncaptured, closed;

        private Scope(Scope outer) {
            this.outer = outer;
        }

        /** True when the build read nothing but its capture. */
        boolean reusable() {
            return !uncaptured;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (outer == null) CURRENT.remove();
            else CURRENT.set(outer);
        }
    }
}
