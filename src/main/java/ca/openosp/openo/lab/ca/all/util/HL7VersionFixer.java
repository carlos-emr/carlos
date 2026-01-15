package ca.openosp.openo.lab.ca.all.util;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.utility.MiscUtils;

/**
 * Utility class for fixing HL7 compatibility issues with HAPI library.
 *
 * MDS/LifeLabs sends non-standard HL7 messages that the official HAPI library cannot parse.
 * The old hacked HAPI jar (version 0.5.1) was modified to handle these, but we're now
 * using the official HAPI library (version 1.0.1) which is stricter about HL7 standards.
 *
 * MDS/LifeLabs specific issues:
 * 1. Version field (MSH-12): sends "2.3.0" instead of standard "2.3"
 * 2. Message type field (MSH-9): sends only "ORU" instead of "ORU^R01"
 *
 * This class fixes these issues to make MDS messages compatible with official HAPI.
 *
 * @since 2025-01-02
 */
public class HL7VersionFixer {

    private static final Logger logger = MiscUtils.getLogger();

    /**
     * Fix MDS/LifeLabs HL7 messages to be compatible with official HAPI library.
     *
     * Fixes:
     * - Version "2.3.0" -> "2.3" (MSH-12 field)
     * - Message type "ORU" -> "ORU^R01" (MSH-9 field)
     * - Move custom Z-segments (ZLB, ZRG, ZMN, ZCL, ZFR, ZCT) to end of message
     *   (HAPI expects standard HL7 structure: MSH -> PID -> PV1 -> OBR -> OBX)
     *
     * @param hl7Body The original HL7 message body
     * @return The HL7 message with corrections for HAPI compatibility
     */
    public static String fixHL7Version(String hl7Body) {
        if (hl7Body == null) {
            return null;
        }

        // MSH segment is always the first segment
        // Format: MSH|^~\&|field3|field4|field5|field6|field7|field8|field9|field10|field11|field12|...
        // MSH-9 is at index 8 (0-based) - Message Type
        // MSH-12 is at index 11 (0-based) - Version ID

        String[] lines = hl7Body.split("\r?\n");
        StringBuilder result = new StringBuilder();
        StringBuilder zSegments = new StringBuilder(); // Collect all Z-segments to append at end

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue; // Skip empty lines
            }

            if (line.startsWith("MSH|")) {
                String[] fields = line.split("\\|", -1); // Use -1 to preserve empty fields
                boolean mshModified = false;

                // Fix MSH-9 (Message Type) - index 8
                if (fields.length > 8) {
                    String messageType = fields[8];
                    // MDS sends just "ORU" but HAPI needs "ORU^R01"
                    if ("ORU".equals(messageType)) {
                        fields[8] = "ORU^R01";
                        mshModified = true;
                        logger.debug("Fixed MSH-9: converted 'ORU' to 'ORU^R01' for MDS/HAPI compatibility");
                    }
                }

                // Fix MSH-12 (Version) - index 11
                if (fields.length > 11) {
                    String version = fields[11];
                    // MDS sends "2.3.0" but HAPI only recognizes "2.3"
                    if ("2.3.0".equals(version)) {
                        fields[11] = "2.3";
                        mshModified = true;
                        logger.debug("Fixed MSH-12: converted version '2.3.0' to '2.3' for MDS/HAPI compatibility");
                    }
                }

                if (mshModified) {
                    line = String.join("|", fields);
                }
                result.append(line).append("\n");
            } else if (line.startsWith("ZLB|") || line.startsWith("ZRG|") ||
                       line.startsWith("ZMN|") || line.startsWith("ZCL|") ||
                       line.startsWith("ZFR|") || line.startsWith("ZCT|") ||
                       line.startsWith("ZMC|")) {
                // Move MDS custom Z-segments to the end
                // These break HAPI's ORU_R01 structure parsing
                // HAPI expects: MSH -> PID -> PV1 -> OBR -> OBX (no Z-segments in between)
                zSegments.append(line).append("\n");
                logger.debug("Moved Z-segment to end of message for HAPI compatibility: " + line.substring(0, 3));
            } else {
                result.append(line).append("\n");
            }
        }

        // Append collected Z-segments at the end
        if (zSegments.length() > 0) {
            result.append(zSegments);
        }

        return result.toString().trim();
    }

    /**
     * Check if the HL7 message needs MDS/LifeLabs compatibility fixes.
     *
     * @param hl7Body The HL7 message body
     * @return true if the message needs fixing, false otherwise
     */
    public static boolean needsVersionFix(String hl7Body) {
        if (hl7Body == null) {
            return false;
        }

        String[] lines = hl7Body.split("\r?\n");
        boolean foundPID = false;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("MSH|")) {
                String[] fields = line.split("\\|", -1);

                // Check MSH-9 (Message Type) - index 8
                if (fields.length > 8 && "ORU".equals(fields[8])) {
                    return true; // MDS sends just "ORU" instead of "ORU^R01"
                }

                // Check MSH-12 (Version) - index 11
                if (fields.length > 11 && "2.3.0".equals(fields[11])) {
                    return true; // MDS sends "2.3.0" instead of "2.3"
                }
            }

            // Check for Z-segments before PID (breaks HAPI structure)
            if (line.startsWith("PID|")) {
                foundPID = true;
            }

            if (!foundPID && line.startsWith("Z")) {
                return true; // MDS sends custom Z-segments before PID
            }
        }

        return false;
    }
}