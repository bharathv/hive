/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hive.jdbc;

import static org.apache.hadoop.hive.ql.exec.ExplainTask.EXPL_COLUMN_NAME;
import static org.apache.hadoop.hive.ql.processors.SetProcessor.SET_COLUMN_NAME;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import junit.framework.TestCase;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.TableType;
import org.apache.hadoop.hive.ql.hooks.ExecuteWithHookContext;
import org.apache.hadoop.hive.ql.hooks.HookContext;
import org.apache.hive.service.cli.operation.ClassicTableTypeMapping;
import org.apache.hive.service.cli.operation.ClassicTableTypeMapping.ClassicTableTypes;
import org.apache.hive.service.cli.operation.HiveTableTypeMapping;
import org.apache.hive.service.cli.operation.TableTypeMappingFactory.TableTypeMappings;

/**
 * TestJdbcDriver2
 *
 */
public class TestJdbcDriver2 extends TestCase {
  private static final String driverName = "org.apache.hive.jdbc.HiveDriver";
  private static final String tableName = "testHiveJdbcDriver_Table";
  private static final String tableComment = "Simple table";
  private static final String viewName = "testHiveJdbcDriverView";
  private static final String viewComment = "Simple view";
  private static final String partitionedTableName = "testHiveJdbcDriverPartitionedTable";
  private static final String partitionedColumnName = "partcolabc";
  private static final String partitionedColumnValue = "20090619";
  private static final String partitionedTableComment = "Partitioned table";
  private static final String dataTypeTableName = "testDataTypeTable";
  private static final String dataTypeTableComment = "Table with many column data types";
  private static final String QueryTag = "myQueryTag";
  private final HiveConf conf;
  private final Path dataFilePath;
  private final Path dataTypeDataFilePath;
  private Connection con;
  private boolean standAloneServer = false;

  public TestJdbcDriver2(String name) {
    super(name);
    conf = new HiveConf(TestJdbcDriver2.class);
    String dataFileDir = conf.get("test.data.files").replace('\\', '/')
        .replace("c:", "");
    dataFilePath = new Path(dataFileDir, "kv1.txt");
    dataTypeDataFilePath = new Path(dataFileDir, "datatypes.txt");
    standAloneServer = "true".equals(System
        .getProperty("test.service.standalone.server"));
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Class.forName(driverName);
    if (standAloneServer) {
      // get connection
      con = DriverManager.getConnection("jdbc:hive2://localhost:10000/default",
          "", "");
    } else {
      con = DriverManager.getConnection("jdbc:hive2://", "", "");
    }
    assertNotNull("Connection is null", con);
    assertFalse("Connection should not be closed", con.isClosed());
    Statement stmt = con.createStatement();
    assertNotNull("Statement is null", stmt);

    stmt.execute("set hive.support.concurrency = false");

    // drop table. ignore error.
    try {
      stmt.execute("drop table " + tableName);
    } catch (Exception ex) {
      fail(ex.toString());
    }

    ResultSet res;
    // create table
    stmt.execute("create table " + tableName
        + " (under_col int comment 'the under column', value string) comment '"
        + tableComment + "'");

    // load data
    stmt.execute("load data local inpath '"
        + dataFilePath.toString() + "' into table " + tableName);

    // also initialize a paritioned table to test against.

    // drop table. ignore error.
    try {
      stmt.execute("drop table " + partitionedTableName);
    } catch (Exception ex) {
      fail(ex.toString());
    }

    stmt.execute("create table " + partitionedTableName
        + " (under_col int, value string) comment '"+partitionedTableComment
            +"' partitioned by (" + partitionedColumnName + " STRING)");

    // load data
    stmt.execute("load data local inpath '"
        + dataFilePath.toString() + "' into table " + partitionedTableName
        + " PARTITION (" + partitionedColumnName + "="
        + partitionedColumnValue + ")");

    // drop table. ignore error.
    try {
      stmt.execute("drop table " + dataTypeTableName);
    } catch (Exception ex) {
      fail(ex.toString());
    }

   stmt.execute("create table " + dataTypeTableName
        + " (c1 int, c2 boolean, c3 double, c4 string,"
        + " c5 array<int>, c6 map<int,string>, c7 map<string,string>,"
        + " c8 struct<r:string,s:int,t:double>,"
        + " c9 tinyint, c10 smallint, c11 float, c12 bigint,"
        + " c13 array<array<string>>,"
        + " c14 map<int, map<int,int>>,"
        + " c15 struct<r:int,s:struct<a:int,b:string>>,"
        + " c16 array<struct<m:map<string,string>,n:int>>,"
        + " c17 timestamp, "
        + " c18 decimal, "
        + " c19 binary) comment'" + dataTypeTableComment
            +"' partitioned by (dt STRING)");

    stmt.execute("load data local inpath '"
        + dataTypeDataFilePath.toString() + "' into table " + dataTypeTableName
        + " PARTITION (dt='20090619')");

    // drop view. ignore error.
    try {
      stmt.execute("drop view " + viewName);
    } catch (Exception ex) {
      fail(ex.toString());
    }

    // create view
    stmt.execute("create view " + viewName + " comment '"+viewComment
            +"' as select * from "+ tableName);
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();

    // drop table
    Statement stmt = con.createStatement();
    assertNotNull("Statement is null", stmt);
    stmt.execute("set hive.server2.blocking.query=true");
    QueryBlockHook.clearAll();
    stmt.execute("drop table " + tableName);
    stmt.execute("drop table " + partitionedTableName);
    stmt.execute("drop table " + dataTypeTableName);

    con.close();
    assertTrue("Connection should be closed", con.isClosed());

    Exception expectedException = null;
    try {
      con.createStatement();
    } catch (Exception e) {
      expectedException = e;
    }

    assertNotNull(
        "createStatement() on closed connection should throw exception",
        expectedException);
  }

  public void testBadURL() throws Exception {
    checkBadUrl("jdbc:hive2://localhost:10000;principal=test");
    checkBadUrl("jdbc:hive2://localhost:10000;" +
    		"principal=hive/HiveServer2Host@YOUR-REALM.COM");
    checkBadUrl("jdbc:hive2://localhost:10000test");
  }


  private void checkBadUrl(String url) throws SQLException {
    try{
      DriverManager.getConnection(url, "", "");
      fail("should have thrown IllegalArgumentException but did not ");
    }catch(SQLException i){
      assertTrue(i.getMessage().contains("Bad URL format. Hostname not found "
          + " in authority part of the url"));
    }
  }

  public void testDataTypes2() throws Exception {
    Statement stmt = con.createStatement();

    ResultSet res = stmt.executeQuery(
        "select c5, c1 from " + dataTypeTableName + " order by c1");
    ResultSetMetaData meta = res.getMetaData();

    // row 1
    assertTrue(res.next());
    // skip the last (partitioning) column since it is always non-null
    for (int i = 1; i < meta.getColumnCount(); i++) {
      assertNull(res.getObject(i));
    }

  }
  public void testErrorDiag() throws SQLException {
    Statement stmt = con.createStatement();

    // verify syntax error
    try {
      ResultSet res = stmt.executeQuery("select from " + dataTypeTableName);
    } catch (SQLException e) {
      assertEquals("42000", e.getSQLState());
    }

    // verify table not fuond error
    try {
      ResultSet res = stmt.executeQuery("select * from nonTable");
    } catch (SQLException e) {
      assertEquals("42S02", e.getSQLState());
    }

    // verify invalid column error
    try {
      ResultSet res = stmt.executeQuery("select zzzz from " + dataTypeTableName);
    } catch (SQLException e) {
      assertEquals("42000", e.getSQLState());
    }

  }

  /**
   * verify 'explain ...' resultset
   * @throws SQLException
   */
  public void testExplainStmt() throws SQLException {
    Statement stmt = con.createStatement();

    ResultSet res = stmt.executeQuery(
        "explain select c1, c2, c3, c4, c5 as a, c6, c7, c8, c9, c10, c11, c12, " +
        "c1*2, sentences(null, null, null) as b from " + dataTypeTableName + " limit 1");

    ResultSetMetaData md = res.getMetaData();
    assertEquals(md.getColumnCount(), 1); // only one result column
    assertEquals(md.getColumnLabel(1), EXPL_COLUMN_NAME); // verify the column name
    //verify that there is data in the resultset
    assertTrue("Nothing returned explain", res.next());
  }

