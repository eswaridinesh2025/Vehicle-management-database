-- Enable DBMS_OUTPUT in SQL Developer before running test blocks.

CREATE OR REPLACE PROCEDURE GET_VEHICLE_REPORT (
    p_veh_id IN NUMBER
)
IS
    v_found NUMBER := 0;
    v_accident_count NUMBER := 0;
BEGIN
    FOR v IN (
        SELECT ve.vehicle_id,
               ve.vehicle_type,
               ve.model,
               ve.manufacturing_year,
               ve.registration_no,
               vo.customer_id,
               i.policy_no,
               i.insurance_company,
               i.expiry_date AS insurance_expiry,
               w.warranty_provider,
               w.expiry_date AS warranty_expiry
        FROM VEHICLE ve
        LEFT JOIN VEHICLE_OWNERSHIP vo
            ON ve.vehicle_id = vo.vehicle_id
        LEFT JOIN INSURANCE i
            ON ve.vehicle_id = i.vehicle_id
        LEFT JOIN WARRANTY w
            ON ve.vehicle_id = w.vehicle_id
        WHERE ve.vehicle_id = p_veh_id
    )
    LOOP
        v_found := 1;

        DBMS_OUTPUT.PUT_LINE('===== VEHICLE REPORT =====');
        DBMS_OUTPUT.PUT_LINE('Vehicle ID: ' || v.vehicle_id);
        DBMS_OUTPUT.PUT_LINE('Vehicle Type: ' || NVL(v.vehicle_type, 'N/A'));
        DBMS_OUTPUT.PUT_LINE('Model: ' || NVL(v.model, 'N/A'));
        DBMS_OUTPUT.PUT_LINE('Manufacturing Year: ' || NVL(TO_CHAR(v.manufacturing_year), 'N/A'));
        DBMS_OUTPUT.PUT_LINE('Registration No: ' || NVL(v.registration_no, 'N/A'));
        DBMS_OUTPUT.PUT_LINE('Owner ID: ' || NVL(TO_CHAR(v.customer_id), 'N/A'));

        DBMS_OUTPUT.PUT_LINE('===== INSURANCE DETAILS =====');
        DBMS_OUTPUT.PUT_LINE('Policy No: ' || NVL(v.policy_no, 'N/A'));
        DBMS_OUTPUT.PUT_LINE('Company: ' || NVL(v.insurance_company, 'N/A'));
        DBMS_OUTPUT.PUT_LINE('Expiry Date: ' || NVL(TO_CHAR(v.insurance_expiry, 'DD-MM-YYYY'), 'N/A'));

        DBMS_OUTPUT.PUT_LINE('===== WARRANTY DETAILS =====');
        DBMS_OUTPUT.PUT_LINE('Provider: ' || NVL(v.warranty_provider, 'N/A'));
        DBMS_OUTPUT.PUT_LINE('Expiry Date: ' || NVL(TO_CHAR(v.warranty_expiry, 'DD-MM-YYYY'), 'N/A'));

        DBMS_OUTPUT.PUT_LINE('===== ACCIDENT HISTORY =====');
        v_accident_count := 0;

        FOR a IN (
            SELECT accident_id, driver_id, accident_date,
                   location, description, damage_cost
            FROM ACCIDENT
            WHERE vehicle_id = p_veh_id
            ORDER BY accident_date
        )
        LOOP
            v_accident_count := v_accident_count + 1;
            DBMS_OUTPUT.PUT_LINE(
                'Accident ID: ' || a.accident_id ||
                ', Driver ID: ' || NVL(TO_CHAR(a.driver_id), 'N/A') ||
                ', Date: ' || NVL(TO_CHAR(a.accident_date, 'DD-MM-YYYY'), 'N/A') ||
                ', Location: ' || NVL(a.location, 'N/A') ||
                ', Description: ' || NVL(a.description, 'N/A') ||
                ', Damage Cost: ' || NVL(TO_CHAR(a.damage_cost), 'N/A')
            );
        END LOOP;

        IF v_accident_count = 0 THEN
            DBMS_OUTPUT.PUT_LINE('No accident history found.');
        END IF;
    END LOOP;

    IF v_found = 0 THEN
        DBMS_OUTPUT.PUT_LINE('Vehicle not found: ' || p_veh_id);
    END IF;
END;
/

CREATE OR REPLACE PROCEDURE GET_MAINTENANCE_PAYMENT (
    p_vehicle_id IN NUMBER
)
IS
    v_vehicle_exists NUMBER := 0;
    v_maintenance_count NUMBER := 0;
