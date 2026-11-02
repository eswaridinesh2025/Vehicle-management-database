# Vehicle Management System

An Oracle SQL and PL/SQL project for maintaining a mixed vehicle fleet, ownership history, drivers, subtype specifications, maintenance and payments, trips, fuel purchases, insurance, warranties, and accidents.

The project also includes a local Java 8 HTTP backend using Oracle JDBC and a responsive browser dashboard.

## Features

- Relational schema with primary keys, foreign keys, uniqueness constraints, and vehicle subtype tables.
- Sample data based on successful inserts shown in the supplied SQL*Plus transcript.
- Numbered SQL queries for vehicle details and associated operational records.
- Four PL/SQL reports: `GET_VEHICLE_REPORT`, `GET_MAINTENANCE_PAYMENT`, `GET_TRIP_FUEL_REPORT`, and `GET_VEHICLE_TYPE_REPORT`.
- Procedure test blocks, schema/data checks, subtype coverage checks, and compilation-error checks.
- Java JDBC API for live inventory, fleet metrics, filters, and vehicle record details.
- Responsive local dashboard with Oracle connection form.
- Read-only browser for all 16 project tables, with per-column search and row counts.

## Requirements

- Oracle Database 12c or later. The supplied transcript identifies Oracle Database 21c Express Edition.
- Oracle SQL Developer or SQL*Plus.
- Java Development Kit 8 or later. The launcher defaults to the JDK and `ojdbc8.jar` bundled with Oracle Database 21c XE; set `JAVA_HOME` and `ORACLE_HOME` when installed elsewhere.
- A dedicated Oracle schema/user for this project is recommended.

## Schema note

The transcript shows that `CUSTOMER` already existed in the `SYSTEM` schema with an unrelated 11-column banking structure. The vehicle project's six-column customer records were successfully created in `CUSTOMER4`; this project therefore retains the real project table name `CUSTOMER4` and its observed columns. It does not drop or modify the unrelated `CUSTOMER` object. `DRIVER` uses the successful six-column vehicle-project definition, and `SERVICE_CENTER` is included because `MAINTENANCE` references it.

The final successful foreign-key definitions refer to `VEHICLE(vehicle_id)` and `DRIVER(driver_id)`. Earlier failed attempts used invalid identifiers `VEHICLE.veh_id` and `DRIVER.id`; the project scripts use the actual primary-key column names. The ownership foreign key to `CUSTOMER4` is included in the clean schema so the existing ownership rows are enforced.

## Run the website

From the project root in PowerShell, start the Java/JDBC backend and static frontend:

```powershell
.\app\run.ps1
```

