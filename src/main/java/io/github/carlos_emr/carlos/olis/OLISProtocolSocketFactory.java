package io.github.carlos_emr.carlos.olis;

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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.security.KeyStore;
import java.security.SecureRandom;

import org.apache.http.conn.ssl.SSLConnectionSocketFactory;

import io.github.carlos_emr.OscarProperties;

import javax.net.SocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

public class OLISProtocolSocketFactory extends SSLConnectionSocketFactory {
    SSLContext context = null;

    public OLISProtocolSocketFactory() throws Exception {
        super(createSSLContext(), new String[]{"TLSv1.2"}, null, SSLConnectionSocketFactory.getDefaultHostnameVerifier());
    }

    private static SSLContext createSSLContext() throws Exception {
        String pKeyFile = OscarProperties.getInstance().getProperty("olis_ssl_keystore").trim();
        String pKeyPassword = OscarProperties.getInstance().getProperty("olis_ssl_keystore_password").trim();

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (InputStream keyInput = new FileInputStream(pKeyFile)) {
            keyStore.load(keyInput, pKeyPassword.toCharArray());
        }
        keyManagerFactory.init(keyStore, pKeyPassword.toCharArray());

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagerFactory.getKeyManagers(), null, new SecureRandom());
        return context;
    }
    
    public Socket createSocket(final String host, final int port, final InetAddress localAddress, final int localPort, final int timeout) throws IOException {
        SocketFactory socketFactory = context.getSocketFactory();

        if (timeout <= 0) {
            return socketFactory.createSocket(host, port, localAddress, localPort);
        } else {
            Socket socket = socketFactory.createSocket();
            SocketAddress localAddr = new InetSocketAddress(localAddress, localPort);
            SocketAddress remoteAddr = new InetSocketAddress(host, port);
            socket.bind(localAddr);
            socket.connect(remoteAddr, timeout);
            return socket;
        }
    }

    public Socket createSocket(String host, int port, InetAddress clientHost, int clientPort) throws IOException {
        return context.getSocketFactory().createSocket(host, port, clientHost, clientPort);
    }

    public Socket createSocket(String host, int port) throws IOException {
        return context.getSocketFactory().createSocket(host, port);
    }

    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
        return context.getSocketFactory().createSocket(socket, host, port, autoClose);
    }

}
