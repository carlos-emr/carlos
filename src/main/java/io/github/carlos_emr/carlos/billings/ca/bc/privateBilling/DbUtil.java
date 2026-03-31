package io.github.carlos_emr.carlos.billings.ca.bc.privateBilling;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import io.github.carlos_emr.CarlosProperties;

/*
 * Author: Charles Liu <charles.liu@nondfa.com>
 * Company: WELL Health Technologies Corp.
 * Date: December 6, 2018
 */
public class DbUtil {

    private static Connection connection = null;

    public static Connection getConnection() {
        if (connection != null) {
            return connection;
        } else {
            try {
                Properties oscarVariables = CarlosProperties.getInstance();
                String db_uri = oscarVariables.getProperty("db_uri");
                String db_name = oscarVariables.getProperty("db_name");
                String db_url = db_uri + db_name;
                String db_username = oscarVariables.getProperty("db_username");
                String db_password = oscarVariables.getProperty("db_password");
                connection = DriverManager.getConnection(db_url, db_username, db_password);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return connection;
        }
    }
}