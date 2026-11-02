-- Run from SQL*Plus as SYSDBA. Creates a new, dedicated app schema in XEPDB1,
-- then installs the existing project tables, sample rows, and procedures.
SET ECHO OFF
SET VERIFY OFF
SET FEEDBACK ON
WHENEVER SQLERROR EXIT SQL.SQLCODE
WHENEVER OSERROR EXIT FAILURE

ACCEPT app_password CHAR PROMPT 'Create a 12-128 character letters-and-digits password for VEHICLE_APP: ' HIDE

ALTER SESSION SET CONTAINER = XEPDB1;

DECLARE
    v_user_count PLS_INTEGER;
    v_password VARCHAR2(128) := '&&app_password';
BEGIN
    IF NOT REGEXP_LIKE(v_password, '^[A-Za-z0-9]{12,128}$') THEN
        RAISE_APPLICATION_ERROR(-20001, 'Password must be 12-128 letters and digits.');
    END IF;

    SELECT COUNT(*)
    INTO v_user_count
    FROM dba_users
    WHERE username = 'VEHICLE_APP';

    IF v_user_count > 0 THEN
        RAISE_APPLICATION_ERROR(-20002, 'VEHICLE_APP already exists; refusing to overwrite it.');
    END IF;

    EXECUTE IMMEDIATE
        'CREATE USER VEHICLE_APP IDENTIFIED BY "' || v_password || '" ' ||
        'DEFAULT TABLESPACE USERS TEMPORARY TABLESPACE TEMP QUOTA UNLIMITED ON USERS';
END;
/

GRANT CREATE SESSION, CREATE TABLE, CREATE PROCEDURE TO VEHICLE_APP;

CONNECT VEHICLE_APP/&&app_password@//localhost:1521/XEPDB1

DECLARE
    v_user VARCHAR2(128) := SYS_CONTEXT('USERENV', 'SESSION_USER');
    v_container VARCHAR2(128) := SYS_CONTEXT('USERENV', 'CON_NAME');
BEGIN
    IF v_user <> 'VEHICLE_APP' OR v_container <> 'XEPDB1' THEN
        RAISE_APPLICATION_ERROR(-20003, 'Installer stopped: connected user/container did not match VEHICLE_APP in XEPDB1.');
    END IF;
END;
/

@@01_create_tables.sql
@@02_insert_data.sql
@@04_procedures.sql
@@05_test_procedures.sql
@@06_validation.sql

EXIT SUCCESS