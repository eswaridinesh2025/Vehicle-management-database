# Database Schema

The project uses the successful definitions and sample data shown in the supplied SQL*Plus transcript. Names are unquoted Oracle identifiers and are shown in uppercase as Oracle stores them.

## Tables

| Table | Columns | Primary key | Foreign keys |
| --- | --- | --- | --- |
| `CUSTOMER4` | `customer_id NUMBER`, `name VARCHAR2(100)`, `phone VARCHAR2(15)`, `email VARCHAR2(100)`, `address VARCHAR2(200)`, `license_no VARCHAR2(30)` | `customer_id`; `license_no` unique | None |
| `DRIVER` | `driver_id NUMBER`, `name VARCHAR2(100)`, `phone VARCHAR2(15)`, `license_no VARCHAR2(30)`, `license_expiry DATE`, `address VARCHAR2(200)` | `driver_id`; `license_no` unique | None |
| `VEHICLE` | `vehicle_id NUMBER`, `registration_no VARCHAR2(20)`, `vehicle_type VARCHAR2(30)`, `brand VARCHAR2(50)`, `model VARCHAR2(50)`, `manufacturing_year NUMBER(4)`, `color VARCHAR2(20)`, `status VARCHAR2(20)` | `vehicle_id`; `registration_no` unique | None |
| `SERVICE_CENTER` | `service_center_id NUMBER`, `center_name VARCHAR2(100)`, `location VARCHAR2(100)`, `phone VARCHAR2(20)` | `service_center_id` | None |
| `BIKE` | `vehicle_id NUMBER`, `mileage NUMBER(5,2)`, `fuel_capacity NUMBER(6,2)`, `bike_type VARCHAR2(30)`, `engine_cc NUMBER` | `vehicle_id` | `vehicle_id` -> `VEHICLE.vehicle_id` |
| `CAR` | `vehicle_id NUMBER`, `mileage NUMBER(5,2)`, `fuel_capacity NUMBER(6,2)`, `boot_space NUMBER(7,2)`, `seats NUMBER`, `horsepower NUMBER`, `car_type VARCHAR2(30)` | `vehicle_id` | `vehicle_id` -> `VEHICLE.vehicle_id` |
| `TRUCK` | `vehicle_id NUMBER`, `mileage NUMBER(5,2)`, `fuel_capacity NUMBER(6,2)`, `capacity NUMBER(10,2)`, `volume NUMBER(10,2)`, `truck_type VARCHAR2(30)` | `vehicle_id` | `vehicle_id` -> `VEHICLE.vehicle_id` |
| `BUS` | `vehicle_id NUMBER`, `mileage NUMBER(5,2)`, `fuel_capacity NUMBER(6,2)`, `seats NUMBER`, `bus_type VARCHAR2(30)` | `vehicle_id` | `vehicle_id` -> `VEHICLE.vehicle_id` |
| `VEHICLE_OWNERSHIP` | `customer_id NUMBER`, `vehicle_id NUMBER`, `start_date DATE`, `end_date DATE` | (`customer_id`, `vehicle_id`) | `customer_id` -> `CUSTOMER4.customer_id`; `vehicle_id` -> `VEHICLE.vehicle_id` |
| `MAINTENANCE` | `maintenance_id NUMBER`, `vehicle_id NUMBER`, `service_center_id NUMBER`, `service_date DATE`, `service_type VARCHAR2(50)`, `description VARCHAR2(200)`, `cost NUMBER(10,2)`, `next_service_date DATE` | `maintenance_id` | `vehicle_id` -> `VEHICLE.vehicle_id`; `service_center_id` -> `SERVICE_CENTER.service_center_id` |
| `PAYMENT` | `payment_id NUMBER`, `vehicle_id NUMBER`, `maintenance_id NUMBER`, `payment_date DATE`, `amount NUMBER(10,2)`, `payment_method VARCHAR2(30)`, `payment_status VARCHAR2(20)` | `payment_id` | `vehicle_id` -> `VEHICLE.vehicle_id`; `maintenance_id` -> `MAINTENANCE.maintenance_id` |
| `TRIP` | `trip_id NUMBER`, `vehicle_id NUMBER`, `driver_id NUMBER`, `start_location VARCHAR2(100)`, `destination VARCHAR2(100)`, `start_time DATE`, `end_time DATE`, `distance NUMBER(10,2)` | `trip_id` | `vehicle_id` -> `VEHICLE.vehicle_id`; `driver_id` -> `DRIVER.driver_id` |
| `WARRANTY` | `warranty_id NUMBER`, `vehicle_id NUMBER`, `warranty_provider VARCHAR2(100)`, `warranty_type VARCHAR2(50)`, `start_date DATE`, `expiry_date DATE`, `coverage_details VARCHAR2(300)`, `warranty_status VARCHAR2(20)` | `warranty_id` | `vehicle_id` -> `VEHICLE.vehicle_id` |
| `ACCIDENT` | `accident_id NUMBER`, `vehicle_id NUMBER`, `driver_id NUMBER`, `accident_date DATE`, `location VARCHAR2(100)`, `description VARCHAR2(300)`, `damage_cost NUMBER(10,2)` | `accident_id` | `vehicle_id` -> `VEHICLE.vehicle_id`; `driver_id` -> `DRIVER.driver_id` |
| `INSURANCE` | `insurance_id NUMBER`, `vehicle_id NUMBER`, `policy_no VARCHAR2(50)`, `insurance_company VARCHAR2(100)`, `start_date DATE`, `expiry_date DATE`, `premium NUMBER(10,2)` | `insurance_id`; `policy_no` unique | `vehicle_id` -> `VEHICLE.vehicle_id` |
| `FUEL_RECORD` | `fuel_id NUMBER`, `vehicle_id NUMBER`, `fuel_date DATE`, `fuel_type VARCHAR2(20)`, `quantity NUMBER(8,2)`, `amount NUMBER(10,2)`, `odometer_reading NUMBER` | `fuel_id` | `vehicle_id` -> `VEHICLE.vehicle_id` |

## Relationships

- A vehicle can have multiple ownership-history rows, policies, warranties, maintenance rows, trips, accidents, and fuel records.
- Each subtype row (`BIKE`, `CAR`, `TRUCK`, or `BUS`) uses the vehicle's ID as both its primary key and a foreign key to `VEHICLE`.
- A customer can be associated with multiple vehicles through `VEHICLE_OWNERSHIP`; the composite key prevents repeating the same customer/vehicle pair.
- A maintenance row belongs to one vehicle and one service center. Payments reference the maintenance row and also retain the recorded vehicle ID.
- Trips and accidents can reference a driver.

## Naming and source notes

The transcript's `DESC CUSTOMER` output identifies a pre-existing unrelated banking table with columns such as `CUSTOMERID`, `CUSTOMERNAME`, and `ACCOUNT_BALANCE`. The vehicle project instead successfully created and populated `CUSTOMER4` with `customer_id`, `name`, `phone`, `email`, `address`, and `license_no`; that project table name is retained here. The supplied successful vehicle definition uses `vehicle_id`; successful trip and accident definitions use `driver_id`. Failed DDL references to `veh_id` and `id` were not used.

`VEHICLE_OWNERSHIP` was successfully created in the transcript with only the vehicle foreign key after an attempted customer reference failed. The clean project DDL adds the intended customer foreign key to `CUSTOMER4`, since its sample IDs exist and it expresses the recorded ownership relationship. `SERVICE_CENTER` is included because the successful `MAINTENANCE` definition references it.