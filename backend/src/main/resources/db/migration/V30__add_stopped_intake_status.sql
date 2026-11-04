ALTER TABLE hr_employees
    DROP CONSTRAINT ck_hr_employee_status;

ALTER TABLE hr_employees
    ADD CONSTRAINT ck_hr_employee_status
        CHECK (employment_status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'INTAKE_STOPPED'));

ALTER TABLE hr_monthly_roster_items
    DROP CONSTRAINT ck_hr_roster_item_status;

ALTER TABLE hr_monthly_roster_items
    ADD CONSTRAINT ck_hr_roster_item_status
        CHECK (employment_status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'INTAKE_STOPPED'));
