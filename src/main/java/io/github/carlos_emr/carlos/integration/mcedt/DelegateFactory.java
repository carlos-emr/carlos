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
package io.github.carlos_emr.carlos.integration.mcedt;

import io.github.carlos_emr.carlos.utility.LogSafe;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.integration.ebs.client.ng.EdtClientBuilder;
import io.github.carlos_emr.carlos.integration.ebs.client.ng.EdtClientBuilderConfig;
import io.github.carlos_emr.carlos.integration.mcedt.mailbox.ActionUtils;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import io.github.carlos_emr.CarlosProperties;
import ca.ontario.health.edt.EDTDelegate;

import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class DelegateFactory {

    private static Logger logger = MiscUtils.getLogger();
    private static UserPropertyDAO userPropertyDAO = SpringUtils.getBean(UserPropertyDAO.class);
    private static Map<String, EDTDelegate> edtDelegates = new HashMap<>();
    private static final Map<String, byte[]> credentialFingerprints = new HashMap<>();

    public static EDTDelegate getEDTDelegateInstance(String serviceId) {
        // Read before the cache monitor: no client construction/cache lock is
        // held while a credential transaction waits on a database row lock.
        String password = currentPassword();
        byte[] fingerprint = passwordFingerprint(password);
        synchronized (edtDelegates) {
            EDTDelegate cached = edtDelegates.get(serviceId);
            if (cached == null || !MessageDigest.isEqual(fingerprint, credentialFingerprints.get(serviceId))) {
                cached = newDelegate(serviceId, password);
                edtDelegates.put(serviceId, cached);
                credentialFingerprints.put(serviceId, fingerprint);
            }
            return cached;
        }
    }

    public static EDTDelegate getEDTDelegateInstance() {
        CarlosProperties props = CarlosProperties.getInstance();
        return getEDTDelegateInstance(props.getProperty("mcedt.service.id"));
    }

    private static String currentPassword() {
        return SpringUtils.getBean(McedtCredentialService.class)
                .getPassword(CarlosProperties.getInstance().getProperty("mcedt.service.pass"));
    }

    /** Cache identity only; neither plaintext nor its digest is logged or exposed. */
    private static byte[] passwordFingerprint(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(password == null ? (byte) 0 : (byte) 1);
            if (password != null) {
                byte[] encoded = password.getBytes(StandardCharsets.UTF_8);
                try {
                    digest.update(encoded);
                } finally {
                    Arrays.fill(encoded, (byte) 0);
                }
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("Unable to identify MCEDT credential state");
        }
    }

    private static EDTDelegate newDelegate(String serviceId, String password) {
        CarlosProperties props = CarlosProperties.getInstance();
        EdtClientBuilderConfig config = new EdtClientBuilderConfig();
        config.setLoggingRequired(!Boolean.valueOf(props.getProperty("mcedt.logging.skip")));
        config.setKeystoreUser(props.getProperty("mcedt.keystore.user"));
        config.setKeystorePassword(props.getProperty("mcedt.keystore.pass"));
        config.setUserNameTokenUser(props.getProperty("mcedt.service.user"));
        config.setUserNameTokenPassword(password);
        config.setServiceUrl(props.getProperty("mcedt.service.url"));
        config.setConformanceKey(props.getProperty("mcedt.service.conformanceKey"));
        config.setServiceId((serviceId == null || serviceId.trim().equals("")) ? props.getProperty("mcedt.service.id") : serviceId);
        config.setMtomEnabled(true);
        EdtClientBuilder builder = new EdtClientBuilder(config);
        setExternalClientKeystoreFilename(builder, props.getProperty("mcedt.service.clientKeystore.properties"));
        EDTDelegate edtDelegate = builder.build(EDTDelegate.class);
        if (logger.isInfoEnabled()) {
            logger.info("Created new EDT delegate " + edtDelegate);
        }
        return edtDelegate;
    }

    /**
     * Refreshes created delegates in the event that information has been updated
     */
    public static void refreshDelegates() {
        String password = currentPassword();
        byte[] fingerprint = passwordFingerprint(password);
        synchronized (edtDelegates) {
            // Explicit refresh still rebuilds clients for certificate/config changes.
            for (String serviceId : ActionUtils.getServiceIds()) {
                edtDelegates.put(serviceId, newDelegate(serviceId, password));
                credentialFingerprints.put(serviceId, fingerprint);
            }
        }
        // Suggests for the Garbage Collector to run in an effort to clean up any lingering connections
        System.gc();
    }

    public static UserPropertyDAO getUserPropertyDAO() {
        return userPropertyDAO;
    }

    public static void setUserPropertyDAO(UserPropertyDAO userPropertyDAO) {
        DelegateFactory.userPropertyDAO = userPropertyDAO;
    }

    /**
     * Set an external client keystore properties file for the EDT client builder.
     * This method configures a custom keystore properties file path for MCEDT service
     * client certificate authentication. If the provided path is null or the file does
     * not exist, the default keystore at src/main/resources/clientKeystore.properties
     * will be used.
     *
     * @param builder EdtClientBuilder the EDT client builder instance to configure
     * @param clientKeystorePropertiesPath String the absolute path to the client keystore properties file, or null to use default
     * @since 2026-01-29
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path derived from trusted configuration/constant/DB value, not user-controllable input")
    private static void setExternalClientKeystoreFilename(EdtClientBuilder builder, String clientKeystorePropertiesPath) {
        if (clientKeystorePropertiesPath == null || clientKeystorePropertiesPath.trim().isEmpty()) {
            return;
        }
        File signatureFile = PathValidationUtils.resolveTrustedPath(new File(clientKeystorePropertiesPath));
        Path signaturePropFile = signatureFile.toPath();
        if (Files.exists(signaturePropFile)) {
            try {
                builder.setClientKeystoreFilename(signatureFile.toURI().toURL().toString());
            } catch (MalformedURLException e) {
                logger.error("Malformed client keystore configuration URL: {}", LogSafe.exceptionTrace(e));
            }
        }
    }

}
