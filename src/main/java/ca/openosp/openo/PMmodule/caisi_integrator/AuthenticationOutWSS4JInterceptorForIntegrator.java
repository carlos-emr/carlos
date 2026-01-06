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


package ca.openosp.openo.PMmodule.caisi_integrator;

/**
 * CXF outbound interceptor for WS-Security authentication with custom CAISI provider identification.
 * <p>
 * This interceptor extends the standard WSS4J interceptor to add both WS-Security username token
 * authentication and a custom SOAP header containing the requesting CAISI provider number. This
 * dual authentication/identification approach is required for the integrator system to:
 * <ul>
 *   <li>Authenticate the facility making the request via WS-Security username/password</li>
 *   <li>Identify the individual provider within that facility making the request</li>
 *   <li>Enable proper audit logging and access control at the provider level</li>
 * </ul>
 * </p>
 * <p>
 * The interceptor is configured on CXF client proxies when establishing connections to
 * integrator web services, ensuring all outbound SOAP messages contain both authentication
 * credentials and provider identification.
 * </p>
 * 
 * @see org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor
 * @see javax.security.auth.callback.CallbackHandler
 */

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.xml.namespace.QName;

import org.apache.cxf.binding.soap.SoapHeader;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.headers.Header;
import org.apache.cxf.helpers.DOMUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.apache.wss4j.common.WSS4JConstants;
import org.apache.wss4j.common.ext.WSPasswordCallback;
import org.apache.wss4j.dom.handler.WSHandlerConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class AuthenticationOutWSS4JInterceptorForIntegrator extends WSS4JOutInterceptor implements CallbackHandler {
    /** Key used for the custom SOAP header identifying the requesting provider */
    private static final String REQUESTING_CAISI_PROVIDER_NO_KEY = "requestingCaisiProviderNo";
    
    /** QName for the custom SOAP header with namespace and prefix */
    private static QName REQUESTING_CAISI_PROVIDER_NO_QNAME = new QName("http://oscarehr.org/caisi", REQUESTING_CAISI_PROVIDER_NO_KEY, "caisi");

    /** Password for WS-Security authentication */
    private String password = null;
    
    /** Provider number of the requesting CAISI provider */
    private String oscarProviderNo = null;

    /**
     * Constructs the interceptor with authentication credentials and provider identification.
     * <p>
     * Initializes both the WS-Security username token authentication (via WSS4J) and stores
     * the provider number for inclusion in custom SOAP headers.
     * </p>
     * 
     * @param user the username for facility authentication
     * @param password the password for facility authentication
     * @param oscarProviderNo the provider number making the request (may be null for facility-level calls)
     */
    public AuthenticationOutWSS4JInterceptorForIntegrator(String user, String password, String oscarProviderNo) {
        this.password = password;
        this.oscarProviderNo = oscarProviderNo;

        // Configure WSS4J properties for username token authentication
        HashMap<String, Object> properties = new HashMap<String, Object>();
        properties.put(WSHandlerConstants.ACTION, WSHandlerConstants.USERNAME_TOKEN);
        properties.put(WSHandlerConstants.USER, user);
        properties.put(WSHandlerConstants.PASSWORD_TYPE, WSS4JConstants.PW_TEXT);
        properties.put(WSHandlerConstants.PW_CALLBACK_REF, this);

        setProperties(properties);
    }

    /**
     * Callback handler for providing the password to WSS4J.
     * <p>
     * This method is called by the WSS4J framework when it needs the password
     * to include in the WS-Security username token.
     * </p>
     * 
     * @param callbacks array of callback objects to handle
     * @throws IOException if an I/O error occurs
     * @throws UnsupportedCallbackException if a callback type is not supported
     */
    // don't like @override until jdk1.6?
    // @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        for (Callback callback : callbacks) {
            if (callback instanceof WSPasswordCallback) {
                WSPasswordCallback wsPasswordCallback = (WSPasswordCallback) callback;
                // Provide the password for WS-Security authentication
                wsPasswordCallback.setPassword(password);
            }
        }
    }

    /**
     * Handles outbound SOAP messages by adding custom provider header and invoking parent processing.
     * <p>
     * This method intercepts outbound messages before they are sent to add the custom CAISI
     * provider number header, then delegates to the parent WSS4JOutInterceptor to add the
     * WS-Security authentication token.
     * </p>
     * 
     * @param message the outbound SOAP message to process
     * @throws Fault if an error occurs during message processing
     */
    public void handleMessage(SoapMessage message) throws Fault {
        // Add custom header identifying the requesting provider
        addRequestingCaisiProviderNo(message, oscarProviderNo);
        // Delegate to parent to add WS-Security token
        super.handleMessage(message);
    }

    /**
     * Adds the requesting CAISI provider number as a custom SOAP header.
     * <p>
     * The provider number is only added if it is not null. This allows the integrator
     * service to log and audit which specific provider made the request, even though
     * the facility credentials are used for authentication.
     * </p>
     * 
     * @param message the SOAP message to add the header to
     * @param providerNo the provider number to include, or null to skip
     */
    private static void addRequestingCaisiProviderNo(SoapMessage message, String providerNo) {
        List<Header> headers = message.getHeaders();

        if (providerNo != null) {
            // Create and add the custom SOAP header
            headers.add(createHeader(REQUESTING_CAISI_PROVIDER_NO_QNAME, REQUESTING_CAISI_PROVIDER_NO_KEY, providerNo));
        }
    }

    /**
     * Creates a SOAP header element with the specified QName, element name, and text value.
     * 
     * @param qName the qualified name for the header (includes namespace and prefix)
     * @param key the local name of the header element
     * @param value the text content of the header
     * @return a CXF Header object ready to be added to the message
     */
    private static Header createHeader(QName qName, String key, String value) {
        // Create a new DOM document for the header element
        Document document = DOMUtils.createDocument();

        // Create the header element in the CAISI namespace
        Element element = document.createElementNS("http://oscarehr.org/caisi", "caisi:" + key);
        element.setTextContent(value);

        // Wrap in a CXF SOAP header
        SoapHeader header = new SoapHeader(qName, element);
        return (header);
    }
}
