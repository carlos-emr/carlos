/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * Copyright (c) 2012-2018. CloudPractice Inc. All Rights Reserved.
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
 * Originally written for the Department of Family Medicine, McMaster University.
 * Ported from JunoEMR to the CARLOS EMR Project, 2026.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.webserv.rest.to;

import java.io.Serializable;

/**
 * Error payload for REST API error responses.
 *
 * <p>Contains a human-readable error message and an optional data payload
 * for additional error context. Used as the error field in
 * {@link GenericRestResponse} when an operation fails.</p>
 *
 * @see GenericRestResponse
 * @see RestResponse
 * @since 2026-02-10
 */
public class RestResponseError implements Serializable {

    private static final long serialVersionUID = 1L;

    private String message;
    private Serializable data;

    /**
     * Default constructor for serialization frameworks.
     */
    public RestResponseError() {
    }

    /**
     * Constructs an error with the specified message.
     *
     * @param message String the error message describing what went wrong
     */
    public RestResponseError(String message) {
        this.message = message;
    }

    /**
     * Constructs an error with a message and additional data.
     *
     * @param message String the error message describing what went wrong
     * @param data Serializable optional additional context about the error
     */
    public RestResponseError(String message, Serializable data) {
        this.message = message;
        this.data = data;
    }

    /**
     * Returns the error message.
     *
     * @return String the error message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the error message.
     *
     * @param message String the error message to set
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Returns optional additional error data.
     *
     * @return Serializable the additional error data, or null if not set
     */
    public Serializable getData() {
        return data;
    }

    /**
     * Sets optional additional error data.
     *
     * @param data Serializable the additional error data to set
     */
    public void setData(Serializable data) {
        this.data = data;
    }
}
