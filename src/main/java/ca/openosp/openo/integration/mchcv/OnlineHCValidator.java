//CHECKSTYLE:OFF
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
 */
package ca.openosp.openo.integration.mchcv;

import ca.ontario.health.ebs.EbsFault;
import ca.ontario.health.hcv.Faultexception;
import ca.ontario.health.hcv.HCValidation;
import ca.ontario.health.hcv.HcvRequest;
import ca.ontario.health.hcv.HcvResults;
import ca.ontario.health.hcv.Requests;

import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.soap.SOAPFault;
import javax.xml.ws.soap.SOAPFaultException;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.integration.ebs.client.ng.EdtClientBuilder;
import ca.openosp.openo.integration.ebs.client.ng.EdtClientBuilderConfig;
import ca.openosp.openo.utility.MiscUtils;

import ca.openosp.OscarProperties;

public class OnlineHCValidator implements HCValidator {
    private static Logger logger = MiscUtils.getLogger();

    private HCValidation validation;
    private EdtClientBuilder builder;

    public OnlineHCValidator() {
        OscarProperties properties = OscarProperties.getInstance();
        EdtClientBuilderConfig config = new EdtClientBuilderConfig();
        config.setLoggingRequired(!Boolean.valueOf(properties.getProperty("hcv.logging.skip")));
        config.setKeystoreUser(properties.getProperty("hcv.keystore.user"));
        config.setKeystorePassword(properties.getProperty("hcv.keystore.pass"));
        config.setUserNameTokenUser(properties.getProperty("hcv.service.user"));
        config.setUserNameTokenPassword(properties.getProperty("hcv.service.pass"));
        config.setServiceUrl(properties.getProperty("hcv.service.url"));
        config.setConformanceKey(properties.getProperty("hcv.service.conformanceKey"));
        config.setServiceId(properties.getProperty("hcv.service.id"));

        EdtClientBuilder hcvBuilder = new EdtClientBuilder(config);
        setBuilder(hcvBuilder);
        setExternalClientKeystoreFilename(hcvBuilder, properties.getProperty("hcv.service.clientKeystore.properties"));
        validation = builder.build(HCValidation.class);
    }

    @Override
    public HCValidationResult validate(String healthCardNumber, String versionCode) {
        return validate(healthCardNumber, versionCode, null);
    }

    @Override
    public HCValidationResult validate(String healthCardNumber, String versionCode, String serviceCode) {
        Requests requests = new Requests();
        HcvRequest request = new HcvRequest();
        request.setHealthNumber(healthCardNumber);
        request.setVersionCode(versionCode);
        if (serviceCode != null && !serviceCode.isEmpty()) {
            request.getFeeServiceCodes().add(serviceCode);
        }
        requests.getHcvRequest().add(request);
        HcvResults results = null;
        HCValidationResult result = null;
        try {
            results = validate(requests, "en");
            if (results != null) {
                result = HCValidator.createSingleResult(results, 0);
            }
        } catch (Faultexception e) {
            result = new HCValidationResult();
            result.setEbsFault(e.getFaultInfo());
            result.setResponseCode(NOT_VALID_RESPONSE_CODE);
        }

        return result;
    }

    @Override
    public HcvResults validate(Requests requests, String local) throws Faultexception {
        HcvResults results;
        try {
            results = validation.validate(requests, local);
        } catch (SOAPFaultException sfx) {
            SOAPFault soapFault = sfx.getFault();
            EbsFault ebsFault = new EbsFault();
            ebsFault.setCode(soapFault.getFaultCode());
            ebsFault.setMessage(soapFault.getFaultString());
            throw new Faultexception("", ebsFault);
        }
        return results;
    }

    public EdtClientBuilder getBuilder() {
        return builder;
    }

    private void setBuilder(EdtClientBuilder builder) {
        this.builder = builder;
    }

    /**
     * Set an external client keystore properties file for the EDT client builder.
     * This method configures a custom keystore properties file path for HCV service
     * client certificate authentication. If the provided path is null or the file does
     * not exist, the default keystore at src/main/resources/clientKeystore.properties
     * will be used.
     *
     * @param builder EdtClientBuilder the EDT client builder instance to configure
     * @param clientKeystorePropertiesPath String the absolute path to the client keystore properties file, or null to use default
     * @since 2026-01-29
     */
    private static void setExternalClientKeystoreFilename(EdtClientBuilder builder, String clientKeystorePropertiesPath) {
        if (clientKeystorePropertiesPath == null || clientKeystorePropertiesPath.trim().isEmpty()) {
            return;
        }
        Path signaturePropFile = Paths.get(clientKeystorePropertiesPath);
        if (Files.exists(signaturePropFile)) {
            File file = new File(clientKeystorePropertiesPath);
            try {
                builder.setClientKeystoreFilename(file.toURI().toURL().toString());
            } catch (MalformedURLException e) {
                logger.error("Malformed URL: " + clientKeystorePropertiesPath, e);
            }
        }
    }
}
