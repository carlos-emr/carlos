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

import java.time.LocalDateTime;

import ca.openosp.openo.commn.model.Lab;
import ca.openosp.openo.commn.model.LabTest;

import static ca.openosp.openo.lab.ca.all.util.Hl7GeneratorUtil.*;

/**
 * Utility class for generating MDS (Medical Data Systems) format HL7 messages.
 *
 * @since 2026-01-12
 */
public class MDSLabHL7Generator {

	private MDSLabHL7Generator() {
		// Private constructor to prevent instantiation
	}

	/**
	 * Generates MDS format HL7 message from Lab object.
	 *
	 * @param lab Lab object containing patient and test information
	 * @return String containing HL7 formatted message
	 */
	public static String generate(Lab lab) {
		StringBuilder sb = new StringBuilder();

		LocalDateTime now = LocalDateTime.now();
		String nowDateTime = now.format(DATE_TIME_FORMAT);
		String nowYear = now.format(YEAR_FORMAT);

		// Clean accession - remove any year prefix AND any internal dashes to prevent MSH-10-1 parsing issues
		String accession = normalizeAccession(lab.getAccession());
		String billingNo = safeWithDefault(lab.getBillingNo(), "00000");
		String providerFullName = buildProviderFullName(lab);

		buildMSH(sb, billingNo, accession, nowDateTime);
		buildZLB(sb, accession);
		buildZRG(sb, lab);
		buildZMNSegments(sb, lab);
		buildZCLSegments(sb, lab, billingNo, providerFullName);
		buildPID(sb, lab, accession, nowYear);
		buildPV1(sb, lab, billingNo, providerFullName);
		buildZFR(sb);
		buildZCT(sb, accession);
		buildTestSegments(sb, lab, accession);
		buildZPD(sb, lab);

		return sb.toString();
	}

	// MSH segment - billing-accession-1 format
	private static void buildMSH(StringBuilder sb, String billingNo, String accession, String nowDateTime) {
		sb.append("MSH|^~\\&|MDS||||").append(nowDateTime).append("||ORU|")
			.append(billingNo).append("-").append(accession).append("-1")
			.append("|P^|2.3.0|||NE|ER\n");
	}

	// ZLB segment - Lab facility information (required for MDS)
	// Format: ZLB||version||address|unknown|unknown|lab_code|lab_name
	private static void buildZLB(StringBuilder sb, String accession) {
		sb.append("ZLB||").append(accession.length() > 2 ? accession.substring(0, 2) : "LA")
			.append("||55 QUEEN ST TORONTO M5C 1R6 1(877)849-3637|30||MDS|MDS\n");
	}

	// ZRG segments - Test group registration (one per test group)
	// Format: ZRG|sequence|groupNum|||groupName|display|
	// For now, put all tests in one group
	private static void buildZRG(StringBuilder sb, Lab lab) {
		if (!lab.getTests().isEmpty()) {
			sb.append("ZRG|1.1|1000|||GENERAL LABORATORY|1|\n");
		}
	}

	// ZMN segments - Test menu definitions (one per test)
	// Format: ZMN||shortName||displayName|units|value|range|loincCode|flag|groupNum
	private static void buildZMNSegments(StringBuilder sb, Lab lab) {
		int testIndex = 1;
		for (LabTest test : lab.getTests()) {
			String testCode = safeWithDefault(test.getCode(), String.valueOf(testIndex * 100));
			String testName = safeWithDefault(test.getName(), "TEST");
			String units = safe(test.getCodeUnit());
			String value = safe(test.getCodeValue());
			String refRange = buildMDSReferenceRange(test);
			String flag = safe(test.getFlag());

			sb.append("ZMN||").append(testName.toUpperCase().replaceAll("\\s+", ""))
				.append("||").append(testName)
				.append("|").append(units)
				.append("|").append(value)
				.append("|").append(refRange)
				.append("|").append(testCode)
				.append("|").append(flag)
				.append("|1000\n"); // Group 1000 matches ZRG above

			testIndex++;
		}
	}

