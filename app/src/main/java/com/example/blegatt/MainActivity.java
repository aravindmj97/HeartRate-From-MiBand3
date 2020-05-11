package com.example.blegatt;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleDevice;

import org.apache.commons.lang3.ArrayUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter mBluetoothAdapter;
    private List<BluetoothDevice> deviceList = new ArrayList<>();
    private boolean mScanning;
    private Handler mHandler;

    private static final int REQUEST_ENABLE_BT = 1;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    RxBleClient rxBleClient;

    private ArrayAdapter<?> genericListAdapter;
    private ArrayList<BluetoothDevice> deviceArrayList;
    private ListView deviceListView;
    private BluetoothGattCallback miBandGattCallBack;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattService variableService;
    private final Object object = new Object();
    private SharedPreferences sharedPreferences;
    private Map<UUID, String> deviceInfoMap;
    private TextView heartRate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rxBleClient = RxBleClient.create(getApplicationContext());

        heartRate = findViewById(R.id.heartData);

        sharedPreferences = getSharedPreferences("MiBandConnectPreferences", Context.MODE_PRIVATE);

        mHandler = new Handler();

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        miBandGattCallBack = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                switch (newState) {
                    case BluetoothGatt.STATE_DISCONNECTED:
                        Log.d("Info", "Device disconnected");

                        break;
                    case BluetoothGatt.STATE_CONNECTED: {
                        Log.d("Info", "Connected with device");
                        Log.d("Info", "Discovering services");
                        gatt.discoverServices();
                    }
                    break;
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {

                if (!sharedPreferences.getBoolean("isAuthenticated", false)) {
                    authoriseMiBand();
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putBoolean("isAuthenticated", true);
                    editor.apply();
                    showToast("Connected", getApplicationContext());
                } else{
                    Log.i("Device", "Already authenticated");
                    showToast("Connected", getApplicationContext());
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

                switch (characteristic.getService().getUuid().toString()) {
                    case UUIDs.DEVICE_INFORMATION_SERVICE_STRING:
                        handleDeviceInfo(characteristic);
                        break;
                    case UUIDs.GENERIC_ACCESS_SERVICE_STRING:
                        //handleGenericAccess(characteristic);
                        break;
                    case UUIDs.GENERIC_ATTRIBUTE_SERVICE_STRING:
                        //handleGenericAttribute(characteristic);
                        break;
                    case UUIDs.ALERT_NOTIFICATION_SERVICE_STRING:
                        //handleAlertNotification(characteristic);
                        break;
                    case UUIDs.IMMEDIATE_ALERT_SERVICE_STRING:
                        //handleImmediateAlert(characteristic);
                        break;
                }
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicWrite(gatt, characteristic, status);
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

                switch (characteristic.getUuid().toString()) {
                    case UUIDs.CUSTOM_SERVICE_AUTH_CHARACTERISTIC_STRING:
                        executeAuthorisationSequence(characteristic);
                        break;
                    case UUIDs.HEART_RATE_MEASUREMENT_CHARACTERISTIC_STRING:
                        Log.d("heart", Byte.toString(characteristic.getValue()[1]));
                        handleHeartRateData(characteristic);
                        break;
                    case UUIDs.BUTTON_TOUCH_UUID:
                        showToast("Touched", getApplicationContext());

                }
            }

            @Override
            public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                Log.d("Descriptor", descriptor.getUuid().toString() + " Read");
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                Log.d("Descriptor", descriptor.getUuid().toString() + " Written");
            }
        };

    }

    private void showToast(String msg, Context context){
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleDeviceInfo(BluetoothGattCharacteristic characteristic) {
        String value = characteristic.getStringValue(0);
        // Log.d("TAG", "onCharacteristicRead: " + value + " UUID " + characteristic.getUuid().toString());
        synchronized (object) {
            object.notify();
        }
        deviceInfoMap.put(characteristic.getUuid(), value);
    }

    private void getHeartRate() {
        variableService = bluetoothGatt.getService(UUIDs.HEART_RATE_SERVICE);
        BluetoothGattCharacteristic heartRateCharacteristic = variableService.getCharacteristic(UUIDs.HEART_RATE_MEASUREMENT_CHARACTERISTIC);
        BluetoothGattDescriptor heartRateDescriptor = heartRateCharacteristic.getDescriptor(UUIDs.HEART_RATE_MEASURMENT_DESCRIPTOR);

        bluetoothGatt.setCharacteristicNotification(heartRateCharacteristic, true);
        heartRateDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        bluetoothGatt.writeDescriptor(heartRateDescriptor);
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if(device.getName() != null && device.getAddress().equals("C4:33:B0:63:1A:72")){
                                deviceList.add(device);
                                Log.d(device.getAddress(), "Device Name");
                                scanLeDevice(false);
                                connectDevice(deviceList.get(0));
                            }
                        }
                    });
                }
            };

    RxBleDevice device;

    private void connectDevice(BluetoothDevice miBand) {

        if (miBand.getBondState() == BluetoothDevice.BOND_NONE) {
            miBand.createBond();
            Log.d("Bond", "Created with Device");
        }

        bluetoothGatt = miBand.connectGatt(getApplicationContext(), true, miBandGattCallBack);
    }

    private void handleAlertNotification(BluetoothGattCharacteristic characteristic) {
        String value = characteristic.getStringValue(0);
        Log.d("TAG", "onCharacteristicRead: " + value + " UUID " + characteristic.getUuid().toString());
        synchronized (object) {
            object.notify();
        }
    }



    private void handleHeartRateData(final BluetoothGattCharacteristic characteristic) {

        Log.e("Heart",characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0).toString());
