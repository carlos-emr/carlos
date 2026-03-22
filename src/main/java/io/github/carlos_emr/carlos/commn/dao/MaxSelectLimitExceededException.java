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
package io.github.carlos_emr.carlos.commn.dao;

/**
 * Exception thrown when a query requests more results than the configured maximum
 * select limit defined by {@link AbstractDao#MAX_LIST_RETURN_SIZE}.
 * <p>
 * This safeguard prevents unbounded memory consumption from excessively large
 * result sets in DAO list queries.
 *
 * @since 2001
 */
public class MaxSelectLimitExceededException extends RuntimeException {

    private int selectLimit;

    private int selectSize;

    /** Constructs a new exception with no detail message. */
    public MaxSelectLimitExceededException() {
        super();
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message String the detail message
     * @param cause   Throwable the cause
     */
    public MaxSelectLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message String the detail message
     */
    public MaxSelectLimitExceededException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified cause.
     *
     * @param cause Throwable the cause
     */
    public MaxSelectLimitExceededException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new exception with the specified message, select limit, and requested select size.
     *
     * @param message     String the detail message
     * @param selectLimit int the configured maximum select limit
     * @param selectSize  int the requested select size that exceeded the limit
     */
    public MaxSelectLimitExceededException(String message, int selectLimit, int selectSize) {
        this(message);
        this.selectLimit = selectLimit;
        this.selectSize = selectSize;
    }

    /**
     * Constructs a new exception with the select limit and requested size.
     *
     * @param selectLimit int the configured maximum select limit
     * @param selectSize  int the requested select size that exceeded the limit
     */
    public MaxSelectLimitExceededException(int selectLimit, int selectSize) {
        this("", selectLimit, selectSize);
    }

    /**
     * Returns the configured maximum select limit.
     *
     * @return int the select limit
     */
    public int getSelectLimit() {
        return selectLimit;
    }

    /**
     * Returns the requested select size that caused the exception.
     *
     * @return int the requested select size
     */
    public int getSelectSize() {
        return selectSize;
    }

}
