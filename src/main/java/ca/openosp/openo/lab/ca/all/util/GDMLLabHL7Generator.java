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
 * Utility class for generating GDML (Gamma-Dynacare Medical Laboratories) format HL7 messages.
 *
 * @since 2026-01-12
 */
public class GDMLLabHL7Generator {

	private GDMLLabHL7Generator() {
		// Private constructor to prevent instantiation
	}

	/**
	 * Generates GDML format HL7 message from Lab object.
	 *
	 * @param lab Lab object containing patient and test information
	 * @return String containing HL7 formatted message
	 */
	public static String generate(Lab lab) {
		StringBuilder sb = new StringBuilder();

		buildMSH(sb);
		buildPID(sb, lab);
		buildZDRSegments(sb, lab);
		if (lab.getTests() != null && !lab.getTests().isEmpty()) {
			buildOBR(sb, lab);
			buildOBXSegments(sb, lab);
		}

		return sb.toString();
	}

	// Working GDML sample structure to match EXACTLY:
	// MSH|^~\&|GDML|GDML|||20230609231252||ORU^R01|MAGENTA .83610181|P|2.3
	// PID|1|9876543225^FW^ON|AC-46222032||TEST FAKE^PATIENT^K||19951211|F|||123 MAIN RD^NORTH YORK^ONTARIO^ON^M6B3Y5||(437)774-5555
	// OBR|1|||253^URINALYSIS CHEMICAL^L253A^URINALYSIS|R|202306090949|202306090949||0000||N|||||045717^P. AZIZI NAMINI||||||20230609231251|||||
	// OBX|1|ST|253&21^GLUCOSE|1|NEG|mmol/L|-NEG^NEGATIVE (mmol/L)|N||F||||||

	// MSH segment - EXACT format from sample
	private static void buildMSH(StringBuilder sb) {
		sb.append("MSH|^~\\&|GDML|GDML|||").append(currentDateTime())
			.append("||ORU^R01|MAGENTA .").append(System.currentTimeMillis()).append("|P|2.3\n");
	}

	// PID segment - CORRECT field mapping from HL7 v2.3 spec and GDMLHandler:
	// Sample: PID|1|9876543225^FW^ON|AC-46222032||TEST FAKE^PATIENT^K||19951211|F|||123 MAIN RD^NORTH YORK^ONTARIO^ON^M6B3Y5||(437)774-5555
	// PID-1 = Set ID (always "1")
	// PID-2 = PatientIDExternalID = HIN^FW^ON (getHealthNum() reads PID-2-1!)
	// PID-3 = PatientIDInternalID = ACCESSION (getAccessionNum() reads PID-3-1!)
	// PID-4 = Alternate Patient ID (empty)
	// PID-5 = Patient Name (LASTNAME^FIRSTNAME^MIDDLENAME)
	// PID-6 = Mother's Maiden Name (empty)
	// PID-7 = Date of Birth
	// PID-8 = Sex
	// PID-9 = Patient Alias (empty)
	// PID-10 = Race (empty)
	// PID-11 = Patient Address
	// PID-12 = County Code (empty)
	// PID-13 = Phone Number - HOME (getHomePhone() reads this field!)
	// PID-14 = Phone Number - BUSINESS (getWorkPhone() reads this field!)
	private static void buildPID(StringBuilder sb, Lab lab) {
		String accession = safeWithDefault(lab.getAccession(), "LAB" + System.currentTimeMillis());

		sb.append("PID|1|")
			.append(safe(lab.getHin())).append("^FW^ON")  // PID-2 = HIN^FW^ON (External ID)
			.append("|").append(accession)  // PID-3 = ACCESSION (Internal ID)
			.append("||").append(safeUpper(lab.getLastName()))  // PID-4 empty, PID-5 starts with name
			.append("^").append(safeUpper(lab.getFirstName()))
			.append("^")  // Middle name placeholder (empty) - PID-5-3
			.append("||").append(formatDate(lab.getDob())).append("|").append(lab.getSex())  // PID-7, PID-8
			.append("|||^^^^||")  // PID-9, PID-10, PID-11, PID-12
			.append(safe(lab.getPhone()))  // PID-13 = HOME phone
			.append("\n");
	}

	// ZDR segment for CC doctors - field 4 has components: givenName^familyName^middleName
	private static void buildZDRSegments(StringBuilder sb, Lab lab) {
		if (lab.getCc() != null && !lab.getCc().isEmpty()) {
			String[] ccDocs = lab.getCc().split(";");
			for (String ccDoc : ccDocs) {
				ccDoc = ccDoc.trim();
				if (!ccDoc.isEmpty()) {
					// Parse "Dr Adward" → split into parts
					// IMPORTANT: Handler's while loop checks "while (givenName != null)"
					// So we MUST put something in givenName for CC to display!
					NameComponents name = parseName(ccDoc);

					// ZDR format: ZDR|||billingNum|givenName^familyName^middleName
					// Field 3 = billing number (for getDocNums()), Field 4 = name (for getCCDocs())
					// Note: Currently we don't capture CC billing numbers from the form, so field 3 is empty
					sb.append("ZDR||||").append(name.givenName).append("^").append(name.familyName).append("^\n");
				}
			}
		}
	}

