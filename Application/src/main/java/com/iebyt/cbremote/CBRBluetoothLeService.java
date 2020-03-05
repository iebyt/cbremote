package com.iebyt.cbremote;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.session.MediaSession;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.media.VolumeProviderCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.KeyEvent;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static android.os.SystemClock.sleep;
import static com.iebyt.cbremote.CBRConstants.ACTION_DATA_AVAILABLE;
import static com.iebyt.cbremote.CBRConstants.ACTION_GATT_CONNECTED;
import static com.iebyt.cbremote.CBRConstants.ACTION_GATT_DISCONNECTED;
import static com.iebyt.cbremote.CBRConstants.ACTION_GATT_IS_PAIRED;
import static com.iebyt.cbremote.CBRConstants.ACTION_GATT_PAIRING_FIRST_PART;
import static com.iebyt.cbremote.CBRConstants.ACTION_GATT_PAIRING_SECOND_PART;
import static com.iebyt.cbremote.CBRConstants.ACTION_GATT_SERVICES_DISCOVERED;
import static com.iebyt.cbremote.CBRConstants.EXTRA_DATA;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class CBRBluetoothLeService extends Service {

    private final static String CHANNEL_ID = "CBRChannelID";
    private final static String ACTION_DISCONNECT = "DISCONNECT";
    private final static String ACTION_DISCONNECT_AND_STOP_FOREGROUND = "DISCONNECT_AND_STOP_FOREGROUND ";
    private final static String ACTION_REPEAT = "REPEAT";
    private final static String ACTION_STOP_SELF = "STOP_SELF";
    private final static String TAG = CBRBluetoothLeService.class.getSimpleName();

    public final static int STATE_DISCONNECTED = 0;
    public final static int STATE_CONNECTING = 1;
    public final static int STATE_CONNECTED = 2;

    public final static int microDelay = 200;
    public final static int CAMERA_SLEEP_TIME = 118;

    public final static int SIGNAL_WAKE_IMMEDIATE = 12;
    public final static int SIGNAL_WAKE_VIDEO = 8;
    public final static int SIGNAL_AF_IMMEDIATE = 76;
    public final static int SIGNAL_AF_VIDEO = 72;
    public final static int SIGNAL_ONE_SHUTTER = 140;
    public final static int SIGNAL_VIDEO_SHUTTER = 136;
    public final static int SIGNAL_T_IMMEDIATE = 44;
    public final static int SIGNAL_W_IMMEDIATE = 28;
    public final static int SIGNAL_T_VIDEO = 40;
    public final static int SIGNAL_W_VIDEO = 24;

    public final static UUID UUID_CANON_REMOTE_SERVICE =
            UUID.fromString(CBRGattAttributes.CANON_BLUETOOTH_REMOTE_SERVICE);

    //Task configs
    public String currentMode = CBRConstants.CBRModes.ONE;
    public String previousMode = CBRConstants.CBRModes.ONE;
    public String lastPhotoMode = CBRConstants.CBRModes.ONE;
    public static String delay = "0s";
    public static String progress = "0/0";

    public static int currentProgressValue = 0;
    public boolean sigLockShutter = false;
    private static int delayValue = 0;
    private static int repeatCountValue = 0;
    private long timeElapsed = 0;
    public static boolean isBulbAuto = false;
    public static int bulbTimer = 0;
    public boolean flagStop = false;

    private Context mContext;
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    public static String currentDeviceName;
    public static String currentDeviceAddress;
    public BluetoothGatt mBluetoothGatt;

    public static int mConnectionState = STATE_DISCONNECTED;
    public static boolean isControlActivityVisible = false;

    private final Handler handler1Shutter = new Handler();
    public PowerManager powerManager;
    public PowerManager.WakeLock wakeLock;
    private MediaSessionCompat ms;

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            //Log.i(TAG, "Connection state changed");
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                //Log.i(TAG, "Connected to GATT server.");

                // Attempts to discover services after successful connection.
                //Log.i(TAG, "Attempting to start service discovery:");
                mBluetoothGatt.discoverServices();
                startCBRForegroundService();


            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                //Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);

                if (isControlActivityVisible == false) {
                    stopForegroundService();
                    stopSelf();
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);

            } else {
                //Log.i(TAG, "onServicesDiscovered received: " + status);
            }
        }

        private final byte checkSum(byte[] bytes) {
            byte sum = 0;
            for (byte b : bytes) {
                sum ^= b;
            }
            return sum;
        }
        @Override
        public void onCharacteristicRead(
                BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status
        ) {
            if (UUID.fromString(CBRGattAttributes.CANON_PAIRING_CHARACTERISTIC_CHECK_IF_PAIRED).equals(characteristic.getUuid()))
            {
                byte[] data = characteristic.getValue();

                int sum = checkSum(data);
                if(checkSum(data) == 0)
                {
                    broadcastUpdate(ACTION_GATT_PAIRING_FIRST_PART, characteristic);
                }
                else
                {
                    broadcastUpdate(ACTION_GATT_IS_PAIRED, characteristic);
                }
                return;

            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(
                BluetoothGatt gatt, BluetoothGattCharacteristic characteristic
        ) {
            if (UUID.fromString(CBRGattAttributes.CANON_PAIRING_CHARACTERISTIC_0).equals(characteristic.getUuid()))
            {
                broadcastUpdate(ACTION_GATT_PAIRING_SECOND_PART , characteristic);
                return;
            }

            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };


    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        if (UUID_CANON_REMOTE_SERVICE.equals(characteristic.getUuid())) {
            int flag = characteristic.getProperties();
            int format = -1;
            if ((flag & 0x01) != 0) {
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
            }
            final int heartRate = characteristic.getIntValue(format, 1);
            intent.putExtra(EXTRA_DATA, String.valueOf(heartRate));
        } else {
            // For all other profiles, writes the data formatted in HEX.
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for (byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
            }
        }
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        CBRBluetoothLeService getService() {
            return CBRBluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        currentDeviceName = intent.getStringExtra(CBRDeviceControlActivity.EXTRAS_DEVICE_NAME);
        currentDeviceAddress = intent.getStringExtra(CBRDeviceControlActivity.EXTRAS_DEVICE_ADDRESS);
        //Log.i(TAG, "Service Bound");
        startService(new Intent(this, CBRBluetoothLeService.class));
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        //close();  //use this to disconnect bluetooth on unbind
        //Log.e(TAG, "Service Unbound");
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                //Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            //Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            //Log.e(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            //Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection."+mBluetoothDeviceAddress);
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            //Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        //Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    public void pairAndConnectFirstPart()
    {
        Handler mHandler = new Handler();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {

                mConnectionState = STATE_CONNECTING;
                sleep(1000);
                boolean result = pairAndConnectByStep(1);
                sleep(1000);
                result = pairAndConnectByStep(2);
                sleep(1000);
                result = pairAndConnectByStep(3);

            }
        }, 1000);
    }


    public void pairAndConnectSecondPart()
    {
        Handler mHandler = new Handler();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {

                boolean result = pairAndConnectByStep(4);
                sleep(1000);
                result = pairAndConnectByStep(5);
                sleep(1000);
                result = pairAndConnectByStep(6);
                sleep(1000);
                result = pairAndConnectByStep(7);
                sleep(1000);

                //mConnectionState = STATE_CONNECTED;

            }
        }, 1000);
    }



    public boolean pairAndConnectByStep( int nbStep)
    {
        final int internNbStep = nbStep;

        BluetoothGattService pairingService1 = mBluetoothGatt.getService(UUID.fromString(CBRGattAttributes.CANON_BLUETOOTH_REMOTE_SERVICE_PHONE_1));
        BluetoothGattService pairingService2 = mBluetoothGatt.getService(UUID.fromString(CBRGattAttributes.CANON_BLUETOOTH_REMOTE_SERVICE_PHONE_2));
        if (pairingService1 == null || pairingService2 == null)  return false;

        BluetoothGattCharacteristic mWriteCharacteristic ;

        byte[] controlChar = new byte[1];

        byte[] name = android.os.Build.MODEL.getBytes(StandardCharsets.US_ASCII);

        controlChar[0] = 0x01;
        byte[] value = new byte[controlChar.length + name.length];
        System.arraycopy(controlChar, 0, value, 0, controlChar.length);
        System.arraycopy(name, 0, value, controlChar.length, name.length);

        switch(internNbStep){
            case 1 :

                mWriteCharacteristic = pairingService1.getCharacteristic(UUID.fromString(CBRGattAttributes.CANON_PAIRING_CHARACTERISTIC_0));
                BluetoothGattDescriptor mWriteCharacteristicDescriptor= mWriteCharacteristic.getDescriptor(UUID.fromString(CBRGattAttributes.CANON_PAIRING_DESCRIPTOR));

                if(!mBluetoothGatt.readDescriptor(mWriteCharacteristicDescriptor))return false;
                if(!mBluetoothGatt.setCharacteristicNotification(mWriteCharacteristic, true))return false;
                break;


            case 2 :
                mWriteCharacteristic = pairingService1.getCharacteristic(UUID.fromString(CBRGattAttributes.CANON_PAIRING_CHARACTERISTIC_0));
                BluetoothGattDescriptor clientConfig = mWriteCharacteristic.getDescriptor(UUID.fromString(CBRGattAttributes.CANON_PAIRING_DESCRIPTOR));

                if(!clientConfig.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) return false;
                if(!mBluetoothGatt.writeDescriptor(clientConfig)) return false;
                break;

            case 3 :
                mWriteCharacteristic = pairingService1.getCharacteristic(UUID.fromString(CBRGattAttributes.CANON_PAIRING_CHARACTERISTIC_0));
                mWriteCharacteristic.setValue(value);

                if (!mBluetoothGatt.writeCharacteristic(mWriteCharacteristic))return false;
                break;

            case 4 :
                value[0] = 0x04;

                mWriteCharacteristic =  pairingService1.getCharacteristic(UUID.fromString(CBRGattAttributes.CANON_PAIRING_CHARACTERISTIC_1));
                mWriteCharacteristic.setValue(value);

                if (!mBluetoothGatt.writeCharacteristic(mWriteCharacteristic))return false;
                break;

            case 5 :

                byte[] control2Chars = new byte[2];
                control2Chars[0] = 0x05;
                control2Chars[1] = 0x02;

                mWriteCharacteristic =  pairingService1.getCharacteristic(UUID.fromString(CBRGattAttributes.CANON_PAIRING_CHARACTERISTIC_1));
                mWriteCharacteristic.setValue(control2Chars);

                if (!mBluetoothGatt.writeCharacteristic(mWriteCharacteristic)) return false;
                break;

            case 6 :
                controlChar[0] = 0x0a;

                mWriteCharacteristic =  pairingService2.getCharacteristic(UUID.fromString(CBRGattAttributes.CANON_PAIRING_SERVICE_PHONE_2));
                mWriteCharacteristic.setValue(controlChar);

                if (!mBluetoothGatt.writeCharacteristic(mWriteCharacteristic)) return false;


                break;

            case 7 :
                controlChar[0] = 0x01;

                mWriteCharacteristic =  pairingService1.getCharacteristic(UUID.fromString(CBRGattAttributes.CANON_PAIRING_CHARACTERISTIC_1));
                mWriteCharacteristic.setValue(controlChar);

                if (!mBluetoothGatt.writeCharacteristic(mWriteCharacteristic)) return false;
                CBRMainActivity.sharedpreferences = getSharedPreferences(mBluetoothDeviceAddress, Context.MODE_PRIVATE);


                broadcastUpdate(ACTION_GATT_CONNECTED);



                break;
        }

        return true;
    }



    public void CheckIfPaired() {


        BluetoothGattService pairingService = mBluetoothGatt.getService(UUID.fromString(CBRGattAttributes.CANON_BLUETOOTH_REMOTE_SERVICE_PHONE_2));

        BluetoothGattCharacteristic mReadCharacteristic = pairingService.getCharacteristic(UUID.fromString(CBRGattAttributes.CANON_PAIRING_CHARACTERISTIC_CHECK_IF_PAIRED));

        mBluetoothGatt.readCharacteristic(mReadCharacteristic);


    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            //Log.e(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }


    // Managing service state


    @Override
    public void onCreate() {
        super.onCreate();
        //Log.i(TAG, "Service created");
        createNotificationChannel();

        mContext = getApplicationContext();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_DISCONNECT);
        filter.addAction(ACTION_DISCONNECT_AND_STOP_FOREGROUND);
        registerReceiver(receiver, filter);

        IntentFilter repeatFilter = new IntentFilter(ACTION_REPEAT);
        repeatFilter.addAction(ACTION_STOP_SELF);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(receiver, repeatFilter);


        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                TAG + "::CBRWakelockTag");


    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startCBRForegroundService();
        setMediaSession();
        //Log.d(TAG, "Service start command");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        //Log.d(TAG, "Service destroyed");
        try {
            disconnect();
            close();
        } catch (Exception ex) {
        }
        ms.release();
        unregisterReceiver(receiver);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(receiver);
        super.onDestroy();
    }

    @Override
    public void onRebind(Intent intent) {
        //Log.d(TAG, "Service rebind");
        super.onRebind(intent);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        //Log.d(TAG, "Service task removed");

        if (!wakeLock.isHeld()) {
            disconnect();
        }

        //TODO move control activity visible set code here
    }


    // manage foreground notifications


    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "CBR Remote";
            String description = "Channel for CB Remote";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.setSound(null, null);
            channel.setVibrationPattern(new long[]{0L});
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }


    private void startCBRForegroundService() {
        //Log.d(TAG, "Start foreground service.");

        // Create notification default intent.
        Intent intent = new Intent(this, CBRMainActivity.class);
        intent.putExtra(CBRDeviceControlActivity.EXTRAS_DEVICE_NAME, currentDeviceName);
        intent.putExtra(CBRDeviceControlActivity.EXTRAS_DEVICE_ADDRESS, currentDeviceAddress);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        // intent to disconnect
        Intent disconnectIntent = new Intent(ACTION_DISCONNECT_AND_STOP_FOREGROUND);
        PendingIntent disconnectPendingIntent =
                PendingIntent.getBroadcast(this, 1, disconnectIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        // Create notification builder.
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID);
        // Make notification show big text.
        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
        bigTextStyle.setBigContentTitle("CB Remote");
        bigTextStyle.bigText("This service will continue long running tasks in background.");
        builder.setStyle(bigTextStyle);
        builder.addAction(0, "Quit", disconnectPendingIntent);
        builder.setWhen(System.currentTimeMillis());
        builder.setSmallIcon(R.drawable.ic_launcher);
        Bitmap largeIconBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher);
        builder.setLargeIcon(largeIconBitmap);
        builder.setSmallIcon(R.drawable.ic_stat_logo2);
        builder.setContentTitle("CBR");
        builder.setContentText("Connected");
        // Make the notification max priority.
        builder.setPriority(NotificationCompat.PRIORITY_LOW);
        builder.setDefaults(NotificationCompat.DEFAULT_LIGHTS)
                .setVibrate(new long[]{0L})
                .setSound(null);
        // Make head-up notification.
        builder.setContentIntent(pendingIntent);
        Notification notification = builder.build();
        // Start foreground service.
        startForeground(1, notification);
    }

    private void stopForegroundService() {
        //Log.d(TAG, "Stop foreground service.");

        // Stop foreground service and remove the notification.
        stopForeground(true);

        // Stop the foreground service.
        stopSelf();
    }

    public static void setDelayValue(int value) {
        delayValue = value;
        if (value >= 0) {
            delay = Integer.toString(delayValue) + "s";
        } else if (value == CBRConstants.CBRShutter.MANUAL) {
            delay = "Manual";
        } else if (value == CBRConstants.CBRShutter.MANUAL) {
            delay = "Auto";
        } else {
            delay = "Error";
        }
    }

    public static void setIntervalValue(int value) {
        delayValue = value;
        if (delayValue >= 0) {
            delay = Long.toString(delayValue) + "s";
        } else {
            delay = "Error";
        }
    }

    public static void setRepeatCountValue(int value) {
        repeatCountValue = value;
        if (repeatCountValue >= 0) {
            progress = "0/" + Long.toString(repeatCountValue);
        } else {
            progress = "Error";
        }
    }

    public static void setBulbConfig(boolean mode, int bulbDelay) {
        isBulbAuto = mode;
        bulbTimer = bulbDelay;
        if (mode) {
            delay = "Auto";
            progress = "0/" + Integer.toString(bulbDelay) + "s";
        } else {
            delay = "Manual";
            progress = "0s";
        }

    }


    public void clickShutter() {
        switch (currentMode) {
            case CBRConstants.CBRModes.ONE:
                timerShutter(delayValue * 1000, false);
                break;
            case CBRConstants.CBRModes.REPEAT:
                wakeLock.acquire();
                doShutter(0, true,false,0);
                break;
            case CBRConstants.CBRModes.BULB:

                if (isBulbAuto) {
                    wakeLock.acquire();
                    doShutter(delayValue * 1000, true,false,0);
                    delayValue = bulbTimer;
                    currentProgressValue = 0;
                    repeatCountValue = 2;
                } else {
                    doShutter(delayValue * 1000, false,false,0);
                }
                break;
            case CBRConstants.CBRModes.VIDEO:
                if (isBulbAuto) {
                    wakeLock.acquire();
                    doShutter(delayValue * 1000, true,false,0);
                    delayValue = bulbTimer;
                    currentProgressValue = 0;
                    repeatCountValue = 2;
                } else {
                    doShutter(delayValue * 1000, false,false,0);
                }

                break;
            default:
                break;
        }
    }


    private void doShutter(final int delayMilliSec, final boolean repeat, final boolean signal, final int signalValue) {

        if (flagStop == true) {
            handler1Shutter.removeCallbacksAndMessages(null);
            flagStop = false;
        }

        if (sigLockShutter == true) {
            return;
        }
        sigLockShutter = true;
        final BluetoothGattService mCustomService =
                mBluetoothGatt.getService(UUID.fromString(CBRGattAttributes.CANON_SHUTTER_CONTROL_SERVICE_PHONE_3));
        if (mCustomService == null) {
            //Log.w(TAG, "Custom BLE Service not found");
            return;
        }

        //Log.d(TAG, "Firing a shot");
        final BluetoothGattCharacteristic mWriteCharacteristicShutter =
                mCustomService.getCharacteristic(UUID.fromString(CBRGattAttributes.CANON_SHUTTER_CONTROL_CHARACTERISTIC));
        final byte[] controlChar = new byte[2];
        //controlChar[0] = new Integer(3).byteValue();
        controlChar[1] = new Integer(1).byteValue();
        mWriteCharacteristicShutter.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        mWriteCharacteristicShutter.setValue(controlChar);
        if (mBluetoothGatt.writeCharacteristic(mWriteCharacteristicShutter) == false) {
            //Log.e(TAG, "Failed to write characteristic");
        }

        handler1Shutter.postDelayed(new Runnable() {
            @Override
            public void run() {

                if (mCustomService == null) {
                    //Log.w(TAG, "Custom BLE Service not found");
                    sigLockShutter = false;
                    return;
                }

                if (flagStop == true) {
                    //Log.d(TAG, "Task stopped by user");
                    try {
                        wakeLock.release();
                    } catch (Exception ex) {

                    }
                    flagStop = false;
                    return;
                }

                //Log.d(TAG, "trying write");
                controlChar[1] = new Integer(2).byteValue();
                mWriteCharacteristicShutter.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                mWriteCharacteristicShutter.setValue(controlChar);
                if (mBluetoothGatt.writeCharacteristic(mWriteCharacteristicShutter) == false) {
                    //Log.e(TAG, "Failed to write characteristic");
                }
                if (mCustomService == null) {
                    //Log.w(TAG, "Custom BLE Service not found");
                    sigLockShutter = false;
                    return;
                }

                SharedPreferences.Editor editor = CBRMainActivity.sharedpreferences.edit();
                editor.putString("deviceaddress", mBluetoothDeviceAddress);
                editor.commit();

//
//                if (signal) {
//                    mWriteCharacteristicShutter.setValue(signalValue,
//                            BluetoothGattCharacteristic.FORMAT_UINT8, 0);
//                } else if (currentMode.equals(CBRConstants.CBRModes.VIDEO)) {
//                    mWriteCharacteristicShutter.setValue(SIGNAL_VIDEO_SHUTTER,
//                            BluetoothGattCharacteristic.FORMAT_UINT8, 0);
//                } else {
//                    mWriteCharacteristicShutter.setValue(SIGNAL_ONE_SHUTTER,
//                            BluetoothGattCharacteristic.FORMAT_UINT8, 0);
//                }
//
//                if (mBluetoothGatt.writeCharacteristic(mWriteCharacteristicShutter) == true && !signal) {
//                    Intent intentProgress = new Intent(CBRConstants.ACTION_MSG_PROGRESS);
//                    if (currentMode.equals(CBRConstants.CBRModes.REPEAT)) {
//                        intentProgress.putExtra("message",
//                                Integer.toString(currentProgressValue + 1) + "/" + Integer.toString(repeatCountValue));
//                    } else if (currentMode.equals(CBRConstants.CBRModes.ONE)) {
//                        intentProgress.putExtra("message", Integer.toString(currentProgressValue + 1));
//                    }
//                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(intentProgress);
//                    final Handler handler2 = new Handler();
//                    handler2.postDelayed(new Runnable() {
//                        @Override
//                        public void run() {
//                            if (mCustomService == null) {
//                                //Log.w(TAG, "Custom BLE Service not found");
//                                sigLockShutter = false;
//                                return;
//                            }
//                            if (currentMode.equals(CBRConstants.CBRModes.VIDEO)) {
//                                mWriteCharacteristicShutter.setValue(SIGNAL_WAKE_VIDEO,
//                                        BluetoothGattCharacteristic.FORMAT_UINT8, 0);
//                            } else {
//                                mWriteCharacteristicShutter.setValue(SIGNAL_WAKE_IMMEDIATE,
//                                        BluetoothGattCharacteristic.FORMAT_UINT8, 0);
//                            }
//                            if (!mBluetoothGatt.writeCharacteristic(mWriteCharacteristicShutter)) {
//                                //Log.e(TAG, "Failed to write characteristic Shutter");
//                            }
//                            sigLockShutter = false;
//                            currentProgressValue++;
//                            if (repeat) {
//                                boolean toRepeat = false;
//                                if (currentProgressValue < repeatCountValue) {
//                                    toRepeat = true;
//                                }
//                                if (toRepeat) {
//                                    Intent intent = new Intent(ACTION_REPEAT);
//                                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
//                                } else {
//                                    //Log.d(TAG, "Task finished");
//                                    try {
//                                        wakeLock.release();
//                                    } catch (Exception ex) {
//
//                                    }
//                                }
//                            }
//                        }
//                    }, microDelay);
//                } else {
                    //Log.e(TAG, "Failed to write characteristic Shutter");
                    sigLockShutter = false;
                //}
            }
        }, delayMilliSec);

    }


    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ACTION_DISCONNECT)) {
                disconnect();
            }

            if (action.equals(ACTION_DISCONNECT_AND_STOP_FOREGROUND)) {
                disconnect();
                stopForegroundService();
                System.exit(0);
            }

            if (action.equals(ACTION_REPEAT)) {
                timerShutter(((delayValue * 1000) - microDelay), true);
            }

            if (action.equals(ACTION_STOP_SELF)) {
                stopSelf();
            }

        }
    };


    private void timerShutter(final long delayMillis, final boolean repeat) {

        //TODO wake camera from sleep before shot

        //Log.d(TAG, "Shutter Timer called");

        long initialDelay = 1000 - microDelay;

        if (currentMode.equals(CBRConstants.CBRModes.ONE)) {
            initialDelay = delayMillis;
        }

        final Handler timerHandler = new Handler();
        timerHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                timeElapsed = timeElapsed + 1000;
                if (timeElapsed < delayMillis) {

                    Intent intent = new Intent(CBRConstants.ACTION_MSG_DELAY);
                    intent.putExtra("message",
                            Long.toString(timeElapsed / 1000) + "/" + Long.toString(delayValue) + "s");

                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);

                    if (delayValue - (timeElapsed / 1000) <= 5 && delayValue > CAMERA_SLEEP_TIME) {
                        if (currentMode.equals(CBRConstants.CBRModes.VIDEO)) {
                            signal(SIGNAL_WAKE_VIDEO);
                        } else {
                            signal(SIGNAL_WAKE_IMMEDIATE);
                        }
                    }
                    timerHandler.postDelayed(this, 1000);
                } else {
                    Intent intent = new Intent(CBRConstants.ACTION_MSG_DELAY);
                    if (delayValue == 0) {
                        intent.putExtra("message", Long.toString(delayValue) + "s");
                    } else {
                        intent.putExtra("message", "0/" + Long.toString(delayValue) + "s");
                    }
                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);

                    Intent intentS = new Intent(CBRConstants.ACTION_MSG_SHUTTER_BUTTON_CLICK);
                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(intentS);
                    doShutter(5, repeat,false,0);
                    timeElapsed = 0;

                }
            }
        }, initialDelay);


    }


    public void signal(int val) {
        doShutter(0, false,true,val);
    }


    public void stopTasksNow() {
        //stop task only available for bulb and video mode
        flagStop = true;
        doShutter(0, false,false,0);
        final Handler handlerStop = new Handler();
        handlerStop.postDelayed(new Runnable() {
            @Override
            public void run() {
                doShutter(0, false,false,0);
            }
        }, microDelay);

    }


    public void tryServiceStopSelf() {
        Intent intentProgress = new Intent(ACTION_STOP_SELF);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intentProgress);
    }


    private void setMediaSession() {
        Intent disconnectIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        PendingIntent disconnectPendingIntent =
                PendingIntent.getBroadcast(this, 4, disconnectIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        ms = new MediaSessionCompat(getApplicationContext(), getPackageName());
        ms.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        ms.setPlaybackState(new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 0) //you simulate a player which plays something.
                .build());

        //this will only work on Lollipop and up, see https://code.google.com/p/android/issues/detail?id=224134
        VolumeProviderCompat myVolumeProvider =
                new VolumeProviderCompat(VolumeProviderCompat.VOLUME_CONTROL_RELATIVE, /*max volume*/100, /*initial volume level*/50) {
                    @Override
                    public void onAdjustVolume(int direction) {

                        if(direction == 1)
                        {
                            this.setCurrentVolume(this.getCurrentVolume()-1);
                            currentMode = CBRConstants.CBRModes.ONE;
                            clickShutter();
                        }
                        if(direction == -1)
                        {
                            this.setCurrentVolume(this.getCurrentVolume()+1);
                            currentMode = CBRConstants.CBRModes.ONE;
                            clickShutter();
                        }
                    }
                };

        ms.setPlaybackToRemote(myVolumeProvider);
        ms.setActive(true);

        ms.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonIntent) {

                final KeyEvent event = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (event.getKeyCode()) {
                        case KeyEvent.KEYCODE_HEADSETHOOK:
                            clickShutter();
                            break;
                    }
                }
                return super.onMediaButtonEvent(mediaButtonIntent);
            }

        });


        ms.setMediaButtonReceiver(disconnectPendingIntent);
        AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 48000, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
                AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT), AudioTrack.MODE_STREAM);
        audioTrack.play();
        audioTrack.stop();
        audioTrack.release();
    }


}