Open [http://localhost:8080](http://localhost:8080) (or `http://localhost:8081` if port 8080 was already occupied). The launcher compiles the backend and serves the frontend locally. In the dashboard, choose **Connect Oracle** and enter the connection for the schema containing the project tables. The default local XE connection fields are host `localhost`, port `1521`, and service `XEPDB1`; the service can differ by installation. Enter the Oracle project-schema username and password in the browser form. The app tests the connection and keeps credentials in server memory only; they are not written to files.

The Oracle tables must already exist and contain project data before connecting. Follow the SQL setup instructions below first. The SQL*Plus transcript connected as `SYSTEM`, but running this project as `SYSTEM` is not recommended because the account contains unrelated objects. A dedicated user that owns the project tables is preferred.

To start the app and enter Oracle credentials in the dashboard, run `app/run.ps1`, open the printed localhost address, and choose **Connect database**. Use host `localhost`, port `1521`, service `XEPDB1`, and the project schema username/password. The supplied installer creates the username `VEHICLE_APP`; use the password entered during installation. Alternatively, `app/connect.ps1` prompts for `VEHICLE_APP` and its password in PowerShell and starts the connected site directly. If Oracle returns `ORA-01017`, verify the username and password used when the schema was created; credentials are not stored in the project.

When Oracle is not connected, the dashboard displays a read-only preview using the sample values from `sql/02_insert_data.sql`. The interface labels this as **Sample data preview**; after a successful Oracle connection, the API switches to live SQL queries. The preview is illustrative and does not insert or change database rows.

The **All tables** workspace lists every project table (`CUSTOMER4`, `DRIVER`, `VEHICLE`, `SERVICE_CENTER`, `BIKE`, `CAR`, `TRUCK`, `BUS`, `VEHICLE_OWNERSHIP`, `MAINTENANCE`, `PAYMENT`, `TRIP`, `WARRANTY`, `ACCIDENT`, `INSURANCE`, and `FUEL_RECORD`). Select a table to see its columns and rows, and search across the displayed values. The endpoint is read-only and accepts only these project table names.

## Setup and execution

To create a separate `VEHICLE_APP` schema in Oracle XE's `XEPDB1` pluggable database and install the existing project tables, sample rows, and procedures, run this from the project root in PowerShell:

```powershell
& 'C:\app\Dell\product\21c\dbhomeXE\bin\sqlplus.exe' '/ as sysdba' '@sql/00_create_vehicle_app.sql'
```

SQL*Plus prompts for a new schema password without displaying it. Use 12-128 letters and digits. The installer refuses to overwrite an existing `VEHICLE_APP` account and checks the connected user/container before running the existing DDL and data scripts. It creates a new database account and inserts the supplied sample rows; it does not modify `SYSTEM` or drop existing objects. After setup, connect the website with host `localhost`, port `1521`, service `XEPDB1`, username `VEHICLE_APP`, and the password you entered.

If the account was created but the installer stopped with `O/S Message: No such file or directory`, run the continuation installer instead. It prompts for the existing `VEHICLE_APP` password and refuses to proceed if any project tables already exist:

```powershell
Set-Location 'C:\Users\Dell\OneDrive\Documents\Vehicle management database\sql'
& 'C:\app\Dell\product\21c\dbhomeXE\bin\sqlplus.exe' '/ as sysdba' '@00_continue_vehicle_app_install.sql'
```

These DDL scripts create objects and are intended for a clean project schema. They do not drop objects and are not rerunnable over an already-created copy of the tables. Do not run the DDL as `SYSTEM` in the database shown in the transcript: that account already contains objects with colliding names and unrelated data. Connect SQL Developer or SQL*Plus to a dedicated project schema instead.

Run the files in this order:

1. `sql/01_create_tables.sql`
2. `sql/02_insert_data.sql`
3. `sql/03_queries.sql` (run individual queries as needed; this file is not a setup step)
4. `sql/04_procedures.sql`
5. `sql/05_test_procedures.sql`
6. `sql/06_validation.sql`

In SQL Developer, open each setup script in order and use **Run Script** (F5). Enable the DBMS Output panel for the connection before running procedure tests. In SQL*Plus, connect to the project schema and run:

```sql
@sql/01_create_tables.sql
@sql/02_insert_data.sql
@sql/04_procedures.sql
@sql/05_test_procedures.sql
@sql/06_validation.sql
```

Run `03_queries.sql` separately and select the query or queries needed. The test script uses vehicle ID `1`, which the supplied transcript shows has ownership, insurance, warranty, accident, maintenance, payment, trip, fuel, and bike subtype rows. It also tests type reports for vehicle IDs `5` (car), `9` (truck), and `14` (bus).

## Project files

- `sql/01_create_tables.sql` — project tables, keys, constraints, and foreign keys.
- `sql/00_create_vehicle_app.sql` — guarded Oracle XE installer for the dedicated `VEHICLE_APP` schema.
- `sql/00_continue_vehicle_app_install.sql` — guarded continuation if schema creation succeeded but nested SQL scripts were not found.
- `sql/02_insert_data.sql` — sample records in dependency order.
- `sql/03_queries.sql` — numbered retrieval and reporting queries.
- `sql/04_procedures.sql` — four stored procedures.
- `sql/05_test_procedures.sql` — valid-ID procedure calls and optional not-found checks.
- `sql/06_validation.sql` — table, row-count, compilation, subtype, and relationship checks.
- `docs/database_schema.md` — column and relationship reference.
- `docs/project_report.md` — project report and transcript-based sample output.
- `app/src/main/java/com/vehiclemanager/VehicleServer.java` — Java HTTP server, API routing, sample preview, and website delivery.
- `app/src/main/java/com/vehiclemanager/VehicleJdbc.java` — Oracle JDBC connections, SQL queries, and procedure calls.
- `app/src/main/resources/web/` — dashboard HTML, CSS, and browser-side JavaScript.
- `app/run.ps1` — compile and launch script for Windows PowerShell.

## Validation status

The supplied transcript records a successful execution of `GET_VEHICLE_REPORT(1)` and reports `GET_MAINTENANCE_PAYMENT` and `GET_TRIP_FUEL_REPORT` as valid in `USER_OBJECTS`; it does not show a successful `GET_VEHICLE_TYPE_REPORT`. The files in this workspace were assembled from that transcript and corrected against its successful table definitions, but have not been executed against an Oracle instance in this environment. Run `sql/06_validation.sql` in the target Oracle schema to confirm compilation and data integrity.

The Java backend compiles with the Oracle XE JDK and JDBC driver available on the development machine. Live API queries require valid credentials for an Oracle schema containing these tables.