	// ZCL segment - ordering provider (Field 2 has billing with dash prefix, then full name)
	// If CC exists, add additional ZCL segments
	// Supports multiple formats:
	// 1. "billingNo,LastName,FirstName" (proper format)
	// 2. "Dr FullName" or just "FullName" (fallback - uses billing 00000)
	private static void buildZCLSegments(StringBuilder sb, Lab lab, String billingNo, String providerFullName) {
		appendZclForDoctor(sb, billingNo, providerFullName, /*ccFlag*/ "0");

		if (lab.getCc() != null && !lab.getCc().isEmpty()) {
			String[] ccDocs = lab.getCc().split(";");
			for (String ccDoc : ccDocs) {
				ccDoc = ccDoc.trim();
				if (!ccDoc.isEmpty()) {
					CCDoctor doctor = parseCCDoctor(ccDoc);
					appendZclForDoctor(sb, doctor.billing, doctor.name, /*ccFlag*/ "1");
				}
			}
		}
	}

	// PID segment - year-accession format
	private static void buildPID(StringBuilder sb, Lab lab, String accession, String nowYear) {
		sb.append("PID|||").append(nowYear).append("-").append(accession)
			.append("|-|").append(safeUpper(lab.getLastName())).append("^")
			.append(safeUpper(lab.getFirstName())).append("^||")
			.append(formatDob(lab.getDob())).append("|").append(safe(lab.getSex()))
			.append("|||||").append(safe(lab.getPhone()))
			.append("||||||X").append(safe(lab.getHin())).append("\n");
	}

	// PV1 segment - Field 8 (referring), Field 9 (CC if exists), Field 17 (admitting)
	// Field 9 - CC doctor if provided (use first CC doctor)
	// Supports both "billingNo,LastName,FirstName" and "Dr FullName" formats
	private static void buildPV1(StringBuilder sb, Lab lab, String billingNo, String providerFullName) {
		sb.append("PV1||R|^^^^^^^^|||||-").append(billingNo).append("^")
			.append(providerFullName).append("^^^^DR.|");

		if (lab.getCc() != null && !lab.getCc().isEmpty()) {
			String firstCc = lab.getCc().split(";")[0].trim();
			if (!firstCc.isEmpty()) {
				CCDoctor doctor = parseCCDoctor(firstCc);
				sb.append("-").append(doctor.billing).append("^").append(doctor.name).append("^^^^DR.^^^^^^^-1");
			}
		}

		sb.append("||||||||-").append(billingNo).append("^")
			.append(providerFullName).append("^^^^DR.^^^^^^^-0||||||||||||||||||||||||1|||")
			.append(formatDate(lab.getLabReqDate())).append("\n");
	}

	// ZFR segment
	private static void buildZFR(StringBuilder sb) {
		sb.append("ZFR||1|1|||0|1\n");
	}

	// Initial ZCT segment - just accession without year
	private static void buildZCT(StringBuilder sb, String accession) {
		sb.append("ZCT||").append(accession).append("||").append(accession).append("|||\n");
	}

	// OBR and OBX segments for each test
	// OBX segment - Result in field 5
	// Format: OBX|setId|valueType|identifier|subId|value|units|refRange|abnormalFlags|||resultStatus||blocked|||performingOrg
	// For MDS format: Use field type "L" (not "MC") for simple text comments
	// Field 3 format: ^text where component 2 contains the comment (MDSHandler reads NTE-3-2)
	private static void buildTestSegments(StringBuilder sb, Lab lab, String accession) {
		int obrCounter = 1;
		for (LabTest test : lab.getTests()) {
			String testDate = formatDateTime(test.getDate());
			String testCode = safeWithDefault(test.getCode(), String.valueOf(obrCounter * 100));

			// Build reference range
			String refRange = buildMDSReferenceRange(test);

			appendObr(sb, obrCounter, testCode, testDate);
			appendObx(sb, test, testCode, refRange);
			appendNotesNte(sb, test);
			appendZctForAccession(sb, accession);

			obrCounter++;
		}
	}

	// OBR segment
	private static void appendObr(StringBuilder sb, int obrCounter, String testCode, String testDate) {
		sb.append("OBR||").append(100 + obrCounter).append("||")
			.append(testCode).append("|||")
			.append(testDate).append("|||||||").append(testDate)
			.append("||||||MDS^MDS|||||||R\n");
	}

