import java.io.FileReader;
import java.sql.*;
import java.util.Properties;

/**
 * This executable class contains a demo application that connects via JDBC to an Oracle database and executes various SQL statements
 * @author Martin Zuckerstaetter
 * @version 1.0
 */
public class Labor07Vorlage {
    /**
     * This function is used to read a properties file and return a corresponding Properties object for futher use
     * @param filename The filename of the properties file
     * @return The Properties object from which key-value pairs from the file can be retrieved
     * @throws Exception Exceptions (e.g. during file operations) need to be handled by the caller
     */
    public static Properties readProperties(String filename) throws Exception {
        // the FileReader can read files from the file system
    	FileReader reader = new FileReader(filename);
    	// the Properties class can load files via a FileReader 
        Properties p = new Properties();
        p.load(reader);
        reader.close();
        return (p);
    }

    /**
     * This function is used to establish a Database connection and return a corresponding Connection object for further use
     * @param driverClass A JDBC driver class name, e.g. oracle.jdbc.driver.OracleDriver
     * @param jdbcUrl A valid JDBC URL, e.g. in the form jdbc:oracle:thin:@host_name:port:service_name
     * @param userName The username of the database account to connect to
     * @param password The password of the database account to connect to
     * @return The Connection object which can be further used to create and execute database statements
     * @throws Exception Exceptions (e.g. during database operations) need to be handled by the caller
     */
    public static Connection getDatabaseConnection(String driverClass, String jdbcUrl, String userName, String password) throws Exception
    {
        // Not required anymore with JDBC 4.0 drivers ... load the driver implementation class
        Class.forName(driverClass);
        // Use the DriverManager to get a Connection object 
        return DriverManager.getConnection(jdbcUrl, userName, password);
    }

    /**
     * This function executes a simple SQL DDL CREATE statement on the database
     * @param conn The JDBC Connection object to be used for the database query
     * @param propsSQL The Properties object, which contains the required SQL statements
     * @throws Exception Exceptions (e.g. during database operations) need to be handled by the caller
     */
    public static void runCreateDDL(Connection conn, Properties propsSQL) throws Exception
    {
        String sql = propsSQL.getProperty("createDDL");
        System.out.println("Running Create DDL: " + sql);
        // Create a statement via the Connection and execute it
        Statement statement = conn.createStatement();
        int result = statement.executeUpdate(sql);
        System.out.println("Return Value from DDL: " + result);
        statement.close();
    }

    /**
     * This function executes a simple SQL DDL DROP statement on the database
     * @param conn The JDBC Connection object to be used for the database query
     * @param propsSQL The Properties object, which contains the required SQL statements
     * @throws Exception Exceptions (e.g. during database operations) need to be handled by the caller
     */
    public static void runDropDDL(Connection conn, Properties propsSQL) throws Exception
    {
        String sql = propsSQL.getProperty("dropDDL");
        System.out.println("Running Drop DDL: " + sql);
        // Create a statement via the Connection and execute it
        Statement statement = conn.createStatement();
        int result = statement.executeUpdate(sql);
        System.out.println("Return Value from DDL: " + result);
        statement.close();
    }

    /**
     * This function executes a simple SQL SELECT statement on the database and returns an employeeID as integer
     * @param conn The JDBC Connection object to be used for the database query
     * @param propsSQL The Properties object, which contains the required SQL statements
     * @return The highest employeeID from the my_employees table, which is queried in this SQL SELECT statement
     * @throws Exception Exceptions (e.g. during database operations) need to be handled by the caller
     */
    public static int runSimpleSelect(Connection conn, Properties propsSQL) throws Exception
    {
        int employeeID = 0;
        String sql = propsSQL.getProperty("simpleSelect");
        System.out.println("Running Simple Select: " + sql);
        // Create a statement via the Connection and execute it
        Statement statement = conn.createStatement();
        ResultSet result = statement.executeQuery(sql);
        // Iterate through the results of the query until we reached the end of the rows in the result set
        while (result.next())
        {
        	// use get* functions for different data types and with the column name as parameter
            employeeID = result.getInt("employee_id");
            String firstName = result.getString("first_name");
            String lastName = result.getString("last_name");
            double salary = result.getDouble("salary");
            System.out.println("Found employee: "
                    + employeeID
                    + " [" + firstName + " " + lastName + ", Salary: " + salary + "]");
        }
        result.close();
        statement.close();
        return (employeeID);
    }

    /**
     * This function executes a prepared SQL SELECT statement on the database
     * @param conn The JDBC Connection object to be used for the database query
     * @param propsSQL The Properties object, which contains the required SQL statements
     * @param searchFirstName The first search parameter, corresponding to the first name in the my_employees table (SQL wildcards allowed)
     * @param searchLastName The second search parameter, corresponding to the last name in the my_employees table (SQL wildcards allowed)
     * @throws Exception Exceptions (e.g. during database operations) need to be handled by the caller
     */
    public static void runPreparedSelect(Connection conn, Properties propsSQL, String searchFirstName, String searchLastName) throws Exception
    {
        String sql = propsSQL.getProperty("preparedSelect");
        System.out.println("Running Prepared Select: " + sql);
        // Prepare a statement via the Connection and set BIND parameter values 
        PreparedStatement prepStatement = conn.prepareStatement(sql);
        prepStatement.setString(1,searchFirstName);
        prepStatement.setString(2,searchLastName);
        // Execute the prepared statement
        ResultSet result = prepStatement.executeQuery();
        // Iterate through the results of the query until we reached the end of the rows in the result set
        while (result.next())
        {
        	// use get* functions for different data types and with the column name as parameter
            int employeeID = result.getInt("employee_id");
            String firstName = result.getString("first_name");
            String lastName = result.getString("last_name");
            double salary = result.getDouble("salary");
            System.out.println("Found employee: "
                    + employeeID
                    + " [" + firstName + " " + lastName + ", Salary: " + salary + "]");
        }
        result.close();
        prepStatement.close();
    }

