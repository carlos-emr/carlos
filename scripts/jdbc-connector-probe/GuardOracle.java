/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
import java.sql.*;
import java.util.*;

/** Records which statements each driver lets through executeQuery(), and whether the database changed. */
public class GuardOracle {
    public static void main(String[] a) throws Exception {
        String kind = a[0];
        Properties p = new Properties();
        p.setProperty("user", "probe");
        p.setProperty("password", "probe");
        String host = System.getProperty("probe.host", "127.0.0.1:33307");
        String opts = "zeroDateTimeBehavior=round&useOldAliasMetadataBehavior=true&jdbcCompliantTruncation=false";
        Driver d;
        String url;
        if (kind.equals("cj")) {
            d = (Driver) Class.forName("com.mysql.cj.jdbc.Driver").getDeclaredConstructor().newInstance();
            url = "jdbc:mysql://" + host + "/carlos?" + opts;
        } else {
            d = (Driver) Class.forName("io.github.carlos_emr.carlos.utility.MariaDbCompatibilityDriver").getDeclaredConstructor().newInstance();
            url = "jdbc:mysql://" + host + "/carlos?" + opts;
        }
        String[] sqls = {
            "SELECT 1", "select 1", "  \n\t SELECT 1", "(SELECT 1)", "((SELECT 1))", "( SELECT 1 ) UNION ( SELECT 2 )", "/* c */ SELECT 1", "/* c */ /* d */ SELECT 1", "-- c\nSELECT 1", "# c\nSELECT 1",
            "--\nSELECT 1", "-- c\n-- d\nSELECT 1", "SELECT 1 -- trailing", "SELECT 1;", "SELECT 1 ; ", ";SELECT 1",
            "SHOW TABLES LIKE 'zz_lock'", "show variables like 'sql_mode'", "DESCRIBE zz_lock", "DESC zz_lock", "EXPLAIN SELECT 1", "EXPLAIN UPDATE zz_lock SET v = v + 1", "ANALYZE TABLE zz_lock",
            "ANALYZE SELECT 1", "ANALYZE UPDATE zz_lock SET v = v + 1", "ANALYZE FORMAT=JSON DELETE FROM zz_lock WHERE id = 3", "CHECK TABLE zz_lock", "CHECKSUM TABLE zz_lock", "OPTIMIZE TABLE zz_lock",
            "REPAIR TABLE zz_lock", "HELP 'contents'", "TABLE zz_lock", "VALUES (1)", "WITH w AS (SELECT 1 a) SELECT a FROM w", "CALL zz_proc()", "call zz_proc()", "{call zz_proc()}",
            "EXECUTE zznostmt", "XA RECOVER", "XA START 'zzx'", "HANDLER zz_lock OPEN", "HANDLER zz_lock READ FIRST",
            "SELECT 1 INTO @zz1", "select 1 into @zz2", "SELECT 'into' ", "SELECT 1 /* INTO */", "SELECT `into` FROM (SELECT 1 AS `into`) x", "SELECT 1 AS into_x", "SELECT ' INTO OUTFILE '",
            "SELECT 1 FROM zz_lock WHERE s = 'x' INTO @zz3", "SELECT id INTO @zz4 FROM zz_lock LIMIT 1", "SELECT id FROM zz_lock LIMIT 1 INTO DUMPFILE '/tmp/zz_guard_dumpfile'",
            "SELECT id FROM zz_lock FOR UPDATE", "SELECT id FROM zz_lock LOCK IN SHARE MODE", "SELECT SLEEP(0)", "SELECT GET_LOCK('zz_g', 0)", "SELECT RELEASE_LOCK('zz_g')", "SELECT LAST_INSERT_ID(5)",
            "SELECT @zzv := 5", "SELECT NEXTVAL(zz_seq)", "SELECT zz_writer()",
            "/*! SELECT 1 */", "/*M! SELECT 1 */", "/*!50000 SELECT 1 */", "(SELECT 1 INTO @zz6)", "SELECT (1) INTO @zz7", "WITH w AS (SELECT 1 a) SELECT a FROM w INTO @zz8",
            "SELECT \"INTO\"", "SELECT 'a''b INTO c'", "SELECT 'a\\' INTO b'", "SELECT 1 -- INTO x", "SELECT 1 # INTO x", "SELECT 1 FROM zz_lock WHERE s = 'x'INTO @zz9", "SELECT 1 /* x */ INTO /* y */ @zz10",
            "SELECT 1\nINTO\n@zz11", "SELECT 1\tINTO\t@zz12", "Select 1", "sElEcT 1", "SHOW/**/TABLES", "XA  RECOVER", "xa recover", "XA\nRECOVER", "EXPLAIN INSERT INTO zz_lock (id, v) VALUES (920, 1)",
            "DESCRIBE UPDATE zz_lock SET v = v + 1", "ANALYZE INSERT INTO zz_lock (id, v) VALUES (921, 1)", "CALL zz_writer_proc()", "SELECT 1 FROM zz_lock INTO OUTFILE '/tmp/zz_guard_outfile'",
            "SELECT * FROM zz_lock WHERE s = 'INSERT INTO x'", "SELECT 1 FROM DUAL WHERE 'a' = 'UPDATE'", "﻿SELECT 1", "SELECT", "SELECT;", "(", "()", "WITH", "CALL", "EXECUTE", "XA", "DESC", "SHOW",
            "select * from zz_lock where id in (select id from zz_lock) into @zz13, @zz14, @zz15", "SELECT `a` FROM (SELECT 1 AS `a`) `into`", "SELECT 1 AS \"into\"", "SELECT 1 AS intox", "SELECT 1 AS x_into",
            "SELECT 1 INTOX", "SELECT 1,2 INTO@zz16,@zz17", "VALUES (1),(2)", "(VALUES (1))", "((VALUES (1)) UNION (SELECT 2))", "SELECT 1 UNION ALL (SELECT 2)", "  (  SELECT 1  )  ",
            "INSERT INTO zz_lock (id, v) VALUES (901, 1)", "insert into zz_lock (id, v) values (902, 1)", "  INSERT INTO zz_lock (id, v) VALUES (903, 1)", "(INSERT INTO zz_lock (id, v) VALUES (904, 1))",
            "/* c */ INSERT INTO zz_lock (id, v) VALUES (905, 1)", "/*! INSERT INTO zz_lock (id, v) VALUES (906, 1) */", "/*!50000 INSERT INTO zz_lock (id, v) VALUES (907, 1) */",
            "/*M! INSERT INTO zz_lock (id, v) VALUES (908, 1) */", "/*M!100000 INSERT INTO zz_lock (id, v) VALUES (909, 1) */", "-- c\nINSERT INTO zz_lock (id, v) VALUES (910, 1)", "# c\nINSERT INTO zz_lock (id, v) VALUES (911, 1)",
            "INSERT INTO zz_lock (id, v) VALUES (912, 1) RETURNING id", "INSERT INTO zz_lock (id, v) SELECT 913, 1", "UPDATE zz_lock SET v = v + 1", "update zz_lock set v = v + 1", "DELETE FROM zz_lock WHERE id = 3",
            "DELETE FROM zz_lock WHERE id = 3 RETURNING id", "REPLACE INTO zz_lock (id, v, s) VALUES (914, 1, 'r')", "CREATE TABLE zz_guard_created (a int)", "CREATE TEMPORARY TABLE zz_guard_tmp (a int)",
            "DROP TABLE zz_guard_victim", "TRUNCATE TABLE zz_guard_victim", "ALTER TABLE zz_guard_victim ADD COLUMN b int", "RENAME TABLE zz_guard_victim TO zz_guard_created", "CREATE OR REPLACE VIEW zz_guard_view AS SELECT 1 a",
            "SET @zz = 1", "SET SESSION group_concat_max_len = 2048", "SET STATEMENT max_statement_time=5 FOR SELECT 1", "SET STATEMENT max_statement_time=5 FOR UPDATE zz_lock SET v = v + 1", "DO SLEEP(0)", "USE carlos",
            "BEGIN", "START TRANSACTION", "COMMIT", "ROLLBACK", "SAVEPOINT zzs", "LOCK TABLES zz_lock WRITE", "UNLOCK TABLES", "FLUSH TABLES", "GRANT USAGE ON *.* TO 'probe'@'127.0.0.1'", "KILL QUERY 999999999",
            "PREPARE zzp FROM 'SELECT 1'", "DEALLOCATE PREPARE zzp", "LOAD DATA LOCAL INFILE '/etc/hostname' INTO TABLE zz_lock", "", "   ", "/* only a comment */", "garbage", "1", "SELEC 1", "SELECTED",
            "SELECT/**/1", "SELECT\n1", "SELECT(1)", "SELECT.1", "WITH w AS (SELECT 1 a) DELETE FROM zz_lock WHERE id IN (SELECT a FROM w)", "SHOW CREATE TABLE zz_lock", "BINLOG ''", "RESET QUERY CACHE", "SIGNAL SQLSTATE '45000'",
            "SELECT 1 UNION SELECT 2 INTO @zz5", "SELECT 1 PROCEDURE ANALYSE()", "EXECUTE IMMEDIATE 'UPDATE zz_lock SET v = v + 1'", "EXECUTE IMMEDIATE 'SELECT 1'", "BEGIN NOT ATOMIC UPDATE zz_lock SET v = v + 1; END"};
        try (Connection admin = d.connect(url, p)) {
            Statement ad = admin.createStatement();
            try { ad.execute("DROP FUNCTION IF EXISTS zz_writer"); ad.execute("CREATE FUNCTION zz_writer() RETURNS INT MODIFIES SQL DATA BEGIN UPDATE zz_lock SET v = v + 1 WHERE id = 1; RETURN 1; END"); } catch (SQLException e) { System.err.println("function setup: " + e.getMessage()); }
            try { ad.execute("DROP PROCEDURE IF EXISTS zz_writer_proc"); ad.execute("CREATE PROCEDURE zz_writer_proc() MODIFIES SQL DATA UPDATE zz_lock SET v = v + 1 WHERE id = 1"); } catch (SQLException e) { System.err.println("proc setup: " + e.getMessage()); }
            try { ad.execute("DROP SEQUENCE IF EXISTS zz_seq"); ad.execute("CREATE SEQUENCE zz_seq"); } catch (SQLException e) { System.err.println("sequence setup: " + e.getMessage()); }
            for (String api : new String[]{"S", "P"}) {
                for (String sql : sqls) {
                    try {
                        try { ad.execute("UNLOCK TABLES"); } catch (SQLException ignored) { }
                        ad.executeUpdate("DELETE FROM zz_lock");
                        ad.executeUpdate("INSERT INTO zz_lock VALUES (1,0,'a'),(2,0,'b'),(3,0,'c')");
                        ad.executeUpdate("DROP TABLE IF EXISTS zz_guard_created");
                        ad.executeUpdate("DROP VIEW IF EXISTS zz_guard_view");
                        ad.executeUpdate("DROP TABLE IF EXISTS zz_guard_victim");
                        ad.executeUpdate("CREATE TABLE zz_guard_victim (a int)");
                        ad.executeUpdate("INSERT INTO zz_guard_victim VALUES (1)");
                    } catch (SQLException e) { System.out.println("RESET-FAILED\t" + e.getMessage()); }
                    String before = state(ad);
                    String outcome;
                    try (Connection c = d.connect(url, p)) {
                        try {
                            if (api.equals("S")) { try (Statement t = c.createStatement(); ResultSet r = t.executeQuery(sql)) { outcome = "ran"; } }
                            else { try (PreparedStatement t = c.prepareStatement(sql); ResultSet r = t.executeQuery()) { outcome = "ran"; } }
                        } catch (SQLException e) {
                            String m = String.valueOf(e.getMessage());
                            outcome = (m.contains("cannot issue statements that do not produce result sets") || m.contains("Can not issue") ) ? "REFUSED-BY-DRIVER" : "server-error:" + e.getErrorCode();
                        } catch (RuntimeException e) { outcome = "runtime:" + e.getClass().getSimpleName(); }
                        try { c.createStatement().execute("UNLOCK TABLES"); } catch (SQLException ignored) { }
                    }
                    String after = state(ad);
                    System.out.println(api + "\t" + sql.replace("\n", "\\n").replace("\t", "\\t") + "\t" + outcome + "\t" + (before.equals(after) ? "unchanged" : "CHANGED"));
                }
            }
        }
    }

    static String state(Statement ad) throws SQLException {
        String victim;
        try (ResultSet r = ad.executeQuery("SELECT CONCAT(COUNT(*), '/', (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'zz_guard_victim')) FROM zz_guard_victim")) { r.next(); victim = r.getString(1); } catch (SQLException e) { victim = "gone"; }
        try (ResultSet r = ad.executeQuery("SELECT CONCAT(COUNT(*), ':', COALESCE(SUM(v), 0), ':', (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('zz_guard_created', 'zz_guard_view')), ':', (SELECT COUNT(*) FROM zz_lock WHERE id > 900)) FROM zz_lock")) {
            r.next();
            return r.getString(1) + "|" + victim;
        }
    }
}
