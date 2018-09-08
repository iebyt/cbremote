package com.iebyt.cbremote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.iebyt.cbremote.views.DialogBulbHoloActivity;
import com.iebyt.cbremote.views.DialogDelayHoloActivity;
import com.iebyt.cbremote.views.DialogRepeatHoloActivity;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code CBRBluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class CBRDeviceControlActivity extends Activity {
    private final static String TAG = CBRDeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";


    private IBinder mIBinder;

    private TextView mConnectionState;
    private String mDeviceName;
    private String mDeviceAddress;
    private CBRBluetoothLeService mBluetoothLeService;
    private boolean mConnected = false;
    private Button mButtonDelay;
    private Button mButtonShutter;
    private Button mButtonAutoFocus;
    private Button mButtonModeOne;
    private Button mButtonModeRepeat;
    private Button mButtonModeBulb;
    private Button mButtonModeVideo;
    private Button mButtonModePhoto;
    private Button mButtonStop;

    private TextView mTvModeTitle;
    private TextView mTvDelayTitle;
    private TextView mTvProgressTitle;

    private TextView mTvMode;
    private TextView mTvDelay;
    private TextView mTvProgress;


    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mIBinder = service;
            initBluetoothConnection(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };


    private void initBluetoothConnection(IBinder service) {
        mBluetoothLeService = ((CBRBluetoothLeService.LocalBinder) service).getService();
        if (!mBluetoothLeService.initialize()) {
            //Log.d(TAG, "Unable to initialize Bluetooth");
            finish();
        }
        // Automatically connects to the device upon successful start-up initialization.
        //Log.d(TAG, "Service in onServiceConnected");
        if (CBRBluetoothLeService.mConnectionState == CBRBluetoothLeService.STATE_CONNECTED) {
            mConnected = true;
            updateConnectionState(R.string.connected);
            invalidateOptionsMenu();
        } else if (CBRBluetoothLeService.mConnectionState == CBRBluetoothLeService.STATE_CONNECTING) {
            // Do wait
        } else {
            mBluetoothLeService.connect(mDeviceAddress);
        }
    }

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (CBRConstants.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (CBRConstants.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
            } else if (CBRConstants.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                mBluetoothLeService.pairAndConnect();
            }
        }
    };

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra("message");

            final String action = intent.getAction();
            if (CBRConstants.ACTION_MSG_PROGRESS.equals(action)) {
                mTvProgress.setText(message);
            } else if (CBRConstants.ACTION_MSG_DELAY.equals(action)) {
                mTvDelay.setText(message);
            }

            if (CBRConstants.ACTION_MSG_SHUTTER_BUTTON_CLICK.equals(action)) {
                //Log.d("receiver", "Got message: " + message);
                mButtonShutter.setPressed(true);
                final Handler timerHandler = new Handler();
                timerHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mButtonShutter.setPressed(false);
                    }
                }, 200);
            }


        }
    };


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cbr_device_control);

        mButtonDelay = findViewById(R.id.btn_delay);
        mButtonAutoFocus = findViewById(R.id.btn_auto_focus);
        mButtonShutter = findViewById(R.id.btn_shutter);
        mButtonModeOne = findViewById(R.id.btn_mode_one_shot);
        mButtonModeRepeat = findViewById(R.id.btn_mode_repeat);
        mButtonModeBulb = findViewById(R.id.btn_mode_bulb);
        mButtonModeVideo = findViewById(R.id.btn_mode_video);
        mButtonModePhoto = findViewById(R.id.btn_mode_photo);
        mButtonStop = findViewById(R.id.btn_stop);

        mButtonDelay.setEnabled(false);
        mButtonShutter.setEnabled(false);
        mButtonAutoFocus.setEnabled(false);
        mButtonModeOne.setEnabled(false);
        mButtonModeRepeat.setEnabled(false);
        mButtonModeBulb.setEnabled(false);
        mButtonModeVideo.setEnabled(false);
        mButtonModePhoto.setEnabled(false);
        mButtonStop.setVisibility(View.GONE);

        mButtonAutoFocus.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (mBluetoothLeService.currentMode.equals(CBRConstants.CBRModes.VIDEO)) {
                            mBluetoothLeService.signal(CBRBluetoothLeService.SIGNAL_AF_VIDEO);
                        } else {
                            mBluetoothLeService.signal(CBRBluetoothLeService.SIGNAL_AF_IMMEDIATE);
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        if (mBluetoothLeService.currentMode.equals(CBRConstants.CBRModes.VIDEO)) {
                            mBluetoothLeService.signal(CBRBluetoothLeService.SIGNAL_WAKE_VIDEO);
                        } else {
                            mBluetoothLeService.signal(CBRBluetoothLeService.SIGNAL_WAKE_IMMEDIATE);
                        }
                        break;
                }
                return true;

            }
        });

        mTvMode = findViewById(R.id.tv_mode);
        mTvDelay = findViewById(R.id.tv_delay);
        mTvProgress = findViewById(R.id.tv_progress);

        mTvModeTitle = findViewById(R.id.tv_mode_title);
        mTvDelayTitle = findViewById(R.id.tv_delay_title);
        mTvProgressTitle = findViewById(R.id.tv_progress_title);


        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);

        mConnectionState = findViewById(R.id.connection_state);

        try{
            getActionBar().setTitle(mDeviceName);
        }catch (Exception ex){}
        getActionBar().setDisplayHomeAsUpEnabled(true);

        Intent gattServiceIntent = new Intent(this, CBRBluetoothLeService.class);
        gattServiceIntent.putExtra(CBRDeviceControlActivity.EXTRAS_DEVICE_NAME, mDeviceName);
        gattServiceIntent.putExtra(CBRDeviceControlActivity.EXTRAS_DEVICE_ADDRESS, mDeviceAddress);
        bindService(gattServiceIntent, mServiceConnection, 0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(new Intent(this, CBRBluetoothLeService.class));
        } else {
            startService(new Intent(this, CBRBluetoothLeService.class));

        }

        //Log.d(TAG, "Activity created");
    }

    @Override
    protected void onResume() {
        super.onResume();
        setConfigDisplay();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, makeUIUpdateIntentFilter());
        if (mBluetoothLeService != null && CBRBluetoothLeService.mConnectionState == CBRBluetoothLeService.STATE_DISCONNECTED) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            //Log.d(TAG, "Connect request result=" + result);
        }

        CBRBluetoothLeService.isControlActivityVisible = true;
        if (CBRBluetoothLeService.mConnectionState == CBRBluetoothLeService.STATE_CONNECTED) {
            updateConnectionState(R.string.connected);
        } else if (CBRBluetoothLeService.mConnectionState == CBRBluetoothLeService.STATE_CONNECTING) {
            updateConnectionState(R.string.connecting);
            invalidateOptionsMenu();
        } else {
            updateConnectionState(R.string.disconnected);
            invalidateOptionsMenu();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        CBRBluetoothLeService.isControlActivityVisible = false;
        unregisterReceiver(mGattUpdateReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.device_control, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_connect:
                mConnectionState.setText(R.string.connecting);
                if (mBluetoothLeService == null) {
                    initBluetoothConnection(mIBinder);
                    return true;
                }
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                warnDisconnect(false);
                return true;
            case android.R.id.home:
                warnDisconnect(true);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
                if (resourceId == R.string.connected) {
                    mConnectionState.setTextColor(Color.parseColor("#FFFFFF"));
                    mButtonDelay.setEnabled(true);
                    mButtonShutter.setEnabled(true);
                    mButtonAutoFocus.setEnabled(true);
                    mButtonModeOne.setEnabled(true);
                    mButtonModeRepeat.setEnabled(true);
                    mButtonModeBulb.setEnabled(true);
                    mButtonModeVideo.setEnabled(true);
                    mButtonModePhoto.setEnabled(true);
                } else {
                    mConnectionState.setTextColor(Color.parseColor("#aa0000"));
                    mButtonDelay.setEnabled(false);
                    mButtonShutter.setEnabled(false);
                    mButtonAutoFocus.setEnabled(false);
                    mButtonModeOne.setEnabled(false);
                    mButtonModeRepeat.setEnabled(false);
                    mButtonModeBulb.setEnabled(false);
                    mButtonModeVideo.setEnabled(false);
                    mButtonModePhoto.setEnabled(false);
                }
            }
        });
    }


    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.


    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(CBRConstants.ACTION_GATT_CONNECTED);
        intentFilter.addAction(CBRConstants.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(CBRConstants.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(CBRConstants.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    private static IntentFilter makeUIUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(CBRConstants.ACTION_MSG_DELAY);
        intentFilter.addAction(CBRConstants.ACTION_MSG_PROGRESS);
        intentFilter.addAction(CBRConstants.ACTION_MSG_SHUTTER_BUTTON_CLICK);
        return intentFilter;
    }

    // Method called when shutter button is clicked
    public void shutterClick(View v) {
        mBluetoothLeService.clickShutter();
        if (mBluetoothLeService.currentMode.equals(CBRConstants.CBRModes.BULB) ||
                mBluetoothLeService.currentMode.equals(CBRConstants.CBRModes.VIDEO)) {

            if(mButtonStop.getVisibility()==View.VISIBLE){
                mButtonStop.setVisibility(View.GONE);
                mButtonDelay.setVisibility(View.VISIBLE);
            }else{
                mButtonDelay.setVisibility(View.GONE);
                mButtonStop.setVisibility(View.VISIBLE);
            }
        }
    }

    // Method called when stop button is clicked
    public void stopClick(View v) {
        mBluetoothLeService.stopTasksNow();
        mButtonStop.setVisibility(View.GONE);
        mButtonDelay.setVisibility(View.VISIBLE);

    }

    // Method called when Delay button is clicked
    public void showDelayDialog(View v) {
        startActivity(new Intent(this, DialogDelayHoloActivity.class));
    }

    // Method called when Photo, Video, One, Repeat or Bulb button is clicked
    public void setMode(View v) {

        switch (v.getId()) {
            case R.id.btn_mode_video:
                mBluetoothLeService.currentMode = CBRConstants.CBRModes.VIDEO;
                break;
            case R.id.btn_mode_photo:
                mBluetoothLeService.currentMode = mBluetoothLeService.lastPhotoMode;
                break;
            case R.id.btn_mode_one_shot:
                mBluetoothLeService.currentMode = CBRConstants.CBRModes.ONE;
                mBluetoothLeService.lastPhotoMode = CBRConstants.CBRModes.ONE;
                break;
            case R.id.btn_mode_repeat:
                mBluetoothLeService.currentMode = CBRConstants.CBRModes.REPEAT;
                mBluetoothLeService.lastPhotoMode = CBRConstants.CBRModes.REPEAT;
                startActivity(new Intent(this, DialogRepeatHoloActivity.class));
                break;
            case R.id.btn_mode_bulb:
                mBluetoothLeService.currentMode = CBRConstants.CBRModes.BULB;
                mBluetoothLeService.lastPhotoMode = CBRConstants.CBRModes.BULB;
                startActivity(new Intent(this, DialogBulbHoloActivity.class));
                break;
            default:
                break;
        }
        if (mBluetoothLeService.currentMode.equals(mBluetoothLeService.previousMode)) {
            return;
        }
        mBluetoothLeService.previousMode=mBluetoothLeService.currentMode;
        resetConfig();
        setConfigDisplay();
    }


    private void resetConfig() {
        //reset all previous mode configs
        CBRBluetoothLeService.currentProgressValue = 0;
        mBluetoothLeService.sigLockShutter = false;
        CBRBluetoothLeService.setDelayValue(0);
        CBRBluetoothLeService.setBulbConfig(false, 0);
        CBRBluetoothLeService.setIntervalValue(0);
        CBRBluetoothLeService.setRepeatCountValue(0);

        switch (mBluetoothLeService.currentMode) {
            case CBRConstants.CBRModes.ONE:
                CBRBluetoothLeService.delay = "0s";
                CBRBluetoothLeService.progress = "0";
                //TODO reset config in service
                break;
            case CBRConstants.CBRModes.REPEAT:
                CBRBluetoothLeService.delay = "0s";
                CBRBluetoothLeService.progress = "0/0";
                //TODO reset config in service
                break;
            case CBRConstants.CBRModes.BULB:
                CBRBluetoothLeService.delay = "Manual";
                CBRBluetoothLeService.progress = "0s";
                CBRBluetoothLeService.isBulbAuto = false;
                CBRBluetoothLeService.bulbTimer = 0;
                break;
            case CBRConstants.CBRModes.VIDEO:
                CBRBluetoothLeService.delay = "Manual";
                CBRBluetoothLeService.progress = "0s";
                CBRBluetoothLeService.isBulbAuto = false;
                CBRBluetoothLeService.bulbTimer = 0;
                //TODO reset config in service
                break;
            default:
                break;
        }
    }

    private void setConfigDisplay() {

        mTvMode.setText(CBRConstants.CBRModes.ONE);
        mTvDelay.setText(CBRBluetoothLeService.delay);
        mTvProgress.setText(CBRBluetoothLeService.progress);

        mButtonShutter.setBackgroundResource(R.drawable.center_btn_shutter_photo);
        mButtonShutter.setText("");
        mButtonModePhoto.setBackgroundResource(R.drawable.btn_bg_active);
        mButtonModeVideo.setBackgroundResource(R.drawable.btn_bg_inactive);
        mButtonModeRepeat.setVisibility(View.VISIBLE);
        mButtonModeBulb.setVisibility(View.VISIBLE);
        mButtonModeOne.setVisibility(View.VISIBLE);
        mButtonDelay.setEnabled(false);


        if (mBluetoothLeService == null) {
            return;
        }
        mTvMode.setText(mBluetoothLeService.currentMode);

        switch (mBluetoothLeService.currentMode) {
            case CBRConstants.CBRModes.ONE:
                mButtonModeRepeat.setBackgroundResource(R.drawable.mode_btn_bg_inactive);
                mButtonModeBulb.setBackgroundResource(R.drawable.mode_btn_bg_inactive);
                mButtonModeOne.setBackgroundResource(R.drawable.mode_btn_bg_active);
                mTvDelayTitle.setText(R.string.label_delay);
                mButtonDelay.setEnabled(true);
                break;
            case CBRConstants.CBRModes.REPEAT:
                mButtonModeOne.setBackgroundResource(R.drawable.mode_btn_bg_inactive);
                mButtonModeBulb.setBackgroundResource(R.drawable.mode_btn_bg_inactive);
                mButtonModeRepeat.setBackgroundResource(R.drawable.mode_btn_bg_active);
                mTvDelayTitle.setText(R.string.label_interval);
                break;
            case CBRConstants.CBRModes.BULB:
                mButtonModeOne.setBackgroundResource(R.drawable.mode_btn_bg_inactive);
                mButtonModeRepeat.setBackgroundResource(R.drawable.mode_btn_bg_inactive);
                mButtonModeBulb.setBackgroundResource(R.drawable.mode_btn_bg_active);
                mTvDelayTitle.setText(R.string.label_shutter);
                break;
            case CBRConstants.CBRModes.VIDEO:
                mButtonShutter.setBackgroundResource(R.drawable.center_btn_shutter_video);
                mButtonModePhoto.setBackgroundResource(R.drawable.btn_bg_inactive);
                mButtonModeVideo.setBackgroundResource(R.drawable.btn_bg_active);
                mButtonModeRepeat.setVisibility(View.INVISIBLE);
                mButtonModeBulb.setVisibility(View.INVISIBLE);
                mButtonModeOne.setVisibility(View.INVISIBLE);
                mTvDelayTitle.setText(R.string.label_shutter);
                break;
            default:
                break;
        }
    }

    @Override
    public void onBackPressed() {
        warnDisconnect(true);
    }

    private void warnDisconnect(final boolean goBack) {

        if (CBRBluetoothLeService.mConnectionState == CBRBluetoothLeService.STATE_DISCONNECTED) {
            CBRBluetoothLeService.isControlActivityVisible = false;
            mBluetoothLeService.tryServiceStopSelf();
            finish();
            return;
        }

        new AlertDialog.Builder(this)
                .setMessage(R.string.bluetooth_disconnect_message)
                .setPositiveButton(R.string.bluetooth_disconnect_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (goBack) {
                            CBRBluetoothLeService.isControlActivityVisible = false;
                        }
                        mBluetoothLeService.disconnect();
                        dialogInterface.dismiss();
                        if (goBack) {
                            finish();
                        }
                    }
                })
                .setNegativeButton(R.string.dialogs_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                    }
                })
                .show();
    }

}
