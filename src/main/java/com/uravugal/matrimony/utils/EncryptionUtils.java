package com.uravugal.matrimony.utils;

import java.util.Base64;

public class EncryptionUtils {
    public static String encryptPin(String pin) {
        return Base64.getEncoder().encodeToString(pin.getBytes());
    }

    public static String decryptPin(String encryptedPin) {
        return new String(Base64.getDecoder().decode(encryptedPin));
    }
}