BEGIN
    SELECT COUNT(*)
    INTO v_vehicle_exists
    FROM VEHICLE
    WHERE vehicle_id = p_vehicle_id;

    IF v_vehicle_exists = 0 THEN
        DBMS_OUTPUT.PUT_LINE('Vehicle not found: ' || p_vehicle_id);
        RETURN;
    END IF;

    FOR m IN (
        SELECT m.maintenance_id,
               m.vehicle_id,
               m.service_date,
               m.service_type,
               m.description,
               m.cost,
               m.next_service_date,
               p.payment_id,
               p.payment_date,
               p.amount,
               p.payment_method,
               p.payment_status
        FROM MAINTENANCE m
        LEFT JOIN PAYMENT p
            ON m.maintenance_id = p.maintenance_id
        WHERE m.vehicle_id = p_vehicle_id
        ORDER BY m.service_date, m.maintenance_id
    )
    LOOP
        v_maintenance_count := v_maintenance_count + 1;
        DBMS_OUTPUT.PUT_LINE('===== MAINTENANCE & PAYMENT =====');
        DBMS_OUTPUT.PUT_LINE('Vehicle ID: ' || m.vehicle_id);
        DBMS_OUTPUT.PUT_LINE('Maintenance ID: ' || m.maintenance_id);
        DBMS_OUTPUT.PUT_LINE('Service Date: ' || NVL(TO_CHAR(m.service_date, 'DD-MM-YYYY'), 'N/A'));
        DBMS_OUTPUT.PUT_LINE('Service Type: ' || NVL(m.service_type, 'N/A'));
        DBMS_OUTPUT.PUT_LINE('Description: ' || NVL(m.description, 'N/A'));
        DBMS_OUTPUT.PUT_LINE('Maintenance Cost: ' || NVL(TO_CHAR(m.cost, 'FM999999990.00'), 'N/A'));
        DBMS_OUTPUT.PUT_LINE('Next Service Date: ' || NVL(TO_CHAR(m.next_service_date, 'DD-MM-YYYY'), 'N/A'));
        DBMS_OUTPUT.PUT_LINE('Payment ID: ' || NVL(TO_CHAR(m.payment_id), 'N/A'));
        DBMS_OUTPUT.PUT_LINE('Payment Date: ' || NVL(TO_CHAR(m.payment_date, 'DD-MM-YYYY'), 'N/A'));
        DBMS_OUTPUT.PUT_LINE('Payment Amount: ' || NVL(TO_CHAR(m.amount, 'FM999999990.00'), 'N/A'));
        DBMS_OUTPUT.PUT_LINE('Payment Method: ' || NVL(m.payment_method, 'N/A'));
        DBMS_OUTPUT.PUT_LINE('Payment Status: ' || NVL(m.payment_status, 'N/A'));
    END LOOP;

    IF v_maintenance_count = 0 THEN
        DBMS_OUTPUT.PUT_LINE('No maintenance records found for vehicle ' || p_vehicle_id || '.');
    END IF;
END;
/

CREATE OR REPLACE PROCEDURE GET_TRIP_FUEL_REPORT (
    p_vehicle_id IN NUMBER
)
IS
    v_vehicle_exists NUMBER := 0;
    v_driver_count NUMBER := 0;
    v_trip_count NUMBER := 0;
    v_fuel_count NUMBER := 0;
