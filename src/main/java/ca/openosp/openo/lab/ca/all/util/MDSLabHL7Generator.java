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

	/**
	 * Appends an MSH (message header) segment to the provided StringBuilder for an MDS HL7 message.
	 *
	 * The segment includes facility, timestamp, message type, HL7 version and a message control ID
	 * composed as `{billingNo}-{accession}-1`.
	 *
	 * @param sb the StringBuilder to append the MSH segment to
	 * @param billingNo the billing number used as the first part of the message control ID
	 * @param accession the accession used as the second part of the message control ID
	 * @param nowDateTime the current date/time string formatted for the MSH timestamp field
	 */
	private static void buildMSH(StringBuilder sb, String billingNo, String accession, String nowDateTime) {
		sb.append("MSH|^~\\&|MDS||||").append(nowDateTime).append("||ORU|")
			.append(billingNo).append("-").append(accession).append("-1")
			.append("|P^|2.3.0|||NE|ER\n");
	}

	// ZLB segment - Lab facility information (required for MDS)
	/**
	 * Appends a ZLB segment containing fixed facility address, contact and MDS lab identifiers.
	 *
	 * The segment uses the first two characters of the provided accession as the lab code; when the
	 * accession string has two or fewer characters the default code "LA" is used.
	 *
	 * @param sb        the StringBuilder to append the ZLB segment to
	 * @param accession accession string whose first two characters are used as the lab code (default "LA")
	 */
	private static void buildZLB(StringBuilder sb, String accession) {
		sb.append("ZLB||").append(accession.length() > 2 ? accession.substring(0, 2) : "LA")
			.append("||55 QUEEN ST TORONTO M5C 1R6 1(877)849-3637|30||MDS|MDS\n");
	}

	// ZRG segments - Test group registration (one per test group)
	// Format: ZRG|sequence|groupNum|||groupName|display|
	/**
	 * Appends a ZRG segment that groups all tests into a single laboratory group when the lab contains tests.
	 *
	 * If the lab has at least one test, a single line with the fixed group designation
	 * "ZRG|1.1|1000|||GENERAL LABORATORY|1|" followed by a newline is appended to the provided StringBuilder.
	 *
	 * @param sb  the StringBuilder to append the ZRG segment to
	 * @param lab the Lab whose tests determine whether the ZRG segment is emitted
	 */
	private static void buildZRG(StringBuilder sb, Lab lab) {
		if (!lab.getTests().isEmpty()) {
			sb.append("ZRG|1.1|1000|||GENERAL LABORATORY|1|\n");
		}
	}

	// ZMN segments - Test menu definitions (one per test)
	/**
	 * Append one ZMN segment per LabTest in the given Lab to the provided StringBuilder.
	 *
	 * Each ZMN line encodes test metadata (normalized short name, display name, units,
	 * value, reference range, LOINC/code, flag) and ends with the fixed group number 1000.
	 *
	 * @param sb  the StringBuilder to which ZMN segments will be appended
	 * @param lab the Lab whose tests will be converted into ZMN segments
	 */
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
	/**
	 * Appends ZCL (ordering clinician) segment(s) for the primary provider and any CC doctors.
	 *
	 * <p>Appends a ZCL line for the primary ordering provider using the provided billing number
	 * and provider full name (marked with CC flag "0"). If the Lab contains CC doctor entries,
	 * each semicolon-separated entry is parsed and results in an additional ZCL line marked with
	 * CC flag "1".</p>
	 *
	 * @param sb the StringBuilder to which ZCL segment lines are appended
	 * @param lab the Lab containing optional CC doctor data
	 * @param billingNo billing number for the primary provider
	 * @param providerFullName full name of the primary provider (last and first combined, uppercase)
	 */
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

	/**
	 * Append a PID segment that encodes the patient identifier as `year-accession` and includes demographic fields.
	 *
	 * @param sb the StringBuilder to append the PID segment to
	 * @param lab the Lab object supplying patient demographics (name, DOB, sex, phone, HIN)
	 * @param accession the accession portion used in the patient identifier (normalized)
	 * @param nowYear the four-digit year used as the prefix for the patient identifier
	 */
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
	/**
	 * Append a PV1 segment containing visit and ordering-provider information to the given StringBuilder.
	 *
	 * The segment includes the primary ordering provider (using the supplied billing number and providerFullName),
	 * appends the first CC (copied/consult) doctor when present, and terminates the segment with the lab request date.
	 *
	 * @param sb the StringBuilder accumulating the HL7 message
	 * @param lab source of patient, CC (copied/consult) and request-date data used in the segment
	 * @param billingNo billing number for the primary ordering provider
	 * @param providerFullName full name of the primary ordering provider (already formatted/uppercased)
	 */
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

	/**
	 * Appends a fixed ZFR segment to the provided message builder.
	 *
	 * The segment appended is "ZFR||1|1|||0|1" followed by a newline.
	 *
	 * @param sb the StringBuilder to append the ZFR segment to
	 */
	private static void buildZFR(StringBuilder sb) {
		sb.append("ZFR||1|1|||0|1\n");
	}

	/**
	 * Append an initial ZCT segment containing the accession identifier.
	 *
	 * @param sb        the StringBuilder receiving the ZCT segment
	 * @param accession the accession identifier to insert (expected without year prefix)
	 */
	private static void buildZCT(StringBuilder sb, String accession) {
		sb.append("ZCT||").append(accession).append("||").append(accession).append("|||\n");
	}

	// OBR and OBX segments for each test
	// OBX segment - Result in field 5
	// Format: OBX|setId|valueType|identifier|subId|value|units|refRange|abnormalFlags|||resultStatus||blocked|||performingOrg
	// For MDS format: Use field type "L" (not "MC") for simple text comments
	/**
	 * Appends OBR, OBX, NTE (notes) and per-test ZCT segments for every test in the provided Lab.
	 *
	 * For each LabTest this method determines the test date and a test code (uses a numeric default
	 * based on the OBR sequence when the test code is missing), computes the MDS reference range,
	 * and appends the following segments in order: OBR, OBX, NTE (if notes present), and a ZCT tied
	 * to the supplied accession.
	 *
	 * @param sb the StringBuilder to which HL7 segments are appended
	 * @param lab the Lab whose tests will be converted into segments
	 * @param accession the accession value used when emitting ZCT segments for each test
	 */
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

	/**
	 * Append an OBR segment for a single test to the provided StringBuilder.
	 *
	 * The appended OBR line contains a sequence number (100 + obrCounter), the test code,
	 * the provided test date in the relevant fields, fixed MDS identifiers, and a final
	 * status marker of `R`.
	 *
	 * @param sb the StringBuilder to append the OBR segment to
	 * @param obrCounter a one-based counter used to compute the sequence number (sequence = 100 + obrCounter)
	 * @param testCode the test identifier to include in the OBR segment
	 * @param testDate the date/time string to place into the OBR date fields
	 */
	private static void appendObr(StringBuilder sb, int obrCounter, String testCode, String testDate) {
		sb.append("OBR||").append(100 + obrCounter).append("||")
			.append(testCode).append("|||")
			.append(testDate).append("|||||||").append(testDate)
			.append("||||||MDS^MDS|||||||R\n");
	}

	/**
	 * Appends an OBX segment representing the given test result to the provided StringBuilder.
	 *
	 * @param sb the StringBuilder to append the OBX segment to
	 * @param test the LabTest containing result value, units, flags, and other metadata
	 * @param testCode the test identifier used in OBX fields
	 * @param refRange the human-readable reference range to include for the test result
	 */
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

	/**
	 * Appends an NTE segment for the test's notes when present.
	 *
	 * @param sb the StringBuilder to append the segment to
	 * @param test the LabTest whose notes will be written as an NTE segment
	 */
	private static void appendNotesNte(StringBuilder sb, LabTest test) {
		if (test.getNotes() != null && !test.getNotes().isEmpty()) {
			sb.append("NTE||L|^").append(test.getNotes()).append("\n");
		}
	}

	/**
	 * Appends a ZCT segment for the given accession to the provided StringBuilder.
	 *
	 * The segment contains the accession value in both primary fields required by the MDS ZCT format.
	 *
	 * @param sb        the StringBuilder to append the segment to
	 * @param accession the accession identifier to include in the ZCT fields
	 */
	private static void appendZctForAccession(StringBuilder sb, String accession) {
		sb.append("ZCT||").append(accession).append("||").append(accession).append("|||\n");
	}

	// Add ZPD segment if any test is BLOCKED
	/**
	 * Appends a ZPD segment marking the report as blocked when any lab test is blocked.
	 *
	 * Checks the lab's tests for a blocked status equal to "BLOCKED"; if found, appends
	 * a ZPD segment with a "Y" in the third field to the provided StringBuilder.
	 *
	 * @param sb  the StringBuilder to append the ZPD segment to
	 * @param lab the Lab whose tests are evaluated for blocked status
	 */
	private static void buildZPD(StringBuilder sb, Lab lab) {
		boolean hasBlockedTest = lab.getTests().stream()
			.anyMatch(t -> "BLOCKED".equals(t.getBlocked()));
		if (hasBlockedTest) {
			sb.append("ZPD|||Y|\n");
		}
	}

	/**
	 * Normalize an accession identifier for inclusion in MSH message fields.
	 *
	 * <p>Removes a leading four-digit year followed by a dash (e.g. "2024-") and all dashes from the accession.
	 * If the provided accession is null or empty, a fallback value starting with "LAB" followed by the current
	 * epoch milliseconds is returned.
	 *
	 * @param accession the original accession identifier, may be null
	 * @return the normalized accession with year prefix and dashes removed, or a generated fallback if input was null
	 */
	private static String normalizeAccession(String accession) {
		String result = safeWithDefault(accession, "LAB" + System.currentTimeMillis());
		result = result.replaceAll("^\\d{4}-", ""); // Remove year prefix if exists
		result = result.replaceAll("-", ""); // Remove all dashes to prevent parsing issues in MSH-10-1
		return result;
	}

	/**
	 * Builds the provider's full name from the Lab's provider first and last name fields.
	 *
	 * @param lab the Lab containing provider name fields
	 * @return the provider full name in uppercase as "LAST FIRST", trimmed; empty string if neither name is present
	 */
	private static String buildProviderFullName(Lab lab) {
		String fullName = (lab.getProviderLastName() != null ? lab.getProviderLastName().toUpperCase() : "") +
		                  " " +
		                  (lab.getProviderFirstName() != null ? lab.getProviderFirstName().toUpperCase() : "");
		return fullName.trim();
	}

	// Try to parse as "billingNo,LastName,FirstName"
	/**
	 * Parses a CC doctor string into a CCDoctor containing a billing code and a normalized uppercase name.
	 *
	 * <p>If the input contains at least two comma-separated parts, the first part is used as the billing
	 * code and the subsequent part(s) are combined into the doctor's name. If the input does not
	 * contain a billing part, a default billing code of "00000" is returned and any leading "Dr." or
	 * "Dr" prefix is removed from the name before uppercasing.
	 *
	 * @param ccDoc the raw CC doctor string (expected either as "billing,name" or a plain name)
	 * @return a CCDoctor with parsed `billing` and normalized `name`
	 */
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

	/**
	 * Builds the MDS-formatted reference range for a lab test.
	 *
	 * @param test the test from which to derive the reference range
	 * @return the reference range text if present; otherwise the "`low-high`" range when both low and high are present; otherwise an empty string
	 */
	private static String buildMDSReferenceRange(LabTest test) {
		if (test.getRefRangeText() != null && !test.getRefRangeText().isEmpty()) {
			return test.getRefRangeText();
		} else if (test.getRefRangeLow() != null && !test.getRefRangeLow().isEmpty()
				&& test.getRefRangeHigh() != null && !test.getRefRangeHigh().isEmpty()) {
			return test.getRefRangeLow() + "-" + test.getRefRangeHigh();
		}
		return "";
	}

	/**
	 * Appends a ZCL segment for a doctor to the given StringBuilder.
	 *
	 * @param sb the StringBuilder to which the ZCL line will be appended
	 * @param billing the doctor's billing number used as the segment identifier
	 * @param name the doctor's name as it should appear in the segment
	 * @param ccFlag "0" for primary ordering provider, "1" for a carbon-copy (CC) doctor
	 */
	private static void appendZclForDoctor(StringBuilder sb, String billing, String name, String ccFlag) {
		sb.append("ZCL||-")
			.append(billing).append("^").append(name)
			.append("^^^^DR.^^^^^^^LP||||01|2|LP||||").append(ccFlag)
      		.append("\n");
	}

	private static class CCDoctor {
		final String billing;
		final String name;

		/**
		 * Creates a CCDoctor holding a provider billing code and display name.
		 *
		 * @param billing the doctor's billing identifier
		 * @param name    the doctor's full display name
		 */
		CCDoctor(String billing, String name) {
			this.billing = billing;
			this.name = name;
		}
	}
}