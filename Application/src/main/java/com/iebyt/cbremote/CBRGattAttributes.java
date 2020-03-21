package com.iebyt.cbremote;

import java.util.HashMap;

/**
 * TODO modify or remove this class
 */
public class CBRGattAttributes {
    private static HashMap<String, String> attributes = new HashMap<>();
    public static String CANON_BLUETOOTH_REMOTE_SERVICE = "00050000-0000-1000-0000-d8492fffa821";
    public static String CANON_PAIRING_SERVICE = "00050002-0000-1000-0000-d8492fffa821";
    public static String CANON_SHUTTER_CONTROL_SERVICE = "00050003-0000-1000-0000-d8492fffa821";

    public static String CANON_BLUETOOTH_REMOTE_SERVICE_PHONE_1 = "00010000-0000-1000-0000-d8492fffa821";
    public static String CANON_BLUETOOTH_REMOTE_SERVICE_PHONE_2 = "00020000-0000-1000-0000-d8492fffa821";
    public static String CANON_SHUTTER_CONTROL_SERVICE_PHONE_3 = "00030000-0000-1000-0000-d8492fffa821";

    public static String CANON_PAIRING_CHARACTERISTIC_0 = "00010006-0000-1000-0000-d8492fffa821";
    public static String CANON_PAIRING_DESCRIPTOR = "00002902-0000-1000-8000-00805f9b34fb";
    public static String CANON_PAIRING_CHARACTERISTIC_1 = "0001000a-0000-1000-0000-d8492fffa821";
    public static String CANON_PAIRING_SERVICE_PHONE_2 = "00020002-0000-1000-0000-d8492fffa821";
    public static String CANON_PAIRING_CHARACTERISTIC_CHECK_IF_PAIRED = "00020004-0000-1000-0000-d8492fffa821";

    public static String CANON_SHUTTER_CONTROL_CHARACTERISTIC = "00030030-0000-1000-0000-d8492fffa821";

    static {
        // Sample Services.
        attributes.put("00050000-0000-1000-0000-d8492fffa821", "Device Control Service");
        // Sample Characteristics.
        attributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Shutter Control");
        attributes.put("00050002-0000-1000-0000-d8492fffa821", "Device Pairing Control");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
