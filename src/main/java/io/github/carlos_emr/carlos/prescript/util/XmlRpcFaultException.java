/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prescript.util;

/**
 * Exception thrown when an XML-RPC server returns a fault response.
 *
 * <p>Replaces {@code org.apache.xmlrpc.XmlRpcException} from the removed
 * xmlrpc:xmlrpc:1.2-b1 dependency. The public {@link #code} field preserves
 * the same API contract used by callers that inspect fault codes.</p>
 *
 * @since 2026-02-26
 */
public class XmlRpcFaultException extends Exception {

    private static final long serialVersionUID = 1L;

    /** The XML-RPC fault code returned by the server. */
    public final int code;

    /**
     * Creates a new XML-RPC fault exception.
     *
     * @param code    int the fault code from the XML-RPC {@code <fault>} response
     * @param message String the fault string from the XML-RPC {@code <fault>} response
     */
    public XmlRpcFaultException(int code, String message) {
        super(message);
        this.code = code;
    }
}
