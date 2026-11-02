-- Vehicle Management System schema
-- Run in a dedicated project schema. CUSTOMER4 is retained because the
-- supplied Oracle SYSTEM schema already has an unrelated CUSTOMER table.

CREATE TABLE CUSTOMER4 (
    customer_id NUMBER PRIMARY KEY,
    name VARCHAR2(100) NOT NULL,
    phone VARCHAR2(15),
    email VARCHAR2(100),
    address VARCHAR2(200),
    license_no VARCHAR2(30) UNIQUE
);

CREATE TABLE DRIVER (
    driver_id NUMBER PRIMARY KEY,
    name VARCHAR2(100) NOT NULL,
    phone VARCHAR2(15),
    license_no VARCHAR2(30) UNIQUE,
    license_expiry DATE,
    address VARCHAR2(200)
);

CREATE TABLE VEHICLE (
    vehicle_id NUMBER PRIMARY KEY,
    registration_no VARCHAR2(20) UNIQUE NOT NULL,
    vehicle_type VARCHAR2(30) NOT NULL,
    brand VARCHAR2(50) NOT NULL,
    model VARCHAR2(50) NOT NULL,
    manufacturing_year NUMBER(4),
    color VARCHAR2(20),
    status VARCHAR2(20)
);

CREATE TABLE SERVICE_CENTER (
    service_center_id NUMBER PRIMARY KEY,
    center_name VARCHAR2(100) NOT NULL,
    location VARCHAR2(100),
    phone VARCHAR2(20)
);

CREATE TABLE BIKE (
    vehicle_id NUMBER PRIMARY KEY,
    mileage NUMBER(5,2),
    fuel_capacity NUMBER(6,2),
    bike_type VARCHAR2(30),
    engine_cc NUMBER,
    CONSTRAINT fk_bike_vehicle FOREIGN KEY (vehicle_id)
        REFERENCES VEHICLE(vehicle_id)
);

CREATE TABLE CAR (
    vehicle_id NUMBER PRIMARY KEY,
    mileage NUMBER(5,2),
    fuel_capacity NUMBER(6,2),
    boot_space NUMBER(7,2),
    seats NUMBER,
    horsepower NUMBER,
    car_type VARCHAR2(30),
    CONSTRAINT fk_car_vehicle FOREIGN KEY (vehicle_id)
        REFERENCES VEHICLE(vehicle_id)
);

CREATE TABLE TRUCK (
    vehicle_id NUMBER PRIMARY KEY,
    mileage NUMBER(5,2),
    fuel_capacity NUMBER(6,2),
    capacity NUMBER(10,2),
    volume NUMBER(10,2),
    truck_type VARCHAR2(30),
    CONSTRAINT fk_truck_vehicle FOREIGN KEY (vehicle_id)
        REFERENCES VEHICLE(vehicle_id)
);

CREATE TABLE BUS (
    vehicle_id NUMBER PRIMARY KEY,
    mileage NUMBER(5,2),
    fuel_capacity NUMBER(6,2),
    seats NUMBER,
    bus_type VARCHAR2(30),
    CONSTRAINT fk_bus_vehicle FOREIGN KEY (vehicle_id)
        REFERENCES VEHICLE(vehicle_id)
);

CREATE TABLE VEHICLE_OWNERSHIP (
    customer_id NUMBER,
    vehicle_id NUMBER,
    start_date DATE,
    end_date DATE,
    CONSTRAINT pk_vehicle_ownership PRIMARY KEY (customer_id, vehicle_id),
    CONSTRAINT fk_ownership_customer FOREIGN KEY (customer_id)
        REFERENCES CUSTOMER4(customer_id),
    CONSTRAINT fk_ownership_vehicle FOREIGN KEY (vehicle_id)
        REFERENCES VEHICLE(vehicle_id)
);

CREATE TABLE MAINTENANCE (
    maintenance_id NUMBER PRIMARY KEY,
    vehicle_id NUMBER NOT NULL,
    service_center_id NUMBER NOT NULL,
    service_date DATE,
    service_type VARCHAR2(50),
    description VARCHAR2(200),
    cost NUMBER(10,2),
    next_service_date DATE,
    CONSTRAINT fk_maintenance_vehicle FOREIGN KEY (vehicle_id)
        REFERENCES VEHICLE(vehicle_id),
    CONSTRAINT fk_maintenance_center FOREIGN KEY (service_center_id)
        REFERENCES SERVICE_CENTER(service_center_id)
);

CREATE TABLE PAYMENT (
    payment_id NUMBER PRIMARY KEY,
    vehicle_id NUMBER NOT NULL,
    maintenance_id NUMBER NOT NULL,
    payment_date DATE,
    amount NUMBER(10,2),
    payment_method VARCHAR2(30),
    payment_status VARCHAR2(20),
    CONSTRAINT fk_payment_vehicle FOREIGN KEY (vehicle_id)
        REFERENCES VEHICLE(vehicle_id),
    CONSTRAINT fk_payment_maintenance FOREIGN KEY (maintenance_id)
        REFERENCES MAINTENANCE(maintenance_id)
);

CREATE TABLE TRIP (
    trip_id NUMBER PRIMARY KEY,
    vehicle_id NUMBER,
    driver_id NUMBER,
    start_location VARCHAR2(100),
    destination VARCHAR2(100),
    start_time DATE,
    end_time DATE,
    distance NUMBER(10,2),
    CONSTRAINT fk_trip_vehicle FOREIGN KEY (vehicle_id)
        REFERENCES VEHICLE(vehicle_id),
    CONSTRAINT fk_trip_driver FOREIGN KEY (driver_id)
        REFERENCES DRIVER(driver_id)
);

CREATE TABLE WARRANTY (
    warranty_id NUMBER PRIMARY KEY,
    vehicle_id NUMBER,
    warranty_provider VARCHAR2(100),
    warranty_type VARCHAR2(50),
    start_date DATE,
    expiry_date DATE,
    coverage_details VARCHAR2(300),
    warranty_status VARCHAR2(20),
    CONSTRAINT fk_warranty_vehicle FOREIGN KEY (vehicle_id)
        REFERENCES VEHICLE(vehicle_id)
);

CREATE TABLE ACCIDENT (
    accident_id NUMBER PRIMARY KEY,
    vehicle_id NUMBER,
    driver_id NUMBER,
    accident_date DATE,
    location VARCHAR2(100),
    description VARCHAR2(300),
    damage_cost NUMBER(10,2),
    CONSTRAINT fk_accident_vehicle FOREIGN KEY (vehicle_id)
        REFERENCES VEHICLE(vehicle_id),
    CONSTRAINT fk_accident_driver FOREIGN KEY (driver_id)
        REFERENCES DRIVER(driver_id)
);

CREATE TABLE INSURANCE (
    insurance_id NUMBER PRIMARY KEY,
    vehicle_id NUMBER,
    policy_no VARCHAR2(50) UNIQUE,
    insurance_company VARCHAR2(100),
    start_date DATE,
    expiry_date DATE,
    premium NUMBER(10,2),
    CONSTRAINT fk_insurance_vehicle FOREIGN KEY (vehicle_id)
        REFERENCES VEHICLE(vehicle_id)
);

CREATE TABLE FUEL_RECORD (
    fuel_id NUMBER PRIMARY KEY,
    vehicle_id NUMBER,
    fuel_date DATE,
    fuel_type VARCHAR2(20),
    quantity NUMBER(8,2),
    amount NUMBER(10,2),
    odometer_reading NUMBER,
    CONSTRAINT fk_fuel_vehicle FOREIGN KEY (vehicle_id)
        REFERENCES VEHICLE(vehicle_id)
);