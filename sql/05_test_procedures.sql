SET SERVEROUTPUT ON SIZE UNLIMITED;

-- Vehicle 1 exists and has an owner, policy, warranty, accident,
-- maintenance/payment, trip/fuel entry, and BIKE subtype row.
BEGIN
    GET_VEHICLE_REPORT(1);
END;
/

BEGIN
    GET_MAINTENANCE_PAYMENT(1);
END;
/

BEGIN
    GET_TRIP_FUEL_REPORT(1);
END;
/

BEGIN
    GET_VEHICLE_TYPE_REPORT(1);
END;
/

-- Exercise other subtype branches with valid sample IDs.
BEGIN
    GET_VEHICLE_TYPE_REPORT(5);
    GET_VEHICLE_TYPE_REPORT(9);
    GET_VEHICLE_TYPE_REPORT(14);
END;
/

-- Optional not-found checks; these should print messages and not raise errors.
BEGIN
    GET_VEHICLE_REPORT(999);
    GET_MAINTENANCE_PAYMENT(999);
    GET_TRIP_FUEL_REPORT(999);
    GET_VEHICLE_TYPE_REPORT(999);
END;
/