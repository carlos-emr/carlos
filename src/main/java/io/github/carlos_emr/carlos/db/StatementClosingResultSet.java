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
 *
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.db;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * A {@link ResultSet} wrapper that ensures the parent {@link Statement} is closed
 * when {@link ResultSet#close()} is called on the result set.
 *
 * <p>{@link DBHandler} creates a {@code Statement} or {@code PreparedStatement},
 * executes it and returns only the {@link ResultSet} to the caller. Because the
 * caller has no reference to the underlying statement it can never close it, which
 * causes a resource (cursor/memory) leak on every call.
 *
 * <p>This class wraps the {@link ResultSet} via a JDK dynamic proxy so that all
 * method calls are forwarded transparently to the real result set. The only
 * behaviour that is changed is {@code close()}: it closes the result set first
 * and then closes the parent statement in a {@code finally} block, guaranteeing
 * that the statement is always released even if closing the result set throws.
 *
 * <p>Usage (inside {@link DBHandler}):
 * <pre>
 *     ResultSet rs = stmt.executeQuery(sql);
 *     return StatementClosingResultSet.wrap(rs, stmt);
 * </pre>
 *
 * <p>Callers need make no changes — the existing {@code rs.close()} already
 * correctly closes both the result set and the statement once this wrapper is
 * in place.
 *
 * @since 2026-04-07
 * @see DBHandler
 */
final class StatementClosingResultSet implements InvocationHandler {

    private final ResultSet delegate;
    private final Statement statement;

    private StatementClosingResultSet(ResultSet delegate, Statement statement) {
        this.delegate = delegate;
        this.statement = statement;
    }

    /**
     * Wraps {@code rs} so that closing the returned {@link ResultSet} also closes
     * the parent {@code stmt}.
     *
     * @param rs   the real {@link ResultSet} to delegate all method calls to; must not be null
     * @param stmt the {@link Statement} that produced {@code rs}; closed after
     *             {@code rs} on {@link ResultSet#close()}; must not be null
     * @return a proxy {@link ResultSet} that forwards all calls to {@code rs}
     *         and additionally closes {@code stmt} when {@code close()} is called
     * @throws IllegalArgumentException if either {@code rs} or {@code stmt} is null
     */
    static ResultSet wrap(ResultSet rs, Statement stmt) {
        if (rs == null) {
            throw new IllegalArgumentException("ResultSet must not be null");
        }
        if (stmt == null) {
            throw new IllegalArgumentException("Statement must not be null");
        }
        return (ResultSet) Proxy.newProxyInstance(
            ResultSet.class.getClassLoader(),
            new Class<?>[] { ResultSet.class },
            new StatementClosingResultSet(rs, stmt)
        );
    }

    /**
     * Intercepts all {@link ResultSet} method calls.
     *
     * <p>For {@code close()}: closes the delegate result set and then the parent
     * statement. If both operations throw, the statement's exception is attached
     * as a {@linkplain Throwable#addSuppressed suppressed exception} on the
     * result-set exception so no information is lost.
     * <p>For all other methods: forwards the call directly to the delegate.
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("close".equals(method.getName())) {
            Throwable rsThrowable = null;
            try {
                delegate.close();
            } catch (Throwable t) {
                rsThrowable = t;
            }
            try {
                statement.close();
            } catch (Throwable stmtThrowable) {
                if (rsThrowable != null) {
                    // rs.close() already failed — attach stmt failure as suppressed
                    rsThrowable.addSuppressed(stmtThrowable);
                } else {
                    // rs.close() succeeded — propagate the stmt failure
                    throw stmtThrowable;
                }
            }
            if (rsThrowable != null) {
                throw rsThrowable;
            }
            return null;
        }
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException e) {
            // Unwrap so callers see the real SQLException, not a reflection wrapper
            throw e.getCause();
        }
    }
}