  public void testPrepareStatement() {

    String sql = "from (select count(1) from "
        + tableName
        + " where   'not?param?not?param' <> 'not_param??not_param' and ?=? "
        + " and 1=? and 2=? and 3.0=? and 4.0=? and 'test\\'string\"'=? and 5=? and ?=? "
        + " ) t  select '2011-03-25' ddate,'China',true bv, 10 num limit 10";

     ///////////////////////////////////////////////
    //////////////////// correct testcase
    //////////////////////////////////////////////
    try {
      PreparedStatement ps = con.prepareStatement(sql);

      ps.setBoolean(1, true);
      ps.setBoolean(2, true);

      ps.setShort(3, Short.valueOf("1"));
      ps.setInt(4, 2);
      ps.setFloat(5, 3f);
      ps.setDouble(6, Double.valueOf(4));
      ps.setString(7, "test'string\"");
      ps.setLong(8, 5L);
      ps.setByte(9, (byte) 1);
      ps.setByte(10, (byte) 1);

      ps.setMaxRows(2);

      assertTrue(true);

      ResultSet res = ps.executeQuery();
      assertNotNull(res);

      while (res.next()) {
        assertEquals("2011-03-25", res.getString("ddate"));
        assertEquals("10", res.getString("num"));
        assertEquals((byte) 10, res.getByte("num"));
        assertEquals("2011-03-25", res.getDate("ddate").toString());
        assertEquals(Double.valueOf(10).doubleValue(), res.getDouble("num"), 0.1);
        assertEquals(10, res.getInt("num"));
        assertEquals(Short.valueOf("10").shortValue(), res.getShort("num"));
        assertEquals(10L, res.getLong("num"));
        assertEquals(true, res.getBoolean("bv"));
        Object o = res.getObject("ddate");
        assertNotNull(o);
        o = res.getObject("num");
        assertNotNull(o);
      }
      res.close();
      assertTrue(true);

      ps.close();
      assertTrue(true);

    } catch (Exception e) {
      e.printStackTrace();
      fail(e.toString());
    }

     ///////////////////////////////////////////////
    //////////////////// other failure testcases
    //////////////////////////////////////////////
    // set nothing for prepared sql
    Exception expectedException = null;
    try {
      PreparedStatement ps = con.prepareStatement(sql);
      ps.executeQuery();
    } catch (Exception e) {
      expectedException = e;
    }
    assertNotNull(
        "Execute the un-setted sql statement should throw exception",
        expectedException);

    // set some of parameters for prepared sql, not all of them.
    expectedException = null;
    try {
      PreparedStatement ps = con.prepareStatement(sql);
      ps.setBoolean(1, true);
      ps.setBoolean(2, true);
      ps.executeQuery();
    } catch (Exception e) {
      expectedException = e;
    }
    assertNotNull(
        "Execute the invalid setted sql statement should throw exception",
        expectedException);

    // set the wrong type parameters for prepared sql.
    expectedException = null;
    try {
      PreparedStatement ps = con.prepareStatement(sql);

      // wrong type here
      ps.setString(1, "wrong");

      assertTrue(true);
      ResultSet res = ps.executeQuery();
      if (!res.next()) {
        throw new Exception("there must be a empty result set");
      }
    } catch (Exception e) {
      expectedException = e;
    }
    assertNotNull(
        "Execute the invalid setted sql statement should throw exception",
        expectedException);
  }

  public final void testSelectAll() throws Exception {
    doTestSelectAll(tableName, -1, -1); // tests not setting maxRows (return all)
    doTestSelectAll(tableName, 0, -1); // tests setting maxRows to 0 (return all)
  }

  public final void testSelectAllPartioned() throws Exception {
    doTestSelectAll(partitionedTableName, -1, -1); // tests not setting maxRows
    // (return all)
    doTestSelectAll(partitionedTableName, 0, -1); // tests setting maxRows to 0
    // (return all)
  }

  public final void testSelectAllMaxRows() throws Exception {
    doTestSelectAll(tableName, 100, -1);
  }

  public final void testSelectAllFetchSize() throws Exception {
    doTestSelectAll(tableName, 100, 20);
  }

  public void testDataTypes() throws Exception {
    Statement stmt = con.createStatement();

    ResultSet res = stmt.executeQuery(
        "select * from " + dataTypeTableName + " order by c1");
    ResultSetMetaData meta = res.getMetaData();

    // row 1
    assertTrue(res.next());
    // skip the last (partitioning) column since it is always non-null
    for (int i = 1; i < meta.getColumnCount(); i++) {
      assertNull(res.getObject(i));
    }
    // getXXX returns 0 for numeric types, false for boolean and null for other
    assertEquals(0, res.getInt(1));
    assertEquals(false, res.getBoolean(2));
    assertEquals(0d, res.getDouble(3));
    assertEquals(null, res.getString(4));
    assertEquals(null, res.getString(5));
    assertEquals(null, res.getString(6));
    assertEquals(null, res.getString(7));
    assertEquals(null, res.getString(8));
    assertEquals(0, res.getByte(9));
    assertEquals(0, res.getShort(10));
    assertEquals(0f, res.getFloat(11));
    assertEquals(0L, res.getLong(12));
    assertEquals(null, res.getString(13));
    assertEquals(null, res.getString(14));
    assertEquals(null, res.getString(15));
    assertEquals(null, res.getString(16));
    assertEquals(null, res.getString(17));
    assertEquals(null, res.getString(18));
    assertEquals(null, res.getString(19));

    // row 2
    assertTrue(res.next());
    assertEquals(-1, res.getInt(1));
    assertEquals(false, res.getBoolean(2));
    assertEquals(-1.1d, res.getDouble(3));
    assertEquals("", res.getString(4));
    assertEquals("[]", res.getString(5));
    assertEquals("{}", res.getString(6));
    assertEquals("{}", res.getString(7));
    assertEquals("{\"r\":null,\"s\":null,\"t\":null}", res.getString(8));
    assertEquals(-1, res.getByte(9));
    assertEquals(-1, res.getShort(10));
    assertEquals(-1.0f, res.getFloat(11));
    assertEquals(-1, res.getLong(12));
    assertEquals("[]", res.getString(13));
    assertEquals("{}", res.getString(14));
    assertEquals("{\"r\":null,\"s\":null}", res.getString(15));
    assertEquals("[]", res.getString(16));
    assertEquals(null, res.getString(17));
    assertEquals(null, res.getTimestamp(17));
    assertEquals(null, res.getBigDecimal(18));
    assertEquals(null, res.getString(19));

    // row 3
    assertTrue(res.next());
    assertEquals(1, res.getInt(1));
    assertEquals(true, res.getBoolean(2));
    assertEquals(1.1d, res.getDouble(3));
    assertEquals("1", res.getString(4));
    assertEquals("[1,2]", res.getString(5));
    assertEquals("{1:\"x\",2:\"y\"}", res.getString(6));
    assertEquals("{\"k\":\"v\"}", res.getString(7));
    assertEquals("{\"r\":\"a\",\"s\":9,\"t\":2.2}", res.getString(8));
    assertEquals(1, res.getByte(9));
    assertEquals(1, res.getShort(10));
    assertEquals(1.0f, res.getFloat(11));
    assertEquals(1, res.getLong(12));
    assertEquals("[[\"a\",\"b\"],[\"c\",\"d\"]]", res.getString(13));
    assertEquals("{1:{11:12,13:14},2:{21:22}}", res.getString(14));
    assertEquals("{\"r\":1,\"s\":{\"a\":2,\"b\":\"x\"}}", res.getString(15));
    assertEquals("[{\"m\":{},\"n\":1},{\"m\":{\"a\":\"b\",\"c\":\"d\"},\"n\":2}]", res.getString(16));
    assertEquals("2012-04-22 09:00:00.123456789", res.getString(17));
    assertEquals("2012-04-22 09:00:00.123456789", res.getTimestamp(17).toString());
    assertEquals("123456789.0123456", res.getBigDecimal(18).toString());
    assertEquals("abcd", res.getString(19));

    // test getBoolean rules on non-boolean columns
    assertEquals(true, res.getBoolean(1));
    assertEquals(true, res.getBoolean(4));

    // no more rows
    assertFalse(res.next());
  }

