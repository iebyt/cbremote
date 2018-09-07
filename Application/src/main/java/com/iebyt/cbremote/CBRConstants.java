package com.iebyt.cbremote;

public class CBRConstants {

    public final static String ACTION_MSG_PROGRESS = "com.iebyt.cbremote.ACTION_MSG_PROGRESS";
    public final static String ACTION_MSG_DELAY = "com.iebyt.cbremote.ACTION_MSG_DELAY";
    public final static String ACTION_MSG_SHUTTER_BUTTON_CLICK = "com.iebyt.cbremote.ACTION_MSG_SHUTTER_BUTTON_CLICK";
    public final static String ACTION_GATT_CONNECTED = "com.iebyt.cbremote.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "com.iebyt.cbremote.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.iebyt.cbremote.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE = "com.iebyt.cbremote.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA = "com.iebyt.cbremote.bluetooth.le.EXTRA_DATA";

    public static class CBRModes{
        public static final String VIDEO="Video";
        public static final String ONE="One";
        public static final String REPEAT="Repeat";
        public static final String BULB="Bulb";
    }

    public static class CBRShutter{
        public static final int MANUAL=-111;
        public static final int AUTO=-222;
    }
}
