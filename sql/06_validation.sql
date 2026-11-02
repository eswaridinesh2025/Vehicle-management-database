-- Confirm expected project tables are present.
SELECT table_name
FROM user_tables
WHERE table_name IN (
    'CUSTOMER4', 'DRIVER', 'VEHICLE', 'SERVICE_CENTER',
    'BIKE', 'CAR', 'TRUCK', 'BUS', 'VEHICLE_OWNERSHIP',
    'MAINTENANCE', 'PAYMENT', 'TRIP', 'WARRANTY', 'ACCIDENT',
    'INSURANCE', 'FUEL_RECORD'
)
ORDER BY 1;

-- Confirm sample data was loaded.
SELECT 'CUSTOMER4' AS table_name, COUNT(*) AS row_count FROM CUSTOMER4
UNION ALL SELECT 'DRIVER', COUNT(*) FROM DRIVER
UNION ALL SELECT 'VEHICLE', COUNT(*) FROM VEHICLE
UNION ALL SELECT 'SERVICE_CENTER', COUNT(*) FROM SERVICE_CENTER
UNION ALL SELECT 'BIKE', COUNT(*) FROM BIKE
UNION ALL SELECT 'CAR', COUNT(*) FROM CAR
UNION ALL SELECT 'TRUCK', COUNT(*) FROM TRUCK
UNION ALL SELECT 'BUS', COUNT(*) FROM BUS
UNION ALL SELECT 'VEHICLE_OWNERSHIP', COUNT(*) FROM VEHICLE_OWNERSHIP
UNION ALL SELECT 'MAINTENANCE', COUNT(*) FROM MAINTENANCE
UNION ALL SELECT 'PAYMENT', COUNT(*) FROM PAYMENT
UNION ALL SELECT 'TRIP', COUNT(*) FROM TRIP
UNION ALL SELECT 'WARRANTY', COUNT(*) FROM WARRANTY
UNION ALL SELECT 'ACCIDENT', COUNT(*) FROM ACCIDENT
UNION ALL SELECT 'INSURANCE', COUNT(*) FROM INSURANCE
UNION ALL SELECT 'FUEL_RECORD', COUNT(*) FROM FUEL_RECORD
ORDER BY table_name;

-- Procedure compilation status.
SELECT object_name, status
FROM user_objects
WHERE object_type = 'PROCEDURE'
  AND object_name IN (
      'GET_VEHICLE_REPORT',
      'GET_MAINTENANCE_PAYMENT',
      'GET_TRIP_FUEL_REPORT',
      'GET_VEHICLE_TYPE_REPORT'
  )
ORDER BY object_name;

-- Compilation errors, if any.
SELECT name, type, line, position, text
FROM user_errors
WHERE name IN (
    'GET_VEHICLE_REPORT',
    'GET_MAINTENANCE_PAYMENT',
    'GET_TRIP_FUEL_REPORT',
    'GET_VEHICLE_TYPE_REPORT'
)
ORDER BY name, sequence;

-- Verify each vehicle's declared type has its matching subtype row.
SELECT v.vehicle_id, v.vehicle_type
FROM VEHICLE v
WHERE (UPPER(v.vehicle_type) = 'BIKE' AND NOT EXISTS
       (SELECT 1 FROM BIKE b WHERE b.vehicle_id = v.vehicle_id))
   OR (UPPER(v.vehicle_type) = 'CAR' AND NOT EXISTS
       (SELECT 1 FROM CAR c WHERE c.vehicle_id = v.vehicle_id))
   OR (UPPER(v.vehicle_type) = 'TRUCK' AND NOT EXISTS
       (SELECT 1 FROM TRUCK t WHERE t.vehicle_id = v.vehicle_id))
   OR (UPPER(v.vehicle_type) = 'BUS' AND NOT EXISTS
       (SELECT 1 FROM BUS b WHERE b.vehicle_id = v.vehicle_id));

-- Verify ownership and payment references do not have orphan rows.
SELECT vo.customer_id, vo.vehicle_id
FROM VEHICLE_OWNERSHIP vo
LEFT JOIN CUSTOMER4 c ON c.customer_id = vo.customer_id
LEFT JOIN VEHICLE v ON v.vehicle_id = vo.vehicle_id
WHERE c.customer_id IS NULL OR v.vehicle_id IS NULL;

SELECT p.payment_id, p.vehicle_id, p.maintenance_id
FROM PAYMENT p
LEFT JOIN VEHICLE v ON v.vehicle_id = p.vehicle_id
LEFT JOIN MAINTENANCE m ON m.maintenance_id = p.maintenance_id
WHERE v.vehicle_id IS NULL OR m.maintenance_id IS NULL;