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
package io.github.carlos_emr.carlos.demographic.cdsimport.support;

import io.github.carlos_emr.carlos.utility.CdsJdbcConnectionTimeouts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mysql.cj.jdbc.Driver;
import io.github.carlos_emr.carlos.demographic.cdsimport.cli.CdsCliSpringConfiguration;
import io.github.carlos_emr.carlos.utility.OscarTrackingBasicDataSource;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.commons.dbcp2.DriverConnectionFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Actual Connector/J merge semantics, framework wiring and bounded local silent-server transport. */
@Tag("unit")
class CdsJdbcConnectionTimeoutsUnitTest {
    private static final String URL="jdbc:mysql://db.invalid/synthetic";

    @Test
    void shouldRoundOwnerBudgetUpAndVerifyServerRetention_beforeTheLeaseCanBeUsed() throws Exception {
        var connection = org.mockito.Mockito.mock(java.sql.Connection.class);
        var change = org.mockito.Mockito.mock(java.sql.PreparedStatement.class);
        var query = org.mockito.Mockito.mock(java.sql.PreparedStatement.class);
        var rows = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        org.mockito.Mockito.when(connection.prepareStatement("SET SESSION wait_timeout=?")).thenReturn(change);
        org.mockito.Mockito.when(connection.prepareStatement("SELECT @@SESSION.wait_timeout")).thenReturn(query);
        org.mockito.Mockito.when(query.executeQuery()).thenReturn(rows);
        org.mockito.Mockito.when(rows.next()).thenReturn(true, false);
        org.mockito.Mockito.when(rows.getLong(1)).thenReturn(3600L);
        CdsJdbcConnectionTimeouts.retainOwnerForDuration(connection, 3_000_001);
        org.mockito.Mockito.verify(change).setLong(1, 3301L);
        org.mockito.Mockito.verify(change).execute();
        org.mockito.Mockito.when(rows.next()).thenReturn(true);
        org.mockito.Mockito.when(rows.getLong(1)).thenReturn(3300L);
        assertThatThrownBy(() -> CdsJdbcConnectionTimeouts.retainOwnerForDuration(connection, 3_000_001))
                .isInstanceOf(SQLException.class).hasMessage("CDS_JDBC_TIMEOUT_CONFIGURATION_INVALID");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MAX_VALUE})
    void shouldRefuseUnsupportedOwnerDuration_beforeAnyConnectionMutation(long duration) {
        var connection = org.mockito.Mockito.mock(java.sql.Connection.class);
        assertThatThrownBy(() -> CdsJdbcConnectionTimeouts.retainOwnerForDuration(connection, duration))
                .isInstanceOf(SQLException.class);
        org.mockito.Mockito.verifyNoInteractions(connection);
    }
    static class Inspectable extends OscarTrackingBasicDataSource {
        DriverConnectionFactory factory() throws SQLException { return (DriverConnectionFactory) createConnectionFactory(); }
    }
    static class CapturingDriver extends Driver {
        Properties seen;
        CapturingDriver() throws SQLException { }
        @Override public java.sql.Connection connect(String url, Properties properties) throws SQLException {
            seen=(Properties) properties.clone();
            throw new SQLException("Synthetic capture stopped before network", "08001");
        }
    }
    enum Boundary { MAINTENANCE, ORDINARY_LEASE, WEB_OPT_IN_POOL, CLI_POOL }

    @Test
    void shouldLeaveOrdinaryDefaultOffPoolUnchangedAndBoundOptIn_beforeFirstBorrow() throws Exception {
        try (var source=source(URL)) {
            assertThat(source.isBoundedCdsConnections()).isFalse();
            assertThat(effective(source.factory())).containsEntry("connectTimeout", "0").containsEntry("socketTimeout", "0");
            source.setBoundedCdsConnections(true);
            assertThat(effective(source.factory())).containsEntry("connectTimeout", "5000").containsEntry("socketTimeout", "60000");
            assertThat(source.getConnectionPool()).isNull();
        }
    }

    @Test
    void shouldPreserveUrlOptionsAndHigherPrecedenceProperties_withoutMutatingThem() throws Exception {
        Properties properties=new Properties();
        properties.setProperty("connectTimeout", "2345"); properties.setProperty("socketTimeout", "3456");
        var original=new DriverConnectionFactory(new Driver(), URL+"?connectTimeout=4567&socketTimeout=5678", properties);
        var bounded=(DriverConnectionFactory) CdsJdbcConnectionTimeouts.bounded(original);
        assertThat(effective(bounded)).containsEntry("connectTimeout", "2345").containsEntry("socketTimeout", "3456");
        assertThat(properties).containsEntry("connectTimeout", "2345").containsEntry("socketTimeout", "3456");
        assertThat(bounded.getProperties()).isNotSameAs(properties);
        var urlOnly=(DriverConnectionFactory) CdsJdbcConnectionTimeouts.bounded(
                new DriverConnectionFactory(new Driver(), URL+"?connectTimeout=4567&socketTimeout=5678", new Properties()));
        assertThat(effective(urlOnly)).containsEntry("connectTimeout", "4567").containsEntry("socketTimeout", "5678");
    }

    @Test
    void shouldRetainFrameworkDefaultHostAndSingleHostDescriptorPrecedence_whenBounded() throws Exception {
        var local=(DriverConnectionFactory) CdsJdbcConnectionTimeouts.bounded(
                new DriverConnectionFactory(new Driver(), "jdbc:mysql:///synthetic", new Properties()));
        assertThat(effective(local)).containsEntry("connectTimeout", "5000").containsEntry("socketTimeout", "60000");
        var addressed=(DriverConnectionFactory) CdsJdbcConnectionTimeouts.bounded(new DriverConnectionFactory(new Driver(),
                "jdbc:mysql://address=(host=db.invalid)(connectTimeout=1000)(socketTimeout=2000)/synthetic?connectTimeout=0&socketTimeout=0", new Properties()));
        assertThat(effective(addressed)).containsEntry("connectTimeout", "1000").containsEntry("socketTimeout", "2000");
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    void shouldScopeMissingNonpoolDefaults_evenWhenOrdinaryPoolOptsOut(boolean ordinaryLease) throws Exception {
        try (var source=source(URL)) {
            var driver=new CapturingDriver(); source.setDriver(driver);
            assertThat(source.isBoundedCdsConnections()).isFalse();
            if (ordinaryLease) assertThatThrownBy(source::acquireOrdinaryAdmissionLease).isInstanceOf(SQLException.class);
            else assertThatThrownBy(source::openUnpooledCdsMaintenanceConnection).isInstanceOf(SQLException.class);
            assertThat(driver.seen).containsEntry("connectTimeout", "5000").containsEntry("socketTimeout", "60000");
            assertThat(effective(source.factory())).containsEntry("connectTimeout", "0").containsEntry("socketTimeout", "0");
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"connectTimeout=0", "socketTimeout=0", "connectTimeout=-1", "socketTimeout=-1",
            "connectTimeout=30001", "socketTimeout=300001", "socketTimeout=private-configuration-value"})
    void shouldRejectUnboundedInvalidAndOverCeilingOptions_withoutLeakingConfiguration(String option) throws Exception {
        var configured=new DriverConnectionFactory(new Driver(), URL+"?"+option, new Properties());
        assertThatThrownBy(() -> CdsJdbcConnectionTimeouts.bounded(configured)).isInstanceOf(SQLException.class)
                .hasMessage("CDS_JDBC_TIMEOUT_CONFIGURATION_INVALID").hasNoCause()
                .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("08001"));
    }

    @ParameterizedTest
    @ValueSource(strings={
            "jdbc:mysql://address=(host=one.invalid)(connectTimeout=1000)(socketTimeout=2000),address=(host=two.invalid)(connectTimeout=3000)(socketTimeout=0)/synthetic",
            "jdbc:mysql://address=(host=two.invalid)(connectTimeout=3000)(socketTimeout=0),address=(host=one.invalid)(connectTimeout=1000)(socketTimeout=2000)/synthetic",
            "jdbc:mysql://one.invalid,two.invalid/synthetic?connectTimeout=1000&socketTimeout=2000",
            "jdbc:mysql:loadbalance://one.invalid/synthetic", "jdbc:mysql:replication://one.invalid/synthetic",
            "jdbc:mysql+srv://unresolved.invalid/synthetic", "jdbc:mysql://unresolved.invalid/synthetic?dnsSrv=true"})
    void shouldRefuseUnsupportedTopology_beforeAnyDriverConnectionOrSrvLookup(String url) throws Exception {
        var driver=new CapturingDriver();
        var factory=new DriverConnectionFactory(driver, url, new Properties());
        assertThatThrownBy(() -> CdsJdbcConnectionTimeouts.bounded(factory)).isInstanceOf(SQLException.class)
                .hasMessage("CDS_JDBC_SINGLE_HOST_REQUIRED");
        assertThat(driver.seen).isNull();
    }

    @ParameterizedTest
    @EnumSource(Boundary.class)
    void shouldTimeOutSilentHandshake_atEveryControlledBoundary(Boundary boundary) throws Exception {
        CountDownLatch accepted=new CountDownLatch(1), release=new CountDownLatch(1);
        try (var server=new ServerSocket(0, 1, InetAddress.getLoopbackAddress()); var peer=Executors.newSingleThreadExecutor()) {
            server.setSoTimeout(3000);
            var serving=peer.submit(() -> {
                try (var socket=server.accept()) {
                    accepted.countDown();
                    // A broken timeout still cannot strand this test or its peer beyond3seconds.
                    release.await(3, TimeUnit.SECONDS);
                }
                return null;
            });
            String host=server.getInetAddress().getHostAddress();
            if (host.contains(":")) host="["+host+"]";
            String uri="jdbc:mysql://"+host+":"+server.getLocalPort()+"/synthetic?connectTimeout=200&socketTimeout=200&useSSL=false";
            try (OscarTrackingBasicDataSource source=boundary==Boundary.CLI_POOL ? cliSource(uri) : source(uri)) {
                if (boundary==Boundary.WEB_OPT_IN_POOL) source.setBoundedCdsConnections(true);
                long started=System.nanoTime();
                Throwable problem;
                try {
                    if (boundary==Boundary.MAINTENANCE) try (var unused=source.openUnpooledCdsMaintenanceConnection()) { throw new AssertionError("Silent peer supplied no handshake"); }
                    else if (boundary==Boundary.ORDINARY_LEASE) try (var unused=source.acquireOrdinaryAdmissionLease()) { throw new AssertionError("Silent peer supplied no handshake"); }
                    else try (var unused=source.getConnection()) { throw new AssertionError("Silent peer supplied no handshake"); }
                } catch (SQLException expected) { problem=expected; }
                Duration elapsed=Duration.ofNanos(System.nanoTime()-started);
                assertThat(accepted.await(1, TimeUnit.SECONDS)).isTrue();
                assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(100)).isLessThan(Duration.ofSeconds(2));
                boolean timedOut=false;
                for (Throwable cause=problem; cause!=null; cause=cause.getCause()) if (cause instanceof SocketTimeoutException) timedOut=true;
                assertThat(timedOut).as("Actual Connector/J socket read timed out").isTrue();
            } finally {
                release.countDown();
                serving.get(4, TimeUnit.SECONDS);
                peer.shutdownNow();
            }
        }
    }

    @Test
    void shouldWireExistingGuardOptInBeforeFlyway_withOrdinaryFalseDefault() throws Exception {
        var factory=DocumentBuilderFactory.newDefaultInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var document=factory.newDocumentBuilder().parse(Path.of("src/main/resources/spring_jpa.xml").toFile());
        var properties=document.getElementsByTagName("property");
        String bounded=null;
        for (int i=0; i<properties.getLength(); i++) {
            var property=(org.w3c.dom.Element) properties.item(i);
            if (property.getAttribute("name").equals("boundedCdsConnections")) {
                assertThat(((org.w3c.dom.Element) property.getParentNode()).getAttribute("id")).isEqualTo("dataSource");
                bounded=property.getAttribute("value");
            }
        }
        assertThat(bounded).isEqualTo("${cds.import.maintenanceGuardEnabled:false}");
    }

    private static Inspectable source(String url) {
        var source=new Inspectable();source.setDriverClassName("com.mysql.cj.jdbc.Driver");source.setUrl(url);return source;
    }
    private static OscarTrackingBasicDataSource cliSource(String url) {
        Properties properties=new Properties();properties.setProperty("db_driver", "com.mysql.cj.jdbc.Driver");
        properties.setProperty("db_uri", url.substring(0, url.lastIndexOf('/')+1));properties.setProperty("db_name", url.substring(url.lastIndexOf('/')+1));
        properties.setProperty("db_username", "synthetic");properties.setProperty("db_password", "synthetic");
        var source=new CdsCliSpringConfiguration().dataSource(properties);
        assertThat(source.isBoundedCdsConnections()).isTrue();return source;
    }
    private static Map<String, String> effective(DriverConnectionFactory factory) throws SQLException {
        Map<String, String> values=new HashMap<>();
        for (DriverPropertyInfo property:factory.getDriver().getPropertyInfo(factory.getConnectionString(), factory.getProperties()))
            if (property.name.equals("connectTimeout") || property.name.equals("socketTimeout")) values.put(property.name, property.value);
        return values;
    }
}
