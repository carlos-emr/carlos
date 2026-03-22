/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.managers.constants;

/**
 * Shared constants for the CARLOS EMR managers layer, primarily used by the
 * FormeCARES clinical decision support integration.
 *
 * <p>Defines enumeration constants for prevention types, measurement types,
 * problem fields, allergy fields, and medication fields used in structured
 * clinical data exchange.</p>
 *
 * @since 2026-03-17
 */
public class Constants {
    Constants() {
        throw new UnsupportedOperationException();
    }

    public static class Cares {

        // OSCAR fields
        public static enum Prevention {
            HepAB, HepA, HepB, Td, Pneumovax, PneuC, Smoking, Flu, RZV, HZV, Zoster,
        }

        public static enum Measurement {
            EFI, EGFR, CRCL, id, date, score
        }

        public static enum Problem {
            problems, id, startdate, code, description, active
        }

        public static enum Medication {
            medications, id, rxDate, prescription, active
        }

        public static enum Tickler {
            tickler, saved, id, priorities, textSuggestions, providers, ticklerCategories, message, comments,
            categoryId, taskAssignedTo, priority, serviceDate, serviceTime, demographicNo, TicklerNote
        }

        // eCARES form fields
        public static enum FormField {
            // preventions
            zoster, influenza, pneumococcal, tetanus_and_diphtheria, hep_a, hep_b, smoke,

            // patient
            demographicNo, demographic_no, patientFirstName, patientLastName, patientPHN, patientDOB, patientGender, patientAge,

            // OSCAR USER
            userFirstName, userLastName, userFullName, userSignature,

            // data
            saved, formData, formId, deficit_based_frailty_score, efi_scores, crcl, ticklerId, tickler, completed,
            incompleteFormExists
        }
    }

}