  private void doTestSelectAll(String tableName, int maxRows, int fetchSize) throws Exception {
    boolean isPartitionTable = tableName.equals(partitionedTableName);

    Statement stmt = con.createStatement();
    if (maxRows >= 0) {
      stmt.setMaxRows(maxRows);
    }
    if (fetchSize > 0) {
      stmt.setFetchSize(fetchSize);
      assertEquals(fetchSize, stmt.getFetchSize());
    }

    // JDBC says that 0 means return all, which is the default
    int expectedMaxRows = maxRows < 1 ? 0 : maxRows;

    assertNotNull("Statement is null", stmt);
    assertEquals("Statement max rows not as expected", expectedMaxRows, stmt
        .getMaxRows());
    assertFalse("Statement should not be closed", stmt.isClosed());

    ResultSet res;

    // run some queries
    res = stmt.executeQuery("select * from " + tableName);
    assertNotNull("ResultSet is null", res);
    assertTrue("getResultSet() not returning expected ResultSet", res == stmt
        .getResultSet());
    assertEquals("get update count not as expected", 0, stmt.getUpdateCount());
    int i = 0;

    ResultSetMetaData meta = res.getMetaData();
    int expectedColCount = isPartitionTable ? 3 : 2;
    assertEquals(
      "Unexpected column count", expectedColCount, meta.getColumnCount());

    boolean moreRow = res.next();
    while (moreRow) {
      try {
        i++;
        assertEquals(res.getInt(1), res.getInt("under_col"));
        assertEquals(res.getString(1), res.getString("under_col"));
        assertEquals(res.getString(2), res.getString("value"));
        if (isPartitionTable) {
          assertEquals(res.getString(3), partitionedColumnValue);
          assertEquals(res.getString(3), res.getString(partitionedColumnName));
        }
        assertFalse("Last result value was not null", res.wasNull());
        assertNull("No warnings should be found on ResultSet", res
            .getWarnings());
        res.clearWarnings(); // verifying that method is supported

        // System.out.println(res.getString(1) + " " + res.getString(2));
        assertEquals(
            "getInt and getString don't align for the same result value",
            String.valueOf(res.getInt(1)), res.getString(1));
        assertEquals("Unexpected result found", "val_" + res.getString(1), res
            .getString(2));
        moreRow = res.next();
      } catch (SQLException e) {
        System.out.println(e.toString());
        e.printStackTrace();
        throw new Exception(e.toString());
      }
    }

    // supposed to get 500 rows if maxRows isn't set
    int expectedRowCount = maxRows > 0 ? maxRows : 500;
    assertEquals("Incorrect number of rows returned", expectedRowCount, i);

    // should have no more rows
    assertEquals(false, moreRow);

    assertNull("No warnings should be found on statement", stmt.getWarnings());
    stmt.clearWarnings(); // verifying that method is supported

    assertNull("No warnings should be found on connection", con.getWarnings());
    con.clearWarnings(); // verifying that method is supported

    stmt.close();
    assertTrue("Statement should be closed", stmt.isClosed());
  }

  public void testErrorMessages() throws SQLException {
    String invalidSyntaxSQLState = "42000";

    // These tests inherently cause exceptions to be written to the test output
    // logs. This is undesirable, since you it might appear to someone looking
    // at the test output logs as if something is failing when it isn't. Not
    // sure
    // how to get around that.
    doTestErrorCase("SELECTT * FROM " + tableName,
        "cannot recognize input near 'SELECTT' '*' 'FROM'",
        invalidSyntaxSQLState, 40000);
    doTestErrorCase("SELECT * FROM some_table_that_does_not_exist",
        "Table not found", "42S02", 10001);
    doTestErrorCase("drop table some_table_that_does_not_exist",
        "Table not found", "42S02", 10001);
    doTestErrorCase("SELECT invalid_column FROM " + tableName,
        "Invalid table alias or column reference", invalidSyntaxSQLState, 10004);
    doTestErrorCase("SELECT invalid_function(under_col) FROM " + tableName,
    "Invalid function", invalidSyntaxSQLState, 10011);

    // TODO: execute errors like this currently don't return good error
    // codes and messages. This should be fixed.
    doTestErrorCase(
        "create table " + tableName + " (key int, value string)",
        "FAILED: Execution Error, return code 1 from org.apache.hadoop.hive.ql.exec.DDLTask",
        "08S01", 1);
  }

  private void doTestErrorCase(String sql, String expectedMessage,
      String expectedSQLState, int expectedErrorCode) throws SQLException {
    Statement stmt = con.createStatement();
    boolean exceptionFound = false;
    try {
      stmt.execute(sql);
    } catch (SQLException e) {
      assertTrue("Adequate error messaging not found for '" + sql + "': "
          + e.getMessage(), e.getMessage().contains(expectedMessage));
      assertEquals("Expected SQLState not found for '" + sql + "'",
          expectedSQLState, e.getSQLState());
      assertEquals("Expected error code not found for '" + sql + "'",
          expectedErrorCode, e.getErrorCode());
      exceptionFound = true;
    }

    assertNotNull("Exception should have been thrown for query: " + sql,
        exceptionFound);
  }

  public void testGetLog() throws Exception {
    HiveStatement stmt = (HiveStatement)con.createStatement();
    assertNotNull("Statement is null", stmt);

    ResultSet res = stmt.executeQuery("select count(*) from " + tableName);
    ResultSetMetaData meta = res.getMetaData();

    boolean moreRow = res.next();
    while (moreRow) {
      try {
        moreRow = res.next();
      } catch (SQLException e) {
        throw e;
      }
    }

    String log = stmt.getLog();
    assertTrue("Operation Log looks incorrect" ,
        log.contains("Parsing command: select count(*) from testHiveJdbcDriver_Table"));
    assertTrue("Operation Log looks incorrect",
        log.contains( "select count(*) from testHiveJdbcDriver_Table"));

  }

  public void testShowTables() throws SQLException {
    Statement stmt = con.createStatement();
    assertNotNull("Statement is null", stmt);

    ResultSet res = stmt.executeQuery("show tables");

    boolean testTableExists = false;
    while (res.next()) {
      assertNotNull("table name is null in result set", res.getString(1));
      if (tableName.equalsIgnoreCase(res.getString(1))) {
        testTableExists = true;
      }
    }

    assertTrue("table name " + tableName
        + " not found in SHOW TABLES result set", testTableExists);
  }

  public void testMetaDataGetTables() throws SQLException {
    getTablesTest(TableType.MANAGED_TABLE.toString(), TableType.VIRTUAL_VIEW.toString());
  }

