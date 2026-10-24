/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.utility;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Date;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpSession;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.DigitalSignatureDao;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class DigitalSignatureUtils {

    private static Logger logger = MiscUtils.getLogger();

    public static final String SIGNATURE_REQUEST_ID_KEY = "signatureRequestId";

    /** Session attribute holding the signature-pad request ids this login was given. */
    static final String ISSUED_REQUEST_IDS_ATTRIBUTE = DigitalSignatureUtils.class.getName() + ".issuedRequestIds";

    /** How many request ids one session keeps; the oldest is dropped beyond this. */
    static final int MAX_ISSUED_REQUEST_IDS = 200;

    private static final Pattern REQUEST_ID_FORMAT = Pattern.compile("[0-9a-f]{32}");

    private static final SecureRandom RANDOM = new SecureRandom();

    // Guards the get-or-create of a session's id set; signature pages are opened rarely.
    private static final Object ISSUED_IDS_LOCK = new Object();

    /**
     * Issues a request id for one signature-pad capture and records it in the session.
     *
     * <p>The pad writes its capture to {@code signature_<id>.jpg} in {@code java.io.tmpdir}, and the
     * page that asked for the signature later reads it back by the same id. Only ids issued to this
     * session are accepted when the capture is uploaded, previewed or saved (see
     * {@link #isIssued(HttpSession, String)}), so a capture belongs to the page that asked for it.
     * The id is 32 random lowercase hex characters, which keeps it valid for the pad's alphanumeric
     * key and the temp file name.</p>
     *
     * @param session the logged-in user's session; must not be {@code null}
     * @return the new request id
     */
    public static String generateSignatureRequestId(HttpSession session) {
        if (session == null) {
            throw new IllegalArgumentException("A session is required to issue a signature request id");
        }
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        String requestId = HexFormat.of().formatHex(bytes);
        synchronized (ISSUED_IDS_LOCK) {
            LinkedHashSet<String> issued = issuedIds(session, true);
            issued.add(requestId);
            for (Iterator<String> oldest = issued.iterator(); issued.size() > MAX_ISSUED_REQUEST_IDS; ) {
                oldest.next();
                oldest.remove();
            }
            // Set it again so a replicated or persisted session store sees the change.
            session.setAttribute(ISSUED_REQUEST_IDS_ATTRIBUTE, issued);
        }
        return requestId;
    }

    /**
     * Reports whether {@code requestId} was issued to this session by
     * {@link #generateSignatureRequestId(HttpSession)}.
     *
     * @return {@code false} for a {@code null} session or id, or an id in any other format
     */
    public static boolean isIssued(HttpSession session, String requestId) {
        if (session == null || requestId == null || !REQUEST_ID_FORMAT.matcher(requestId).matches()) {
            return false;
        }
        synchronized (ISSUED_IDS_LOCK) {
            LinkedHashSet<String> issued;
            try {
                issued = issuedIds(session, false);
            } catch (IllegalStateException invalidated) {
                return false;
            }
            return issued != null && issued.contains(requestId);
        }
    }

    @SuppressWarnings("unchecked")
    private static LinkedHashSet<String> issuedIds(HttpSession session, boolean create) {
        Object value = session.getAttribute(ISSUED_REQUEST_IDS_ATTRIBUTE);
        if (value instanceof LinkedHashSet<?> set) {
            return (LinkedHashSet<String>) set;
        }
        if (!create) {
            return null;
        }
        LinkedHashSet<String> issued = new LinkedHashSet<>();
        session.setAttribute(ISSUED_REQUEST_IDS_ATTRIBUTE, issued);
        return issued;
    }

    public static String getTempFilePath(String signatureRequestId) {
        String temppath = System.getProperty("java.io.tmpdir");
        File tempDir = PathValidationUtils.validateConfiguredDirectory(temppath, "java.io.tmpdir");
        String signatureFileName = PathValidationUtils.validateGeneratedFileName("signature_" + signatureRequestId + ".jpg");
        Path path = PathValidationUtils.validateGeneratedChildPath(signatureFileName, tempDir).toPath();
        return path.toString();
    }

    /**
     * This method will check if digital signatures is enabled or not. It will only attempt to save it if it's enabled.
     *
     * @param demographicId of the owner of this signature
     * @throws FileNotFoundException if missing or error in image when one was expected
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public static DigitalSignature storeDigitalSignatureFromTempFileToDB(LoggedInInfo loggedInInfo, String signatureRequestId, int demographicId) {
        DigitalSignature digitalSignature = null;

        if (loggedInInfo.getCurrentFacility().isEnableDigitalSignatures()) {
            if (!isIssued(loggedInInfo.getSession(), signatureRequestId)) {
                logger.debug("Signature request id was not issued to this session; nothing to store.");
                return digitalSignature;
            }
            String filename = DigitalSignatureUtils.getTempFilePath(signatureRequestId);
            if (filename == null || filename.isEmpty()) {
                return digitalSignature;
            }
            File signatureFile = PathValidationUtils.validateExistingPath(new File(filename), PathValidationUtils.validateConfiguredDirectory(System.getProperty("java.io.tmpdir"), "java.io.tmpdir"));
            try (FileInputStream fileInputStream = new FileInputStream(signatureFile)) {
                // Read the exact signature bytes rather than a fixed 256KB buffer (a single read()
                // can under-fill and the fixed array persisted padding/truncated the stored image).
                byte[] image = fileInputStream.readAllBytes();

                digitalSignature = new DigitalSignature();
                digitalSignature.setDateSigned(new Date());
                digitalSignature.setDemographicId(demographicId);
                digitalSignature.setFacilityId(loggedInInfo.getCurrentFacility().getId());
                digitalSignature.setProviderNo(loggedInInfo.getLoggedInProviderNo());
                digitalSignature.setSignatureImage(image);

                DigitalSignatureDao digitalSignatureDao = SpringUtils.getBean(DigitalSignatureDao.class);
                digitalSignatureDao.persist(digitalSignature);
            } catch (FileNotFoundException e) {
                logger.debug("Signature file not found. User probably didn't collect a signature.", e);
            } catch (Exception e) {
                logger.error("UnexpectedError.", e);
            }
        }

        return digitalSignature;
    }

}
