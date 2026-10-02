package com.finova.common.domain;

/** Platform roles. Authorisation is always resolved from the JWT claim. */
public enum Role {
    CUSTOMER,
    ADMIN;

    public static final String CUSTOMER_AUTHORITY = "ROLE_CUSTOMER";
    public static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    public String authority() {
        return "ROLE_" + name();
    }
}
