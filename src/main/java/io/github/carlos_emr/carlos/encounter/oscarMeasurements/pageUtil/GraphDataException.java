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
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

/**
 * A stored value a graph cannot be drawn from, with what is wrong and which values it concerns (#5853), so the
 * graph window can tell the clinician in plain words.
 *
 * <p>It is an {@link IllegalArgumentException}, so it keeps the 422 status that
 * {@link io.github.carlos_emr.carlos.eform.util.ChartRenderFailure#status} gives invalid data. Its message is fixed
 * text and its subject is the measurement type's or lab test's name, never a stored value, so neither carries a
 * patient's results into a log.</p>
 *
 * @since 2026-11-12
 */
final class GraphDataException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    /** What is wrong with the stored values, each with the message the graph window shows. */
    enum Problem {
        BLOOD_PRESSURE_FORMAT("clinical.graph.invalidData.bloodPressure",
                "This graph cannot be drawn: some stored {0} values are not in the form 120/80."),
        NO_DATE("clinical.graph.invalidData.noDate",
                "This graph cannot be drawn: a stored {0} value has no date."),
        UNREADABLE_DATE("clinical.graph.invalidData.unreadableDate",
                "This graph cannot be drawn: a stored {0} result has a date that cannot be read."),
        REFERENCE_RANGE("clinical.graph.invalidData.referenceRange",
                "This graph cannot be drawn: a stored reference range for {0} cannot be read."),
        PRESCRIPTION_START("clinical.graph.invalidData.prescriptionStart",
                "The medication graph cannot be drawn: a prescription has no start date.");

        private final String key;
        private final String english;

        Problem(String key, String english) {
            this.key = key;
            this.english = english;
        }

        String key() {
            return key;
        }

        /** The English message, used when the viewer's bundle lacks the key. */
        String english() {
            return english;
        }
    }

    private final Problem problem;
    private final String subject;

    /**
     * @param problem what is wrong
     * @param subject the measurement type's or lab test's name the problem concerns, or {@code null}
     * @param message fixed diagnostic text, never a stored value
     */
    GraphDataException(Problem problem, String subject, String message) {
        super(message);
        this.problem = problem;
        this.subject = subject;
    }

    /** As above, keeping the parse failure that found the problem. */
    GraphDataException(Problem problem, String subject, String message, Throwable cause) {
        super(message, cause);
        this.problem = problem;
        this.subject = subject;
    }

    Problem problem() {
        return problem;
    }

    /** The measurement type's or lab test's name, or {@code null} when the graph does not know it. */
    String subject() {
        return subject;
    }
}