    /**
     * This function executes a prepared SQL INSERT statement on the database
     * @param conn The JDBC Connection object to be used for the database query
     * @param propsSQL The Properties object, which contains the required SQL statements
     * @param employeeID The employeeID to be used for the SQL INSERT into the my_employees table
     * @throws Exception Exceptions (e.g. during database operations) need to be handled by the caller
     */
    public static void runPreparedInsert(Connection conn, Properties propsSQL, int employeeID) throws Exception
    {
        String sql = propsSQL.getProperty("preparedInsert");
        System.out.println("Running Prepared Insert: " + sql);
        System.out.println("Using employee_id = " + employeeID);
        // Prepare a statement via the Connection and set BIND parameter values 
        PreparedStatement prepStatement = conn.prepareStatement(sql);
        prepStatement.setInt(1,employeeID);
        prepStatement.setString(2,"Maximilian");
        prepStatement.setString(3,"Mustermann");
        prepStatement.setString(4,"MMUSTERM");
        prepStatement.setString(5,"1-555-123456789");
        prepStatement.setDate(6, new Date(System.currentTimeMillis()));
        prepStatement.setString(7,"IT_PROG");
        prepStatement.setDouble(8,2000.0);
        prepStatement.setDouble(9,0.05);
        prepStatement.setInt(10,103);
        prepStatement.setInt(11,60);
        // Execute the prepared statement
        int rowsInserted = prepStatement.executeUpdate();
        System.out.println("Rows inserted: " + rowsInserted);
        prepStatement.close();
    }

    /**
     * This function executes a prepared SQL UPDATE statement on the database
     * @param conn The JDBC Connection object to be used for the database query
     * @param propsSQL The Properties object, which contains the required SQL statements
     * @param employeeID The employeeID to be used for restricting the SQL UPDATE on the my_employees table
     * @param salaryIncrease The salary increase value to be applied on the my_employees table
     * @throws Exception Exceptions (e.g. during database operations) need to be handled by the caller
     */
    public static void runPreparedUpdate(Connection conn, Properties propsSQL, int employeeID, double salaryIncrease) throws Exception
    {
        String sql = propsSQL.getProperty("preparedUpdate");
        System.out.println("Running Prepared Update: " + sql);
        System.out.println("Using employee_id = " + employeeID);
        // Prepare a statement via the Connection and set BIND parameter values 
        PreparedStatement prepStatement = conn.prepareStatement(sql);
        prepStatement.setDouble(1, salaryIncrease);
        prepStatement.setInt(2, employeeID);
        // Execute the prepared statement
        int rowsUpdated = prepStatement.executeUpdate();
        System.out.println("Rows updated: " + rowsUpdated);
        prepStatement.close();
    }

    /**
     * The main procedure of this demo JDBC application
     * First, two properties files are read, which contain database connection details and SQL statements
     * Then, a database connection is established
     * Afterwards, several SQL statements are executed on the database:
     * - A CREATE statement, which creates a working table
     * - A SELECT statement, which returns the highest employeeID
     * - An INSERT statement, which creates a new entry in the my_employees table
     * - An UPDATE statement, which increases the salary in the newly created entry in the my_employees table
     * - A SELECT statement, which retrieves details of the newly created employee
     * - A DROP statement, which drops our working table
     * @param args No command line arguments expected
     */
    public static void main(String[] args) {
        try
        {
        	// Read the connection details and SQL statements from properties files
            Properties propsConn = readProperties("connection.properties");
            Properties propsSQL = readProperties("sql.properties");
            // Establish the database connection
            Connection conn = getDatabaseConnection(propsConn.getProperty("driverClass"),
                    propsConn.getProperty("jdbcUrl"),
                    propsConn.getProperty("userName"),
                    propsConn.getProperty("password"));
            // Run the different SQL statements
            runCreateDDL(conn, propsSQL);
            int employeeID = runSimpleSelect(conn, propsSQL);
            employeeID++;
            runPreparedInsert(conn, propsSQL, employeeID);
            runPreparedUpdate(conn, propsSQL, employeeID, 1000.0);
            runPreparedSelect(conn, propsSQL, "Max%", "Muster%");
            runDropDDL(conn, propsSQL);
            conn.close();

        }
        catch (Exception e)
        {
        	// For simplicity, we just pass through all exceptions and print them here ...
            System.out.println("An Exception has occurred! See Details below:");
            e.printStackTrace();
        }
    }
}