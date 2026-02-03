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

import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;

import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.transport.common.gzip.GZIPInInterceptor;
import org.apache.cxf.transport.common.gzip.GZIPOutInterceptor;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.ConnectionType;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.logging.log4j.Logger;

public class CxfClientUtils {
    private static Logger logger = MiscUtils.getLogger();
    private static long connectionTimeout = 1500L;
    private static long receiveTimeout = 4000L;
    private static boolean useGZip = true;
    private static int gZipThreshold = 0;

    public CxfClientUtils() {
    }

    public static void initSslFromConfig() {
    }

    private static void initialiseFromConfigXml() {
        try {
            connectionTimeout = Long.parseLong(ConfigXmlUtils.getPropertyString("misc", "ws_client_connection_timeout_ms"));
        } catch (Throwable var6) {
        }

        try {
            receiveTimeout = Long.parseLong(ConfigXmlUtils.getPropertyString("misc", "ws_client_receive_timeout_ms"));
        } catch (Throwable var5) {
        }

        try {
            String booleanString = ConfigXmlUtils.getPropertyString("misc", "ws_client_use_gzip");
            if (booleanString != null) {
                useGZip = Boolean.parseBoolean(booleanString);
            }
        } catch (Throwable var4) {
        }

        try {
            gZipThreshold = Integer.parseInt(ConfigXmlUtils.getPropertyString("misc", "ws_client_gzip_threshold_bytes"));
        } catch (Throwable var3) {
        }

        boolean allowAllSsl = Boolean.parseBoolean(ConfigXmlUtils.getPropertyString("misc", "allow_all_ssl_certificates"));
        if (allowAllSsl) {
            try {
                MiscUtils.setJvmDefaultSSLSocketFactoryAllowAllCertificates();
            } catch (Exception var2) {
                logger.error("Unexpected error", var2);
            }
        }

        logger.info("CxfClientUtils using : connectionTimeout=" + connectionTimeout + ", receiveTimeout=" + receiveTimeout + ", useGZip=" + useGZip + ", gZipThreshold=" + gZipThreshold + ", allowAllSsl=" + allowAllSsl);
    }

    public static void configureClientConnection(Object wsPort) {
        Client cxfClient = ClientProxy.getClient(wsPort);
        HTTPConduit httpConduit = (HTTPConduit) cxfClient.getConduit();
        configureSsl(httpConduit);
        configureTimeout(httpConduit);
        if (useGZip) {
            configureGzip(cxfClient);
        }

    }

    public static void configureGzip(Client cxfClient) {
        cxfClient.getInInterceptors().add(new GZIPInInterceptor());
        cxfClient.getOutInterceptors().add(new GZIPOutInterceptor(gZipThreshold));
    }

    public static void configureTimeout(HTTPConduit httpConduit) {
        HTTPClientPolicy httpClientPolicy = new HTTPClientPolicy();
        httpClientPolicy.setConnection(ConnectionType.KEEP_ALIVE);
        httpClientPolicy.setConnectionTimeout(connectionTimeout);
        httpClientPolicy.setAllowChunking(false);
        httpClientPolicy.setReceiveTimeout(receiveTimeout);
        httpConduit.setClient(httpClientPolicy);
    }

    public static void configureSsl(HTTPConduit httpConduit) {
        TLSClientParameters tslClientParameters = httpConduit.getTlsClientParameters();
        if (tslClientParameters == null) {
            tslClientParameters = new TLSClientParameters();
        }

        tslClientParameters.setDisableCNCheck(true);
        CxfClientUtils.TrustAllManager[] tam = new CxfClientUtils.TrustAllManager[]{new CxfClientUtils.TrustAllManager()};
        tslClientParameters.setTrustManagers(tam);
        tslClientParameters.setSecureSocketProtocol("SSLv3");
        httpConduit.setTlsClientParameters(tslClientParameters);
    }

    public static void addWSS4JAuthentication(Object user, String password, Object wsPort) {
        Client cxfClient = ClientProxy.getClient(wsPort);
        cxfClient.getOutInterceptors().add(new AuthenticationOutWSS4JInterceptor(user, password));
    }

    static {
        initialiseFromConfigXml();
    }

    public static class TrustAllManager implements X509TrustManager {
        public TrustAllManager() {
        }

        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        public void checkClientTrusted(X509Certificate[] certs, String authType) {
        }

        public void checkServerTrusted(X509Certificate[] certs, String authType) {
        }
    }
}
