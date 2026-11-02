-- Continue after VEHICLE_APP was created but the original install stopped
-- before loading tables. This script never creates, drops, or replaces users.
SET ECHO OFF
SET VERIFY OFF
SET FEEDBACK ON
WHENEVER SQLERROR EXIT SQL.SQLCODE
WHENEVER OSERROR EXIT FAILURE

ACCEPT app_password CHAR PROMPT 'Enter the VEHICLE_APP password: ' HIDE
CONNECT VEHICLE_APP/&&app_password@//localhost:1521/XEPDB1

DECLARE
    v_user VARCHAR2(128) := SYS_CONTEXT('USERENV', 'SESSION_USER');
    v_container VARCHAR2(128) := SYS_CONTEXT('USERENV', 'CON_NAME');
    v_table_count PLS_INTEGER;
BEGIN
    IF v_user <> 'VEHICLE_APP' OR v_container <> 'XEPDB1' THEN
        RAISE_APPLICATION_ERROR(-20003, 'Connected user/container is not VEHICLE_APP in XEPDB1.');
    END IF;

    SELECT COUNT(*)
    INTO v_table_count
    FROM user_tables
    WHERE table_name IN (
        'CUSTOMER4', 'DRIVER', 'VEHICLE', 'SERVICE_CENTER', 'BIKE', 'CAR',
        'TRUCK', 'BUS', 'VEHICLE_OWNERSHIP', 'MAINTENANCE', 'PAYMENT', 'TRIP',
        'WARRANTY', 'ACCIDENT', 'INSURANCE', 'FUEL_RECORD'
    );

    IF v_table_count > 0 THEN
        RAISE_APPLICATION_ERROR(-20004, 'Project tables already exist; refusing to duplicate or overwrite them.');
    END IF;
END;
/

@@01_create_tables.sql
@@02_insert_data.sql
@@04_procedures.sql
@@05_test_procedures.sql
@@06_validation.sql

EXIT SUCCESS