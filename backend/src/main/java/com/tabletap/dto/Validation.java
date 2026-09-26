package com.tabletap.dto;

/** Shared validation rules. bcrypt only uses the first 72 bytes, hence the max. */
public final class Validation {
    private Validation() {}

    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{10,72}$";
    public static final String PASSWORD_MSG = "must be 10-72 characters and include a letter and a number";
}
