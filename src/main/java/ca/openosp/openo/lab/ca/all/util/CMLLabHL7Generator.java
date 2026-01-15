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
 * Utility class for generating CML (Calgary Medical Laboratory) format HL7 messages.
 *
 * @since 2026-01-12
 */
public class CMLLabHL7Generator {

	private CMLLabHL7Generator() {
		// Private constructor to prevent instantiation
	}

	/**
	 * Generates CML format HL7 message from Lab object.
	 *
	 * @param lab Lab object containing patient and test information
	 * @return String containing HL7 formatted message
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

	private static void buildMSH(StringBuilder sb) {
		String timestamp = currentDateTime();
		sb.append("MSH|^~\\&|CML|CML|OSCAR|OSCAR|").append(timestamp)
			.append("||ORU^R01|BAR").append(timestamp.substring(2, 14))
			.append("|P|2.3|||ER|AL\n");
	}

	private static void buildPID(StringBuilder sb, Lab lab) {
		String hin = safe(lab.getHin());
		sb.append("PID||||").append(hin)
			.append("|").append(safe(lab.getLastName())).append("^").append(safe(lab.getFirstName()))
			.append("||").append(formatDate(lab.getDob())).append("|").append(safe(lab.getSex()))
			.append("|||||").append(safe(lab.getPhone()))
			.append("||||||X").append(hin).append("\n");
	}

	private static void buildORC(StringBuilder sb, Lab lab) {
		String firstTestDate = formatDateTime(lab.getTests().get(0).getDate());
		sb.append("ORC|RE|").append(safe(lab.getAccession()))
			.append("|||F|||||||").append(safe(lab.getBillingNo()))
			.append("^").append(safe(lab.getProviderLastName()))
			.append("^").append(safe(lab.getProviderFirstName()))
			.append("|||").append(firstTestDate).append("\n");
	}

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

	private static void buildOBXSegments(StringBuilder sb, Lab lab) {
		int testNo = 1;
		for (LabTest test : lab.getTests()) {
			appendObxSegment(sb, test, testNo);
			appendNteIfPresent(sb, test);
			testNo++;
		}
	}

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

	private static void appendNteIfPresent(StringBuilder sb, LabTest test) {
		if (test.getNotes() != null && !test.getNotes().isEmpty()) {
			sb.append("NTE|1|L|NOTE: ").append(test.getNotes()).append("\n");
		}
	}
}
