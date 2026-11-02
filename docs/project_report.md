# Vehicle Management System Project Report

## Introduction

The Vehicle Management System is a relational database project for organizing records about a mixed fleet. It connects vehicle details and ownership to operational records such as service, payments, trips, fuel, insurance, warranties, and accidents.

## Objectives

- Store and retrieve consistent vehicle and driver information.
- Represent bike, car, truck, and bus-specific attributes without overloading the core vehicle row.
- Maintain ownership history and operational records linked by keys.
- Produce readable PL/SQL reports for a selected vehicle.
- Validate schema objects, sample data, and stored procedure compilation.

## Features

- Sixteen related tables, including four vehicle subtype tables.
- Primary keys, unique values, and foreign keys for the project's relationships.
- Transcript-derived sample records: 20 vehicles, 5 drivers, 5 project customers, 20 warranties, 20 insurance policies, and 20 fuel records, along with the successful service, payment, ownership, trip, and accident inserts.
- Eighteen numbered query examples.
- Four procedures: `GET_VEHICLE_REPORT`, `GET_MAINTENANCE_PAYMENT`, `GET_TRIP_FUEL_REPORT`, and `GET_VEHICLE_TYPE_REPORT`.
- Test blocks covering a valid vehicle with records and each subtype report branch.

## Database design

`VEHICLE` stores common identifying attributes. `BIKE`, `CAR`, `TRUCK`, and `BUS` each reference one vehicle and hold the attributes specific to that type. `CUSTOMER4` and `VEHICLE_OWNERSHIP` represent the project customer records and dated ownership relationship. `DRIVER` is referenced by trip and accident data. `MAINTENANCE` links vehicles to service centers; `PAYMENT` refers to maintenance. Insurance, warranty, accident, trip, and fuel tables retain their own dated records and keys.

The transcript shows an unrelated `CUSTOMER` table already present in Oracle `SYSTEM`. Project rows were instead successfully inserted into `CUSTOMER4`, so the project keeps `CUSTOMER4` rather than altering or renaming the unrelated table. The detailed definitions and keys are in [database_schema.md](database_schema.md).

## Implementation

The SQL scripts are ordered so that parent tables and rows are created before dependent tables and rows. Date literals use Oracle `DATE` literals where possible; trip timestamps use explicit `TO_DATE` format masks. Procedures use outer joins or separate cursors for optional data, `NVL`/`TO_CHAR` for readable null and date output, and messages for missing vehicles or optional record groups.

`GET_VEHICLE_REPORT` follows the final successful implementation in the transcript: it loads vehicle, ownership ID, insurance, and warranty details, then displays accident history. The supplied log records its successful output for vehicle 1. The transcript also shows `GET_MAINTENANCE_PAYMENT` and `GET_TRIP_FUEL_REPORT` as valid objects, but contains no working `GET_VEHICLE_TYPE_REPORT`; all four procedures in this project must be compiled and verified in the target schema.

## Sample output

The following is the vehicle-1 output shown in the supplied transcript for the previously compiled `GET_VEHICLE_REPORT` implementation. It is included as source evidence, not as a claim that the newly organized scripts were executed:

```text
===== VEHICLE REPORT =====
Vehicle ID: 1
Vehicle Type: Bike
Model: Bear 650
Manufacturing Year: 2022
Registration No: TN01AB1234
Owner ID: 1
===== INSURANCE DETAILS =====
Policy No: POL10001
Company: ICICI Lombard
Expiry Date: 15-06-2027
===== WARRANTY DETAILS =====
Provider: Royal Enfield
Expiry Date: 15-06-2027
===== ACCIDENT HISTORY =====
Accident ID: 1, Driver ID: 1, Date: 15-02-2026, Location: Chennai,
Description: Minor bumper damage, Damage Cost: 12000
```

The supplied transcript's vehicle query returned 20 rows. These are results from the user's prior Oracle session; no output from the newly created scripts is represented here.

## Execution and validation

Use a dedicated Oracle schema, then run `01_create_tables.sql`, `02_insert_data.sql`, `04_procedures.sql`, `05_test_procedures.sql`, and `06_validation.sql` in order. Execute individual statements from `03_queries.sql` as required. The validation script checks required tables, table row counts, procedure status in `USER_OBJECTS`, compiler errors in `USER_ERRORS`, subtype coverage, and ownership/payment references.

## Conclusion

This project organizes the successful vehicle-management definitions and inserts from the provided SQL*Plus transcript into a documented Oracle SQL/PLSQL workflow. It also resolves failed foreign-key identifiers using the actual primary-key names and accounts for the pre-existing unrelated `CUSTOMER` object. Oracle compilation and execution of these consolidated files remain to be confirmed in the user's project schema.