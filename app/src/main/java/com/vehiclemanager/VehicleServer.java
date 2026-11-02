package com.vehiclemanager;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

public final class VehicleServer {
    private static final VehicleJdbc database = new VehicleJdbc();
    private static final Set<String> PROJECT_TABLES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "CUSTOMER4", "DRIVER", "VEHICLE", "SERVICE_CENTER", "BIKE", "CAR", "TRUCK", "BUS",
            "VEHICLE_OWNERSHIP", "MAINTENANCE", "PAYMENT", "TRIP", "WARRANTY", "ACCIDENT",
            "INSURANCE", "FUEL_RECORD")));

    private VehicleServer() { }

    public static void main(String[] args) throws Exception {
        VehicleJdbc.loadDriver();
        int port = Integer.parseInt(System.getenv().getOrDefault("APP_PORT", "8080"));
        String dbUrl = System.getenv("DB_URL");
        String dbUser = System.getenv("DB_USER");
        String dbPassword = System.getenv("DB_PASSWORD");
        if (dbUrl != null && dbUser != null && dbPassword != null) {
            database.configure(dbUrl, dbUser, dbPassword);
        }

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/", VehicleServer::handleApi);
        server.createContext("/", VehicleServer::handleStatic);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Vehicle Management System running at http://localhost:" + port);
    }

    private static void handleApi(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        try {
            if ("GET".equals(method) && "/api/status".equals(path)) {
                sendJson(exchange, 200, status());
            } else if ("POST".equals(method) && "/api/connect".equals(path)) {
                connect(exchange);
            } else if ("GET".equals(method) && "/api/summary".equals(path)) {
                sendJson(exchange, 200, summary());
            } else if ("GET".equals(method) && "/api/tables".equals(path)) {
                sendJson(exchange, 200, tableNames());
            } else if ("GET".equals(method) && path.matches("/api/tables/[A-Za-z0-9_]+")) {
                sendJson(exchange, 200, tableData(path.substring(path.lastIndexOf('/') + 1), exchange));
            } else if ("GET".equals(method) && "/api/vehicles".equals(path)) {
                sendJson(exchange, 200, vehicles(exchange));
            } else if ("GET".equals(method) && path.matches("/api/vehicles/\\d+")) {
                sendJson(exchange, 200, vehicleDetails(Long.parseLong(path.substring(path.lastIndexOf('/') + 1))));
            } else if ("GET".equals(method) && path.matches("/api/vehicles/\\d+/report")) {
                long vehicleId = Long.parseLong(path.split("/")[3]);
                sendJson(exchange, 200, vehicleProcedureReport(vehicleId));
            } else {
                sendJson(exchange, 404, error("API endpoint not found."));
            }
        } catch (SQLException ex) {
            sendJson(exchange, 503, error(database.databaseMessage(ex)));
        } catch (IllegalArgumentException ex) {
            sendJson(exchange, 400, error(ex.getMessage()));
        } catch (IOException | RuntimeException ex) {
            sendJson(exchange, 500, error("The request could not be completed."));
            ex.printStackTrace(System.err);
        } finally {
            exchange.close();
        }
    }

    private static Map<String, Object> status() {
        return database.status();
    }

    private static void connect(HttpExchange exchange) throws IOException, SQLException {
        Map<String, String> form = parseForm(readBody(exchange));
        String host = valueOr(form.get("host"), "localhost");
        String port = valueOr(form.get("port"), "1521");
        String service = valueOr(form.get("service"), "XEPDB1");
        String user = valueOr(form.get("username"), "");
        String password = valueOr(form.get("password"), "");
        if (user.trim().isEmpty() || password.isEmpty()) {
            throw new IllegalArgumentException("Enter the Oracle username and password.");
        }
        int portNumber;
        try {
            portNumber = Integer.parseInt(port);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Oracle port must be a number.");
        }
        if (portNumber < 1 || portNumber > 65535) {
            throw new IllegalArgumentException("Oracle port must be between 1 and 65535.");
        }

        database.connect("jdbc:oracle:thin:@//" + host + ":" + portNumber + "/" + service,
            user.trim(), password);
        sendJson(exchange, 200, status());
    }

    private static Map<String, Object> summary() throws SQLException {
        if (!database.isConnected()) {
            return SampleData.summary();
        }
        return database.summary();
    }

    private static List<Map<String, Object>> tableNames() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (String name : Arrays.asList(
                "CUSTOMER4", "DRIVER", "VEHICLE", "SERVICE_CENTER", "BIKE", "CAR", "TRUCK", "BUS",
                "VEHICLE_OWNERSHIP", "MAINTENANCE", "PAYMENT", "TRIP", "WARRANTY", "ACCIDENT",
                "INSURANCE", "FUEL_RECORD")) {
            result.add(row("name", name));
        }
        return result;
    }

    private static Map<String, Object> tableData(String requestedName, HttpExchange exchange) throws SQLException, IOException {
        String tableName = requestedName.toUpperCase();
        if (!PROJECT_TABLES.contains(tableName)) {
            throw new IllegalArgumentException("That table is not part of this project.");
        }
        Map<String, String> queryParams = parseForm(exchange.getRequestURI().getRawQuery());
        String vehicleIdValue = valueOr(queryParams.get("vehicle_id"), "").trim();
        Long vehicleId = null;
        if (!vehicleIdValue.isEmpty()) {
            if (!vehicleIdValue.matches("\\d+")) {
                throw new IllegalArgumentException("Vehicle ID must be a positive number.");
            }
            if (!hasVehicleForeignKey(tableName)) {
                throw new IllegalArgumentException(tableName + " does not reference VEHICLE directly.");
            }
            vehicleId = Long.valueOf(vehicleIdValue);
        }
        List<Map<String, Object>> records;
        boolean demo = !database.isConnected();
        if (demo) {
            records = SampleData.tableRows(tableName);
            if (vehicleId != null) {
                List<Map<String, Object>> filtered = new ArrayList<>();
                for (Map<String, Object> record : records) {
                    Object recordVehicleId = record.get("VEHICLE_ID");
                    if (recordVehicleId instanceof Number && ((Number) recordVehicleId).longValue() == vehicleId) {
                        filtered.add(record);
                    }
                }
                records = filtered;
            }
        } else {
            records = database.tableRows(tableName, vehicleId);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("table", tableName);
        result.put("demo", demo);
        if (vehicleId != null) {
            result.put("vehicleId", vehicleId);
        }
        result.put("count", records.size());
        result.put("rows", records);
        return result;
    }

    private static boolean hasVehicleForeignKey(String tableName) {
        return Arrays.asList(
                "VEHICLE", "BIKE", "CAR", "TRUCK", "BUS", "VEHICLE_OWNERSHIP",
                "MAINTENANCE", "PAYMENT", "TRIP", "WARRANTY", "ACCIDENT", "INSURANCE", "FUEL_RECORD"
        ).contains(tableName);
    }

    private static List<Map<String, Object>> vehicles(HttpExchange exchange) throws SQLException, IOException {
        Map<String, String> queryParams = parseForm(exchange.getRequestURI().getRawQuery());
        String search = valueOr(queryParams.get("search"), "").trim();
        String type = valueOr(queryParams.get("type"), "").trim();
        String status = valueOr(queryParams.get("status"), "").trim();
        if (!database.isConnected()) {
            return SampleData.vehicles(search, type, status);
        }
        return database.vehicles(search, type, status);
    }

    private static Map<String, Object> vehicleDetails(long vehicleId) throws SQLException {
        if (!database.isConnected()) {
            return SampleData.vehicleDetails(vehicleId);
        }
        return database.vehicleDetails(vehicleId);
    }

    private static Map<String, Object> vehicleProcedureReport(long vehicleId) throws SQLException {
        if (!database.isConnected()) {
            throw new SQLException("Connect the Oracle project schema to run its PL/SQL procedures.");
        }
        return database.vehicleProcedureReport(vehicleId);
    }

    private static void handleStatic(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/".equals(path)) {
            path = "/index.html";
        } else if ("/customers.html".equals(path)) {
            path = "/customer4.html";
        }
        if (path.contains("..") || path.contains("\\")) {
            sendText(exchange, 400, "Invalid path.", "text/plain; charset=utf-8");
            exchange.close();
            return;
        }
        String resource = "web" + path;
        try (InputStream input = VehicleServer.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                sendText(exchange, 404, "Not found.", "text/plain; charset=utf-8");
            } else {
                String contentType = path.endsWith(".css") ? "text/css; charset=utf-8" :
                        path.endsWith(".js") ? "application/javascript; charset=utf-8" : "text/html; charset=utf-8";
                byte[] content = readAll(input);
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
                exchange.sendResponseHeaders(200, content.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(content);
                }
            }
        } finally {
            exchange.close();
        }
    }

    private static void sendJson(HttpExchange exchange, int status, Object value) throws IOException {
        sendText(exchange, status, Json.encode(value), "application/json; charset=utf-8");
    }

    private static void sendText(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] content = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, content.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(content);
        }
    }

    private static Map<String, String> parseForm(String encoded) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        if (encoded == null || encoded.isEmpty()) {
            return result;
        }
        for (String pair : encoded.split("&")) {
            String[] pieces = pair.split("=", 2);
            String key = URLDecoder.decode(pieces[0], "UTF-8");
            String value = pieces.length > 1 ? URLDecoder.decode(pieces[1], "UTF-8") : "";
            result.put(key, value);
        }
        return result;
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(readAll(exchange.getRequestBody()), StandardCharsets.UTF_8);
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int length;
        while ((length = input.read(buffer)) != -1) {
            output.write(buffer, 0, length);
        }
        return output.toByteArray();
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("error", message);
        return result;
    }

    private static String valueOr(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private static final class SampleData {
        private static final Object[][] VEHICLES = {
            {1, "TN01AB1234", "Bike", "Royal Enfield", "Bear 650", 2022, "Black", "Active"},
            {2, "TN02CD5678", "Bike", "Yamaha", "R3", 2024, "Blue", "Active"},
            {3, "TN03EF9012", "Bike", "Honda", "GoldWing", 2020, "White", "Under Maintenance"},
            {4, "TN04GH3456", "Bike", "TVS", "Apache RTR 160", 2018, "Red", "Active"},
            {5, "TN05IJ7890", "Car", "Toyota", "Innova Crysta", 2019, "White", "Active"},
            {6, "TN06KL2345", "Car", "Hyundai", "Creta", 2023, "Grey", "Active"},
            {7, "TN07MN6789", "Car", "Tata", "Safari", 2021, "Black", "Accident Repair"},
            {8, "TN08OP0123", "Car", "Kia", "Carnival", 2017, "Silver", "Active"},
            {9, "TN09QR4567", "Truck", "Tata", "407", 2016, "Blue", "Active"},
            {10, "TN10ST8901", "Truck", "Ashok Leyland", "Dost", 2021, "White", "Under Maintenance"},
            {11, "TN11UV2345", "Truck", "Eicher", "Pro 3015", 2019, "Red", "Active"},
            {12, "TN12WX6789", "Truck", "BharatBenz", "3123R", 2023, "White", "Active"},
            {13, "TN13YZ1234", "Bus", "Ashok Leyland", "Viking", 2018, "Red", "Active"},
            {14, "TN14AB5678", "Bus", "Tata", "Starbus", 2022, "White", "Active"},
            {15, "TN15CD9012", "Bus", "Volvo", "9400", 2015, "Blue", "Under Maintenance"},
            {16, "TN16EF3456", "Bus", "Eicher", "Skyline Pro", 2020, "Yellow", "Inactive"},
            {17, "TN17GH7890", "Bike", "KTM", "Duke 390", 2023, "Orange", "Active"},
            {18, "TN18IJ2345", "Car", "Honda", "City", 2020, "White", "Active"},
            {19, "TN19KL6789", "Truck", "Ashok Leyland", "Partner", 2022, "Grey", "Active"},
            {20, "TN20MN0123", "Bus", "Eicher", "Skyline Pro E", 2021, "Yellow", "Active"}
        };

        private SampleData() { }

        private static Map<String, Object> summary() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("demo", true);
            result.put("vehicles", 20);
            result.put("active", 15);
            result.put("maintenance", 6);
            result.put("trips", 5);
            result.put("accidents", 5);
            result.put("fuelEntries", 20);
            result.put("types", list(
                    row("NAME", "Bike", "COUNT", 5),
                    row("NAME", "Bus", "COUNT", 5),
                    row("NAME", "Car", "COUNT", 5),
                    row("NAME", "Truck", "COUNT", 5)));
            return result;
        }

        private static List<Map<String, Object>> tableRows(String tableName) {
            List<Map<String, Object>> result = new ArrayList<>();
            if ("CUSTOMER4".equals(tableName)) {
                result.add(row("CUSTOMER_ID", 1, "NAME", "Arun Kumar", "PHONE", "9876543210", "EMAIL", "arun@gmail.com", "ADDRESS", "Chennai", "LICENSE_NO", "TN01L123456"));
                result.add(row("CUSTOMER_ID", 2, "NAME", "Priya Sharma", "PHONE", "9876543211", "EMAIL", "priya@gmail.com", "ADDRESS", "Coimbatore", "LICENSE_NO", "TN02L234567"));
                result.add(row("CUSTOMER_ID", 3, "NAME", "Rahul Raj", "PHONE", "9876543212", "EMAIL", "rahul@gmail.com", "ADDRESS", "Madurai", "LICENSE_NO", "TN03L345678"));
                result.add(row("CUSTOMER_ID", 4, "NAME", "Sneha Krishnan", "PHONE", "9876543213", "EMAIL", "sneha@gmail.com", "ADDRESS", "Trichy", "LICENSE_NO", "TN04L456789"));
                result.add(row("CUSTOMER_ID", 5, "NAME", "Vikram Singh", "PHONE", "9876543214", "EMAIL", "vikram@gmail.com", "ADDRESS", "Salem", "LICENSE_NO", "TN05L567890"));
            } else if ("DRIVER".equals(tableName)) {
                result.add(row("DRIVER_ID", 1, "NAME", "Karthikeyan Sudhakar", "PHONE", "8751786509", "LICENSE_NO", "DL01BNC1564", "LICENSE_EXPIRY", "2029-05-10", "ADDRESS", "Chennai"));
                result.add(row("DRIVER_ID", 2, "NAME", "Gauthaum Vishwanath", "PHONE", "8778725121", "LICENSE_NO", "DL02ABN1059", "LICENSE_EXPIRY", "2028-11-09", "ADDRESS", "Madurai"));
                result.add(row("DRIVER_ID", 3, "NAME", "Shyam Sarath", "PHONE", "9486456584", "LICENSE_NO", "DL03ABJ1324", "LICENSE_EXPIRY", "2030-01-08", "ADDRESS", "Dindugal"));
                result.add(row("DRIVER_ID", 4, "NAME", "Aaryan Ajit", "PHONE", "9606300418", "LICENSE_NO", "DL03ABL1258", "LICENSE_EXPIRY", "2027-03-09", "ADDRESS", "Pune"));
                result.add(row("DRIVER_ID", 5, "NAME", "Rohit Sowmian", "PHONE", "9597007896", "LICENSE_NO", "DL03ABK8790", "LICENSE_EXPIRY", "2028-12-08", "ADDRESS", "Madurai"));
            } else if ("VEHICLE".equals(tableName)) {
                for (Object[] vehicle : VEHICLES) {
                    result.add(row("VEHICLE_ID", vehicle[0], "REGISTRATION_NO", vehicle[1],
                            "VEHICLE_TYPE", vehicle[2], "BRAND", vehicle[3], "MODEL", vehicle[4],
                            "MANUFACTURING_YEAR", vehicle[5], "COLOR", vehicle[6], "STATUS", vehicle[7]));
                }
            } else if ("SERVICE_CENTER".equals(tableName)) {
                result.add(row("SERVICE_CENTER_ID", 1, "CENTER_NAME", "Chennai Auto Care", "LOCATION", "Chennai", "PHONE", "04440001001"));
                result.add(row("SERVICE_CENTER_ID", 2, "CENTER_NAME", "Madurai Vehicle Works", "LOCATION", "Madurai", "PHONE", "04524000102"));
                result.add(row("SERVICE_CENTER_ID", 3, "CENTER_NAME", "Dindigul Service Hub", "LOCATION", "Dindigul", "PHONE", "04514000103"));
                result.add(row("SERVICE_CENTER_ID", 4, "CENTER_NAME", "Pune Fleet Services", "LOCATION", "Pune", "PHONE", "02040001004"));
                result.add(row("SERVICE_CENTER_ID", 5, "CENTER_NAME", "Coimbatore Motors", "LOCATION", "Coimbatore", "PHONE", "04224000105"));
                result.add(row("SERVICE_CENTER_ID", 6, "CENTER_NAME", "Salem Truck Care", "LOCATION", "Salem", "PHONE", "04274000106"));
            } else if ("BIKE".equals(tableName) || "CAR".equals(tableName)
                    || "TRUCK".equals(tableName) || "BUS".equals(tableName)) {
                for (Object[] vehicle : VEHICLES) {
                    if (tableName.equalsIgnoreCase(String.valueOf(vehicle[2]))) {
                        result.addAll(subtype(((Number) vehicle[0]).longValue(), String.valueOf(vehicle[2])));
                    }
                }
            } else if ("VEHICLE_OWNERSHIP".equals(tableName)) {
                for (Object[] vehicle : VEHICLES) {
                    long id = ((Number) vehicle[0]).longValue();
                    for (Map<String, Object> record : records(id, ownershipRows())) {
                        result.add(row("CUSTOMER_ID", record.get("CUSTOMER_ID"), "VEHICLE_ID", id,
                                "START_DATE", record.get("START_DATE"), "END_DATE", record.get("END_DATE")));
                    }
                }
            } else if ("MAINTENANCE".equals(tableName)) {
                int[] centerIds = {1, 3, 2, 4, 6, 5};
                for (int index = 0; index < maintenanceRows().length; index++) {
                    Object[] source = maintenanceRows()[index];
                    long id = ((Number) source[0]).longValue();
                    Map<String, Object> record = records(id, new Object[][] {source}).get(0);
                    result.add(row("MAINTENANCE_ID", record.get("MAINTENANCE_ID"), "VEHICLE_ID", id,
                            "SERVICE_CENTER_ID", centerIds[index], "SERVICE_DATE", record.get("SERVICE_DATE"),
                            "SERVICE_TYPE", record.get("SERVICE_TYPE"), "DESCRIPTION", record.get("DESCRIPTION"),
                            "COST", record.get("COST"), "NEXT_SERVICE_DATE", record.get("NEXT_SERVICE_DATE")));
                }
            } else if ("PAYMENT".equals(tableName)) {
                result.add(row("PAYMENT_ID", 1, "VEHICLE_ID", 1, "MAINTENANCE_ID", 1, "PAYMENT_DATE", "2026-01-15", "AMOUNT", 3500, "PAYMENT_METHOD", "UPI", "PAYMENT_STATUS", "Paid"));
                result.add(row("PAYMENT_ID", 2, "VEHICLE_ID", 3, "MAINTENANCE_ID", 2, "PAYMENT_DATE", "2026-02-10", "AMOUNT", 5200, "PAYMENT_METHOD", "Card", "PAYMENT_STATUS", "Paid"));
                result.add(row("PAYMENT_ID", 3, "VEHICLE_ID", 7, "MAINTENANCE_ID", 3, "PAYMENT_DATE", "2026-03-05", "AMOUNT", 18500, "PAYMENT_METHOD", "UPI", "PAYMENT_STATUS", "Paid"));
                result.add(row("PAYMENT_ID", 4, "VEHICLE_ID", 10, "MAINTENANCE_ID", 4, "PAYMENT_DATE", "2026-03-20", "AMOUNT", 7500, "PAYMENT_METHOD", "Cash", "PAYMENT_STATUS", "Paid"));
                result.add(row("PAYMENT_ID", 5, "VEHICLE_ID", 15, "MAINTENANCE_ID", 5, "PAYMENT_DATE", "2026-04-12", "AMOUNT", 12500, "PAYMENT_METHOD", "Bank Transfer", "PAYMENT_STATUS", "Paid"));
                result.add(row("PAYMENT_ID", 6, "VEHICLE_ID", 5, "MAINTENANCE_ID", 6, "PAYMENT_DATE", "2026-04-25", "AMOUNT", 6000, "PAYMENT_METHOD", "Card", "PAYMENT_STATUS", "Paid"));
            } else if ("TRIP".equals(tableName)) {
                int[] driverIds = {1, 2, 3, 4, 5};
                Object[][] sourceRows = tripRows();
                for (int index = 0; index < sourceRows.length; index++) {
                    Object[] source = sourceRows[index];
                    long vehicleId = ((Number) source[0]).longValue();
                    Map<String, Object> record = records(vehicleId, new Object[][] {source}).get(0);
                    result.add(row("TRIP_ID", record.get("TRIP_ID"), "VEHICLE_ID", vehicleId,
                            "DRIVER_ID", driverIds[index], "START_LOCATION", record.get("START_LOCATION"),
                            "DESTINATION", record.get("DESTINATION"), "START_TIME", record.get("START_TIME"),
                            "END_TIME", record.get("END_TIME"), "DISTANCE", record.get("DISTANCE")));
                }
            } else if ("WARRANTY".equals(tableName)) {
                appendRecords(result, warrantyRows(), new String[0]);
            } else if ("ACCIDENT".equals(tableName)) {
                appendRecords(result, accidentRows(), new String[] {"DRIVER_NAME"});
            } else if ("INSURANCE".equals(tableName)) {
                appendRecords(result, insuranceRows(), new String[0]);
            } else if ("FUEL_RECORD".equals(tableName)) {
                appendRecords(result, fuelRows(), new String[0]);
            }
            return result;
        }

        private static void appendRecords(List<Map<String, Object>> target, Object[][] source, String[] excluded) {
            for (Object[] entry : source) {
                long vehicleId = ((Number) entry[0]).longValue();
                Map<String, Object> record = records(vehicleId, new Object[][] {entry}).get(0);
                Map<String, Object> tableRow = new LinkedHashMap<>();
                tableRow.put("VEHICLE_ID", vehicleId);
                for (Map.Entry<String, Object> field : record.entrySet()) {
                    if (!Arrays.asList(excluded).contains(field.getKey())) {
                        tableRow.put(field.getKey(), field.getValue());
                    }
                }
                target.add(tableRow);
            }
        }

        private static List<Map<String, Object>> vehicles(String search, String type, String status) {
            List<Map<String, Object>> result = new ArrayList<>();
            String searchText = search.toUpperCase();
            for (Object[] vehicle : VEHICLES) {
                String vehicleType = (String) vehicle[2];
                String vehicleStatus = (String) vehicle[7];
                String searchable = (vehicle[1] + " " + vehicle[3] + " " + vehicle[4]).toUpperCase();
                if (!type.isEmpty() && !type.equalsIgnoreCase(vehicleType)) continue;
                if (!status.isEmpty() && !status.equalsIgnoreCase(vehicleStatus)) continue;
                if (!searchText.isEmpty() && !searchable.contains(searchText)) continue;
                int vehicleId = (Integer) vehicle[0];
                int maintenanceCount = vehicleId == 1 || vehicleId == 3 || vehicleId == 5 ||
                        vehicleId == 7 || vehicleId == 10 || vehicleId == 15 ? 1 : 0;
                int accidentCount = vehicleId == 1 || vehicleId == 4 || vehicleId == 7 ||
                        vehicleId == 10 || vehicleId == 15 ? 1 : 0;
                result.add(row(
                        "VEHICLE_ID", vehicle[0], "REGISTRATION_NO", vehicle[1],
                        "VEHICLE_TYPE", vehicle[2], "BRAND", vehicle[3], "MODEL", vehicle[4],
                        "MANUFACTURING_YEAR", vehicle[5], "COLOR", vehicle[6], "STATUS", vehicle[7],
                        "MAINTENANCE_COUNT", maintenanceCount, "ACCIDENT_COUNT", accidentCount));
            }
            return result;
        }

        private static Map<String, Object> vehicleDetails(long vehicleId) {
            Object[] vehicle = null;
            for (Object[] candidate : VEHICLES) {
                if (((Integer) candidate[0]).longValue() == vehicleId) {
                    vehicle = candidate;
                    break;
                }
            }
            if (vehicle == null) {
                throw new IllegalArgumentException("Sample vehicle " + vehicleId + " was not found.");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("demo", true);
            result.put("vehicle", row(
                    "VEHICLE_ID", vehicle[0], "REGISTRATION_NO", vehicle[1], "VEHICLE_TYPE", vehicle[2],
                    "BRAND", vehicle[3], "MODEL", vehicle[4], "MANUFACTURING_YEAR", vehicle[5],
                    "COLOR", vehicle[6], "STATUS", vehicle[7]));
                result.put("ownership", records(vehicleId, ownershipRows()));
                result.put("insurance", records(vehicleId, insuranceRows()));
                result.put("warranty", records(vehicleId, warrantyRows()));
                result.put("maintenance", records(vehicleId, maintenanceRows()));
                result.put("accidents", records(vehicleId, accidentRows()));
                result.put("trips", records(vehicleId, tripRows()));
                result.put("fuel", records(vehicleId, fuelRows()));
            result.put("subtype", subtype(vehicleId, String.valueOf(vehicle[2])));
            return result;
        }

        private static List<Map<String, Object>> records(long vehicleId, Object[][] source) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object[] entry : source) {
                if (((Number) entry[0]).longValue() != vehicleId) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                for (int index = 1; index + 1 < entry.length; index += 2) {
                    item.put(String.valueOf(entry[index]), entry[index + 1]);
                }
                result.add(item);
            }
            return result;
        }

        private static Object[][] ownershipRows() {
            return new Object[][] {
                {1, "CUSTOMER_ID", 1, "OWNER_NAME", "Arun Kumar", "PHONE", "9876543210", "START_DATE", "2022-06-15", "END_DATE", "2028-06-15"},
                {2, "CUSTOMER_ID", 2, "OWNER_NAME", "Priya Sharma", "PHONE", "9876543211", "START_DATE", "2023-03-20", "END_DATE", "2029-03-20"},
                {3, "CUSTOMER_ID", 3, "OWNER_NAME", "Rahul Raj", "PHONE", "9876543212", "START_DATE", "2021-08-10", "END_DATE", "2028-08-10"},
                {4, "CUSTOMER_ID", 4, "OWNER_NAME", "Sneha Krishnan", "PHONE", "9876543213", "START_DATE", "2020-11-05", "END_DATE", "2029-11-05"},
                {5, "CUSTOMER_ID", 5, "OWNER_NAME", "Vikram Singh", "PHONE", "9876543214", "START_DATE", "2019-07-12", "END_DATE", "2028-07-12"},
                {6, "CUSTOMER_ID", 1, "OWNER_NAME", "Arun Kumar", "PHONE", "9876543210", "START_DATE", "2024-02-18", "END_DATE", "2029-02-18"},
                {7, "CUSTOMER_ID", 2, "OWNER_NAME", "Priya Sharma", "PHONE", "9876543211", "START_DATE", "2023-09-25", "END_DATE", "2028-09-25"},
                {8, "CUSTOMER_ID", 3, "OWNER_NAME", "Rahul Raj", "PHONE", "9876543212", "START_DATE", "2017-04-15", "END_DATE", "2029-04-15"},
                {9, "CUSTOMER_ID", 4, "OWNER_NAME", "Sneha Krishnan", "PHONE", "9876543213", "START_DATE", "2016-12-10", "END_DATE", "2028-12-10"},
                {10, "CUSTOMER_ID", 5, "OWNER_NAME", "Vikram Singh", "PHONE", "9876543214", "START_DATE", "2021-05-22", "END_DATE", "2029-05-22"},
                {11, "CUSTOMER_ID", 1, "OWNER_NAME", "Arun Kumar", "PHONE", "9876543210", "START_DATE", "2019-10-30", "END_DATE", "2028-10-30"},
                {12, "CUSTOMER_ID", 2, "OWNER_NAME", "Priya Sharma", "PHONE", "9876543211", "START_DATE", "2021-01-15", "END_DATE", "2029-01-15"},
                {13, "CUSTOMER_ID", 3, "OWNER_NAME", "Rahul Raj", "PHONE", "9876543212", "START_DATE", "2018-06-20", "END_DATE", "2028-06-20"},
                {14, "CUSTOMER_ID", 4, "OWNER_NAME", "Sneha Krishnan", "PHONE", "9876543213", "START_DATE", "2022-03-12", "END_DATE", "2029-03-12"},
                {15, "CUSTOMER_ID", 5, "OWNER_NAME", "Vikram Singh", "PHONE", "9876543214", "START_DATE", "2020-08-05", "END_DATE", "2028-08-05"}
            };
        }

        private static Object[][] insuranceRows() {
            return new Object[][] {
                {1, "INSURANCE_ID", 1, "POLICY_NO", "POL10001", "INSURANCE_COMPANY", "ICICI Lombard", "START_DATE", "2026-06-15", "EXPIRY_DATE", "2027-06-15", "PREMIUM", 12500},
                {2, "INSURANCE_ID", 2, "POLICY_NO", "POL10002", "INSURANCE_COMPANY", "HDFC ERGO", "START_DATE", "2026-03-20", "EXPIRY_DATE", "2027-03-20", "PREMIUM", 14000},
                {3, "INSURANCE_ID", 3, "POLICY_NO", "POL10003", "INSURANCE_COMPANY", "Bajaj Allianz", "START_DATE", "2026-08-10", "EXPIRY_DATE", "2027-08-10", "PREMIUM", 15000},
                {4, "INSURANCE_ID", 4, "POLICY_NO", "POL10004", "INSURANCE_COMPANY", "Tata AIG", "START_DATE", "2026-05-05", "EXPIRY_DATE", "2027-05-05", "PREMIUM", 12000},
                {5, "INSURANCE_ID", 5, "POLICY_NO", "POL10005", "INSURANCE_COMPANY", "SBI General", "START_DATE", "2026-07-12", "EXPIRY_DATE", "2027-07-12", "PREMIUM", 28000},
                {6, "INSURANCE_ID", 6, "POLICY_NO", "POL10006", "INSURANCE_COMPANY", "ICICI Lombard", "START_DATE", "2026-02-18", "EXPIRY_DATE", "2027-02-18", "PREMIUM", 26000},
                {7, "INSURANCE_ID", 7, "POLICY_NO", "POL10007", "INSURANCE_COMPANY", "HDFC ERGO", "START_DATE", "2026-09-25", "EXPIRY_DATE", "2027-09-25", "PREMIUM", 30000},
                {8, "INSURANCE_ID", 8, "POLICY_NO", "POL10008", "INSURANCE_COMPANY", "Bajaj Allianz", "START_DATE", "2026-04-15", "EXPIRY_DATE", "2027-12-15", "PREMIUM", 32000},
                {9, "INSURANCE_ID", 9, "POLICY_NO", "POL10009", "INSURANCE_COMPANY", "Tata AIG", "START_DATE", "2026-12-10", "EXPIRY_DATE", "2027-12-10", "PREMIUM", 35000},
                {10, "INSURANCE_ID", 10, "POLICY_NO", "POL10010", "INSURANCE_COMPANY", "SBI General", "START_DATE", "2026-05-22", "EXPIRY_DATE", "2028-05-22", "PREMIUM", 38000},
                {11, "INSURANCE_ID", 11, "POLICY_NO", "POL10011", "INSURANCE_COMPANY", "ICICI Lombard", "START_DATE", "2026-10-30", "EXPIRY_DATE", "2027-10-30", "PREMIUM", 40000},
                {12, "INSURANCE_ID", 12, "POLICY_NO", "POL10012", "INSURANCE_COMPANY", "HDFC ERGO", "START_DATE", "2026-01-15", "EXPIRY_DATE", "2028-01-15", "PREMIUM", 42000},
                {13, "INSURANCE_ID", 13, "POLICY_NO", "POL10013", "INSURANCE_COMPANY", "Bajaj Allianz", "START_DATE", "2026-06-20", "EXPIRY_DATE", "2028-06-20", "PREMIUM", 48000},
                {14, "INSURANCE_ID", 14, "POLICY_NO", "POL10014", "INSURANCE_COMPANY", "Tata AIG", "START_DATE", "2026-03-12", "EXPIRY_DATE", "2028-03-12", "PREMIUM", 52000},
                {15, "INSURANCE_ID", 15, "POLICY_NO", "POL10015", "INSURANCE_COMPANY", "SBI General", "START_DATE", "2026-08-05", "EXPIRY_DATE", "2028-08-05", "PREMIUM", 60000},
                {16, "INSURANCE_ID", 16, "POLICY_NO", "POL10016", "INSURANCE_COMPANY", "ICICI Lombard", "START_DATE", "2026-10-18", "EXPIRY_DATE", "2028-10-18", "PREMIUM", 58000},
                {17, "INSURANCE_ID", 17, "POLICY_NO", "POL10017", "INSURANCE_COMPANY", "HDFC ERGO", "START_DATE", "2026-09-10", "EXPIRY_DATE", "2027-09-10", "PREMIUM", 13500},
                {18, "INSURANCE_ID", 18, "POLICY_NO", "POL10018", "INSURANCE_COMPANY", "Bajaj Allianz", "START_DATE", "2026-05-18", "EXPIRY_DATE", "2027-05-18", "PREMIUM", 27000},
                {19, "INSURANCE_ID", 19, "POLICY_NO", "POL10019", "INSURANCE_COMPANY", "Tata AIG", "START_DATE", "2026-11-25", "EXPIRY_DATE", "2028-11-25", "PREMIUM", 36000},
                {20, "INSURANCE_ID", 20, "POLICY_NO", "POL10020", "INSURANCE_COMPANY", "SBI General", "START_DATE", "2026-01-12", "EXPIRY_DATE", "2028-01-12", "PREMIUM", 45000}
            };
        }

        private static Object[][] warrantyRows() {
            return new Object[][] {
                {1, "WARRANTY_ID", 1, "WARRANTY_PROVIDER", "Royal Enfield", "WARRANTY_TYPE", "Manufacturer", "START_DATE", "2024-06-15", "EXPIRY_DATE", "2027-06-15", "COVERAGE_DETAILS", "Engine and transmission", "WARRANTY_STATUS", "Active"},
                {2, "WARRANTY_ID", 2, "WARRANTY_PROVIDER", "Yamaha", "WARRANTY_TYPE", "Manufacturer", "START_DATE", "2024-03-20", "EXPIRY_DATE", "2027-03-20", "COVERAGE_DETAILS", "Engine and electrical systems", "WARRANTY_STATUS", "Active"},
                {3, "WARRANTY_ID", 3, "WARRANTY_PROVIDER", "Honda", "WARRANTY_TYPE", "Manufacturer", "START_DATE", "2024-08-10", "EXPIRY_DATE", "2027-08-10", "COVERAGE_DETAILS", "Engine and major components", "WARRANTY_STATUS", "Active"},
                {4, "WARRANTY_ID", 4, "WARRANTY_PROVIDER", "TVS", "WARRANTY_TYPE", "Manufacturer", "START_DATE", "2024-11-05", "EXPIRY_DATE", "2027-11-05", "COVERAGE_DETAILS", "Engine and transmission", "WARRANTY_STATUS", "Active"},
                {5, "WARRANTY_ID", 5, "WARRANTY_PROVIDER", "Toyota", "WARRANTY_TYPE", "Manufacturer", "START_DATE", "2024-07-12", "EXPIRY_DATE", "2027-07-12", "COVERAGE_DETAILS", "Engine and electrical systems", "WARRANTY_STATUS", "Active"},
                {6, "WARRANTY_ID", 6, "WARRANTY_PROVIDER", "Hyundai", "WARRANTY_TYPE", "Manufacturer", "START_DATE", "2024-02-18", "EXPIRY_DATE", "2027-02-18", "COVERAGE_DETAILS", "Engine and transmission", "WARRANTY_STATUS", "Active"},
                {7, "WARRANTY_ID", 7, "WARRANTY_PROVIDER", "Tata", "WARRANTY_TYPE", "Manufacturer", "START_DATE", "2024-09-25", "EXPIRY_DATE", "2027-09-25", "COVERAGE_DETAILS", "Engine and major components", "WARRANTY_STATUS", "Active"},
                {8, "WARRANTY_ID", 8, "WARRANTY_PROVIDER", "Kia", "WARRANTY_TYPE", "Manufacturer", "START_DATE", "2024-04-15", "EXPIRY_DATE", "2027-12-15", "COVERAGE_DETAILS", "Engine, transmission and electrical systems", "WARRANTY_STATUS", "Active"},
                {9, "WARRANTY_ID", 9, "WARRANTY_PROVIDER", "Tata", "WARRANTY_TYPE", "Manufacturer", "START_DATE", "2025-01-10", "EXPIRY_DATE", "2028-01-10", "COVERAGE_DETAILS", "Engine and transmission", "WARRANTY_STATUS", "Active"},
                {10, "WARRANTY_ID", 10, "WARRANTY_PROVIDER", "Ashok Leyland", "WARRANTY_TYPE", "Manufacturer", "START_DATE", "2024-05-22", "EXPIRY_DATE", "2028-05-22", "COVERAGE_DETAILS", "Engine and transmission", "WARRANTY_STATUS", "Active"},
                {11, "WARRANTY_ID", 11, "WARRANTY_PROVIDER", "Eicher", "WARRANTY_TYPE", "Manufacturer", "START_DATE", "2024-10-30", "EXPIRY_DATE", "2028-10-30", "COVERAGE_DETAILS", "Engine and drivetrain", "WARRANTY_STATUS", "Active"},
                {12, "WARRANTY_ID", 12, "WARRANTY_PROVIDER", "BharatBenz", "WARRANTY_TYPE", "Manufacturer", "START_DATE", "2024-01-15", "EXPIRY_DATE", "2028-01-15", "COVERAGE_DETAILS", "Engine and transmission", "WARRANTY_STATUS", "Active"},
                {13, "WARRANTY_ID", 13, "WARRANTY_PROVIDER", "Ashok Leyland", "WARRANTY_TYPE", "Manufacturer", "START_DATE", "2024-06-20", "EXPIRY_DATE", "2028-06-20", "COVERAGE_DETAILS", "Engine and drivetrain", "WARRANTY_STATUS", "Active"},
                {14, "WARRANTY_ID", 14, "WARRANTY_PROVIDER", "Tata", "WARRANTY_TYPE", "Manufacturer", "START_DATE", "2024-03-12", "EXPIRY_DATE", "2028-03-12", "COVERAGE_DETAILS", "Engine and transmission", "WARRANTY_STATUS", "Active"},
                {15, "WARRANTY_ID", 15, "WARRANTY_PROVIDER", "Volvo", "WARRANTY_TYPE", "Manufacturer", "START_DATE", "2024-08-05", "EXPIRY_DATE", "2028-08-05", "COVERAGE_DETAILS", "Engine, transmission and electrical systems", "WARRANTY_STATUS", "Active"},
                {16, "WARRANTY_ID", 16, "WARRANTY_PROVIDER", "Eicher", "WARRANTY_TYPE", "Manufacturer", "START_DATE", "2024-10-18", "EXPIRY_DATE", "2028-10-18", "COVERAGE_DETAILS", "Engine and drivetrain", "WARRANTY_STATUS", "Active"},
                {17, "WARRANTY_ID", 17, "WARRANTY_PROVIDER", "KTM", "WARRANTY_TYPE", "Manufacturer", "START_DATE", "2024-09-10", "EXPIRY_DATE", "2027-09-10", "COVERAGE_DETAILS", "Engine and transmission", "WARRANTY_STATUS", "Active"},
                {18, "WARRANTY_ID", 18, "WARRANTY_PROVIDER", "Honda", "WARRANTY_TYPE", "Manufacturer", "START_DATE", "2024-05-18", "EXPIRY_DATE", "2028-05-18", "COVERAGE_DETAILS", "Engine and electrical systems", "WARRANTY_STATUS", "Active"},
                {19, "WARRANTY_ID", 19, "WARRANTY_PROVIDER", "Ashok Leyland", "WARRANTY_TYPE", "Manufacturer", "START_DATE", "2024-11-25", "EXPIRY_DATE", "2028-11-25", "COVERAGE_DETAILS", "Engine and drivetrain", "WARRANTY_STATUS", "Active"},
                {20, "WARRANTY_ID", 20, "WARRANTY_PROVIDER", "Eicher", "WARRANTY_TYPE", "Manufacturer", "START_DATE", "2024-01-12", "EXPIRY_DATE", "2028-01-12", "COVERAGE_DETAILS", "Engine and drivetrain", "WARRANTY_STATUS", "Active"}
            };
        }

        private static Object[][] maintenanceRows() {
            return new Object[][] {
                {1, "MAINTENANCE_ID", 1, "SERVICE_DATE", "2026-01-15", "SERVICE_TYPE", "General Service", "DESCRIPTION", "Engine oil change and routine inspection", "COST", 3500, "NEXT_SERVICE_DATE", "2026-07-15", "CENTER_NAME", "Chennai Auto Care", "PAYMENT_ID", 1, "PAYMENT_DATE", "2026-01-15", "AMOUNT", 3500, "PAYMENT_METHOD", "UPI", "PAYMENT_STATUS", "Paid"},
                {3, "MAINTENANCE_ID", 2, "SERVICE_DATE", "2026-02-10", "SERVICE_TYPE", "Repair", "DESCRIPTION", "Brake and suspension repair", "COST", 5200, "NEXT_SERVICE_DATE", "2026-08-10", "CENTER_NAME", "Dindigul Service Hub", "PAYMENT_ID", 2, "PAYMENT_DATE", "2026-02-10", "AMOUNT", 5200, "PAYMENT_METHOD", "Card", "PAYMENT_STATUS", "Paid"},
                {7, "MAINTENANCE_ID", 3, "SERVICE_DATE", "2026-03-05", "SERVICE_TYPE", "Accident Repair", "DESCRIPTION", "Body and bumper repair after accident", "COST", 18500, "NEXT_SERVICE_DATE", "2026-09-05", "CENTER_NAME", "Madurai Vehicle Works", "PAYMENT_ID", 3, "PAYMENT_DATE", "2026-03-05", "AMOUNT", 18500, "PAYMENT_METHOD", "UPI", "PAYMENT_STATUS", "Paid"},
                {10, "MAINTENANCE_ID", 4, "SERVICE_DATE", "2026-03-20", "SERVICE_TYPE", "General Service", "DESCRIPTION", "Engine inspection and oil replacement", "COST", 7500, "NEXT_SERVICE_DATE", "2026-09-20", "CENTER_NAME", "Pune Fleet Services", "PAYMENT_ID", 4, "PAYMENT_DATE", "2026-03-20", "AMOUNT", 7500, "PAYMENT_METHOD", "Cash", "PAYMENT_STATUS", "Paid"},
                {15, "MAINTENANCE_ID", 5, "SERVICE_DATE", "2026-04-12", "SERVICE_TYPE", "General Service", "DESCRIPTION", "Engine and transmission inspection", "COST", 12500, "NEXT_SERVICE_DATE", "2026-10-12", "CENTER_NAME", "Salem Truck Care", "PAYMENT_ID", 5, "PAYMENT_DATE", "2026-04-12", "AMOUNT", 12500, "PAYMENT_METHOD", "Bank Transfer", "PAYMENT_STATUS", "Paid"},
                {5, "MAINTENANCE_ID", 6, "SERVICE_DATE", "2026-04-25", "SERVICE_TYPE", "General Service", "DESCRIPTION", "Oil change, brake check and tyre inspection", "COST", 6000, "NEXT_SERVICE_DATE", "2026-10-25", "CENTER_NAME", "Coimbatore Motors", "PAYMENT_ID", 6, "PAYMENT_DATE", "2026-04-25", "AMOUNT", 6000, "PAYMENT_METHOD", "Card", "PAYMENT_STATUS", "Paid"}
            };
        }

        private static Object[][] accidentRows() {
            return new Object[][] {
                {1, "ACCIDENT_ID", 1, "ACCIDENT_DATE", "2026-02-15", "LOCATION", "Chennai", "DESCRIPTION", "Minor bumper damage", "DAMAGE_COST", 12000, "DRIVER_ID", 1, "DRIVER_NAME", "Karthikeyan Sudhakar"},
                {4, "ACCIDENT_ID", 2, "ACCIDENT_DATE", "2026-03-20", "LOCATION", "Dindigul", "DESCRIPTION", "Front bumper damaged", "DAMAGE_COST", 25000, "DRIVER_ID", 3, "DRIVER_NAME", "Shyam Sarath"},
                {7, "ACCIDENT_ID", 3, "ACCIDENT_DATE", "2026-05-10", "LOCATION", "Madurai", "DESCRIPTION", "Rear bumper damaged", "DAMAGE_COST", 18000, "DRIVER_ID", 2, "DRIVER_NAME", "Gauthaum Vishwanath"},
                {10, "ACCIDENT_ID", 4, "ACCIDENT_DATE", "2026-06-25", "LOCATION", "Pune", "DESCRIPTION", "Side door damage", "DAMAGE_COST", 15000, "DRIVER_ID", 4, "DRIVER_NAME", "Aaryan Ajit"},
                {15, "ACCIDENT_ID", 5, "ACCIDENT_DATE", "2026-07-18", "LOCATION", "Chennai", "DESCRIPTION", "Front bumper damaged", "DAMAGE_COST", 22000, "DRIVER_ID", 5, "DRIVER_NAME", "Rohit Sowmian"}
            };
        }

        private static Object[][] tripRows() {
            return new Object[][] {
                {1, "TRIP_ID", 1, "START_LOCATION", "Chennai", "DESTINATION", "Pondicherry", "START_TIME", "2026-08-01 08:00:00", "END_TIME", "2026-08-01 11:00:00", "DISTANCE", 155.5, "DRIVER_NAME", "Karthikeyan Sudhakar"},
                {2, "TRIP_ID", 2, "START_LOCATION", "Madurai", "DESTINATION", "Dindigul", "START_TIME", "2026-08-03 09:00:00", "END_TIME", "2026-08-03 11:00:00", "DISTANCE", 65.0, "DRIVER_NAME", "Gauthaum Vishwanath"},
                {4, "TRIP_ID", 3, "START_LOCATION", "Dindigul", "DESTINATION", "Chennai", "START_TIME", "2026-08-05 06:00:00", "END_TIME", "2026-08-05 13:00:00", "DISTANCE", 460.0, "DRIVER_NAME", "Shyam Sarath"},
                {5, "TRIP_ID", 4, "START_LOCATION", "Pune", "DESTINATION", "Mumbai", "START_TIME", "2026-08-07 08:00:00", "END_TIME", "2026-08-07 11:00:00", "DISTANCE", 150.0, "DRIVER_NAME", "Aaryan Ajit"},
                {6, "TRIP_ID", 5, "START_LOCATION", "Madurai", "DESTINATION", "Chennai", "START_TIME", "2026-08-10 07:00:00", "END_TIME", "2026-08-10 13:00:00", "DISTANCE", 460.0, "DRIVER_NAME", "Rohit Sowmian"}
            };
        }

        private static Object[][] fuelRows() {
            return new Object[][] {
                {1, "FUEL_ID", 1, "FUEL_DATE", "2026-08-01", "FUEL_TYPE", "Petrol", "QUANTITY", 12.5, "AMOUNT", 1250, "ODOMETER_READING", 12500},
                {2, "FUEL_ID", 2, "FUEL_DATE", "2026-08-02", "FUEL_TYPE", "Petrol", "QUANTITY", 10, "AMOUNT", 1000, "ODOMETER_READING", 18200},
                {3, "FUEL_ID", 3, "FUEL_DATE", "2026-08-03", "FUEL_TYPE", "Petrol", "QUANTITY", 15, "AMOUNT", 1500, "ODOMETER_READING", 9800},
                {4, "FUEL_ID", 4, "FUEL_DATE", "2026-08-04", "FUEL_TYPE", "Petrol", "QUANTITY", 8.5, "AMOUNT", 850, "ODOMETER_READING", 22500},
                {5, "FUEL_ID", 5, "FUEL_DATE", "2026-08-05", "FUEL_TYPE", "Diesel", "QUANTITY", 35, "AMOUNT", 3000, "ODOMETER_READING", 15700},
                {6, "FUEL_ID", 6, "FUEL_DATE", "2026-08-06", "FUEL_TYPE", "Diesel", "QUANTITY", 30, "AMOUNT", 2550, "ODOMETER_READING", 19800},
                {7, "FUEL_ID", 7, "FUEL_DATE", "2026-08-07", "FUEL_TYPE", "Diesel", "QUANTITY", 40, "AMOUNT", 3400, "ODOMETER_READING", 24500},
                {8, "FUEL_ID", 8, "FUEL_DATE", "2026-08-08", "FUEL_TYPE", "Diesel", "QUANTITY", 45, "AMOUNT", 3825, "ODOMETER_READING", 31200},
                {9, "FUEL_ID", 9, "FUEL_DATE", "2026-08-09", "FUEL_TYPE", "Diesel", "QUANTITY", 50, "AMOUNT", 4250, "ODOMETER_READING", 45600},
                {10, "FUEL_ID", 10, "FUEL_DATE", "2026-08-10", "FUEL_TYPE", "Diesel", "QUANTITY", 55, "AMOUNT", 4675, "ODOMETER_READING", 52300},
                {11, "FUEL_ID", 11, "FUEL_DATE", "2026-08-11", "FUEL_TYPE", "Diesel", "QUANTITY", 60, "AMOUNT", 5100, "ODOMETER_READING", 48700},
                {12, "FUEL_ID", 12, "FUEL_DATE", "2026-08-12", "FUEL_TYPE", "Diesel", "QUANTITY", 65, "AMOUNT", 5525, "ODOMETER_READING", 60100},
                {13, "FUEL_ID", 13, "FUEL_DATE", "2026-08-13", "FUEL_TYPE", "Diesel", "QUANTITY", 70, "AMOUNT", 5950, "ODOMETER_READING", 72500},
                {14, "FUEL_ID", 14, "FUEL_DATE", "2026-08-14", "FUEL_TYPE", "Diesel", "QUANTITY", 65, "AMOUNT", 5525, "ODOMETER_READING", 68400},
                {15, "FUEL_ID", 15, "FUEL_DATE", "2026-08-15", "FUEL_TYPE", "Diesel", "QUANTITY", 75, "AMOUNT", 6375, "ODOMETER_READING", 81200},
                {16, "FUEL_ID", 16, "FUEL_DATE", "2026-08-16", "FUEL_TYPE", "Diesel", "QUANTITY", 70, "AMOUNT", 5950, "ODOMETER_READING", 75600},
                {17, "FUEL_ID", 17, "FUEL_DATE", "2026-08-17", "FUEL_TYPE", "Petrol", "QUANTITY", 11, "AMOUNT", 1100, "ODOMETER_READING", 14300},
                {18, "FUEL_ID", 18, "FUEL_DATE", "2026-08-18", "FUEL_TYPE", "Petrol", "QUANTITY", 30, "AMOUNT", 3000, "ODOMETER_READING", 22100},
                {19, "FUEL_ID", 19, "FUEL_DATE", "2026-08-19", "FUEL_TYPE", "Diesel", "QUANTITY", 45, "AMOUNT", 3825, "ODOMETER_READING", 49800},
                {20, "FUEL_ID", 20, "FUEL_DATE", "2026-08-20", "FUEL_TYPE", "Diesel", "QUANTITY", 60, "AMOUNT", 5100, "ODOMETER_READING", 63500}
            };
        }

        private static List<Map<String, Object>> subtype(long vehicleId, String type) {
            if ("Bike".equalsIgnoreCase(type)) {
                if (vehicleId == 1) return list(row("VEHICLE_ID", 1, "MILEAGE", 35.00, "FUEL_CAPACITY", 17.00, "BIKE_TYPE", "Roadster", "ENGINE_CC", 648));
                if (vehicleId == 2) return list(row("VEHICLE_ID", 2, "MILEAGE", 29.00, "FUEL_CAPACITY", 14.00, "BIKE_TYPE", "Sport", "ENGINE_CC", 321));
                if (vehicleId == 3) return list(row("VEHICLE_ID", 3, "MILEAGE", 15.00, "FUEL_CAPACITY", 21.10, "BIKE_TYPE", "Touring", "ENGINE_CC", 1833));
                if (vehicleId == 4) return list(row("VEHICLE_ID", 4, "MILEAGE", 45.00, "FUEL_CAPACITY", 12.00, "BIKE_TYPE", "Street", "ENGINE_CC", 159));
                if (vehicleId == 17) return list(row("VEHICLE_ID", 17, "MILEAGE", 28.00, "FUEL_CAPACITY", 13.40, "BIKE_TYPE", "Sport", "ENGINE_CC", 373));
            } else if ("Car".equalsIgnoreCase(type)) {
                if (vehicleId == 5) return list(row("VEHICLE_ID", 5, "MILEAGE", 14.00, "FUEL_CAPACITY", 55.00, "BOOT_SPACE", 300.00, "SEATS", 7, "HORSEPOWER", 150, "CAR_TYPE", "MPV"));
                if (vehicleId == 6) return list(row("VEHICLE_ID", 6, "MILEAGE", 17.00, "FUEL_CAPACITY", 50.00, "BOOT_SPACE", 433.00, "SEATS", 5, "HORSEPOWER", 160, "CAR_TYPE", "SUV"));
                if (vehicleId == 7) return list(row("VEHICLE_ID", 7, "MILEAGE", 14.00, "FUEL_CAPACITY", 50.00, "BOOT_SPACE", 447.00, "SEATS", 7, "HORSEPOWER", 170, "CAR_TYPE", "SUV"));
                if (vehicleId == 8) return list(row("VEHICLE_ID", 8, "MILEAGE", 13.00, "FUEL_CAPACITY", 60.00, "BOOT_SPACE", 540.00, "SEATS", 7, "HORSEPOWER", 200, "CAR_TYPE", "MPV"));
                if (vehicleId == 18) return list(row("VEHICLE_ID", 18, "MILEAGE", 17.80, "FUEL_CAPACITY", 40.00, "BOOT_SPACE", 506.00, "SEATS", 5, "HORSEPOWER", 121, "CAR_TYPE", "Sedan"));
            } else if ("Truck".equalsIgnoreCase(type)) {
                if (vehicleId == 9) return list(row("VEHICLE_ID", 9, "MILEAGE", 10.00, "FUEL_CAPACITY", 60.00, "CAPACITY", 4500.00, "VOLUME", 25.00, "TRUCK_TYPE", "Light Truck"));
                if (vehicleId == 10) return list(row("VEHICLE_ID", 10, "MILEAGE", 12.00, "FUEL_CAPACITY", 40.00, "CAPACITY", 1250.00, "VOLUME", 10.00, "TRUCK_TYPE", "Mini Truck"));
                if (vehicleId == 11) return list(row("VEHICLE_ID", 11, "MILEAGE", 8.00, "FUEL_CAPACITY", 190.00, "CAPACITY", 15000.00, "VOLUME", 40.00, "TRUCK_TYPE", "Heavy Truck"));
                if (vehicleId == 12) return list(row("VEHICLE_ID", 12, "MILEAGE", 6.50, "FUEL_CAPACITY", 215.00, "CAPACITY", 31000.00, "VOLUME", 55.00, "TRUCK_TYPE", "Heavy Truck"));
                if (vehicleId == 19) return list(row("VEHICLE_ID", 19, "MILEAGE", 12.00, "FUEL_CAPACITY", 60.00, "CAPACITY", 2000.00, "VOLUME", 18.00, "TRUCK_TYPE", "Light Truck"));
            } else if ("Bus".equalsIgnoreCase(type)) {
                if (vehicleId == 13) return list(row("VEHICLE_ID", 13, "MILEAGE", 5.00, "FUEL_CAPACITY", 185.00, "SEATS", 55, "BUS_TYPE", "City Bus"));
                if (vehicleId == 14) return list(row("VEHICLE_ID", 14, "MILEAGE", 6.00, "FUEL_CAPACITY", 160.00, "SEATS", 50, "BUS_TYPE", "Double Decker"));
                if (vehicleId == 15) return list(row("VEHICLE_ID", 15, "MILEAGE", 3.50, "FUEL_CAPACITY", 300.00, "SEATS", 45, "BUS_TYPE", "Luxury Bus"));
                if (vehicleId == 16) return list(row("VEHICLE_ID", 16, "MILEAGE", 5.50, "FUEL_CAPACITY", 180.00, "SEATS", 30, "BUS_TYPE", "Mini Bus"));
                if (vehicleId == 20) return list(row("VEHICLE_ID", 20, "MILEAGE", 6.50, "FUEL_CAPACITY", 180.00, "SEATS", 35, "BUS_TYPE", "Mini Bus"));
            }
            return new ArrayList<>();
        }

        private static List<Map<String, Object>> list(Map<String, Object> row) {
            List<Map<String, Object>> result = new ArrayList<>();
            result.add(row);
            return result;
        }

        private static List<Map<String, Object>> list(Map<String, Object> first, Map<String, Object> second,
                                                       Map<String, Object> third, Map<String, Object> fourth) {
            List<Map<String, Object>> result = new ArrayList<>();
            result.add(first);
            result.add(second);
            result.add(third);
            result.add(fourth);
            return result;
        }
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private static final class Json {
        private Json() { }

        private static String encode(Object value) {
            if (value == null) {
                return "null";
            }
            if (value instanceof Number || value instanceof Boolean) {
                return String.valueOf(value);
            }
            if (value instanceof Map) {
                StringBuilder json = new StringBuilder("{");
                boolean first = true;
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    if (!first) json.append(',');
                    json.append(quote(String.valueOf(entry.getKey()))).append(':').append(encode(entry.getValue()));
                    first = false;
                }
                return json.append('}').toString();
            }
            if (value instanceof Iterable) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                for (Object item : (Iterable<?>) value) {
                    if (!first) json.append(',');
                    json.append(encode(item));
                    first = false;
                }
                return json.append(']').toString();
            }
            return quote(String.valueOf(value));
        }

        private static String quote(String value) {
            StringBuilder escaped = new StringBuilder("\"");
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                if (character == '"') {
                    escaped.append("\\\"");
                } else if (character == '\\') {
                    escaped.append("\\\\");
                } else if (character == '\b') {
                    escaped.append("\\b");
                } else if (character == '\f') {
                    escaped.append("\\f");
                } else if (character == '\n') {
                    escaped.append("\\n");
                } else if (character == '\r') {
                    escaped.append("\\r");
                } else if (character == '\t') {
                    escaped.append("\\t");
                } else if (character < 0x20) {
                    escaped.append(String.format("\\u%04x", (int) character));
                } else {
                    escaped.append(character);
                }
            }
            return escaped.append('"').toString();
        }
    }
}