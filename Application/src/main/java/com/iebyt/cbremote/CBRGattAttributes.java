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

    static {
        // Sample Services.
        attributes.put("00050000-0000-1000-0000-d8492fffa821", "Device Control Service");
        // Sample Characteristics.
        attributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Shutter Control");
        attributes.put("00050002-0000-1000-0000-d8492fffa821", "Device Pairing Control");

        //attributes.put("00050000-0000-1000-0000-d8492fffa821", "Manufacturer Name String");

    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
