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
package io.github.carlos_emr.carlos.utility;

import java.time.LocalDate;
import java.time.Period;
import java.util.Calendar;

/**
 * Utility class for calculating a patient's age from their date of birth.
 *
 * <p>Uses the Java Time API ({@link java.time.Period}) for accurate age calculation
 * that accounts for leap years and varying month lengths.
 *
 * @since 2026-03-17
 */
public class AgeCalculator {

    /**
     * Calculates the age in years, months, and days from the given birth date to today.
     *
     * @param birthDate Calendar the patient's date of birth
     * @return Age an object containing the calculated years, months, and days
     */
    public static Age calculateAge(Calendar birthDate) {
        LocalDate birthdate = LocalDate.of(
                birthDate.get(Calendar.YEAR),
                birthDate.get(Calendar.MONTH) + 1,
                birthDate.get(Calendar.DAY_OF_MONTH));
        LocalDate now = LocalDate.now();
        Period period = Period.between(birthdate, now);

        return new Age(period.getDays(), period.getMonths(), period.getYears());
    }
}
