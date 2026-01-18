//CHECKSTYLE:OFF
/**
 * Copyright (c) 2026. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2026.
 */

package ca.openosp.openo.lab.ca.all.util;

import ca.openosp.openo.commn.model.Lab;
import ca.openosp.openo.commn.model.LabTest;

import static ca.openosp.openo.lab.ca.all.util.Hl7GeneratorUtil.*;

/**
 * Utility class for generating CML format HL7 messages.
 *
 * @since 2026-01-12
 */
public class CMLLabHL7Generator {

	/**
	 * Prevents instantiation of this utility class.
	 */
	private CMLLabHL7Generator() {
		// Private constructor to prevent instantiation
	}

	/**
	 * Builds a complete CML HL7 message from the provided Lab data.
	 *
	 * The message includes MSH, PID, ORC, OBR and OBX (with optional NTE) segments constructed from
	 * the patient, order and test information contained in the Lab object.
	 *
	 * @param lab the Lab instance containing patient demographics, ordering information, and tests
	 * @return the assembled HL7 message as a string
	 */
	public static String generate(Lab lab) {
		StringBuilder sb = new StringBuilder();

		buildMSH(sb);
		buildPID(sb, lab);
		if (lab.getTests() != null && !lab.getTests().isEmpty()) {
			buildORC(sb, lab);
			buildOBR(sb, lab);
			buildOBXSegments(sb, lab);
		}

		return sb.toString();
	}

	/**
	 * Appends a CML-format HL7 MSH header segment to the provided StringBuilder.
	 *
	 * The segment includes sending/receiving application and facility, message datetime,
	 * message type (ORU^R01), a control ID derived from the timestamp, and HL7 version 2.3 fields.
	 *
	 * @param sb the StringBuilder to append the MSH segment to
	 */
	private static void buildMSH(StringBuilder sb) {
		String timestamp = currentDateTime();
		sb.append("MSH|^~\\&|CML|CML|OSCAR|OSCAR|").append(timestamp)
			.append("||ORU^R01|BAR").append(timestamp.substring(2, 14))
			.append("|P|2.3|||ER|AL\n");
	}

	/**
	 * Appends a PID (Patient Identification) HL7 segment for the given Lab's patient to the provided StringBuilder.
	 *
	 * The segment includes the patient's HIN, last and first name, date of birth, sex, phone number,
	 * and an accession-based identifier placed at the segment end.
	 *
	 * @param sb  the StringBuilder to which the PID segment will be appended
	 * @param lab the Lab containing patient demographic fields used to populate the PID segment
	 */
	private static void buildPID(StringBuilder sb, Lab lab) {
		String hin = safe(lab.getHin());
		sb.append("PID||||").append(hin)
			.append("|").append(safe(lab.getLastName())).append("^").append(safe(lab.getFirstName()))
			.append("||").append(formatDate(lab.getDob())).append("|").append(safe(lab.getSex()))
			.append("|||||").append(safe(lab.getPhone()))
			.append("||||||X").append(hin).append("\n");
	}

	/**
	 * Appends an ORC (Common Order) segment to the provided StringBuilder.
	 *
	 * The segment contains the accession, billing number, ordering provider name
	 * (last name ^ first name) and the order date taken from the first test;
	 * the segment is terminated with a newline.
	 *
	 * @param sb  the StringBuilder to which the ORC segment will be appended
	 * @param lab the Lab whose accession, billing, provider and first-test date are used
	 */
	private static void buildORC(StringBuilder sb, Lab lab) {
		String firstTestDate = formatDateTime(lab.getTests().get(0).getDate());
		sb.append("ORC|RE|").append(safe(lab.getAccession()))
			.append("|||F|||||||").append(safe(lab.getBillingNo()))
			.append("^").append(safe(lab.getProviderLastName()))
			.append("^").append(safe(lab.getProviderFirstName()))
			.append("|||").append(firstTestDate).append("\n");
	}

	/**
	 * Builds and appends the OBR (Observation Request) segment for the given lab to the provided StringBuilder.
	 *
	 * The segment includes the lab request date, the first test date (from the first test in lab.getTests()),
	 * billing number, ordering provider names, and the compiled CC doctor string. The completed OBR line
	 * is appended to `sb` and terminated with a newline.
	 *
	 * @param sb  the StringBuilder to which the OBR segment will be appended
	 * @param lab the Lab object providing labReqDate, the first test's date, billingNo, provider names, and CC doctors
	 */
	private static void buildOBR(StringBuilder sb, Lab lab) {
		String labReqDate = formatDateTime(lab.getLabReqDate());
		String firstTestDate = formatDateTime(lab.getTests().get(0).getDate());

		StringBuilder ccString = parseCCDoctors(lab.getCc());

		sb.append("OBR|1|||UR^General Lab^L1^GENERAL LAB||")
			.append(labReqDate).append("|")
			.append(firstTestDate).append("|||||||")
			.append(labReqDate).append("||")
			.append(safe(lab.getBillingNo())).append("^")
			.append(safe(lab.getProviderLastName())).append("^")
			.append(safe(lab.getProviderFirstName()))
			.append("||||||").append(firstTestDate)
			.append("||LAB|F|||").append(ccString).append("\n");
	}

	/**
	 * Appends OBX observation segments and associated NTE note segments for each test in the given Lab to the provided StringBuilder.
	 *
	 * @param sb  the StringBuilder to append HL7 OBX and NTE segments to
	 * @param lab the Lab whose tests will be converted into OBX (and optional NTE) segments
	 */
	private static void buildOBXSegments(StringBuilder sb, Lab lab) {
		int testNo = 1;
		for (LabTest test : lab.getTests()) {
			appendObxSegment(sb, test, testNo);
			appendNteIfPresent(sb, test);
			testNo++;
		}
	}

	/**
	 * Appends an OBX segment for a single lab test to the provided StringBuilder.
	 *
	 * The appended segment encodes the test's identifying code, name, description,
	 * result value, units, reference range, flag, status, blocked status, and test date/time.
	 *
	 * @param sb     the StringBuilder to receive the OBX segment
	 * @param test   the LabTest whose fields will be encoded into the OBX segment
	 * @param testNo the sequential OBX set ID for this test (e.g., 1 for the first test)
	 */
	private static void appendObxSegment(StringBuilder sb, LabTest test, int testNo) {
		String refRange = buildReferenceRange(test);
		sb.append("OBX|").append(testNo).append("|")
		.append(safeWithDefault(test.getCodeType(), "FT")).append("|")
		.append(safe(test.getCode())).append("^")
		.append(safe(test.getName())).append("^")
		.append(safe(test.getDescription()))
		.append("|GENERAL|")
		.append(safe(test.getCodeValue())).append("|")
		.append(safe(test.getCodeUnit())).append("|")
		.append(refRange).append("|")
		.append(safe(test.getFlag())).append("|||")
		.append(safeWithDefault(test.getStat(), "F")).append("||")
		.append(getBlockedStatus(test))
		.append("|").append(formatDateTime(test.getDate())).append("\n");
	}

	/**
	 * Appends an HL7 NTE segment for the given test when the test contains notes.
	 *
	 * The appended segment has the format: `NTE|1|L|NOTE: <notes>\n`.
	 *
	 * @param sb   the StringBuilder to append the NTE segment to
	 * @param test the LabTest whose notes will be added if present and non-empty
	 */
	private static void appendNteIfPresent(StringBuilder sb, LabTest test) {
		if (test.getNotes() != null && !test.getNotes().isEmpty()) {
			sb.append("NTE|1|L|NOTE: ").append(test.getNotes()).append("\n");
		}
	}
}