  public  void testMetaDataGetTablesHive() throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("set " + HiveConf.ConfVars.HIVE_SERVER2_TABLE_TYPE_MAPPING.varname +
        " = " + TableTypeMappings.HIVE.toString());
    getTablesTest(TableType.MANAGED_TABLE.toString(), TableType.VIRTUAL_VIEW.toString());
  }

  public  void testMetaDataGetTablesClassic() throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("set " + HiveConf.ConfVars.HIVE_SERVER2_TABLE_TYPE_MAPPING.varname +
        " = " + TableTypeMappings.CLASSIC.toString());
    stmt.close();
    getTablesTest(ClassicTableTypes.TABLE.toString(), ClassicTableTypes.VIEW.toString());
  }

  private void getTablesTest(String tableTypeName, String viewTypeName) throws SQLException {
    Map<String, Object[]> tests = new HashMap<String, Object[]>();
    tests.put("test%jdbc%", new Object[]{"testhivejdbcdriver_table"
            , "testhivejdbcdriverpartitionedtable"
            , "testhivejdbcdriverview"});
    tests.put("%jdbcdriver\\_table", new Object[]{"testhivejdbcdriver_table"});
    tests.put("testhivejdbcdriver\\_table", new Object[]{"testhivejdbcdriver_table"});
    tests.put("test_ivejdbcdri_er\\_table", new Object[]{"testhivejdbcdriver_table"});
    tests.put("test_ivejdbcdri_er_table", new Object[]{"testhivejdbcdriver_table"});
    tests.put("test_ivejdbcdri_er%table", new Object[]{
        "testhivejdbcdriver_table", "testhivejdbcdriverpartitionedtable" });
    tests.put("%jdbc%", new Object[]{ "testhivejdbcdriver_table"
            , "testhivejdbcdriverpartitionedtable"
            , "testhivejdbcdriverview"});
    tests.put("", new Object[]{});

    for (String checkPattern: tests.keySet()) {
      ResultSet rs = (ResultSet)con.getMetaData().getTables("default", null, checkPattern, null);
      ResultSetMetaData resMeta = rs.getMetaData();
      assertEquals(5, resMeta.getColumnCount());
      assertEquals("TABLE_CAT", resMeta.getColumnName(1));
      assertEquals("TABLE_SCHEM", resMeta.getColumnName(2));
      assertEquals("TABLE_NAME", resMeta.getColumnName(3));
      assertEquals("TABLE_TYPE", resMeta.getColumnName(4));
      assertEquals("REMARKS", resMeta.getColumnName(5));

      int cnt = 0;
      while (rs.next()) {
        String resultTableName = rs.getString("TABLE_NAME");
        assertEquals("Get by index different from get by name.", rs.getString(3), resultTableName);
        assertEquals("Excpected a different table.", tests.get(checkPattern)[cnt], resultTableName);
        String resultTableComment = rs.getString("REMARKS");
        assertTrue("Missing comment on the table.", resultTableComment.length()>0);
        String tableType = rs.getString("TABLE_TYPE");
        if (resultTableName.endsWith("view")) {
          assertEquals("Expected a tabletype view but got something else.", viewTypeName, tableType);
        } else {
          assertEquals("Expected a tabletype table but got something else.", tableTypeName, tableType);
        }
        cnt++;
      }
      rs.close();
      assertEquals("Received an incorrect number of tables.", tests.get(checkPattern).length, cnt);
    }

    // only ask for the views.
    ResultSet rs = (ResultSet)con.getMetaData().getTables("default", null, null
            , new String[]{viewTypeName});
    int cnt=0;
    while (rs.next()) {
      cnt++;
    }
    rs.close();
    assertEquals("Incorrect number of views found.", 1, cnt);
  }

  public void testMetaDataGetCatalogs() throws SQLException {
    ResultSet rs = (ResultSet)con.getMetaData().getCatalogs();
    ResultSetMetaData resMeta = rs.getMetaData();
    assertEquals(1, resMeta.getColumnCount());
    assertEquals("TABLE_CAT", resMeta.getColumnName(1));

    assertFalse(rs.next());
  }

  public void testMetaDataGetSchemas() throws SQLException {
    ResultSet rs = (ResultSet)con.getMetaData().getSchemas();
    ResultSetMetaData resMeta = rs.getMetaData();
    assertEquals(2, resMeta.getColumnCount());
    assertEquals("TABLE_SCHEM", resMeta.getColumnName(1));
    assertEquals("TABLE_CATALOG", resMeta.getColumnName(2));

    assertTrue(rs.next());
    assertEquals("default", rs.getString(1));
//    assertNull(rs.getString(2));

    assertFalse(rs.next());
    rs.close();
  }

  public void testMetaDataGetTableTypes() throws SQLException {
    metaDataGetTableTypeTest(new HiveTableTypeMapping().getTableTypeNames());
  }

  public void testMetaDataGetHiveTableTypes() throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("set " + HiveConf.ConfVars.HIVE_SERVER2_TABLE_TYPE_MAPPING.varname +
        " = " + TableTypeMappings.HIVE.toString());
    stmt.close();
    metaDataGetTableTypeTest(new HiveTableTypeMapping().getTableTypeNames());
  }

  public void testMetaDataGetClassicTableTypes() throws SQLException {
    Statement stmt = con.createStatement();
    stmt.execute("set " + HiveConf.ConfVars.HIVE_SERVER2_TABLE_TYPE_MAPPING.varname +
        " = " + TableTypeMappings.CLASSIC.toString());
    stmt.close();
    metaDataGetTableTypeTest(new ClassicTableTypeMapping().getTableTypeNames());
  }

  private void metaDataGetTableTypeTest(Set<String> tabletypes)
      throws SQLException {
    ResultSet rs = (ResultSet)con.getMetaData().getTableTypes();

    int cnt = 0;
    while (rs.next()) {
      String tabletype = rs.getString("TABLE_TYPE");
      assertEquals("Get by index different from get by name", rs.getString(1), tabletype);
      tabletypes.remove(tabletype);
      cnt++;
    }
    rs.close();
    assertEquals("Incorrect tabletype count.", 0, tabletypes.size());
    assertTrue("Found less tabletypes then we test for.", cnt >= tabletypes.size());
  }

  public void testMetaDataGetColumns() throws SQLException {
    Map<String[], Integer> tests = new HashMap<String[], Integer>();
    tests.put(new String[]{"testhivejdbcdriver\\_table", null}, 2);
    tests.put(new String[]{"testhivejdbc%", null}, 7);
    tests.put(new String[]{"testhiveJDBC%", null}, 7);
    tests.put(new String[]{"%jdbcdriver\\_table", null}, 2);
    tests.put(new String[]{"%jdbcdriver\\_table%", "under\\_col"}, 1);
//    tests.put(new String[]{"%jdbcdriver\\_table%", "under\\_COL"}, 1);
    tests.put(new String[]{"%jdbcdriver\\_table%", "under\\_co_"}, 1);
    tests.put(new String[]{"%jdbcdriver\\_table%", "under_col"}, 1);
    tests.put(new String[]{"%jdbcdriver\\_table%", "und%"}, 1);
    tests.put(new String[]{"%jdbcdriver\\_table%", "%"}, 2);
    tests.put(new String[]{"%jdbcdriver\\_table%", "_%"}, 2);

    for (String[] checkPattern: tests.keySet()) {
      ResultSet rs = con.getMetaData().getColumns(null, null, checkPattern[0],
          checkPattern[1]);

      // validate the metadata for the getColumns result set
      ResultSetMetaData rsmd = rs.getMetaData();
      assertEquals("TABLE_CAT", rsmd.getColumnName(1));

      int cnt = 0;
      while (rs.next()) {
        String columnname = rs.getString("COLUMN_NAME");
        int ordinalPos = rs.getInt("ORDINAL_POSITION");
        switch(cnt) {
          case 0:
            assertEquals("Wrong column name found", "under_col", columnname);
            assertEquals("Wrong ordinal position found", ordinalPos, 1);
            break;
          case 1:
            assertEquals("Wrong column name found", "value", columnname);
            assertEquals("Wrong ordinal position found", ordinalPos, 2);
            break;
          default:
            break;
        }
        cnt++;
      }
      rs.close();
      assertEquals("Found less columns then we test for.", tests.get(checkPattern).intValue(), cnt);
    }
  }

  /**
   * Validate the Metadata for the result set of a metadata getColumns call.
   */
  public void testMetaDataGetColumnsMetaData() throws SQLException {
    ResultSet rs = (ResultSet)con.getMetaData().getColumns(null, null
            , "testhivejdbcdriver\\_table", null);

    ResultSetMetaData rsmd = rs.getMetaData();

    assertEquals("TABLE_CAT", rsmd.getColumnName(1));
    assertEquals(Types.VARCHAR, rsmd.getColumnType(1));
    assertEquals(Integer.MAX_VALUE, rsmd.getColumnDisplaySize(1));

    assertEquals("ORDINAL_POSITION", rsmd.getColumnName(17));
    assertEquals(Types.INTEGER, rsmd.getColumnType(17));
    assertEquals(11, rsmd.getColumnDisplaySize(17));
  }

  /*
  public void testConversionsBaseResultSet() throws SQLException {
    ResultSet rs = new HiveMetaDataResultSet(Arrays.asList("key")
            , Arrays.asList("long")
            , Arrays.asList(1234, "1234", "abc")) {
      private int cnt=1;
      public boolean next() throws SQLException {
        if (cnt<data.size()) {
          row = Arrays.asList(data.get(cnt));
          cnt++;
          return true;
        } else {
          return false;
        }
      }
    };

    while (rs.next()) {
      String key = rs.getString("key");
      if ("1234".equals(key)) {
        assertEquals("Converting a string column into a long failed.", rs.getLong("key"), 1234L);
        assertEquals("Converting a string column into a int failed.", rs.getInt("key"), 1234);
      } else if ("abc".equals(key)) {
        Object result = null;
        Exception expectedException = null;
        try {
          result = rs.getLong("key");
        } catch (SQLException e) {
          expectedException = e;
        }
        assertTrue("Trying to convert 'abc' into a long should not work.", expectedException!=null);
        try {
          result = rs.getInt("key");
        } catch (SQLException e) {
          expectedException = e;
        }
        assertTrue("Trying to convert 'abc' into a int should not work.", expectedException!=null);
      }
    }
  }
  */

  public void testDescribeTable() throws SQLException {
    Statement stmt = con.createStatement();
    assertNotNull("Statement is null", stmt);

    ResultSet res = stmt.executeQuery("describe " + tableName);

    res.next();
    assertEquals("Column name 'under_col' not found", "under_col", res.getString(1));
    assertEquals("Column type 'under_col' for column under_col not found", "int", res
        .getString(2));
    res.next();
    assertEquals("Column name 'value' not found", "value", res.getString(1));
    assertEquals("Column type 'string' for column key not found", "string", res
        .getString(2));

    assertFalse("More results found than expected", res.next());

  }

  public void testDatabaseMetaData() throws SQLException {
    DatabaseMetaData meta = con.getMetaData();

    assertEquals("Hive", meta.getDatabaseProductName());
    assertEquals("0.10.0", meta.getDatabaseProductVersion());
    assertEquals(DatabaseMetaData.sqlStateSQL99, meta.getSQLStateType());
    assertFalse(meta.supportsCatalogsInTableDefinitions());
    assertFalse(meta.supportsSchemasInTableDefinitions());
    assertFalse(meta.supportsSchemasInDataManipulation());
    assertFalse(meta.supportsMultipleResultSets());
    assertFalse(meta.supportsStoredProcedures());
    assertTrue(meta.supportsAlterTableWithAddColumn());

    //-1 indicates malformed version.
    assertTrue(meta.getDatabaseMajorVersion() > -1);
    assertTrue(meta.getDatabaseMinorVersion() > -1);
  }

  public void testResultSetMetaData() throws SQLException {
    Statement stmt = con.createStatement();

    ResultSet res = stmt.executeQuery(
        "select c1, c2, c3, c4, c5 as a, c6, c7, c8, c9, c10, c11, c12, " +
        "c1*2, sentences(null, null, null) as b, c17, c18 from " + dataTypeTableName + " limit 1");
    ResultSetMetaData meta = res.getMetaData();

    ResultSet colRS = con.getMetaData().getColumns(null, null,
        dataTypeTableName.toLowerCase(), null);

    assertEquals(16, meta.getColumnCount());

    assertTrue(colRS.next());

    assertEquals("c1", meta.getColumnName(1));
    assertEquals(Types.INTEGER, meta.getColumnType(1));
    assertEquals("int", meta.getColumnTypeName(1));
    assertEquals(11, meta.getColumnDisplaySize(1));
    assertEquals(10, meta.getPrecision(1));
    assertEquals(0, meta.getScale(1));

    assertEquals("c1", colRS.getString("COLUMN_NAME"));
    assertEquals(Types.INTEGER, colRS.getInt("DATA_TYPE"));
    assertEquals("int", colRS.getString("TYPE_NAME").toLowerCase());
    assertEquals(meta.getPrecision(1), colRS.getInt("COLUMN_SIZE"));
    assertEquals(meta.getScale(1), colRS.getInt("DECIMAL_DIGITS"));

    assertTrue(colRS.next());

    assertEquals("c2", meta.getColumnName(2));
    assertEquals("boolean", meta.getColumnTypeName(2));
    assertEquals(Types.BOOLEAN, meta.getColumnType(2));
    assertEquals(1, meta.getColumnDisplaySize(2));
    assertEquals(1, meta.getPrecision(2));
    assertEquals(0, meta.getScale(2));

    assertEquals("c2", colRS.getString("COLUMN_NAME"));
    assertEquals(Types.BOOLEAN, colRS.getInt("DATA_TYPE"));
    assertEquals("boolean", colRS.getString("TYPE_NAME").toLowerCase());
    assertEquals(meta.getScale(2), colRS.getInt("DECIMAL_DIGITS"));

    assertTrue(colRS.next());

    assertEquals("c3", meta.getColumnName(3));
    assertEquals(Types.DOUBLE, meta.getColumnType(3));
    assertEquals("double", meta.getColumnTypeName(3));
    assertEquals(25, meta.getColumnDisplaySize(3));
    assertEquals(15, meta.getPrecision(3));
    assertEquals(15, meta.getScale(3));

    assertEquals("c3", colRS.getString("COLUMN_NAME"));
    assertEquals(Types.DOUBLE, colRS.getInt("DATA_TYPE"));
    assertEquals("double", colRS.getString("TYPE_NAME").toLowerCase());
    assertEquals(meta.getPrecision(3), colRS.getInt("COLUMN_SIZE"));
    assertEquals(meta.getScale(3), colRS.getInt("DECIMAL_DIGITS"));

    assertTrue(colRS.next());

    assertEquals("c4", meta.getColumnName(4));
    assertEquals(Types.VARCHAR, meta.getColumnType(4));
    assertEquals("string", meta.getColumnTypeName(4));
    assertEquals(Integer.MAX_VALUE, meta.getColumnDisplaySize(4));
    assertEquals(Integer.MAX_VALUE, meta.getPrecision(4));
    assertEquals(0, meta.getScale(4));

    assertEquals("c4", colRS.getString("COLUMN_NAME"));
    assertEquals(Types.VARCHAR, colRS.getInt("DATA_TYPE"));
    assertEquals("string", colRS.getString("TYPE_NAME").toLowerCase());
    assertEquals(meta.getPrecision(4), colRS.getInt("COLUMN_SIZE"));
    assertEquals(meta.getScale(4), colRS.getInt("DECIMAL_DIGITS"));

    assertTrue(colRS.next());

    assertEquals("a", meta.getColumnName(5));
    assertEquals(Types.VARCHAR, meta.getColumnType(5));
    assertEquals("string", meta.getColumnTypeName(5));
    assertEquals(Integer.MAX_VALUE, meta.getColumnDisplaySize(5));
    assertEquals(Integer.MAX_VALUE, meta.getPrecision(5));
    assertEquals(0, meta.getScale(5));

    assertEquals("c5", colRS.getString("COLUMN_NAME"));
    assertEquals(Types.VARCHAR, colRS.getInt("DATA_TYPE"));
    assertEquals("array<int>", colRS.getString("TYPE_NAME").toLowerCase());

    assertTrue(colRS.next());

    assertEquals("c6", meta.getColumnName(6));
    assertEquals(Types.VARCHAR, meta.getColumnType(6));
    assertEquals("string", meta.getColumnTypeName(6));
    assertEquals(Integer.MAX_VALUE, meta.getColumnDisplaySize(6));
    assertEquals(Integer.MAX_VALUE, meta.getPrecision(6));
    assertEquals(0, meta.getScale(6));

    assertEquals("c6", colRS.getString("COLUMN_NAME"));
    assertEquals(Types.VARCHAR, colRS.getInt("DATA_TYPE"));
    assertEquals("map<int,string>", colRS.getString("TYPE_NAME").toLowerCase());

    assertTrue(colRS.next());

    assertEquals("c7", meta.getColumnName(7));
    assertEquals(Types.VARCHAR, meta.getColumnType(7));
    assertEquals("string", meta.getColumnTypeName(7));
    assertEquals(Integer.MAX_VALUE, meta.getColumnDisplaySize(7));
    assertEquals(Integer.MAX_VALUE, meta.getPrecision(7));
    assertEquals(0, meta.getScale(7));

    assertEquals("c7", colRS.getString("COLUMN_NAME"));
    assertEquals(Types.VARCHAR, colRS.getInt("DATA_TYPE"));
    assertEquals("map<string,string>", colRS.getString("TYPE_NAME").toLowerCase());

    assertTrue(colRS.next());

    assertEquals("c8", meta.getColumnName(8));
    assertEquals(Types.VARCHAR, meta.getColumnType(8));
    assertEquals("string", meta.getColumnTypeName(8));
    assertEquals(Integer.MAX_VALUE, meta.getColumnDisplaySize(8));
    assertEquals(Integer.MAX_VALUE, meta.getPrecision(8));
    assertEquals(0, meta.getScale(8));

    assertEquals("c8", colRS.getString("COLUMN_NAME"));
    assertEquals(Types.VARCHAR, colRS.getInt("DATA_TYPE"));
    assertEquals("struct<r:string,s:int,t:double>", colRS.getString("TYPE_NAME").toLowerCase());

    assertTrue(colRS.next());

    assertEquals("c9", meta.getColumnName(9));
    assertEquals(Types.TINYINT, meta.getColumnType(9));
    assertEquals("tinyint", meta.getColumnTypeName(9));
    assertEquals(4, meta.getColumnDisplaySize(9));
    assertEquals(3, meta.getPrecision(9));
    assertEquals(0, meta.getScale(9));

    assertEquals("c9", colRS.getString("COLUMN_NAME"));
    assertEquals(Types.TINYINT, colRS.getInt("DATA_TYPE"));
    assertEquals("tinyint", colRS.getString("TYPE_NAME").toLowerCase());
    assertEquals(meta.getPrecision(9), colRS.getInt("COLUMN_SIZE"));
    assertEquals(meta.getScale(9), colRS.getInt("DECIMAL_DIGITS"));

    assertTrue(colRS.next());

    assertEquals("c10", meta.getColumnName(10));
    assertEquals(Types.SMALLINT, meta.getColumnType(10));
    assertEquals("smallint", meta.getColumnTypeName(10));
    assertEquals(6, meta.getColumnDisplaySize(10));
    assertEquals(5, meta.getPrecision(10));
    assertEquals(0, meta.getScale(10));

    assertEquals("c10", colRS.getString("COLUMN_NAME"));
    assertEquals(Types.SMALLINT, colRS.getInt("DATA_TYPE"));
    assertEquals("smallint", colRS.getString("TYPE_NAME").toLowerCase());
    assertEquals(meta.getPrecision(10), colRS.getInt("COLUMN_SIZE"));
    assertEquals(meta.getScale(10), colRS.getInt("DECIMAL_DIGITS"));

    assertTrue(colRS.next());

    assertEquals("c11", meta.getColumnName(11));
    assertEquals(Types.FLOAT, meta.getColumnType(11));
    assertEquals("float", meta.getColumnTypeName(11));
    assertEquals(24, meta.getColumnDisplaySize(11));
    assertEquals(7, meta.getPrecision(11));
    assertEquals(7, meta.getScale(11));

    assertEquals("c11", colRS.getString("COLUMN_NAME"));
    assertEquals(Types.FLOAT, colRS.getInt("DATA_TYPE"));
    assertEquals("float", colRS.getString("TYPE_NAME").toLowerCase());
    assertEquals(meta.getPrecision(11), colRS.getInt("COLUMN_SIZE"));
    assertEquals(meta.getScale(11), colRS.getInt("DECIMAL_DIGITS"));

    assertTrue(colRS.next());

    assertEquals("c12", meta.getColumnName(12));
    assertEquals(Types.BIGINT, meta.getColumnType(12));
    assertEquals("bigint", meta.getColumnTypeName(12));
    assertEquals(20, meta.getColumnDisplaySize(12));
    assertEquals(19, meta.getPrecision(12));
    assertEquals(0, meta.getScale(12));

    assertEquals("c12", colRS.getString("COLUMN_NAME"));
    assertEquals(Types.BIGINT, colRS.getInt("DATA_TYPE"));
    assertEquals("bigint", colRS.getString("TYPE_NAME").toLowerCase());
    assertEquals(meta.getPrecision(12), colRS.getInt("COLUMN_SIZE"));
    assertEquals(meta.getScale(12), colRS.getInt("DECIMAL_DIGITS"));

    assertEquals("_c12", meta.getColumnName(13));
    assertEquals(Types.INTEGER, meta.getColumnType(13));
    assertEquals("int", meta.getColumnTypeName(13));
    assertEquals(11, meta.getColumnDisplaySize(13));
    assertEquals(10, meta.getPrecision(13));
    assertEquals(0, meta.getScale(13));

    assertEquals("b", meta.getColumnName(14));
    assertEquals(Types.VARCHAR, meta.getColumnType(14));
    assertEquals("string", meta.getColumnTypeName(14));
    assertEquals(Integer.MAX_VALUE, meta.getColumnDisplaySize(14));
    assertEquals(Integer.MAX_VALUE, meta.getPrecision(14));
    assertEquals(0, meta.getScale(14));

    assertEquals("c17", meta.getColumnName(15));
    assertEquals(Types.TIMESTAMP, meta.getColumnType(15));
    assertEquals("timestamp", meta.getColumnTypeName(15));
    assertEquals(29, meta.getColumnDisplaySize(15));
    assertEquals(29, meta.getPrecision(15));
    assertEquals(9, meta.getScale(15));

    assertEquals("c18", meta.getColumnName(16));
    assertEquals(Types.DECIMAL, meta.getColumnType(16));
    assertEquals("decimal", meta.getColumnTypeName(16));
    assertEquals(Integer.MAX_VALUE, meta.getColumnDisplaySize(16));
    assertEquals(Integer.MAX_VALUE, meta.getPrecision(16));
    assertEquals(Integer.MAX_VALUE, meta.getScale(16));

    for (int i = 1; i <= meta.getColumnCount(); i++) {
      assertFalse(meta.isAutoIncrement(i));
      assertFalse(meta.isCurrency(i));
      assertEquals(ResultSetMetaData.columnNullable, meta.isNullable(i));
    }
  }

  // [url] [host] [port] [db]
  private static final String[][] URL_PROPERTIES = new String[][] {
      {"jdbc:hive2://", "", "", "default"},
      {"jdbc:hive2://localhost:10001/default", "localhost", "10001", "default"},
      {"jdbc:hive2://localhost/notdefault", "localhost", "10000", "notdefault"},
      {"jdbc:hive2://foo:1243", "foo", "1243", "default"}};

  public void testDriverProperties() throws SQLException {
    HiveDriver driver = new HiveDriver();

    for (String[] testValues : URL_PROPERTIES) {
      DriverPropertyInfo[] dpi = driver.getPropertyInfo(testValues[0], null);
      assertEquals("unexpected DriverPropertyInfo array size", 3, dpi.length);
      assertDpi(dpi[0], "HOST", testValues[1]);
      assertDpi(dpi[1], "PORT", testValues[2]);
      assertDpi(dpi[2], "DBNAME", testValues[3]);
    }

  }

  private static void assertDpi(DriverPropertyInfo dpi, String name,
      String value) {
    assertEquals("Invalid DriverPropertyInfo name", name, dpi.name);
    assertEquals("Invalid DriverPropertyInfo value", value, dpi.value);
    assertEquals("Invalid DriverPropertyInfo required", false, dpi.required);
  }


  /**
   * validate schema generated by "set" command
   * @throws SQLException
   */
  public void testSetCommand() throws SQLException {
    // execute set command
    String sql = "set -v";
    Statement stmt = con.createStatement();
    ResultSet res = stmt.executeQuery(sql);

    // Validate resultset columns
    ResultSetMetaData md = res.getMetaData() ;
    assertEquals(1, md.getColumnCount());
    assertEquals(SET_COLUMN_NAME, md.getColumnLabel(1));

    //check if there is data in the resultset
    assertTrue("Nothing returned by set -v", res.next());

    res.close();
    stmt.close();
  }

  /**
   * Validate error on closed resultset
   * @throws SQLException
   */
  public void testPostClose() throws SQLException {
    Statement stmt = con.createStatement();
    ResultSet res = stmt.executeQuery("select * from " + tableName);
    assertNotNull("ResultSet is null", res);
    res.close();
    try { res.getInt(1); fail("Expected SQLException"); }
    catch (SQLException e) { }
    try { res.getMetaData(); fail("Expected SQLException"); }
    catch (SQLException e) { }
    try { res.setFetchSize(10); fail("Expected SQLException"); }
    catch (SQLException e) { }
  }

  /*
   * The JDBC spec says when you have duplicate column names,
   * the first one should be returned.
   */
  public void testDuplicateColumnNameOrder() throws SQLException {
    Statement stmt = con.createStatement();
    ResultSet rs = stmt.executeQuery("SELECT 1 AS a, 2 AS a from " + tableName);
    assertTrue(rs.next());
    assertEquals(1, rs.getInt("a"));
  }


  /**
   * Test bad args to getXXX()
   * @throws SQLException
   */
  public void testOutOfBoundCols() throws SQLException {
    Statement stmt = con.createStatement();

    ResultSet res = stmt.executeQuery(
        "select * from " + tableName);

    // row 1
    assertTrue(res.next());

    try {
      res.getInt(200);
    } catch (SQLException e) {
    }

    try {
      res.getInt("zzzz");
    } catch (SQLException e) {
    }

  }

  /**
   * Verify selecting using builtin UDFs
   * @throws SQLException
   */
  public void testBuiltInUDFCol() throws SQLException {
    Statement stmt = con.createStatement();
    ResultSet res = stmt.executeQuery("select c12, bin(c12) from " + dataTypeTableName
        + " where c1=1");
    ResultSetMetaData md = res.getMetaData();
    assertEquals(md.getColumnCount(), 2); // only one result column
    assertEquals(md.getColumnLabel(2), "_c1" ); // verify the system generated column name
    assertTrue(res.next());
    assertEquals(res.getLong(1), 1);
    assertEquals(res.getString(2), "1");
    res.close();
  }

  /**
   * Verify selecting named expression columns
   * @throws SQLException
   */
  public void testExprCol() throws SQLException {
    Statement stmt = con.createStatement();
    ResultSet res = stmt.executeQuery("select c1+1 as col1, length(c4) as len from " + dataTypeTableName
        + " where c1=1");
    ResultSetMetaData md = res.getMetaData();
    assertEquals(md.getColumnCount(), 2); // only one result column
    assertEquals(md.getColumnLabel(1), "col1" ); // verify the column name
    assertEquals(md.getColumnLabel(2), "len" ); // verify the column name
    assertTrue(res.next());
    assertEquals(res.getInt(1), 2);
    assertEquals(res.getInt(2), 1);
    res.close();
  }

  /**
   * test getProcedureColumns()
   * @throws SQLException
   */
  public void testProcCols() throws SQLException {
    DatabaseMetaData dbmd = con.getMetaData();
    assertNotNull(dbmd);
    // currently getProcedureColumns always returns an empty resultset for Hive
    ResultSet res = dbmd.getProcedureColumns(null, null, null, null);
    ResultSetMetaData md = res.getMetaData();
    assertEquals(md.getColumnCount(), 20);
    assertFalse(res.next());
  }

  /**
   * test testProccedures()
   * @throws SQLException
   */
  public void testProccedures() throws SQLException {
    DatabaseMetaData dbmd = con.getMetaData();
    assertNotNull(dbmd);
    // currently testProccedures always returns an empty resultset for Hive
    ResultSet res = dbmd.getProcedures(null, null, null);
    ResultSetMetaData md = res.getMetaData();
    assertEquals(md.getColumnCount(), 9);
    assertFalse(res.next());
  }

  /**
   * test getPrimaryKeys()
   * @throws SQLException
   */
  public void testPrimaryKeys() throws SQLException {
    DatabaseMetaData dbmd = con.getMetaData();
    assertNotNull(dbmd);
    // currently getPrimaryKeys always returns an empty resultset for Hive
    ResultSet res = dbmd.getPrimaryKeys(null, null, null);
    ResultSetMetaData md = res.getMetaData();
    assertEquals(md.getColumnCount(), 6);
    assertFalse(res.next());
  }

  /**
   * test getImportedKeys()
   * @throws SQLException
   */
  public void testImportedKeys() throws SQLException {
    DatabaseMetaData dbmd = con.getMetaData();
    assertNotNull(dbmd);
    // currently getImportedKeys always returns an empty resultset for Hive
    ResultSet res = dbmd.getImportedKeys(null, null, null);
    ResultSetMetaData md = res.getMetaData();
    assertEquals(md.getColumnCount(), 14);
    assertFalse(res.next());
  }

  /**
   * Test the cursor repositioning to start of resultset
   * @throws Exception
   */
  public void testFetchFirstQuery() throws Exception {
    execFetchFirst("select c1 from " + dataTypeTableName + " order by c1", false);
    execFetchFirst("select c1 from " + dataTypeTableName + " order by c1", true);
  }

  public void testFetchFirstNonMR() throws Exception {
    execFetchFirst("select * from " + dataTypeTableName, false);
  }

  /**
   * Read the results locally. Then reset the read position to start and read the rows again
   * verify that we get the same results next time.
   * @param sqlStmt
   * @param oneRowOnly
   * @throws Exception
   */
  private void execFetchFirst(String sqlStmt, boolean oneRowOnly) throws Exception {
    Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
          ResultSet.CONCUR_READ_ONLY);
    ResultSet res = stmt.executeQuery(sqlStmt);

    List<Integer> results = new ArrayList<Integer> ();
    assertTrue(res.isBeforeFirst());
    int rowNum = 0;
    while (res.next()) {
      results.add(res.getInt(1));
      assertEquals(++rowNum, res.getRow());
      assertFalse(res.isBeforeFirst());
      if (oneRowOnly) {
        break;
      }
    }
    // reposition at the begining
    res.beforeFirst();
    assertTrue(res.isBeforeFirst());
    rowNum = 0;
    while (res.next()) {
      // compare the results fetched last time
      assertEquals(results.get(rowNum++).intValue(), res.getInt(1));
      assertEquals(rowNum, res.getRow());
      assertFalse(res.isBeforeFirst());
      if (oneRowOnly) {
        break;
      }
    }
  }

  public void testFetchFirstCmdsNeg() throws Exception {
    // verify that fetch_first is not supported
    Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
        ResultSet.CONCUR_READ_ONLY);
    ResultSet res = stmt.executeQuery("set -v");
    res.next();
    res.beforeFirst();
    try {
      res.next();
      assertTrue("fetch_first should fail", false);
    } catch (SQLException e) {
      // cmd processor does support fetch first at this point
      assertEquals("HY106", e.getSQLState());
    }
  }

  public void testUnsupportedFetchTypes() throws Exception {
    Statement stmt;
    try {
      stmt = con.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE,
        ResultSet.CONCUR_READ_ONLY);
      assertTrue("createStatement with TYPE_SCROLL_SENSITIVE should fail", false);
    } catch(SQLException e) {
      assertEquals("Method not supported", e.getMessage());
    }

    try {
      stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,
        ResultSet.CONCUR_UPDATABLE);
      assertTrue("createStatement with CONCUR_UPDATABLE should fail", false);
    } catch(SQLException e) {
      assertEquals("Method not supported", e.getMessage());
    }
  }

  public void testFetchFirstError() throws Exception {
    Statement stmt = con.createStatement();
    ResultSet res = stmt.executeQuery("select * from " + tableName);
    try {
      res.beforeFirst();
      assertTrue("beforeFirst() should fail for normal resultset", false);
    } catch (SQLException e) {
      assertEquals("Method not supported for TYPE_FORWARD_ONLY resultset", e.getMessage());
    }
  }

  /**
   * run DDLs with async mode
   * @throws SQLException
   */
  public void testAsyncStmts() throws SQLException {
    String fooQueryTag = "foo";
    Statement stmt = con.createStatement();
    stmt.execute("DROP TABLE IF EXISTS tabz");
    stmt.close();
    stmt = con.createStatement();
    stmt.execute("set hive.server2.blocking.query=false");
    stmt.execute("set " + QueryTag + " = " + fooQueryTag);
    stmt.close();
    stmt = con.createStatement();
    stmt.execute("set hive.exec.post.hooks = org.apache.hive.jdbc.TestJdbcDriver2$QueryBlockHook");
    stmt.close();
    QueryBlockHook.setupBlock(fooQueryTag);
    stmt = con.createStatement();
    stmt.execute("CREATE TABLE tabz (id INT)");
    assertNotNull(stmt.getWarnings());
    QueryBlockHook.clearBlock(fooQueryTag);
    stmt.close();
    stmt = con.createStatement();
    QueryBlockHook.setupBlock(fooQueryTag);
    stmt.execute("DROP TABLE IF EXISTS tabz");
    assertNotNull(stmt.getWarnings());
    QueryBlockHook.clearBlock(fooQueryTag);
    stmt.close();
  }

  /**
   * run queries in async mode
   * @throws SQLException
   */
  public void testAsyncQuery() throws SQLException {
    String fooQueryTag = "foo";
    HiveStatement stmt = (HiveStatement)con.createStatement();
    stmt.execute("set hive.server2.blocking.query=false");
    stmt.close();
    stmt = (HiveStatement)con.createStatement();
    stmt.execute("set hive.exec.post.hooks = org.apache.hive.jdbc.TestJdbcDriver2$QueryBlockHook");
    stmt.close();
    stmt = (HiveStatement)con.createStatement();
    stmt.execute("set " + QueryTag + " = " + fooQueryTag);
    QueryBlockHook.setupBlock(fooQueryTag);
    ResultSet res = stmt.executeQuery("select c1 from " + dataTypeTableName +
          " where c1 = 1");
    assertNotNull(stmt.getWarnings());
    ResultSetMetaData md = res.getMetaData();
    // sanity check metadata
    assertEquals(md.getColumnCount(), 1); // only one result column
    assertEquals(md.getColumnLabel(1), "c1" ); // verify the column name
    try {
      res.next();
      assertTrue(false);
    } catch (SQLException e) {
      // verify that the fetch fails with query still running error
      assertEquals("HY010", e.getSQLState());
    }

    //test log retrieval
    String log = stmt.getLog();
    assertTrue(log.length() > 1);
    assertTrue("Operation log looks incorrect",
        log.contains("Parsing command: select c1 from testDataTypeTable where c1 = 1"));
    assertTrue("Operation log looks incorrect",
        log.contains("Starting command: select c1 from testDataTypeTable where c1 = 1"));

    QueryBlockHook.clearBlock(fooQueryTag); // continue query
    // verify that we can now fetch data
    // the query could be still be running and might take a few more iteration to finish
    do {
      try {
        res.next();
        break;
      } catch (SQLException e) {
        if (e.getSQLState().equals("HY010")) {
          // if query is not complete, then try next time
          continue;
        } else {
          throw e;
        }
      }
    } while (true);
    assertEquals(1, res.getInt(1));

    log = stmt.getLog();
    assertTrue(log.length() > 1);
    assertTrue("Operation log looks incorrect",
        log.contains("Execution completed successfully"));

    stmt.close();
  }

  /**
   * Run multiple queries in async mode
   */
  /**
   * run queries in async mode
   * @throws SQLException
   */
  public void testMultiAsyncQueries() throws SQLException {
    String fooQueryTag = "foo";
    String barQueryTag = "bar";

    HiveStatement stmt = (HiveStatement)con.createStatement();
    stmt.execute("set hive.server2.blocking.query=false");
    stmt.close();
    stmt = (HiveStatement)con.createStatement();
    stmt.execute("set hive.exec.post.hooks = org.apache.hive.jdbc.TestJdbcDriver2$QueryBlockHook");
    stmt.close();

    // start foo query
    stmt = (HiveStatement)con.createStatement();
    stmt.execute("set " + QueryTag + " = " + fooQueryTag);
    QueryBlockHook.setupBlock(fooQueryTag);
    ResultSet res = stmt.executeQuery("select c1 from " + dataTypeTableName +
          " where c1 = 1");
    assertNotNull(stmt.getWarnings());
    ResultSetMetaData md = res.getMetaData();
    // sanity check metadata
    assertEquals(md.getColumnCount(), 1); // only one result column
    assertEquals(md.getColumnLabel(1), "c1" ); // verify the column name
    try {
      res.next();
      assertTrue(false);
    } catch (SQLException e) {
      // verify that the fetch fails with query still running error
      assertEquals("HY010", e.getSQLState());
    }

    // start bar query
    HiveStatement stmt2 = (HiveStatement)con.createStatement();
    stmt2.execute("set " + QueryTag + " = " + barQueryTag);
    QueryBlockHook.setupBlock(barQueryTag);
    ResultSet res2 = stmt2.executeQuery("select c3 from " + dataTypeTableName +
    " where c1 = 1");
    assertNotNull(stmt2.getWarnings());
    ResultSetMetaData md2 = res2.getMetaData();
    // sanity check metadata
    assertEquals(md2.getColumnCount(), 1); // only one result column
    assertEquals(md2.getColumnLabel(1), "c3" ); // verify the column name
    try {
      res2.next();
      assertTrue(false);
    } catch (SQLException e) {
      // verify that the fetch fails with query still running error
      assertEquals("HY010", e.getSQLState());
    }

    //test log retrieval
    String log = stmt.getLog();
    assertTrue(log.length() > 1);
    assertTrue("Operation log looks incorrect",
        log.contains("Parsing command: select c1 from testDataTypeTable where c1 = 1"));
    assertTrue("Operation log looks incorrect",
        log.contains("Starting command: select c1 from testDataTypeTable where c1 = 1"));

    QueryBlockHook.clearBlock(fooQueryTag); // continue foo query
    // verify that we can now fetch data
    // the query could be still be running and might take a few more iteration to finish
    do {
      try {
        res.next();
        break;
      } catch (SQLException e) {
        if (e.getSQLState().equals("HY010")) {
          // if query is not complete, then try next time
          continue;
        } else {
          throw e;
        }
      }
    } while (true);

    //get log again
    log = stmt.getLog();
    assertTrue(log.length() > 1);
    assertTrue("Operation log looks incorrect",
        log.contains("Execution completed successfully"));
    stmt.close();

    // verify that the bar query is still blocked
    try {
      res2.next();
      assertTrue(false);
    } catch (SQLException e) {
      // verify that the fetch fails with query still running error
      assertEquals("HY010", e.getSQLState());
    }

   //test log retrieval
   log = stmt2.getLog();
   assertTrue(log.length() > 1);
   assertTrue("Operation log looks incorrect",
        log.contains("Parsing command: select c3 from testDataTypeTable where c1 = 1"));
   assertTrue("Operation log looks incorrect",
        log.contains("Starting command: select c3 from testDataTypeTable where c1 = 1"));

    QueryBlockHook.clearBlock(barQueryTag); // continue bar query
    // verify that we can now fetch data for bar query
    // the query could be still be running and might take a few more iteration to finish
    do {
      try {
        res2.next();
        break;
      } catch (SQLException e) {
        if (e.getSQLState().equals("HY010")) {
          // if query is not complete, then try next time
          continue;
        } else {
          throw e;
        }
      }
    } while (true);
    assertEquals(1, res2.getInt(1));
    //get log again
    log = stmt2.getLog();
    assertTrue(log.length() > 1);
    assertTrue("Operation log looks incorrect",
        log.contains("Execution completed successfully"));
    stmt2.close();
  }

  /**
   * run prepared statement in async mode
   * @throws SQLException
   */
  public void testAsyncPreparedStmt() throws SQLException {
    String fooQueryTag = "foo";

    Statement stmt = con.createStatement();
    stmt.execute("set hive.server2.blocking.query=false");
    stmt.execute("set hive.exec.post.hooks = org.apache.hive.jdbc.TestJdbcDriver2$QueryBlockHook");
    stmt.execute("set " + QueryTag + " = " + fooQueryTag);
    stmt.close();
    QueryBlockHook.setupBlock(fooQueryTag);
    PreparedStatement pStmt = con.prepareStatement("select c1 from " + dataTypeTableName +
          " where c1 = 1") ;
    ResultSet res = pStmt.executeQuery();
    assertNotNull(pStmt.getWarnings());
    ResultSetMetaData md = res.getMetaData();
    // sanity check metadata
    assertEquals(md.getColumnCount(), 1); // only one result column
    assertEquals(md.getColumnLabel(1), "c1" ); // verify the column name
    try {
      res.next();
      assertTrue(false);
    } catch (SQLException e) {
      // verify that the fetch fails with query still running error
      assertEquals("HY010", e.getSQLState());
    }
    QueryBlockHook.clearBlock(fooQueryTag); // continue query
    // verify that we can now fetch data
    // the query could be still be running and might take a few more iteration to finish
    do {
      try {
        res.next();
        break;
      } catch (SQLException e) {
        if (e.getSQLState().equals("HY010")) {
          // if query is not complete, then try next time
          continue;
        } else {
          throw e;
        }
      }
    } while (true);
    assertEquals(1, res.getInt(1));
    pStmt.close();
  }

  /**
   *  Post execute hook that blocks the execution
   *  Used for async query testing
   */
  public static class QueryBlockHook implements ExecuteWithHookContext {

    private static Map<String, Boolean> triggerMap = new ConcurrentHashMap<String, Boolean>();

    public void run(HookContext hookContext) {
      String myTag = hookContext.getConf().get(QueryTag, "");
      if (myTag.isEmpty() || !triggerMap.containsKey(myTag)) {
        return;
      }
      while (triggerMap.get(myTag)) {
        try {
          Thread.sleep(200);
        } catch (InterruptedException e) {
          break;
        }
      }
    }

    // enable the post hook wait
    public static void setupBlock(String queryTag) {
      if (triggerMap.containsKey(queryTag)) {
        triggerMap.put(queryTag, true);
      }
    }

    // resume the post hook
    public static void clearBlock(String queryTag) {
      if (triggerMap.containsKey(queryTag)) {
        triggerMap.put(queryTag, false);
      }
    }

    public static void clearAll() {
      for (Map.Entry<String, Boolean> entry : triggerMap.entrySet()) {
        entry.setValue(false);
      }
    }

  }
}