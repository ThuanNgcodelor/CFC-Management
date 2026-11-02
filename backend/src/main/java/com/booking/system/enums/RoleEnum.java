package com.booking.system.enums;

public enum RoleEnum {
    ADMIN,
    MANAGER,
    EMPLOYEE,
    HR_STAFF;

    public boolean canLogin() {
        return this == ADMIN || this == MANAGER || this == HR_STAFF;
    }
}