//        Log.e("Heart",characteristic.getValue());

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                heartRate.setText(Byte.toString(characteristic.getValue()[1])+" bpm");
            }
        });

    }

    public void getNotifications() {

        Log.d("Touch Tag", "* Getting gatt service, UUID:" );
        variableService = bluetoothGatt.getService(UUIDs.CUSTOM_SERVICE_FEE0);
        if (variableService != null) {
            Log.d("Touch Tag2", "* Getting gatt Characteristic. UUID: ");

            BluetoothGattCharacteristic characteristic
                    = variableService.getCharacteristic(UUIDs.UUID_BUTTON_TOUCH);
            if (characteristic != null) {
                Log.d("Touch Tag3", "* Statring listening");

                // second parametes is for starting\stopping the listener.
                boolean status =  bluetoothGatt.setCharacteristicNotification(characteristic, true);
                Log.d("Touch Tag4", "* Set notification status :" + status);
            }
        }
    }

    private void immediateAlert() {
        variableService = bluetoothGatt.getService(UUIDs.IMMEDIATE_ALERT_SERVICE);
        try {
            for (BluetoothGattCharacteristic characteristic : variableService.getCharacteristics()) {
                bluetoothGatt.setCharacteristicNotification(characteristic, true);
                bluetoothGatt.readCharacteristic(characteristic);
                synchronized (object) {
                    object.wait(2000);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startScan(View view) {
        scanLeDevice(true);
        String macAddress = "F4:7C:45:2C:EE:1B";
    }

    @Override
    protected void onDestroy() {
        bluetoothGatt.disconnect();
        super.onDestroy();
    }

    public static final String BASE_UUID = "0000%s-0000-1000-8000-00805f9b34fb";

    @SuppressLint("CheckResult")
    public void goToDevice(View view) {
        getHeartRate();
    }

    /*------Methods to send requests to the device------*/
    private void authoriseMiBand() {
        BluetoothGattService service = bluetoothGatt.getService(UUIDs.CUSTOM_SERVICE_FEE1);

        BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUIDs.CUSTOM_SERVICE_AUTH_CHARACTERISTIC);
        bluetoothGatt.setCharacteristicNotification(characteristic, true);
        for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
            if (descriptor.getUuid().equals(UUIDs.CUSTOM_SERVICE_AUTH_DESCRIPTOR)) {
                Log.d("INFO", "Found NOTIFICATION BluetoothGattDescriptor: " + descriptor.getUuid().toString());
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            }
        }

        characteristic.setValue(new byte[]{0x01, 0x8, 0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x40, 0x41, 0x42, 0x43, 0x44, 0x45});
        bluetoothGatt.writeCharacteristic(characteristic);
    }

    private void executeAuthorisationSequence(BluetoothGattCharacteristic characteristic) {
        byte[] value = characteristic.getValue();
        if (value[0] == 0x10 && value[1] == 0x01 && value[2] == 0x01) {
            characteristic.setValue(new byte[]{0x02, 0x8});
            bluetoothGatt.writeCharacteristic(characteristic);
        } else if (value[0] == 0x10 && value[1] == 0x02 && value[2] == 0x01) {
            try {
                byte[] tmpValue = Arrays.copyOfRange(value, 3, 19);
                Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");

                SecretKeySpec key = new SecretKeySpec(new byte[]{0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x40, 0x41, 0x42, 0x43, 0x44, 0x45}, "AES");

                cipher.init(Cipher.ENCRYPT_MODE, key);
                byte[] bytes = cipher.doFinal(tmpValue);


                byte[] rq = ArrayUtils.addAll(new byte[]{0x03, 0x8}, bytes);
                characteristic.setValue(rq);
                bluetoothGatt.writeCharacteristic(characteristic);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void alertNotification() {
        try{
            variableService = bluetoothGatt.getService(UUIDs.ALERT_NOTIFICATION_SERVICE);
            BluetoothGattCharacteristic heartRateCharacteristic = variableService.getCharacteristic(UUIDs.NEW_ALERT_CHARACTERISTIC);
            bluetoothGatt.writeCharacteristic(heartRateCharacteristic);
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    public void setupHeartBeat() {
        /*
        Steps to read heartbeat:
            - Register Notification (like in touch press)
                - Extra step with description
            - Write predefined bytes to control_point to trigger measurement
            - Listener will get result
        */

        getNotificationsWithDescriptor(
                UUIDs.HEART_RATE_SERVICE,
                UUIDs.UUID_NOTIFICATION_HEARTRATE,
                UUIDs.UUID_DESCRIPTOR_UPDATE_NOTIFICATION
        );


        // Need to wait before first trigger, maybe something about the descriptor....
        /*
        Toast.makeText(MainActivity.this, "Wait for heartbeat setup...", Toast.LENGTH_LONG).show();
        try {
            Thread.sleep(5000,0);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        */
    }


    public void getNewHeartBeat()  {

        writeData(
                UUIDs.HEART_RATE_SERVICE,
                UUIDs.UUID_START_HEARTRATE_CONTROL_POINT,
                UUIDs.BYTE_NEW_HEART_RATE_SCAN
        );
    }

    /* =========  Handling Data  ============== */

    public void readData(UUID service, UUID Characteristics) {


        Log.d(TAG, "* Getting gatt service, UUID:" + service.toString());
        BluetoothGattService myGatService =
                bluetoothGatt.getService(service /*Consts.UUID_SERVICE_GENERIC*/);
        if (myGatService != null) {
            Log.d(TAG, "* Getting gatt Characteristic. UUID: " + Characteristics.toString());

            BluetoothGattCharacteristic myGatChar
                    = myGatService.getCharacteristic(Characteristics /*Consts.UUID_CHARACTERISTIC_DEVICE_NAME*/);
            if (myGatChar != null) {
                Log.d(TAG, "* Reading data");

                boolean status =  bluetoothGatt.readCharacteristic(myGatChar);
                Log.d(TAG, "* Read status :" + status);
            }
        }
    }

    String TAG = "";

    public void writeData(UUID service, UUID Characteristics,byte[] data) {


        Log.d(TAG, "* Getting gatt service, UUID:" + service.toString());
        BluetoothGattService myGatService =
                bluetoothGatt.getService(service /*Consts.UUID_SERVICE_HEARTBEAT*/);
        if (myGatService != null) {
            Log.d(TAG, "* Getting gatt Characteristic. UUID: " + Characteristics.toString());

            BluetoothGattCharacteristic myGatChar
                    = myGatService.getCharacteristic(Characteristics /*Consts.UUID_START_HEARTRATE_CONTROL_POINT*/);
            if (myGatChar != null) {
                Log.d(TAG, "* Writing trigger");
                myGatChar.setValue(data /*Consts.BYTE_NEW_HEART_RATE_SCAN*/);

                boolean status =  bluetoothGatt.writeCharacteristic(myGatChar);
                Log.d(TAG, "* Writting trigger status :" + status);
            }
        }
    }

    public void getNotifications(UUID service, UUID Characteristics) {

        Log.d(TAG, "* Getting gatt service, UUID:" + service.toString());
        BluetoothGattService myGatService =
                bluetoothGatt.getService(service/*Consts.UUID_SERVICE_MIBAND_SERVICE*/);
        if (myGatService != null) {
            Log.d(TAG, "* Getting gatt Characteristic. UUID: " + Characteristics.toString());

            BluetoothGattCharacteristic myGatChar
                    = myGatService.getCharacteristic(Characteristics/*Consts.UUID_BUTTON_TOUCH*/);
            if (myGatChar != null) {
                Log.d(TAG, "* Statring listening");

                // second parametes is for starting\stopping the listener.
                boolean status =  bluetoothGatt.setCharacteristicNotification(myGatChar, true);
                Log.d(TAG, "* Set notification status :" + status);
            }
        }
    }

    /**
     * Get notification but also set descriptor to Enable notification. You need to wait couple of
     *      seconds before you could use it (at least in the mi band 2)
     * @param service
     * @param Characteristics
     */
    public void getNotificationsWithDescriptor(UUID service, UUID Characteristics, UUID Descriptor) {


        Log.d(TAG, "* Getting gatt service, UUID:" + service.toString());
        BluetoothGattService myGatService =
                bluetoothGatt.getService(service/*Consts.UUID_SERVICE_MIBAND_SERVICE*/);
        if (myGatService != null) {
            Log.d(TAG, "* Getting gatt Characteristic. UUID: " + Characteristics.toString());

            BluetoothGattCharacteristic myGatChar
                    = myGatService.getCharacteristic(Characteristics/*Consts.UUID_BUTTON_TOUCH*/);
            if (myGatChar != null) {
                Log.d(TAG, "* Statring listening");

                // second parametes is for starting\stopping the listener.
                boolean status = bluetoothGatt.setCharacteristicNotification(myGatChar, true);
                Log.d(TAG, "* Set notification status :" + status);

                BluetoothGattDescriptor myDescriptor
                        = myGatChar.getDescriptor(Descriptor/*Consts.UUID_DESCRIPTOR_UPDATE_NOTIFICATION*/);
                if (myDescriptor != null) {
                    Log.d(TAG, "Writing decriptor: " + Descriptor.toString());
                    myDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    status = bluetoothGatt.writeDescriptor(myDescriptor);
                    Log.d(TAG, "Writing decriptors result: " + status);
                }
            }
        }
    }


    public void getData(View view) {
        getNewHeartBeat();
    }
}