BEGIN
    FOR v IN (
        SELECT vehicle_id, vehicle_type, model,
               manufacturing_year, registration_no
        FROM VEHICLE
        WHERE vehicle_id = p_vehicle_id
    )
    LOOP
        v_vehicle_exists := 1;
        DBMS_OUTPUT.PUT_LINE('===== TRIP & FUEL REPORT =====');
        DBMS_OUTPUT.PUT_LINE('Vehicle ID: ' || v.vehicle_id);
        DBMS_OUTPUT.PUT_LINE('Vehicle Type: ' || NVL(v.vehicle_type, 'N/A'));
        DBMS_OUTPUT.PUT_LINE('Model: ' || NVL(v.model, 'N/A'));
        DBMS_OUTPUT.PUT_LINE('Manufacturing Year: ' || NVL(TO_CHAR(v.manufacturing_year), 'N/A'));
        DBMS_OUTPUT.PUT_LINE('Registration No: ' || NVL(v.registration_no, 'N/A'));

        DBMS_OUTPUT.PUT_LINE('===== DRIVERS =====');
        FOR d IN (
            SELECT DISTINCT dr.driver_id, dr.name, dr.phone,
                            dr.license_no, dr.license_expiry
            FROM DRIVER dr
            JOIN TRIP t ON dr.driver_id = t.driver_id
            WHERE t.vehicle_id = p_vehicle_id
            ORDER BY dr.driver_id
        )
        LOOP
            v_driver_count := v_driver_count + 1;
            DBMS_OUTPUT.PUT_LINE(
                'Driver ID: ' || d.driver_id ||
                ', Name: ' || NVL(d.name, 'N/A') ||
                ', Phone: ' || NVL(d.phone, 'N/A') ||
                ', License: ' || NVL(d.license_no, 'N/A') ||
                ', Expiry: ' || NVL(TO_CHAR(d.license_expiry, 'DD-MM-YYYY'), 'N/A')
            );
        END LOOP;
        IF v_driver_count = 0 THEN
            DBMS_OUTPUT.PUT_LINE('No drivers or trips found.');
        END IF;

        DBMS_OUTPUT.PUT_LINE('===== TRIPS =====');
        FOR t IN (
            SELECT trip_id, start_location, destination,
                   start_time, end_time, distance
            FROM TRIP
            WHERE vehicle_id = p_vehicle_id
            ORDER BY start_time, trip_id
        )
        LOOP
            v_trip_count := v_trip_count + 1;
            DBMS_OUTPUT.PUT_LINE(
                'Trip ID: ' || t.trip_id ||
                ', From: ' || NVL(t.start_location, 'N/A') ||
                ', To: ' || NVL(t.destination, 'N/A') ||
                ', Start: ' || NVL(TO_CHAR(t.start_time, 'DD-MM-YYYY HH24:MI'), 'N/A') ||
                ', End: ' || NVL(TO_CHAR(t.end_time, 'DD-MM-YYYY HH24:MI'), 'N/A') ||
                ', Distance: ' || NVL(TO_CHAR(t.distance), 'N/A') || ' km'
            );
        END LOOP;
        IF v_trip_count = 0 THEN
            DBMS_OUTPUT.PUT_LINE('No trip records found.');
        END IF;

        DBMS_OUTPUT.PUT_LINE('===== FUEL RECORDS =====');
        FOR f IN (
            SELECT fuel_id, fuel_date, fuel_type,
                   quantity, amount, odometer_reading
            FROM FUEL_RECORD
            WHERE vehicle_id = p_vehicle_id
            ORDER BY fuel_date, fuel_id
        )
        LOOP
            v_fuel_count := v_fuel_count + 1;
            DBMS_OUTPUT.PUT_LINE(
                'Fuel ID: ' || f.fuel_id ||
                ', Date: ' || NVL(TO_CHAR(f.fuel_date, 'DD-MM-YYYY'), 'N/A') ||
                ', Type: ' || NVL(f.fuel_type, 'N/A') ||
                ', Quantity: ' || NVL(TO_CHAR(f.quantity), 'N/A') ||
                ', Amount: ' || NVL(TO_CHAR(f.amount, 'FM999999990.00'), 'N/A') ||
                ', Odometer: ' || NVL(TO_CHAR(f.odometer_reading), 'N/A')
            );
        END LOOP;
        IF v_fuel_count = 0 THEN
            DBMS_OUTPUT.PUT_LINE('No fuel records found.');
        END IF;
    END LOOP;

    IF v_vehicle_exists = 0 THEN
        DBMS_OUTPUT.PUT_LINE('Vehicle not found: ' || p_vehicle_id);
    END IF;
END;
/

CREATE OR REPLACE PROCEDURE GET_VEHICLE_TYPE_REPORT (
    p_vehicle_id IN NUMBER
)
IS
    v_found NUMBER := 0;
