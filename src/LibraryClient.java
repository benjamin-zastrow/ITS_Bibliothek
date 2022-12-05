import java.io.FileReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Scanner;

public class LibraryClient {
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


    public static void main(String[] args) {
        Connection conn = null;
        Scanner input = null;
        try
        {
            input = new Scanner(System.in);
        	// Read the connection details and SQL statements from properties files
            System.out.println("-- Verbindungseinstellungen und Queries werden geladen...");
            Properties propsConn = readProperties("connection.properties");
            Properties propsSQL = readProperties("sql.properties");
            // Establish the database connection
            System.out.println("-- Verbindung zur DB wird hergestellt...");
            conn = getDatabaseConnection(propsConn.getProperty("driverClass"),
                    propsConn.getProperty("jdbcUrl"),
                    propsConn.getProperty("userName"),
                    propsConn.getProperty("password"));
            conn.setAutoCommit(false);

            int choice;
            String strChoice;
            System.out.println("-- Willkommen bei ITSBib --");
            while(true) {
                System.out.println("-- Aktionen: \n-1- Reservieren\n-2- Ausleihen\n-3- Rückgabe\n\n-0- Programm beenden");
                System.out.print("Auswahl> ");
                choice = input.nextInt();
                if(choice == 0) {
                    break;
                }
                else if(choice == 1) {
                    int exemplarID = -1, customerID = -1, mediaID = -1;
                    String fetchDueDate = "";
                    while(true) {
                        System.out.println("-- Exemplarsuche:\n-1- Exemplar-ID\n-2- Titel\n\n-0- Abbruch");
                        System.out.print("Auswahl> ");
                        choice = input.nextInt();
                        if(choice == 0) {
                            break;
                        }
                        else if(choice == 1) {
                            System.out.print("Exemplar-ID> ");
                            choice = input.nextInt();
                            ArrayList<Integer> tmp = reservationFindTitleByExemplarID(conn, propsSQL, choice);
                            System.out.print("Dieses Exemplar wählen? (y/n)> ");
                            strChoice = input.next();
                            if(!strChoice.toLowerCase().equals("y")) {
                                break;
                            }
                            exemplarID = tmp.get(0);
                            mediaID = tmp.get(1);
                            break;
                        }
                        else if(choice == 2) {
                            System.out.print("Eingabe des Titels> ");
                            strChoice = input.next();
                            ArrayList<Integer> tmp = reservationFindTitleByName(conn, propsSQL, strChoice);
                            System.out.print("E-ID, die reserviert werden soll> ");
                            exemplarID = input.nextInt();
                            for(int i = 0; i < tmp.size(); i = i + 2) {
                                if(tmp.get(i) == exemplarID) {
                                    mediaID = tmp.get(i+1);
                                    break;
                                }
                            }
                            break;
                        }
                    }

                    if(choice==0) break;

                    while(customerID < 1) {
                        System.out.print("Kunden-ID> ");
                        customerID = input.nextInt();
                    }
                    if(!fskCheck(conn, propsSQL, customerID, exemplarID)) {
                        System.err.println("!! FSK-Check fehlgeschlagen. Reservierung nicht möglich.");
                        break;
                    }
                    System.out.print("Abholdatum ('DD-MM-YYYY')> ");
                    fetchDueDate = input.next();

                    if(!borrowableCheck(conn, propsSQL, exemplarID)) {
                        System.err.println("!! Fehler: In der Zwischenzeit wurde das Exemplar reserviert oder ausgeliehen. Abbruch!");
                        break;
                    }

                    if(!createReservation(conn, propsSQL, fetchDueDate, exemplarID, mediaID, customerID)) {
                        System.err.println("!! Fehler beim Anlegen einer Reservierung!");
                        break;
                    }
                    customerID = -1;
                    System.out.println("-- Reservierung erfolgreich erstellt! Die ReservierungsID zum direkten Abholen lautet: " + getLatestReservationID(conn, propsSQL));
                }
                else if (choice == 2) {
                    int exemplarID = -1, mediaID = -1, customerID = -1, reservationID = -1;
                    String estimatedReturnTime;
                    boolean basedOnReservation = false;
                    while(true) {
                        System.out.println("-- Ausleihe:\n-1- nach Reservierungs-ID\n-2- nach Exemplar-ID\n-3- Medien-Suche\n-0- Abbruch");
                        System.out.print("Auswahl> ");
                        choice = input.nextInt();
                        if(choice == 0) {
                            break;
                        }
                        else if(choice == 1) {
                            System.out.print("Reservierungs-ID> ");
                            choice = input.nextInt();
                            reservationID = choice;
                            ArrayList<Integer> tmp = borrowFindTitleByReservationID(conn, propsSQL, choice);
                            if(tmp.size() != 0) {
                                System.out.print("Diese Reservierung wählen? (y/n)> ");
                                strChoice = input.next();
                                if(!strChoice.toLowerCase().equals("y")) {
                                    break;
                                }
                                exemplarID = tmp.get(0);
                                mediaID = tmp.get(1);
                                basedOnReservation = true;
                            } else {
                                System.err.println("!! Keine Reservierung zur Abholung am heutigen Tage verfügbar.");
                                break;
                            }
                        } 
                        else if(choice == 2) {
                            System.out.print("Exemplar-ID> ");
                            choice = input.nextInt();
                            ArrayList<Integer> tmp = borrowFindTitleByExemplarID(conn, propsSQL, choice);
                            if(tmp.size() != 0) {
                                System.out.print("Dieses Exemplar wählen? (y/n)> ");
                                strChoice = input.next();
                                if(!strChoice.toLowerCase().equals("y")) {
                                    break;
                                }
                                exemplarID = tmp.get(0);
                                mediaID = tmp.get(1);
                            } else {
                                System.err.println("!! Kein ausleihbares Exemplar mit dieser ID gefunden");
                            }

                        }
                        else if(choice == 3) {
                            while(true) {
                                System.out.println("-- Medien-Suche:\n-1- nach Medien-ID\n-2- nach Medien-Titel\n-3- nach Medientyp\n-0- Abbruch");
                                System.out.print("Auswahl> ");
                                choice = input.nextInt();
                                if(choice == 0) {
                                    break;
                                }
                                else if(choice == 1) {
                                    System.out.print("Medien-ID> ");
                                    choice = input.nextInt();
                                    ArrayList<Integer> tmp = borrowFindTitleByMediaID(conn, propsSQL, choice);
                                    if(tmp.size() != 0) {
                                        System.out.print("E-ID, die ausgeliehen werden soll> ");
                                        exemplarID = input.nextInt();
                                        mediaID = choice;
                                        break;
                                    } else {
                                        System.err.println("!! Keine ausleihbaren Titel mit dieser Medien-ID gefunden");
                                        break;
                                    }
                                }
                                else if(choice == 2) {
                                    System.out.print("Medien-Titel> ");
                                    strChoice = input.next();
                                    ArrayList<Integer> tmp = borrowFindTitleByName(conn, propsSQL, strChoice);
                                    if(tmp.size() != 0) {
                                        System.out.print("E-ID, die ausgeliehen werden soll> ");
                                        exemplarID = input.nextInt();
                                        for(int i = 0; i < tmp.size(); i = i + 2) {
                                            if(tmp.get(i) == exemplarID) {
                                                mediaID = tmp.get(i+1);
                                                break;
                                            }
                                        }
                                        break;
                                    } else {
                                        System.err.println("!! Keine ausleihbaren Titel mit diesem Namen gefunden");
                                    }

                                }
                                else if(choice == 3) {
                                    System.out.print("Medientyp (Book, Movie, Magazine oder Audio)> ");
                                    strChoice = input.next();
                                    ArrayList<Integer> tmp = borrowFindTitleByMediaType(conn, propsSQL, strChoice);
                                    if(tmp.size() != 0) {
                                        System.out.print("E-ID, die ausgeliehen werden soll> ");
                                        exemplarID = input.nextInt();
                                        for(int i = 0; i < tmp.size(); i = i + 2) {
                                            if(tmp.get(i) == exemplarID) {
                                                mediaID = tmp.get(i+1);
                                                break;
                                            }
                                        }
                                        break;
                                    } else {
                                        System.err.println("!! Keine ausleihbaren Titel dieses Medientyps gefunden");
                                    }

                                }
                            }
                        }
                        if(exemplarID == -1) {break;}

                        while(customerID < 1) {
                            System.out.print("Kunden-ID> ");
                            customerID = input.nextInt();
                        }
                        if(!fskCheck(conn, propsSQL, customerID, exemplarID)) {
                            System.err.println("!! FSK-Check fehlgeschlagen. Ausleihe nicht möglich.");
                            break;
                        }
                        System.out.print("Zu erwartende Rückgabezeit ('DD-MM-YYYY')> ");
                        estimatedReturnTime = input.next();

                        if(!basedOnReservation) {
                            System.out.print("Basiert auf Reservierung (y/n)> ");
                            strChoice = input.next();
                            if(!strChoice.toLowerCase().equals("y")) {
                                basedOnReservation = false;
                            } else {
                                basedOnReservation = true;
                            }
                        }
    
                        if(!borrowableCheck(conn, propsSQL, exemplarID)) {
                            System.err.println("!! Fehler: In der Zwischenzeit wurde das Exemplar reserviert oder ausgeliehen. Abbruch!");
                            break;
                        }
                        if(!setExemplarBorrowStatus(conn, propsSQL, exemplarID, true)) {
                            System.err.println("!! Fehler bei dem Anlegen einer Ausleihe!");
                            break;
                        }
                        if(!createAusleihe(conn, propsSQL, estimatedReturnTime, basedOnReservation, mediaID, exemplarID, customerID, reservationID)) {
                            System.err.println("!! Fehler bei dem Anlegen einer Ausleihe!");
                            break;
                        }
                        System.out.println("-- Ausleihe erfolgreich erstellt!");
                        customerID = -1;
                        break;
                    }
                }
                else if (choice == 3) {
                    int exemplarID = -1, mediaID = -1, customerID = -1;
                    while(true) {
                        System.out.println("-- Rückgabe:\n-1- nach Exemplar-ID\n-2- Medien-Suche\n-0- Abbruch");
                        System.out.print("Auswahl> ");
                        choice = input.nextInt();
                        if(choice == 0) {
                            break;
                        }
                        else if(choice == 1) {
                            System.out.print("Exemplar-ID> ");
                            choice = input.nextInt();
                            ArrayList<Integer> tmp = returnFindTitleByExemplarID(conn, propsSQL, choice);
                            if(tmp.size() != 0) {
                                System.out.print("Dieses Exemplar wählen? (y/n)> ");
                                strChoice = input.next();
                                if(!strChoice.toLowerCase().equals("y")) {
                                    break;
                                }
                                exemplarID = tmp.get(0);
                                mediaID = tmp.get(1);
                            } else {
                                System.err.println("!! Es konnte kein retournierbares Exemplar mit dieser ID gefunden werden.");
                            }
                        }
                        else if(choice == 2) {
                            while(true) {
                                System.out.println("-- Medien-Suche:\n-1- nach Medien-ID\n-2- nach Medien-Titel\n-3- nach Medientyp\n-0- Abbruch");
                                System.out.print("Auswahl> ");
                                choice = input.nextInt();
                                if(choice == 0) {
                                    break;
                                }
                                else if(choice == 1) {
                                    System.out.print("Medien-ID> ");
                                    choice = input.nextInt();
                                    mediaID = choice;
                                    returnFindTitleByMediaID(conn, propsSQL, choice);
                                    System.out.print("E-ID, die zurückgegeben werden soll> ");
                                    exemplarID = input.nextInt();
                                    break;
                                }
                                else if(choice == 2) {
                                    System.out.print("Medien-Titel> ");
                                    strChoice = input.next();
                                    ArrayList<Integer> tmp = returnFindTitleByName(conn, propsSQL, strChoice);
                                    System.out.print("E-ID, die zurückgegeben werden soll> ");
                                    exemplarID = input.nextInt();
                                    for(int i = 0; i < tmp.size(); i = i + 2) {
                                        if(tmp.get(i) == exemplarID) {
                                            mediaID = tmp.get(i+1);
                                            break;
                                        }
                                    }
                                    break;
                                }
                                else if(choice == 3) {
                                    System.out.print("Medientyp (Book, Movie, Magazine or Audio)> ");
                                    strChoice = input.next();
                                    ArrayList<Integer> tmp = returnFindTitleByMediaType(conn, propsSQL, strChoice);
                                    System.out.print("E-ID, die zurückgegeben werden soll> ");
                                    exemplarID = input.nextInt();
                                    for(int i = 0; i < tmp.size(); i = i + 2) {
                                        if(tmp.get(i) == exemplarID) {
                                            mediaID = tmp.get(i+1);
                                            break;
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                        if(exemplarID == -1) {break;}

                        while(customerID < 1) {
                            System.out.print("Kunden-ID> ");
                            customerID = input.nextInt();
                        }

                        if(!returnTitle(conn, propsSQL, exemplarID, customerID, mediaID)) {
                            System.err.println("!! Fehler bei der Rückgabe");
                        }
                        System.out.println("-- Titel retourniert!");
                        break;
                    }
                }
            }

            conn.close();
            input.close();

        }
        catch (Exception e)
        {
        	// For simplicity, we just pass through all exceptions and print them here ...
            System.out.println("An Exception has occurred! See Details below:");
            e.printStackTrace();
            try {
                conn.close();
            } catch(SQLException f) {
                System.err.println("Error closing connection within the exception handling. Gosh.");
                System.err.println("Error Information:" + f.getMessage() + "; SQL-State" + f.getSQLState());
            }
            input.close();
        }
    }

    public static ArrayList<Integer> reservationFindTitleByExemplarID(Connection conn, Properties propsSQL, int exemplarID) throws SQLException {
        ArrayList<Integer> retVal = new ArrayList<>();
        String sql = propsSQL.getProperty("reservationFindTitleByExemplarID");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setInt(1, exemplarID);
            try(ResultSet result = prepStatement.executeQuery()) {
                if(result.next()) {
                    System.out.println("-- Suchergebnis");
                    System.out.println("Titel: " + result.getString(1));
                    int eID = result.getInt(2), mID = result.getInt(3);
                    System.out.println("Autor: " + result.getString(4));
                    System.out.println("E-ID: " + eID);
                    retVal.add(eID);
                    retVal.add(mID);
                }
                else {
                    System.err.println("!! Kein Suchergebnis");
                }
            } catch(SQLException e) {
                System.err.println("!! Exception in reservationFindTitleByExemplarID: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
                conn.rollback();
            }
        } catch(SQLException e) {
            System.err.println("!! Exception in reservationFindTitleByExemplarID: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            conn.rollback();
        }
        conn.commit();
        return retVal;
    }
    public static ArrayList<Integer> reservationFindTitleByName(Connection conn, Properties propsSQL, String name) throws SQLException {
        ArrayList<Integer> retVal = new ArrayList<>();
        String sql = propsSQL.getProperty("reservationFindTitleByName");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setString(1, name);
            try(ResultSet result = prepStatement.executeQuery()) {
                System.out.println("-- Suchergebnisse");
                while(result.next()) {
                    System.out.println("Titel: " + result.getString(1));
                    int eID = result.getInt(2), mID = result.getInt(3);
                    System.out.println("Autor: " + result.getString(4));
                    System.out.println("E-ID: " + eID);
                    retVal.add(eID);
                    retVal.add(mID);
                    System.out.println("--");
                }
            } catch(SQLException e) {
                System.err.println("!! Exception in reservationFindTitleByName: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
                conn.rollback();
            }
        } catch(SQLException e) {
            System.err.println("!! Exception in reservationFindTitleByName: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            conn.rollback();
        }
        conn.commit();
        return retVal;
    }
    public static boolean fskCheck(Connection conn, Properties propsSQL, int customerID, int exemplarID) throws SQLException {
        boolean retVal = false;
        String sql = propsSQL.getProperty("fskCheck");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setInt(1, customerID);
            prepStatement.setInt(2, exemplarID);
            try(ResultSet result = prepStatement.executeQuery()) {
                if(result.next()) {
                    if(result.getInt(1) == 0) {
                        retVal = true;
                    } else {
                        retVal = false;
                    }
                }
            } catch(SQLException e) {
                System.err.println("!! Exception in fskCheck: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
                conn.rollback();
            }
        } catch(SQLException e) {
            System.err.println("!! Exception in fskCheck: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            conn.rollback();
        }
        conn.commit();
        return retVal;
    }
    public static boolean reservableCheck(Connection conn, Properties propsSQL, int exemplarID) throws SQLException {
        boolean retVal = false;
        String sql = propsSQL.getProperty("fskCheck");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setInt(1, exemplarID);
            try(ResultSet result = prepStatement.executeQuery()) {
                if(result.next()) {
                    if(result.getInt(1) == 0) {
                        retVal = true;
                    } else {
                        retVal = false;
                    }
                }
            } catch(SQLException e) {
                System.err.println("!! Exception in fskCheck: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
                conn.rollback();
            }
        } catch(SQLException e) {
            System.err.println("!! Exception in fskCheck: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            conn.rollback();
        }
        conn.commit();
        return retVal;
    }
    public static boolean createReservation(Connection conn, Properties propsSQL, String fetchDueTime, int exemplarID, int mediaID, int customerID) throws SQLException {
        boolean retVal = true;
        String sql = propsSQL.getProperty("createReservation");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setString(1, fetchDueTime);
            prepStatement.setInt(2, exemplarID);
            prepStatement.setInt(3, mediaID);
            prepStatement.setInt(4, customerID);
            prepStatement.executeUpdate();
        } catch(SQLException e) {
            System.err.println("!! Exception in createReservation: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            retVal = false;
            conn.rollback();
        }
        conn.commit();
        return retVal;
    }
    public static int getLatestReservationID(Connection conn, Properties propsSQL) throws SQLException {
        int retVal = -1;
        String sql = propsSQL.getProperty("getLatestReservationID");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            try(ResultSet result = prepStatement.executeQuery()) {
                if(result.next()) {
                    retVal = result.getInt(1);
                }
            } catch(SQLException e) {
                System.err.println("!! Exception in getLatestReservationID: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
                conn.rollback();
            }
        } catch(SQLException e) {
            System.err.println("!! Exception in getLatestReservationID: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            conn.rollback();
        }
        conn.commit();
        return retVal;
    }

    public static ArrayList<Integer> borrowFindTitleByReservationID(Connection conn, Properties propsSQL, int reservationID) throws SQLException {
        ArrayList<Integer> retVal = new ArrayList<>();
        String sql = propsSQL.getProperty("borrowFindTitleByReservationID");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setInt(1, reservationID);
            try(ResultSet result = prepStatement.executeQuery()) {
                System.out.println("-- Suchergebnisse");
                while(result.next()) {
                    System.out.println("Titel: " + result.getString(1));
                    int eID = result.getInt(2), mID = result.getInt(3);
                    retVal.add(eID);
                    retVal.add(mID);
                    System.out.println("Autor: " + result.getString(4));
                    System.out.println("E-ID: " + eID);
                    System.out.println("Reservator: " + result.getString(6));
                }
            } catch(SQLException e) {
                System.err.println("!! Exception in borrowFindTitleByReservationID: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
                conn.rollback();
            }
        } catch(SQLException e) {
            System.err.println("!! Exception in borrowFindTitleByReservationID: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            conn.rollback();
        }
        conn.commit();
        return retVal;
    }
    public static ArrayList<Integer> borrowFindTitleByExemplarID(Connection conn, Properties propsSQL, int exemplarID) throws SQLException {
        ArrayList<Integer> retVal = new ArrayList<>();
        String sql = propsSQL.getProperty("borrowFindTitleByExemplarID");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setInt(1, exemplarID);
            try(ResultSet result = prepStatement.executeQuery()) {
                System.out.println("-- Suchergebnisse");
                while(result.next()) {
                    System.out.println("Titel: " + result.getString(1));
                    int eID = result.getInt(2), mID = result.getInt(3);
                    retVal.add(eID);
                    retVal.add(mID);
                    System.out.println("Autor: " + result.getString(4));
                    System.out.println("E-ID: " + eID);
                    System.out.println("Reservator: " + result.getString(6));
                }
            } catch(SQLException e) {
                System.err.println("!! Exception in borrowFindTitleByExemplarID: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
                conn.rollback();
            }
        } catch(SQLException e) {
            System.err.println("!! Exception in borrowFindTitleByExemplarID: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            conn.rollback();
        }
        conn.commit();
        return retVal;
    }
    public static ArrayList<Integer> borrowFindTitleByMediaID(Connection conn, Properties propsSQL, int mediaID) throws SQLException {
        ArrayList<Integer> retVal = new ArrayList<>();
        String sql = propsSQL.getProperty("borrowFindTitleByMediaID");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setInt(1, mediaID);
            try(ResultSet result = prepStatement.executeQuery()) {
                System.out.println("-- Suchergebnisse");
                while(result.next()) {
                    System.out.println("Titel: " + result.getString(1));
                    int eID = result.getInt(2), mID = result.getInt(3);
                    retVal.add(eID);
                    retVal.add(mID);
                    System.out.println("Autor: " + result.getString(4));
                    System.out.println("E-ID: " + eID);
                    System.out.println("Reservator: " + result.getString(6));
                }
            } catch(SQLException e) {
                System.err.println("!! Exception in borrowFindTitleByMediaID: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
                conn.rollback();
            }
        } catch(SQLException e) {
            System.err.println("!! Exception in borrowFindTitleByMediaID: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            conn.rollback();
        }
        conn.commit();
        return retVal;
    }
    public static ArrayList<Integer> borrowFindTitleByName(Connection conn, Properties propsSQL, String name) throws SQLException {
        ArrayList<Integer> retVal = new ArrayList<>();
        String sql = propsSQL.getProperty("borrowFindTitleByName");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setString(1, name);
            try(ResultSet result = prepStatement.executeQuery()) {
                System.out.println("-- Suchergebnisse");
                while(result.next()) {
                    System.out.println("Titel: " + result.getString(1));
                    int eID = result.getInt(2), mID = result.getInt(3);
                    retVal.add(eID);
                    retVal.add(mID);
                    System.out.println("Autor: " + result.getString(4));
                    System.out.println("E-ID: " + eID);
                    System.out.println("Reservator: " + result.getString(6));
                }
            } catch(SQLException e) {
                System.err.println("!! Exception in borrowFindTitleByName: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
                conn.rollback();
            }
        } catch(SQLException e) {
            System.err.println("!! Exception in borrowFindTitleByName: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            conn.rollback();
        }
        conn.commit();
        return retVal;
    }
    public static ArrayList<Integer> borrowFindTitleByMediaType(Connection conn, Properties propsSQL, String mediaType) throws SQLException {
        ArrayList<Integer> retVal = new ArrayList<>();
        String sql = propsSQL.getProperty("borrowFindTitleByMediaType");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setString(1, mediaType);
            try(ResultSet result = prepStatement.executeQuery()) {
                System.out.println("-- Suchergebnisse");
                while(result.next()) {
                    System.out.println("Titel: " + result.getString(1));
                    int eID = result.getInt(2), mID = result.getInt(3);
                    retVal.add(eID);
                    retVal.add(mID);
                    System.out.println("Autor: " + result.getString(4));
                    System.out.println("E-ID: " + eID);
                    System.out.println("Reservator: " + result.getString(6));
                    System.out.println("--");
                }
            } catch(SQLException e) {
                System.err.println("!! Exception in borrowFindTitleByMediaType: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
                conn.rollback();
            }
        } catch(SQLException e) {
            System.err.println("!! Exception in borrowFindTitleByMediaType: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            conn.rollback();
        }
        conn.commit();
        return retVal;
    }
    public static boolean borrowableCheck(Connection conn, Properties propsSQL, int exemplarID) throws SQLException {
        boolean retVal = false;
        String sql = propsSQL.getProperty("borrowableCheck");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setInt(1, exemplarID);
            try(ResultSet result = prepStatement.executeQuery()) {
                if(result.next()) {
                    if(result.getInt(1) == 0) {
                        retVal = true;
                    } else {
                        retVal = false;
                    }
                }
            } catch(SQLException e) {
                System.err.println("!! Exception in borrowableCheck: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
                conn.rollback();
            }
        } catch(SQLException e) {
            System.err.println("!! Exception in borrowableCheck: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            conn.rollback();
        }
        conn.commit();
        return retVal;
    }
    public static boolean setExemplarBorrowStatus(Connection conn, Properties propsSQL, int exemplarID, boolean isBorrowed) throws SQLException {
        boolean retVal = true;
        String sql = propsSQL.getProperty("setExemplarBorrowStatus");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setInt(2, exemplarID);
            if(isBorrowed) {
                prepStatement.setString(1, "t");
            } else {
                prepStatement.setString(1, "f");
            }
            prepStatement.executeUpdate();
        } catch(SQLException e) {
            System.err.println("!! Exception in setExemplarBorrowStatus: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            retVal = false;
            conn.rollback();
        }
        conn.commit();
        return retVal;
    }
    public static boolean createAusleihe(Connection conn, Properties propsSQL, String estimatedReturnTime, boolean basedOnReservation, int mediaID, int exemplarID, int customerID, int reservationID) throws SQLException {
        boolean retVal = true;
        String sql = propsSQL.getProperty("createAusleihe");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setString(1, estimatedReturnTime);
            if(basedOnReservation) {
                prepStatement.setString(2, "t");
                prepStatement.setInt(6, reservationID);
            } else {
                prepStatement.setString(2, "f");
                prepStatement.setNull(6, java.sql.Types.NUMERIC);
            }
            prepStatement.setInt(3, mediaID);
            prepStatement.setInt(4, exemplarID);
            prepStatement.setInt(5, customerID);
            prepStatement.setInt(7, mediaID);
            prepStatement.executeUpdate();
        } catch(SQLException e) {
            System.err.println("!! Exception in createAusleihe: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            retVal = false;
            conn.rollback();
        }
        conn.commit();
        return retVal;
    }
    public static int getLatestAusleiheID(Connection conn, Properties propsSQL) throws SQLException {
        int retVal = -1;
        String sql = propsSQL.getProperty("getLatestAusleiheID");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            try(ResultSet result = prepStatement.executeQuery()) {
                if(result.next()) {
                    retVal = result.getInt(1);
                }
            } catch(SQLException e) {
                System.err.println("!! Exception in getLatestAusleiheID: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
                conn.rollback();
            }
        } catch(SQLException e) {
            System.err.println("!! Exception in getLatestAusleiheID: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            conn.rollback();
        }
        conn.commit();
        return retVal;
    }

    public static ArrayList<Integer> returnFindTitleByExemplarID(Connection conn, Properties propsSQL, int exemplarID) throws SQLException {
        ArrayList<Integer> retVal = new ArrayList<>();
        String sql = propsSQL.getProperty("returnFindTitleByExemplarID");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setInt(1, exemplarID);
            try(ResultSet result = prepStatement.executeQuery()) {
                System.out.println("-- Suchergebnisse");
                while(result.next()) {
                    System.out.println("Titel: " + result.getString(1));
                    int eID = result.getInt(2), mID = result.getInt(3);
                    retVal.add(eID);
                    retVal.add(mID);
                    System.out.println("Autor: " + result.getString(4));
                    System.out.println("E-ID: " + eID);
                    System.out.println("Reservator: " + result.getString(6));
                    System.out.println("Ausleihzeit: " + result.getDate(7));
                    System.out.println("erwartete Rückgabezeit: " + result.getDate(8));
                }
            } catch(SQLException e) {
                System.err.println("!! Exception in returnFindTitleByExemplarID: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
                conn.rollback();
            }
        } catch(SQLException e) {
            System.err.println("!! Exception in returnFindTitleByExemplarID: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            conn.rollback();
        }
        conn.commit();
        return retVal;
    }
    public static ArrayList<Integer> returnFindTitleByMediaID(Connection conn, Properties propsSQL, int mediaID) throws SQLException {
        ArrayList<Integer> retVal = new ArrayList<>();
        String sql = propsSQL.getProperty("returnFindTitleByMediaID");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setInt(1, mediaID);
            try(ResultSet result = prepStatement.executeQuery()) {
                System.out.println("-- Suchergebnisse");
                while(result.next()) {
                    System.out.println("Titel: " + result.getString(1));
                    int eID = result.getInt(2), mID = result.getInt(3);
                    retVal.add(eID);
                    retVal.add(mID);
                    System.out.println("Autor: " + result.getString(4));
                    System.out.println("E-ID: " + eID);
                    System.out.println("Reservator: " + result.getString(6));
                    System.out.println("Ausleihzeit: " + result.getDate(7));
                    System.out.println("erwartete Rückgabezeit: " + result.getDate(8));
                }
            } catch(SQLException e) {
                System.err.println("!! Exception in returnFindTitleByMediaID: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
                conn.rollback();
            }
        } catch(SQLException e) {
            System.err.println("!! Exception in returnFindTitleByMediaID: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            conn.rollback();
        }
        conn.commit();
        return retVal;
    }
    public static ArrayList<Integer> returnFindTitleByName(Connection conn, Properties propsSQL, String name) throws SQLException {
        ArrayList<Integer> retVal = new ArrayList<>();
        String sql = propsSQL.getProperty("returnFindTitleByName");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setString(1, name);
            try(ResultSet result = prepStatement.executeQuery()) {
                System.out.println("-- Suchergebnisse");
                while(result.next()) {
                    System.out.println("Titel: " + result.getString(1));
                    int eID = result.getInt(2), mID = result.getInt(3);
                    retVal.add(eID);
                    retVal.add(mID);
                    System.out.println("Autor: " + result.getString(4));
                    System.out.println("E-ID: " + eID);
                    System.out.println("Reservator: " + result.getString(6));
                    System.out.println("Ausleihzeit: " + result.getDate(7));
                    System.out.println("erwartete Rückgabezeit: " + result.getDate(8));
                }
            } catch(SQLException e) {
                System.err.println("!! Exception in returnFindTitleByName: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
                conn.rollback();
            }
        } catch(SQLException e) {
            System.err.println("!! Exception in returnFindTitleByName: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            conn.rollback();
        }
        conn.commit();
        return retVal;
    }
    public static ArrayList<Integer> returnFindTitleByMediaType(Connection conn, Properties propsSQL, String mediaType) throws SQLException {
        ArrayList<Integer> retVal = new ArrayList<>();
        String sql = propsSQL.getProperty("returnFindTitleByMediaType");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setString(1, mediaType);
            try(ResultSet result = prepStatement.executeQuery()) {
                System.out.println("-- Suchergebnisse");
                while(result.next()) {
                    System.out.println("Titel: " + result.getString(1));
                    int eID = result.getInt(2), mID = result.getInt(3);
                    retVal.add(eID);
                    retVal.add(mID);
                    System.out.println("Autor: " + result.getString(4));
                    System.out.println("E-ID: " + eID);
                    System.out.println("Reservator: " + result.getString(6));
                    System.out.println("Ausleihzeit: " + result.getDate(7));
                    System.out.println("erwartete Rückgabezeit: " + result.getDate(8));
                }
            } catch(SQLException e) {
                System.err.println("!! Exception in returnFindTitleByMediaType: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
                conn.rollback();
            }
        } catch(SQLException e) {
            System.err.println("!! Exception in returnFindTitleByMediaType: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            conn.rollback();
        }
        conn.commit();
        return retVal;
    }
    public static boolean returnTitle(Connection conn, Properties propsSQL, int exemplarID, int customerID, int mediaID) throws SQLException {
        boolean retVal = true;
        String sql = propsSQL.getProperty("returnTitle1");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setInt(1, exemplarID);
            prepStatement.setInt(2, exemplarID);
            prepStatement.executeUpdate();
        } catch(SQLException e) {
            System.err.println("!! Exception in returnTitle: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            retVal = false;
            conn.rollback();
        }

        if(!retVal) return retVal;

        sql = propsSQL.getProperty("returnTitle2");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setInt(1, exemplarID);
            prepStatement.executeUpdate();
        } catch(SQLException e) {
            System.err.println("!! Exception in returnTitle: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            retVal = false;
            conn.rollback();
        }

        if(!retVal) return retVal;

        sql = propsSQL.getProperty("returnTitle3");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setInt(1, exemplarID);
            prepStatement.setInt(2, mediaID);
            prepStatement.setInt(3, customerID);
            prepStatement.setInt(4, exemplarID);
            prepStatement.executeUpdate();
        } catch(SQLException e) {
            System.err.println("!! Exception in returnTitle: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            retVal = false;
            conn.rollback();
        }
        conn.commit();
        return retVal;
    }


    


    /* public static int borrowFindTitleByExemplarID(Connection conn, Properties propsSQL, int exemplarID) throws Exception
    {
        int retval = -1;
        String sql = propsSQL.getProperty("borrowFindTitleByExemplarID");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setInt(1, exemplarID);
            try(ResultSet result = prepStatement.executeQuery()) {
                if(result.next()) {
                    System.out.println("-- Suchergebnis");
                    System.out.println("Titel: " + result.getString(1));
                    System.out.println("Autor: " + result.getString(3));
                    retval = result.getInt(2);
                    System.out.println("E-ID: " + retval);
                    System.out.println("Reservator: " + result.getString(5));
                    Date d;
                    if((d = result.getDate(6)) != null) {
                        System.out.println("Ausleihzeit: " + d.toString());
                    }
                    if((d = result.getDate(7)) != null) {
                        System.out.println("Erwartete Rückgabezeit: " + d.toString());
                    }
                }
                else {
                    System.err.println("!! Kein Suchergebnis");
                }
            } catch(SQLException e) {
                System.err.println("!! Exception in borrowFindTitleByExemplarID: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            }
        } catch(SQLException e) {
            System.err.println("!! Exception in borrowFindTitleByExemplarID: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
        }
        return retval;
    }

    public static void borrowFindTitleByName(Connection conn, Properties propsSQL, String name) throws Exception
    {
        String sql = propsSQL.getProperty("borrowFindTitleByName");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setString(1, name);
            try(ResultSet result = prepStatement.executeQuery()) {
                System.out.println("-- Suchergebnisse");
                while(result.next()) {
                    System.out.println("Titel: " + result.getString(1));
                    System.out.println("Autor: " + result.getString(3));
                    System.out.println("E-ID: " + result.getInt(2));
                    System.out.println("Reservator: " + result.getString(5));
                    Date d;
                    if((d = result.getDate(6)) != null) {
                        System.out.println("Ausleihzeit: " + d.toString());
                    }
                    if((d = result.getDate(7)) != null) {
                        System.out.println("Erwartete Rückgabezeit: " + d.toString());
                    }
                    System.out.println("--");
                }
            } catch(SQLException e) {
                System.err.println("!! Exception in borrowFindTitleByName: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            }
        } catch(SQLException e) {
            System.err.println("!! Exception in borrowFindTitleByName: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
        }
    }

    public static void borrowFindTitleByMediaID(Connection conn, Properties propsSQL, int mediaID) throws Exception
    {
        String sql = propsSQL.getProperty("borrowFindTitleByMediaID");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setInt(1, mediaID);
            try(ResultSet result = prepStatement.executeQuery()) {
                System.out.println("-- Suchergebnisse");
                while(result.next()) {
                    System.out.println("Titel: " + result.getString(1));
                    System.out.println("Autor: " + result.getString(3));
                    System.out.println("E-ID: " + result.getInt(2));
                    System.out.println("Reservator: " + result.getString(5));
                    Date d;
                    if((d = result.getDate(6)) != null) {
                        System.out.println("Ausleihzeit: " + d.toString());
                    }
                    if((d = result.getDate(7)) != null) {
                        System.out.println("Erwartete Rückgabezeit: " + d.toString());
                    }
                    System.out.println("--");
                }
            } catch(SQLException e) {
                System.err.println("!! Exception in borrowFindTitleByMediaID: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            }
        } catch(SQLException e) {
            System.err.println("!! Exception in borrowFindTitleByMediaID: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
        }
    }

    public static void borrowFindTitleByMediaType(Connection conn, Properties propsSQL, String mediaType) throws Exception
    {
        String sql = propsSQL.getProperty("borrowFindTitleByMediaType");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setString(1, mediaType);
            try(ResultSet result = prepStatement.executeQuery()) {
                System.out.println("-- Suchergebnisse");
                while(result.next()) {
                    System.out.println("Titel: " + result.getString(1));
                    System.out.println("Autor: " + result.getString(3));
                    System.out.println("E-ID: " + result.getInt(2));
                    System.out.println("Reservator: " + result.getString(5));
                    Date d;
                    if((d = result.getDate(6)) != null) {
                        System.out.println("Ausleihzeit: " + d.toString());
                    }
                    if((d = result.getDate(7)) != null) {
                        System.out.println("Erwartete Rückgabezeit: " + d.toString());
                    }
                    System.out.println("--");
                }
            } catch(SQLException e) {
                System.err.println("!! Exception in borrowFindTitleByMediaType: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            }
        } catch(SQLException e) {
            System.err.println("!! Exception in borrowFindTitleByMediaType: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
        }
    }

    public static boolean fskCheck(Connection conn, Properties propsSQL, int customerID, int exemplarID) throws Exception
    {
        boolean retval = false;
        String sql = propsSQL.getProperty("fskCheck");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setInt(1, customerID);
            prepStatement.setInt(2, exemplarID);
            try(ResultSet result = prepStatement.executeQuery()) {
                if(result.next()) {
                    retval = true;
                }
            } catch(SQLException e) {
                System.err.println("!! Exception in fskCheck: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            }
        } catch(SQLException e) {
            System.err.println("!! Exception in fskCheck: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
        }

        return retval;
    }

    public static boolean borrowableCheck(Connection conn, Properties propsSQL, int exemplarID) throws Exception
    {
        boolean retval = false;
        String sql = propsSQL.getProperty("borrowableCheck");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setInt(1, exemplarID);
            try(ResultSet result = prepStatement.executeQuery()) {
                if(result.next()) {
                    retval = true;
                }
            } catch(SQLException e) {
                System.err.println("!! Exception in borrowableCheck: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            }
        } catch(SQLException e) {
            System.err.println("!! Exception in borrowableCheck: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
        }

        return retval;
    }

    public static int createReservationAusleihe(Connection conn, Properties propsSQL, int mediaID, int exemplarID, int customerID) throws Exception
    {
        int retval = -1;
        String sql = propsSQL.getProperty("createReservationAusleihe");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setInt(1, mediaID);
            prepStatement.setInt(2, exemplarID);
            prepStatement.setInt(3, customerID);
            prepStatement.executeUpdate();
        } catch(SQLException e) {
            System.err.println("!! Exception in createReservationAusleihe: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
        }

        sql = propsSQL.getProperty("getLatestReservationAusleiheID");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            try(ResultSet result = prepStatement.executeQuery()) {
                if(result.next()) {
                    retval = result.getInt(1);
                }
            } catch(SQLException e) {
                System.err.println("!! Exception in createReservationAusleihe: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            }
        } catch(SQLException e) {
            System.err.println("!! Exception in createReservationAusleihe: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
        }

        return retval;
    }

    public static int createReservation(Connection conn, Properties propsSQL, String fetchDueTime, int ausleiheID) throws Exception
    {
        int retval = -1;
        String sql = propsSQL.getProperty("createReservation");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setString(1, fetchDueTime);
            prepStatement.setInt(2, ausleiheID);
            prepStatement.executeUpdate();
        } catch(SQLException e) {
            System.err.println("!! Exception in createReservation: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
        }

        sql = propsSQL.getProperty("getLatestReservationID");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            try(ResultSet result = prepStatement.executeQuery()) {
                if(result.next()) {
                    retval = result.getInt(1);
                }
            } catch(SQLException e) {
                System.err.println("!! Exception in createReservation: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            }
        } catch(SQLException e) {
            System.err.println("!! Exception in createReservation: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
        }

        return retval;
    }

    public static int createAusleihe(Connection conn, Properties propsSQL, String estimatedReturnTime, boolean basedOnReservation, int mediaID, int exemplarID, int customerID) throws Exception
    {
        int retval = -1;
        String sql = propsSQL.getProperty("createAusleihe");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setString(1, estimatedReturnTime);
            if(basedOnReservation) {
                prepStatement.setString(2, "t");
            } else {
                prepStatement.setString(2, "t");
            }
            prepStatement.setInt(3, mediaID);
            prepStatement.setInt(4, exemplarID);
            prepStatement.setInt(5, customerID);
            prepStatement.executeUpdate();
        } catch(SQLException e) {
            System.err.println("!! Exception in createAusleihe: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
        }

        sql = propsSQL.getProperty("getLatestAusleiheID");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            try(ResultSet result = prepStatement.executeQuery()) {
                if(result.next()) {
                    retval = result.getInt(1);
                }
            } catch(SQLException e) {
                System.err.println("!! Exception in createAusleihe: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            }
        } catch(SQLException e) {
            System.err.println("!! Exception in createAusleihe: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
        }

        return retval;
    }
    
    public static void setExemplarBorrowStatus(Connection conn, Properties propsSQL, int exemplarID, boolean borrowStatus) throws Exception
    {
        String sql = propsSQL.getProperty("setExemplarBorrowStatus");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            if(borrowStatus) {
                prepStatement.setString(1, "t");
            } else {
                prepStatement.setString(1, "f");
            }
            prepStatement.setInt(2, exemplarID);
            prepStatement.executeUpdate();
        } catch(SQLException e) {
            System.err.println("!! Exception in setExemplarBorrowStatus: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
        }
    }

    public static int returnFindTitleByExemplarID(Connection conn, Properties propsSQL, int exemplarID) throws Exception
    {
        int retval = -1;
        String sql = propsSQL.getProperty("returnFindTitleByExemplarID");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setInt(1, exemplarID);
            try(ResultSet result = prepStatement.executeQuery()) {
                if(result.next()) {
                    System.out.println("-- Suchergebnis");
                    System.out.println("Titel: " + result.getString(1));
                    System.out.println("Autor: " + result.getString(3));
                    retval = result.getInt(2);
                    System.out.println("E-ID: " + retval);
                    System.out.println("Reservator: " + result.getString(5));
                    Date d;
                    if((d = result.getDate(6)) != null) {
                        System.out.println("Ausleihzeit: " + d.toString());
                    }
                    if((d = result.getDate(7)) != null) {
                        System.out.println("Erwartete Rückgabezeit: " + d.toString());
                    }
                }
                else {
                    System.err.println("!! Kein Suchergebnis");
                }
            } catch(SQLException e) {
                System.err.println("!! Exception in returnFindTitleByExemplarID: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            }
        } catch(SQLException e) {
            System.err.println("!! Exception in returnFindTitleByExemplarID: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
        }
        return retval;
    }

    public static void returnFindTitleByMediaID(Connection conn, Properties propsSQL, int mediaID) throws Exception
    {
        String sql = propsSQL.getProperty("returnFindTitleByMediaID");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setInt(1, mediaID);
            try(ResultSet result = prepStatement.executeQuery()) {
                System.out.println("-- Suchergebnisse");
                while(result.next()) {
                    System.out.println("Titel: " + result.getString(1));
                    System.out.println("Autor: " + result.getString(3));
                    System.out.println("E-ID: " + result.getInt(2));
                    System.out.println("Reservator: " + result.getString(5));
                    Date d;
                    if((d = result.getDate(6)) != null) {
                        System.out.println("Ausleihzeit: " + d.toString());
                    }
                    if((d = result.getDate(7)) != null) {
                        System.out.println("Erwartete Rückgabezeit: " + d.toString());
                    }
                    System.out.println("--");
                }
            } catch(SQLException e) {
                System.err.println("!! Exception in returnFindTitleByMediaID: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            }
        } catch(SQLException e) {
            System.err.println("!! Exception in returnFindTitleByMediaID: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
        }
    }

    public static void returnFindTitleByName(Connection conn, Properties propsSQL, String name) throws Exception
    {
        String sql = propsSQL.getProperty("returnFindTitleByName");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setString(1, name);
            try(ResultSet result = prepStatement.executeQuery()) {
                System.out.println("-- Suchergebnisse");
                while(result.next()) {
                    System.out.println("Titel: " + result.getString(1));
                    System.out.println("Autor: " + result.getString(3));
                    System.out.println("E-ID: " + result.getInt(2));
                    System.out.println("Reservator: " + result.getString(5));
                    Date d;
                    if((d = result.getDate(6)) != null) {
                        System.out.println("Ausleihzeit: " + d.toString());
                    }
                    if((d = result.getDate(7)) != null) {
                        System.out.println("Erwartete Rückgabezeit: " + d.toString());
                    }
                    System.out.println("--");
                }
            } catch(SQLException e) {
                System.err.println("!! Exception in returnFindTitleByName: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            }
        } catch(SQLException e) {
            System.err.println("!! Exception in returnFindTitleByName: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
        }
    }

    public static void returnFindTitleByMediaType(Connection conn, Properties propsSQL, String mediaType) throws Exception
    {
        String sql = propsSQL.getProperty("returnFindTitleByMediaType");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setString(1, mediaType);
            try(ResultSet result = prepStatement.executeQuery()) {
                System.out.println("-- Suchergebnisse");
                while(result.next()) {
                    System.out.println("Titel: " + result.getString(1));
                    System.out.println("Autor: " + result.getString(3));
                    System.out.println("E-ID: " + result.getInt(2));
                    System.out.println("Reservator: " + result.getString(5));
                    Date d;
                    if((d = result.getDate(6)) != null) {
                        System.out.println("Ausleihzeit: " + d.toString());
                    }
                    if((d = result.getDate(7)) != null) {
                        System.out.println("Erwartete Rückgabezeit: " + d.toString());
                    }
                    System.out.println("--");
                }
            } catch(SQLException e) {
                System.err.println("!! Exception in returnFindTitleByMediaType: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
            }
        } catch(SQLException e) {
            System.err.println("!! Exception in returnFindTitleByMediaType: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
        }
    }

    public static void returnTitle(Connection conn, Properties propsSQL, int exemplarID, int customerID) throws Exception
    {
        String sql = propsSQL.getProperty("returnTitle");
        try(PreparedStatement prepStatement = conn.prepareStatement(sql)) {
            prepStatement.setInt(1, exemplarID);
            prepStatement.setInt(2, exemplarID);
            prepStatement.setInt(3, exemplarID);
            prepStatement.setInt(4, exemplarID);
            prepStatement.setInt(5, exemplarID);
            prepStatement.setInt(6, customerID);
            prepStatement.executeUpdate();
        } catch(SQLException e) {
            System.err.println("!! Exception in setExemplarBorrowStatus: " +  e.getMessage() + "; SQL-State: " + e.getSQLState());
        }
    } */
}