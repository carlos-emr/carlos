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
package ca.openosp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Wrapper for SSLSocketFactory that applies custom SSL parameters to all created sockets.
 * 
 * <p>This class wraps an existing SSLSocketFactory and automatically applies
 * specified SSL parameters (such as cipher suites, protocols, etc.) to every
 * socket it creates. This ensures consistent SSL/TLS configuration across all
 * connections.</p>
 * 
 * <p>Example usage:</p>
 * <pre>
 * SSLSocketFactory baseFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
 * SSLParameters params = new SSLParameters();
 * params.setProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
 * SSLSocketFactoryWrapper wrapper = new SSLSocketFactoryWrapper(baseFactory, params);
 * Socket socket = wrapper.createSocket("example.com", 443);
 * </pre>
 * 
 * <p><strong>Security Note:</strong> Ensure SSL parameters are configured according to
 * current security best practices. Avoid deprecated protocols and weak cipher suites.</p>
 */
public class SSLSocketFactoryWrapper extends SSLSocketFactory {

    private final SSLSocketFactory wrappedFactory;
    private final SSLParameters sslParameters;

    /**
     * Constructs a new SSLSocketFactoryWrapper.
     * 
     * @param factory the underlying SSLSocketFactory to wrap
     * @param sslParameters the SSL parameters to apply to all created sockets
     */
    public SSLSocketFactoryWrapper(SSLSocketFactory factory, SSLParameters sslParameters) {
        this.wrappedFactory = factory;
        this.sslParameters = sslParameters;
    }

    /**
     * Creates a socket and connects it to the specified host and port.
     * Applies configured SSL parameters before returning.
     * 
     * @param host the server host name
     * @param port the server port
     * @return the SSL socket with configured parameters
     * @throws IOException if an I/O error occurs
     * @throws UnknownHostException if the host cannot be resolved
     */
    @Override
    public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
        SSLSocket socket = (SSLSocket) wrappedFactory.createSocket(host, port);
        setParameters(socket);
        return socket;
    }

    /**
     * Creates a socket and connects it to the specified host and port,
     * binding to the specified local address and port.
     * Applies configured SSL parameters before returning.
     * 
     * @param host the server host name
     * @param port the server port
     * @param localHost the local address to bind to
     * @param localPort the local port to bind to
     * @return the SSL socket with configured parameters
     * @throws IOException if an I/O error occurs
     * @throws UnknownHostException if the host cannot be resolved
     */
    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
            throws IOException, UnknownHostException {
        SSLSocket socket = (SSLSocket) wrappedFactory.createSocket(host, port, localHost, localPort);
        setParameters(socket);
        return socket;
    }


    /**
     * Creates a socket and connects it to the specified address and port.
     * Applies configured SSL parameters before returning.
     * 
     * @param host the server IP address
     * @param port the server port
     * @return the SSL socket with configured parameters
     * @throws IOException if an I/O error occurs
     */
    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        SSLSocket socket = (SSLSocket) wrappedFactory.createSocket(host, port);
        setParameters(socket);
        return socket;
    }

    /**
     * Creates a socket and connects it to the specified address and port,
     * binding to the specified local address and port.
     * Applies configured SSL parameters before returning.
     * 
     * @param address the server IP address
     * @param port the server port
     * @param localAddress the local address to bind to
     * @param localPort the local port to bind to
     * @return the SSL socket with configured parameters
     * @throws IOException if an I/O error occurs
     */
    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        SSLSocket socket = (SSLSocket) wrappedFactory.createSocket(address, port, localAddress, localPort);
        setParameters(socket);
        return socket;

    }

    /**
     * Creates an unconnected socket.
     * Applies configured SSL parameters before returning.
     * 
     * @return the unconnected SSL socket with configured parameters
     * @throws IOException if an I/O error occurs
     */
    @Override
    public Socket createSocket() throws IOException {
        SSLSocket socket = (SSLSocket) wrappedFactory.createSocket();
        setParameters(socket);
        return socket;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return wrappedFactory.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return wrappedFactory.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        SSLSocket socket = (SSLSocket) wrappedFactory.createSocket(s, host, port, autoClose);
        setParameters(socket);
        return socket;
    }

    private void setParameters(SSLSocket socket) {
        socket.setSSLParameters(sslParameters);
    }

}