	// OBR segment - EXACT format from sample
	// OBR with proper XCN format for ordering provider (field 16)
	// Provider XCN format: ID^FamilyName^GivenName^Middle^Suffix^Prefix^Degree
	// Handler reads: Prefix + GivenName + Middle + FamilyName
	// Example: 045717^NAMINI^P.^^^^^  would display as "P. NAMINI"
	private static void buildOBR(StringBuilder sb, Lab lab) {
		LabTest firstTest = lab.getTests().get(0);
		String testDateTime = formatShortDateTime(firstTest.getDate());
		String testCode = safeWithDefault(firstTest.getCode(), "253");
		String testName = safeWithDefault(firstTest.getName(), "LABORATORY");

		sb.append("OBR|1|||")
			.append(testCode).append("^").append(testName.toUpperCase())
			.append("^L").append(testCode).append("A^").append(testName.toUpperCase())
			.append("|R|").append(testDateTime).append("|").append(testDateTime)
			.append("||0000||N|||||")
			// OBR-16: Ordering Provider in XCN format
			.append(safeWithDefault(lab.getBillingNo(), "00000"))  // XCN-1: ID
			.append("^").append(safeUpper(lab.getProviderLastName()))  // XCN-2: Family Name
			.append("^").append(safeUpper(lab.getProviderFirstName()))  // XCN-3: Given Name
			.append("^")  // XCN-4: Middle (empty)
			.append("^")  // XCN-5: Suffix (empty)
			.append("^")  // XCN-6: Prefix (empty - could be "DR." if needed)
			.append("^")  // XCN-7: Degree (empty)
			.append("||||||").append(currentDateTime()).append("|||||\n");
	}

	// OBX segments - EXACT format from sample
	// OBX format matching GDML sample EXACTLY:
	// OBX|1|ST|253&21^GLUCOSE|1|NEG|mmol/L|-NEG^NEGATIVE (mmol/L)|N||F||||||
	// Fields: 1=setId, 2=type, 3=identifier, 4=subId, 5=value, 6=units, 7=refRange,
	//         8=abnormalFlag, 9=probability, 10=natureOfAbnormal, 11=resultStatus,
	//         12=responsibleObserver, 13=observationMethod(BLOCKED goes here per handler line 937)
	private static void buildOBXSegments(StringBuilder sb, Lab lab) {
		LabTest firstTest = lab.getTests().get(0);
		String firstTestCode = safeWithDefault(firstTest.getCode(), "253");

		int testCounter = 1;
		for (LabTest test : lab.getTests()) {
			// Reference range MUST use two-component format: low-high^formatted text
			// Sample formats: -NEG^NEGATIVE (mmol/L) or 1.005-1.030^1.005 - 1.030
			String refRange = buildGDMLReferenceRange(test);

			// Use ST not FT - FT causes tests to not display (GDMLHandler line 221 filters FT types)
			String valueType = normalizeValueType(test.getCodeType());

			String identifier = buildObxIdentifier(test, firstTestCode, testCounter);
			appendObx(sb, testCounter, valueType, identifier, test, refRange);
        	appendNteIfPresent(sb, test);

			testCounter++;
		}
	}

	private static String buildObxIdentifier(LabTest test, String defaultCode, int testCounter) {
		String code = safeWithDefault(test.getCode(), defaultCode);
		return code + "&" + (20 + testCounter) + "^" + safeUpper(test.getName());
	}

	private static void appendObx(StringBuilder sb, int testCounter, String valueType, String identifier, LabTest test, String refRange) {
		sb.append("OBX|").append(testCounter).append("|") // OBX-1
			.append(valueType).append("|") // OBX-2
			.append(identifier).append("|") // OBX-3
			.append("1|") // OBX-4
			.append(safe(test.getCodeValue())).append("|") // OBX-5
			.append(safe(test.getCodeUnit())).append("|") // OBX-6
			.append(refRange).append("|") // OBX-7
			.append(safeWithDefault(test.getFlag(), "N")).append("|") // OBX-8
			.append("|")  // OBX-9
			.append("|")  // OBX-10
			.append(safeWithDefault(test.getStat(), "F")).append("|") // OBX-11
			.append("|")  // OBX-12
			.append(getBlockedStatus(test)).append("|") // OBX-13
			.append("|")  // OBX-14
			.append("|")  // OBX-15
			.append("|\n"); // OBX-16
	}

	// NTE format: NTE|||comment
	private static void appendNteIfPresent(StringBuilder sb, LabTest test) {
		if (test.getNotes() != null && !test.getNotes().isEmpty()) {
			sb.append("NTE|||").append(test.getNotes()).append("\n");
		}
	}

	private static String buildGDMLReferenceRange(LabTest test) {
		if (test.getRefRangeLow() != null && !test.getRefRangeLow().isEmpty()
				&& test.getRefRangeHigh() != null && !test.getRefRangeHigh().isEmpty()) {
			// Build format: low-high^low - high (with space around dash in formatted part)
			String low = test.getRefRangeLow();
			String high = test.getRefRangeHigh();
			return low + "-" + high + "^" + low + " - " + high;
		} else if (test.getRefRangeText() != null && !test.getRefRangeText().isEmpty()) {
			// If only text provided, try to parse it or use as second component
			String text = test.getRefRangeText();
			// Check if it contains " - " pattern to extract low and high
			if (text.contains(" - ")) {
				String[] parts = text.split(" - ");
				if (parts.length == 2) {
					return parts[0].trim() + "-" + parts[1].trim() + "^" + text;
				} else {
					// Can't parse, use as-is (not ideal but better than nothing)
					return text;
				}
			} else {
				// Single value or non-standard format, use as-is
				return text;
			}
		}
		return "";
	}

	private static String normalizeValueType(String valueType) {
		return "FT".equals(valueType) ? "ST" : safeWithDefault(valueType, "ST");
	}
}
