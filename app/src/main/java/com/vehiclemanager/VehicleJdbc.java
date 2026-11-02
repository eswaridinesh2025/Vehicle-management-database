package com.vehiclemanager;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class VehicleJdbc {
    private volatile DatabaseConfig databaseConfig;

    static void loadDriver() throws ClassNotFoundException {
        Class.forName("oracle.jdbc.OracleDriver");
    }

    void configure(String url, String username, String password) {
        databaseConfig = new DatabaseConfig(url, username, password);
    }

    boolean isConnected() {
        return databaseConfig != null;
    }

    Connection openConnection() throws SQLException {
        DatabaseConfig config = databaseConfig;
        if (config == null) {
            throw new SQLException("Database not connected. Use the Connect Oracle form.");
        }
        return config.open();
    }

    Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!isConnected()) {
            result.put("connected", false);
            result.put("demo", true);
            result.put("message", "Showing sample records from sql/02_insert_data.sql.");
            return result;
        }
        try (Connection connection = openConnection()) {
            verifyConnection(connection);
            result.put("connected", true);
            result.put("message", "Oracle database connected.");
        } catch (SQLException ex) {
            result.put("connected", false);
            result.put("message", databaseMessage(ex));
        }
        return result;
    }

    void connect(String url, String username, String password) throws SQLException {
        DatabaseConfig candidate = new DatabaseConfig(url, username, password);
        try (Connection connection = candidate.open()) {
            verifyConnection(connection);
            databaseConfig = candidate;
        }
    }

    Map<String, Object> summary() throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        try (Connection connection = openConnection()) {
            result.put("vehicles", scalar(connection, "SELECT COUNT(*) FROM VEHICLE"));
            result.put("active", scalar(connection, "SELECT COUNT(*) FROM VEHICLE WHERE UPPER(status) = 'ACTIVE'"));
            result.put("maintenance", scalar(connection, "SELECT COUNT(*) FROM MAINTENANCE"));
            result.put("trips", scalar(connection, "SELECT COUNT(*) FROM TRIP"));
            result.put("accidents", scalar(connection, "SELECT COUNT(*) FROM ACCIDENT"));
            result.put("fuelEntries", scalar(connection, "SELECT COUNT(*) FROM FUEL_RECORD"));
            result.put("types", query(connection,
                    "SELECT vehicle_type AS name, COUNT(*) AS count FROM VEHICLE GROUP BY vehicle_type ORDER BY vehicle_type"));
        }
        return result;
    }

    List<Map<String, Object>> tableRows(String tableName, Long vehicleId) throws SQLException {
        try (Connection connection = openConnection()) {
            return vehicleId == null
                    ? query(connection, "SELECT * FROM " + tableName)
                    : query(connection, "SELECT * FROM " + tableName + " WHERE vehicle_id = ?", vehicleId);
        }
    }

    List<Map<String, Object>> vehicles(String search, String type, String status) throws SQLException {
        String sql = "SELECT v.vehicle_id, v.registration_no, v.vehicle_type, v.brand, v.model, " +
                "v.manufacturing_year, v.color, v.status, " +
                "(SELECT COUNT(*) FROM MAINTENANCE m WHERE m.vehicle_id = v.vehicle_id) AS maintenance_count, " +
                "(SELECT COUNT(*) FROM ACCIDENT a WHERE a.vehicle_id = v.vehicle_id) AS accident_count " +
                "FROM VEHICLE v WHERE (? IS NULL OR UPPER(v.vehicle_type) = UPPER(?)) " +
                "AND (? IS NULL OR UPPER(v.status) = UPPER(?)) " +
                "AND (? IS NULL OR UPPER(v.registration_no || ' ' || v.brand || ' ' || v.model) LIKE UPPER(?)) " +
                "ORDER BY v.vehicle_id";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, emptyToNull(type));
            statement.setString(2, type);
            statement.setString(3, emptyToNull(status));
            statement.setString(4, status);
            statement.setString(5, emptyToNull(search));
            statement.setString(6, "%" + search + "%");
            return rows(statement);
        }
    }

    Map<String, Object> vehicleDetails(long vehicleId) throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        try (Connection connection = openConnection()) {
            List<Map<String, Object>> vehicleRows = query(connection,
                    "SELECT * FROM VEHICLE WHERE vehicle_id = ?", vehicleId);
            if (vehicleRows.isEmpty()) {
                throw new IllegalArgumentException("Vehicle " + vehicleId + " was not found.");
            }
            result.put("vehicle", vehicleRows.get(0));
            result.put("ownership", query(connection,
                    "SELECT vo.customer_id, c.name AS owner_name, c.phone, vo.start_date, vo.end_date " +
                    "FROM VEHICLE_OWNERSHIP vo LEFT JOIN CUSTOMER4 c ON c.customer_id = vo.customer_id " +
                    "WHERE vo.vehicle_id = ? ORDER BY vo.start_date DESC", vehicleId));
            result.put("insurance", query(connection,
                    "SELECT insurance_id, policy_no, insurance_company, start_date, expiry_date, premium " +
                    "FROM INSURANCE WHERE vehicle_id = ? ORDER BY expiry_date", vehicleId));
            result.put("warranty", query(connection,
                    "SELECT warranty_id, warranty_provider, warranty_type, start_date, expiry_date, " +
                    "coverage_details, warranty_status FROM WARRANTY WHERE vehicle_id = ? ORDER BY expiry_date", vehicleId));
            result.put("maintenance", query(connection,
                    "SELECT m.maintenance_id, m.service_date, m.service_type, m.description, m.cost, " +
                    "m.next_service_date, sc.center_name, p.payment_id, p.payment_date, p.amount, " +
                    "p.payment_method, p.payment_status FROM MAINTENANCE m " +
                    "LEFT JOIN SERVICE_CENTER sc ON sc.service_center_id = m.service_center_id " +
                    "LEFT JOIN PAYMENT p ON p.maintenance_id = m.maintenance_id " +
                    "WHERE m.vehicle_id = ? ORDER BY m.service_date DESC", vehicleId));
            result.put("accidents", query(connection,
                    "SELECT a.accident_id, a.accident_date, a.location, a.description, a.damage_cost, " +
                    "d.driver_id, d.name AS driver_name FROM ACCIDENT a " +
                    "LEFT JOIN DRIVER d ON d.driver_id = a.driver_id " +
                    "WHERE a.vehicle_id = ? ORDER BY a.accident_date DESC", vehicleId));
            result.put("trips", query(connection,
                    "SELECT t.trip_id, t.start_location, t.destination, t.start_time, t.end_time, " +
                    "t.distance, d.name AS driver_name FROM TRIP t " +
                    "LEFT JOIN DRIVER d ON d.driver_id = t.driver_id " +
                    "WHERE t.vehicle_id = ? ORDER BY t.start_time DESC", vehicleId));
            result.put("fuel", query(connection,
                    "SELECT fuel_id, fuel_date, fuel_type, quantity, amount, odometer_reading " +
                    "FROM FUEL_RECORD WHERE vehicle_id = ? ORDER BY fuel_date DESC", vehicleId));
            result.put("subtype", subtype(connection, vehicleId,
                    String.valueOf(vehicleRows.get(0).get("VEHICLE_TYPE"))));
        }
        return result;
    }

    Map<String, Object> vehicleProcedureReport(long vehicleId) throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("vehicleId", vehicleId);
        List<Map<String, Object>> reports = new ArrayList<>();
        try (Connection connection = openConnection()) {
            reports.add(procedureReport(connection, "GET_VEHICLE_REPORT", vehicleId));
            reports.add(procedureReport(connection, "GET_MAINTENANCE_PAYMENT", vehicleId));
            reports.add(procedureReport(connection, "GET_TRIP_FUEL_REPORT", vehicleId));
            reports.add(procedureReport(connection, "GET_VEHICLE_TYPE_REPORT", vehicleId));
        }
        result.put("reports", reports);
        return result;
    }

    String databaseMessage(SQLException ex) {
        String message = ex.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Oracle connection failed. Check the connection settings and project schema.";
        }
        if (message.contains("CUSTOMER4") || message.contains("ORA-00942")) {
            return "Project tables were not found. Create/load the project schema, then reconnect.";
        }
        return message.replaceAll("(?i)(password|passwd)=([^ ;]+)", "$1=***");
    }

    private Map<String, Object> procedureReport(Connection connection, String procedureName, long vehicleId)
            throws SQLException {
        if (!Arrays.asList("GET_VEHICLE_REPORT", "GET_MAINTENANCE_PAYMENT", "GET_TRIP_FUEL_REPORT",
                "GET_VEHICLE_TYPE_REPORT").contains(procedureName)) {
            throw new IllegalArgumentException("Unsupported report procedure.");
        }
        try (CallableStatement call = connection.prepareCall(
                "BEGIN DBMS_OUTPUT.ENABLE(NULL); " + procedureName + "(?); END;")) {
            call.setLong(1, vehicleId);
            call.execute();
        }

        List<String> lines = new ArrayList<>();
        try (CallableStatement output = connection.prepareCall("BEGIN DBMS_OUTPUT.GET_LINE(?, ?); END;")) {
            output.registerOutParameter(1, java.sql.Types.VARCHAR);
            output.registerOutParameter(2, java.sql.Types.INTEGER);
            while (true) {
                output.execute();
                if (output.getInt(2) != 0) {
                    break;
                }
                lines.add(output.getString(1));
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("procedure", procedureName);
        report.put("lines", lines);
        return report;
    }

    private List<Map<String, Object>> subtype(Connection connection, long vehicleId, String type)
            throws SQLException {
        String table;
        if ("BIKE".equalsIgnoreCase(type)) {
            table = "BIKE";
        } else if ("CAR".equalsIgnoreCase(type)) {
            table = "CAR";
        } else if ("TRUCK".equalsIgnoreCase(type)) {
            table = "TRUCK";
        } else if ("BUS".equalsIgnoreCase(type)) {
            table = "BUS";
        } else {
            return new ArrayList<>();
        }
        return query(connection, "SELECT * FROM " + table + " WHERE vehicle_id = ?", vehicleId);
    }

    private long scalar(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getLong(1) : 0L;
        }
    }

    private List<Map<String, Object>> query(Connection connection, String sql, Object... parameters)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            return rows(statement);
        }
    }

    private List<Map<String, Object>> rows(PreparedStatement statement) throws SQLException {
        List<Map<String, Object>> resultRows = new ArrayList<>();
        try (ResultSet result = statement.executeQuery()) {
            ResultSetMetaData metadata = result.getMetaData();
            while (result.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int column = 1; column <= metadata.getColumnCount(); column++) {
                    Object value = result.getObject(column);
                    if (value instanceof java.sql.Timestamp || value instanceof java.sql.Date) {
                        value = value.toString();
                    }
                    row.put(metadata.getColumnLabel(column), value);
                }
                resultRows.add(row);
            }
        }
        return resultRows;
    }

    private String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private void verifyConnection(Connection connection) throws SQLException {
        if (!connection.isValid(2)) {
            throw new SQLException("Oracle connection is not valid.");
        }
    }

    private static final class DatabaseConfig {
        private final String url;
        private final String username;
        private final String password;

        private DatabaseConfig(String url, String username, String password) {
            this.url = url;
            this.username = username;
            this.password = password;
        }

        private Connection open() throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }
    }
}