package com.csc301.util;

public class Validator {
    public static boolean isValidEmail(String email) {
        return email != null && email.contains("@") && email.contains(".");
    }

    public static boolean isNotEmpty(String str) {
        return str != null && !str.trim().isEmpty();
    }

    public static boolean isValidPrice(float price) {
        return price >= 0;
    }

    public static boolean isValidQuantity(int quantity) {
        return quantity >= 0;
    }
}