BEGIN
    FOR v IN (
        SELECT ve.vehicle_id,
               ve.registration_no,
               ve.vehicle_type,
               ve.brand,
               ve.model,
               b.mileage AS bike_mileage,
               b.fuel_capacity AS bike_fuel_capacity,
               b.bike_type,
               b.engine_cc,
               c.mileage AS car_mileage,
               c.fuel_capacity AS car_fuel_capacity,
               c.boot_space,
               c.seats AS car_seats,
               c.horsepower,
               c.car_type,
               tr.mileage AS truck_mileage,
               tr.fuel_capacity AS truck_fuel_capacity,
               tr.capacity AS truck_capacity,
               tr.volume AS truck_volume,
               tr.truck_type,
               bu.mileage AS bus_mileage,
               bu.fuel_capacity AS bus_fuel_capacity,
               bu.seats AS bus_seats,
               bu.bus_type
        FROM VEHICLE ve
        LEFT JOIN BIKE b ON b.vehicle_id = ve.vehicle_id
        LEFT JOIN CAR c ON c.vehicle_id = ve.vehicle_id
        LEFT JOIN TRUCK tr ON tr.vehicle_id = ve.vehicle_id
        LEFT JOIN BUS bu ON bu.vehicle_id = ve.vehicle_id
        WHERE ve.vehicle_id = p_vehicle_id
    )
    LOOP
        v_found := 1;
        DBMS_OUTPUT.PUT_LINE('===== VEHICLE TYPE REPORT =====');
        DBMS_OUTPUT.PUT_LINE('Vehicle ID: ' || v.vehicle_id);
        DBMS_OUTPUT.PUT_LINE('Registration No: ' || NVL(v.registration_no, 'N/A'));
        DBMS_OUTPUT.PUT_LINE('Vehicle Type: ' || NVL(v.vehicle_type, 'N/A'));
        DBMS_OUTPUT.PUT_LINE('Brand / Model: ' || NVL(v.brand, 'N/A') || ' / ' || NVL(v.model, 'N/A'));

        IF UPPER(v.vehicle_type) = 'BIKE' AND v.bike_type IS NOT NULL THEN
            DBMS_OUTPUT.PUT_LINE('Subtype: ' || v.bike_type);
            DBMS_OUTPUT.PUT_LINE('Engine CC: ' || NVL(TO_CHAR(v.engine_cc), 'N/A'));
            DBMS_OUTPUT.PUT_LINE('Mileage: ' || NVL(TO_CHAR(v.bike_mileage), 'N/A'));
            DBMS_OUTPUT.PUT_LINE('Fuel Capacity: ' || NVL(TO_CHAR(v.bike_fuel_capacity), 'N/A'));
        ELSIF UPPER(v.vehicle_type) = 'CAR' AND v.car_type IS NOT NULL THEN
            DBMS_OUTPUT.PUT_LINE('Subtype: ' || v.car_type);
            DBMS_OUTPUT.PUT_LINE('Seats: ' || NVL(TO_CHAR(v.car_seats), 'N/A'));
            DBMS_OUTPUT.PUT_LINE('Horsepower: ' || NVL(TO_CHAR(v.horsepower), 'N/A'));
            DBMS_OUTPUT.PUT_LINE('Boot Space: ' || NVL(TO_CHAR(v.boot_space), 'N/A'));
            DBMS_OUTPUT.PUT_LINE('Mileage: ' || NVL(TO_CHAR(v.car_mileage), 'N/A'));
            DBMS_OUTPUT.PUT_LINE('Fuel Capacity: ' || NVL(TO_CHAR(v.car_fuel_capacity), 'N/A'));
        ELSIF UPPER(v.vehicle_type) = 'TRUCK' AND v.truck_type IS NOT NULL THEN
            DBMS_OUTPUT.PUT_LINE('Subtype: ' || v.truck_type);
            DBMS_OUTPUT.PUT_LINE('Capacity: ' || NVL(TO_CHAR(v.truck_capacity), 'N/A'));
            DBMS_OUTPUT.PUT_LINE('Volume: ' || NVL(TO_CHAR(v.truck_volume), 'N/A'));
            DBMS_OUTPUT.PUT_LINE('Mileage: ' || NVL(TO_CHAR(v.truck_mileage), 'N/A'));
            DBMS_OUTPUT.PUT_LINE('Fuel Capacity: ' || NVL(TO_CHAR(v.truck_fuel_capacity), 'N/A'));
        ELSIF UPPER(v.vehicle_type) = 'BUS' AND v.bus_type IS NOT NULL THEN
            DBMS_OUTPUT.PUT_LINE('Subtype: ' || v.bus_type);
            DBMS_OUTPUT.PUT_LINE('Seats: ' || NVL(TO_CHAR(v.bus_seats), 'N/A'));
            DBMS_OUTPUT.PUT_LINE('Mileage: ' || NVL(TO_CHAR(v.bus_mileage), 'N/A'));
            DBMS_OUTPUT.PUT_LINE('Fuel Capacity: ' || NVL(TO_CHAR(v.bus_fuel_capacity), 'N/A'));
        ELSE
            DBMS_OUTPUT.PUT_LINE('No subtype detail row found for this vehicle.');
        END IF;
    END LOOP;

    IF v_found = 0 THEN
        DBMS_OUTPUT.PUT_LINE('Vehicle not found: ' || p_vehicle_id);
    END IF;
END;
/