	// OBX segment
	private static void appendObx(StringBuilder sb, LabTest test, String testCode, String refRange) {
		sb.append("OBX|1|")
			.append(safeWithDefault(test.getCodeType(), "ST")).append("|-")
			.append(testCode).append("^")
			.append(safeUpper(test.getName(), "TEST"))
			.append("^L|")
			.append(testCode).append("-1-").append(testCode)
			.append("|")
			.append(safe(test.getCodeValue()))
			.append("|").append(safe(test.getCodeUnit()))
			.append("|").append(refRange)
			.append("|").append(safe(test.getFlag()))
			.append("|||").append(safeWithDefault(test.getStat(), "F"))
			.append("||").append(getBlockedStatus(test))
			.append("|||10^100 INTERNATIONAL BLVD TORONTO M9W 6J6 1(877)849-3637^L\n");
	}

	// Add NTE if notes exist
	private static void appendNotesNte(StringBuilder sb, LabTest test) {
		if (test.getNotes() != null && !test.getNotes().isEmpty()) {
			sb.append("NTE||L|^").append(test.getNotes()).append("\n");
		}
	}

	// Add ZCT segment after each test
	private static void appendZctForAccession(StringBuilder sb, String accession) {
		sb.append("ZCT||").append(accession).append("||").append(accession).append("|||\n");
	}

	// Add ZPD segment if any test is BLOCKED
	// MDSHandler checks for ZPD segment with field 3 = "Y" to indicate blocked status
	private static void buildZPD(StringBuilder sb, Lab lab) {
		boolean hasBlockedTest = lab.getTests().stream()
			.anyMatch(t -> "BLOCKED".equals(t.getBlocked()));
		if (hasBlockedTest) {
			sb.append("ZPD|||Y|\n");
		}
	}

	private static String normalizeAccession(String accession) {
		String result = safeWithDefault(accession, "LAB" + System.currentTimeMillis());
		result = result.replaceAll("^\\d{4}-", ""); // Remove year prefix if exists
		result = result.replaceAll("-", ""); // Remove all dashes to prevent parsing issues in MSH-10-1
		return result;
	}

	private static String buildProviderFullName(Lab lab) {
		String fullName = (lab.getProviderLastName() != null ? lab.getProviderLastName().toUpperCase() : "") +
		                  " " +
		                  (lab.getProviderFirstName() != null ? lab.getProviderFirstName().toUpperCase() : "");
		return fullName.trim();
	}

	// Try to parse as "billingNo,LastName,FirstName"
	// Fallback: just a name like "Dr Adward" or "John Smith"
	private static CCDoctor parseCCDoctor(String ccDoc) {
		String ccBilling;
		String ccName;

		String[] ccParts = ccDoc.split(",");
		if (ccParts.length >= 2) {
			// Proper format with billing number
			ccBilling = ccParts[0].trim();
			ccName = ccParts[1].trim().toUpperCase();
			if (ccParts.length >= 3) {
				ccName += " " + ccParts[2].trim().toUpperCase();
			}
		} else {
			// Fallback: just a name like "Dr Adward" or "John Smith"
			ccBilling = "00000";
			ccName = ccDoc.replaceAll("(?i)^DR\\.?\\s*", "").trim().toUpperCase(); // Remove "Dr" prefix
		}

		return new CCDoctor(ccBilling, ccName);
	}

	private static String buildMDSReferenceRange(LabTest test) {
		if (test.getRefRangeText() != null && !test.getRefRangeText().isEmpty()) {
			return test.getRefRangeText();
		} else if (test.getRefRangeLow() != null && !test.getRefRangeLow().isEmpty()
				&& test.getRefRangeHigh() != null && !test.getRefRangeHigh().isEmpty()) {
			return test.getRefRangeLow() + "-" + test.getRefRangeHigh();
		}
		return "";
	}

	private static void appendZclForDoctor(StringBuilder sb, String billing, String name, String ccFlag) {
		sb.append("ZCL||-")
			.append(billing).append("^").append(name)
			.append("^^^^DR.^^^^^^^LP||||01|2|LP||||").append(ccFlag)
      		.append("\n");
	}

	private static class CCDoctor {
		final String billing;
		final String name;

		CCDoctor(String billing, String name) {
			this.billing = billing;
			this.name = name;
		}
	}
}
