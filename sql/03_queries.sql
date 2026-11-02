-- Vehicle Management System assignment and reporting queries
-- Run individual statements as needed; vehicle_id = 1 exists in sample data.

-- 1. Full vehicle inventory (the original assignment's basic vehicle query).
SELECT vehicle_id, registration_no, vehicle_type, brand, model,
       manufacturing_year, color, status
FROM VEHICLE
ORDER BY vehicle_id;

-- 2. Vehicle details with current recorded ownership, including unowned vehicles.
SELECT v.vehicle_id, v.registration_no, v.vehicle_type, v.brand, v.model,
       vo.customer_id, c.name AS owner_name, vo.start_date, vo.end_date
FROM VEHICLE v
LEFT JOIN VEHICLE_OWNERSHIP vo ON vo.vehicle_id = v.vehicle_id
LEFT JOIN CUSTOMER4 c ON c.customer_id = vo.customer_id
ORDER BY v.vehicle_id, vo.customer_id;

-- 3. Ownership history for a vehicle.
SELECT vo.vehicle_id, vo.customer_id, c.name AS owner_name,
       vo.start_date, vo.end_date
FROM VEHICLE_OWNERSHIP vo
JOIN CUSTOMER4 c ON c.customer_id = vo.customer_id
WHERE vo.vehicle_id = 1
ORDER BY vo.start_date;

-- 4. Insurance policies and expiry dates for a vehicle.
SELECT v.vehicle_id, v.registration_no, i.policy_no,
       i.insurance_company, i.start_date, i.expiry_date, i.premium
FROM VEHICLE v
LEFT JOIN INSURANCE i ON i.vehicle_id = v.vehicle_id
WHERE v.vehicle_id = 1
ORDER BY i.expiry_date;

-- 5. Warranty coverage for a vehicle.
SELECT v.vehicle_id, v.registration_no, w.warranty_provider,
       w.warranty_type, w.start_date, w.expiry_date,
       w.coverage_details, w.warranty_status
FROM VEHICLE v
LEFT JOIN WARRANTY w ON w.vehicle_id = v.vehicle_id
WHERE v.vehicle_id = 1
ORDER BY w.expiry_date;

-- 6. Accident history with associated driver details.
SELECT a.accident_id, a.vehicle_id, a.accident_date, a.location,
       a.description, a.damage_cost, d.driver_id, d.name AS driver_name
FROM ACCIDENT a
LEFT JOIN DRIVER d ON d.driver_id = a.driver_id
WHERE a.vehicle_id = 1
ORDER BY a.accident_date;

-- 7. Maintenance history and service-center details.
SELECT m.maintenance_id, m.vehicle_id, m.service_date,
       m.service_type, m.description, m.cost, m.next_service_date,
       sc.center_name, sc.location AS service_center_location
FROM MAINTENANCE m
JOIN SERVICE_CENTER sc ON sc.service_center_id = m.service_center_id
WHERE m.vehicle_id = 1
ORDER BY m.service_date;

-- 8. Payments associated with maintenance for a vehicle.
SELECT p.payment_id, p.vehicle_id, p.maintenance_id,
       m.service_date, p.payment_date, p.amount,
       p.payment_method, p.payment_status
FROM PAYMENT p
JOIN MAINTENANCE m ON m.maintenance_id = p.maintenance_id
WHERE p.vehicle_id = 1
ORDER BY p.payment_date;

-- 9. Trips with driver information.
SELECT t.trip_id, t.vehicle_id, t.start_location, t.destination,
       t.start_time, t.end_time, t.distance,
       d.driver_id, d.name AS driver_name, d.license_no
FROM TRIP t
LEFT JOIN DRIVER d ON d.driver_id = t.driver_id
WHERE t.vehicle_id = 1
ORDER BY t.start_time;

-- 10. Fuel purchase and odometer records for a vehicle.
SELECT fuel_id, vehicle_id, fuel_date, fuel_type,
       quantity, amount, odometer_reading
FROM FUEL_RECORD
WHERE vehicle_id = 1
ORDER BY fuel_date;

-- 11. Bike subtype details.
SELECT v.vehicle_id, v.registration_no, v.brand, v.model,
       b.mileage, b.fuel_capacity, b.bike_type, b.engine_cc
FROM VEHICLE v
JOIN BIKE b ON b.vehicle_id = v.vehicle_id
ORDER BY v.vehicle_id;

-- 12. Car subtype details.
SELECT v.vehicle_id, v.registration_no, v.brand, v.model,
       c.mileage, c.fuel_capacity, c.boot_space,
       c.seats, c.horsepower, c.car_type
FROM VEHICLE v
JOIN CAR c ON c.vehicle_id = v.vehicle_id
ORDER BY v.vehicle_id;

-- 13. Truck subtype details.
SELECT v.vehicle_id, v.registration_no, v.brand, v.model,
       tr.mileage, tr.fuel_capacity, tr.capacity, tr.volume, tr.truck_type
FROM VEHICLE v
JOIN TRUCK tr ON tr.vehicle_id = v.vehicle_id
ORDER BY v.vehicle_id;

-- 14. Bus subtype details.
SELECT v.vehicle_id, v.registration_no, v.brand, v.model,
       b.mileage, b.fuel_capacity, b.seats, b.bus_type
FROM VEHICLE v
JOIN BUS b ON b.vehicle_id = v.vehicle_id
ORDER BY v.vehicle_id;

-- 15. Vehicle totals by declared type.
SELECT vehicle_type, COUNT(*) AS vehicle_count
FROM VEHICLE
GROUP BY vehicle_type
ORDER BY vehicle_type;

-- 16. Drivers and their licenses.
SELECT driver_id, name, phone, license_no, license_expiry, address
FROM DRIVER
ORDER BY driver_id;

-- 17. Customers in the vehicle project's CUSTOMER4 table.
SELECT customer_id, name, phone, email, address, license_no
FROM CUSTOMER4
ORDER BY customer_id;

-- 18. Service centers.
SELECT service_center_id, center_name, location, phone
FROM SERVICE_CENTER
ORDER BY service_center_id;