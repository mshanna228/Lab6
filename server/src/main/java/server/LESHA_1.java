package server;
import common.model.Worker;

import java.io.IOException;
import java.sql.*;
import java.util.stream.Collectors;

public class LESHA_1 {
    public static LESHA_1 INSTANCE = new LESHA_1();

    LESHA_1() {
        try {
            Class.forName("org.postgresql.Driver");
            connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/studs", "s505045", "x0E4qloofOgALNWu");
        } catch (SQLException e) {
            System.out.println(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        try {
            Statement statement = connection.createStatement();
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS Worker( id SERIAL PRIMARY KEY, name VARCHAR(100) NOT NULL, coordinates_x Integer, coordinates_y DOUBLE PRECISION, creationDate timestamp, salary Integer, position Integer, status Integer, organization INTEGER NOT NULL REFERENCES Organization(organization_id)); ");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS Organization(id DERIAL PRIMARY KEY, Organization_type Integer));");


        } catch (SQLException e) {
            System.out.println(e);
        }
        // организации в отдельной таблице, орг . как вн. клю

    }


    Connection connection; //= DriverManager.getConnection(url, user, passwd);
    // https://javarush.com/groups/posts/2172-jdbc-ili-s-chego-vsje-nachinaetsja


    boolean S_Reurnt(Worker worker) { //вот тут аргумент который вставляем да...
        // подготовленный statement с параметрами
        try {
            PreparedStatement Fedya = connection.prepareStatement("INSERT INTO Worker(name, coordinates_x, coordinates_y, creationDate, salary, position, status, organization); VALUES ();");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        // INSERT


    }

}

/*



    private static final Logger logger = LoggerFactory.getLogger(ServerApp.class);
    private static final int DEFAULT_PORT = 1821;

    public static void main(String[] args) {
        if (args.length == 0) {
            logger.error("Ошибка: Имя файла коллекции JSON должно передаваться как аргумент командной строки.");
            System.err.println("Использование: java -jar server.jar <имя_файла_коллекции>");
            System.exit(1);
        }

        что тут модификаторы public synchronized
        что тут тип результата
        что тут имя show
        что тут аргументы () либо collection
        что тут тело

public synchronized String show() {
    if (collection.isEmpty()) {
        return "Коллекция пуста.";
    }
    // Объекты в коллекции, передаваемой клиенту, отсортированы по местоположению (Worker.compareTo)
    return collection.stream()
            .sorted()
            .map(Worker::toString)
            .collect(Collectors.joining("\n"));
}

        <modifer>* Type id '(' (Type name ',')* ')' '{' body '}'  // метод

        <modifer>*  id '(' (Type name ',')* ')' '{' body '}' //конструтор
            id - имя класса*/

//package server;
//
//import java.io.IOException;
//import java.sql.*;
//import java.util.stream.Collectors;
//
//public class LESHA_1 {
//    public static final LESHA_1 INSTANCE = new LESHA_1();
//    private Connection connection;
//
//    private LESHA_1() {
//        try {
//            Class.forName("org.postgresql.Driver");
//            connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/studs", "s505045", "x0E4qloofOgALNWu");
//        } catch (SQLException e) {
//            System.out.println(e);
//        } catch (ClassNotFoundException e) {
//            throw new RuntimeException(e);
//        }
//
//        try {
//            Statement statement = connection.createStatement();
//            statement.executeUpdate("CREATE TABLE IF NOT EXISTS Organization(id SERIAL PRIMARY KEY, Organization_type Integer));");
//
//            statement.executeUpdate("CREATE TABLE IF NOT EXISTS Worker( id SERIAL PRIMARY KEY, name VARCHAR(100) NOT NULL, coordinates_x Integer, coordinates_y DOUBLE PRECISION, creationDate timestamp, salary Integer, position Integer, status Integer, organization INTEGER NOT NULL REFERENCES Organization(organization_id)); ");
//
//
//        } catch (SQLException e) {
//            System.out.println(e);
//        }
//        // организации в отдельной таблице, орг . как вн. клю
//
//    }
//
//
//    Connection connection; //= DriverManager.getConnection(url, user, passwd);
//    // https://javarush.com/groups/posts/2172-jdbc-ili-s-chego-vsje-nachinaetsja
//
//
//    /**
//     * вот тут аргумент который вставляем да...
//     * @return
//     */
//
//    boolean S_Return (Worker worker) {
//        // подготовленный statement с параметрами
//        try {
//            PreparedStatement Fedya = connection.prepareStatement("INSERT INTO Worker(name, coordinates_x, coordinates_y, creationDate, salary, position, status, organization); VALUES ();");
//        } catch (SQLException e) {
//            throw new RuntimeException(e);
//        }
//        // INSERT
//
//
//    }
//
//
//
///*
//
//
//
//    private static final Logger logger = LoggerFactory.getLogger(ServerApp.class);
//    private static final int DEFAULT_PORT = 1821;
//
//    public static void main(String[] args) {
//        if (args.length == 0) {
//            logger.error("Ошибка: Имя файла коллекции JSON должно передаваться как аргумент командной строки.");
//            System.err.println("Использование: java -jar server.jar <имя_файла_коллекции>");
//            System.exit(1);
//        }
//
//        что тут модификаторы public synchronized
//        что тут тип результата
//        что тут имя show
//        что тут аргументы () либо collection
//        что тут тело
//
//public synchronized String show() {
//    if (collection.isEmpty()) {
//        return "Коллекция пуста.";
//    }
//    // Объекты в коллекции, передаваемой клиенту, отсортированы по местоположению (Worker.compareTo)
//    return collection.stream()
//            .sorted()
//            .map(Worker::toString)
//            .collect(Collectors.joining("\n"));
//}
//
//        <modifer>* Type id '(' (Type name ',')* ')' '{' body '}'  // метод
//
//        <modifer>*  id '(' (Type name ',')* ')' '{' body '}' //конструтор
//            id - имя класса*/