package com.hapticvest.david.ble_framework2;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.util.Log;

import com.unity3d.player.UnityPlayer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity{

    private Activity _unityActivity;

    /* Singleton instance */
    private static volatile MainActivity _instance;

    /* Definition of the BLE Unity message methods used to communicate back with Unity */
    public static final String BLEUnityMessageName_OnBleDidInitialize = "OnBleDidInitialize";
    public static final String BLEUnityMessageName_OnBleDidConnect = "OnBleDidConnect";
    public static final String BLEUnityMessageName_OnBleDidCompletePeripheralScan = "OnBleDidCompletePeripheralScan";
    public static final String BLEUnityMessageName_OnBleDidDisconnect = "OnBleDidDisconnect";
    public static final String BLEUnityMessageName_OnBleDidReceiveData = "OnBleDidReceiveData";

    /* Static variables */
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_ENABLE_BT = 1;
    private static final long SCAN_PERIOD = 3000;
    public static final int REQUEST_CODE = 30;

    /* List containing all the discovered bluetooth devices */
    private List<BluetoothDevice> _mDevice = new ArrayList<BluetoothDevice>();

    /* The latest received data */
    private byte[] _dataRx = new byte[3];

    /* Bluetooth service */
    private HM10Service _mBluetoothLeService;

    private Map<UUID, BluetoothGattCharacteristic> _map = new HashMap<UUID, BluetoothGattCharacteristic>();

    /* Bluetooth adapter */
    private BluetoothAdapter _mBluetoothAdapter;

    /* Bluetooth device address and name to which the app is currently connected */
    private BluetoothDevice _device;
    private String _mDeviceAddress;
    private String _mDeviceName;

    /* Boolean variables used to estabilish the status of the connection */
    private boolean _connState = false;
    private boolean _searchingDevice = false;

    /* The service connection containing the actions definition onServiceConnected and onServiceDisconnected */
    private final ServiceConnection _mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            _mBluetoothLeService = ((HM10Service.LocalBinder) service).getService();
            if (!_mBluetoothLeService.initialize()) {
                Log.e(TAG, "onServiceConnected: Unable to initialize Bluetooth");
            } else {
                Log.d(TAG, "onServiceConnected: Bluetooth initialized correctly");
                _mBluetoothLeService.connect(_mDeviceAddress);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "onServiceDisconnected: Bluetooth disconnected");
            _mBluetoothLeService = null;
        }
    };


    /*
    Callback called when the scan of bluetooth devices is finished
    */
    private BluetoothAdapter.LeScanCallback _mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
            _unityActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "onLeScan: run()");
                    if (device != null && device.getName() != null) {
                        Log.d(TAG, "onLeScan: device is not null");
                        if (_mDevice.indexOf(device) == -1) {
                            Log.d(TAG, "onLeScan: add device to _mDevice");
                            _mDevice.add(device);
                        }
                    } else {
                        Log.e(TAG, "onLeScan: device is null");
                    }
                }
            });
        }
    };


    /*
    Callback called when the bluetooth device receive relevant updates about connection, disconnection, service discovery, data available, rssi update
    */
    private final BroadcastReceiver _mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (HM10Service.ACTION_GATT_CONNECTED.equals(action)) {
                _connState = true;

                Log.d(TAG, "Connection estabilished with: " + _mDeviceAddress);
            } else if (HM10Service.ACTION_GATT_DISCONNECTED.equals(action)) {
                _connState = false;

                UnityPlayer.UnitySendMessage("BLEControllerEventHandler", BLEUnityMessageName_OnBleDidDisconnect, "Success");

                Log.d(TAG, "Connection lost");
            } else if (HM10Service.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                Log.d(TAG, "Service discovered! Registering GattService ACTION_GATT_SERVICES_DISCOVERED");
                getGattService(_mBluetoothLeService.getSupportedGattService());

                Log.d(TAG, "Send BLEUnityMessageName_OnBleDidConnect success signal to Unity");
                UnityPlayer.UnitySendMessage("BLEControllerEventHandler", BLEUnityMessageName_OnBleDidConnect, "Success");
            } else if (HM10Service.ACTION_DATA_AVAILABLE.equals(action)) {
                Log.d(TAG, "New Data received by the server");
                _dataRx = intent.getByteArrayExtra(HM10Service.EXTRA_DATA);

                UnityPlayer.UnitySendMessage("BLEControllerEventHandler", BLEUnityMessageName_OnBleDidReceiveData, String.valueOf(_dataRx.length));
            } else if (HM10Service.ACTION_GATT_RSSI.equals(action)) {
                String rssiData = intent.getStringExtra(HM10Service.EXTRA_DATA);
                Log.d(TAG, "RSSI: " + rssiData);
            }
        }
    };


    /*
    METHODS DEFINITION
    */
    public static MainActivity getInstance(Activity activity) {
        if (_instance == null ) {
            synchronized (MainActivity.class) {
                if (_instance == null) {
                    Log.d(TAG, "BleFramework: Creation of _instance");
                    _instance = new MainActivity(activity);
                }
            }
        }
        return _instance;
    }

    public MainActivity(Activity activity) {
        this._unityActivity = activity;
    }

    public MainActivity() {}

    /*
    Method used to create a filter for the bluetooth actions that you like to receive
    */
    private static IntentFilter makeGattUpdateIntentFilter()
    {
        final IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(HM10Service.ACTION_GATT_CONNECTED);
        intentFilter.addAction(HM10Service.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(HM10Service.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(HM10Service.ACTION_DATA_AVAILABLE);

        return intentFilter;
    }


    /*
    Method used to initialize the characteristic for data transmission
    */
    private void getGattService(BluetoothGattService gattService) {

        if (gattService == null)
            return;

        BluetoothGattCharacteristic characteristic = gattService.getCharacteristic(HM10Service.HM_10_UUID_CHARAC);
        _map.put(characteristic.getUuid(), characteristic);

        /*
        BluetoothGattCharacteristic characteristicRx = gattService.getCharacteristic(HM10Service.UUID_BLE_SHIELD_RX);
        _mBluetoothLeService.setCharacteristicNotification(characteristicRx, true);
        _mBluetoothLeService.readCharacteristic(characteristicRx);
        */
    }


    /*
    Method used to scan for available bluetooth low energy devices
    */
    private void scanLeDevice() {
        new Thread() {

            @Override
            public void run() {
                _searchingDevice = true;
                Log.d(TAG, "scanLeDevice: _mBluetoothAdapter StartLeScan");
                _mBluetoothAdapter.startLeScan(_mLeScanCallback);

                try {
                    Log.d(TAG, "scanLeDevice: scan for 3 seconds then abort");
                    Thread.sleep(SCAN_PERIOD);
                } catch (InterruptedException e) {
                    Log.d(TAG, "scanLeDevice: InterruptedException");
                    e.printStackTrace();
                }

                Log.d(TAG, "scanLeDevice: _mBluetoothAdapter StopLeScan");
                _mBluetoothAdapter.stopLeScan(_mLeScanCallback);
                _searchingDevice = false;
                Log.d(TAG, "scanLeDevice: _mDevice size is " + _mDevice.size());

                UnityPlayer.UnitySendMessage("BLEControllerEventHandler", BLEUnityMessageName_OnBleDidCompletePeripheralScan, "Success");
            }
        }.start();
    }


    private void unregisterBleUpdatesReceiver() {
        Log.d(TAG,"unregisterBleUpdatesReceiver:");
        _unityActivity.unregisterReceiver(_mGattUpdateReceiver);
    }

    private void registerBleUpdatesReceiver() {
        Log.d(TAG,"registerBleUpdatesReceiver:");
        if (!_mBluetoothAdapter.isEnabled()) {
            Log.d(TAG,"registerBleUpdatesReceiver: WARNING: _mBluetoothAdapter is not enabled!");
        }
        Log.d(TAG,"registerBleUpdatesReceiver: registerReceiver");
        _unityActivity.registerReceiver(_mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }


    /*
    Public methods that can be directly called by Unity
    */
    public boolean _InitBLEFramework() {
        System.out.println("Android Executing: _InitBLEFramework");
        if (!_unityActivity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.d(TAG,"onCreate: fail: missing FEATURE_BLUETOOTH_LE");
            UnityPlayer.UnitySendMessage("BLEControllerEventHandler", BLEUnityMessageName_OnBleDidInitialize, "Fail: missing FEATURE_BLUETOOTH_LE");
            return false;
        }

        final BluetoothManager mBluetoothManager = (BluetoothManager) _unityActivity.getSystemService(Context.BLUETOOTH_SERVICE);
        _mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (_mBluetoothAdapter == null) {
            Log.d(TAG,"onCreate: fail: _mBluetoothAdapter is null");
            UnityPlayer.UnitySendMessage("BLEControllerEventHandler", BLEUnityMessageName_OnBleDidInitialize, "Fail: Context.BLUETOOTH_SERVICE");
            return false;
        }

        registerBleUpdatesReceiver();

        Log.d(TAG,"onCreate: _mBluetoothAdapter correctly initialized");
        UnityPlayer.UnitySendMessage("BLEControllerEventHandler", BLEUnityMessageName_OnBleDidInitialize, "Success");
        return true;
    }

    public void _ScanForPeripherals() {
        Log.d(TAG, "_ScanForPeripherals: Launching scanLeDevice");
        scanLeDevice();
    }

    public boolean _IsDeviceConnected() {
        Log.d(TAG,"_IsDeviceConnected");
        return _connState;
    }

    public boolean _SearchDeviceDidFinish() {
        Log.d(TAG,"_SearchDeviceDidFinish");
        return !_searchingDevice;
    }

    public String _GetListOfDevices() {
        String jsonListString;
        if (_mDevice.size() > 0) {
            Log.d(TAG,"_GetListOfDevices");
            String[] uuidsArray = new String[_mDevice.size()];

            for (int i = 0; i < _mDevice.size(); i++) {
                BluetoothDevice bd = _mDevice.get(i);
                uuidsArray[i] = bd.getAddress();
            }
            Log.d(TAG, "_GetListOfDevices: Building JSONArray");
            JSONArray uuidsJSON = new JSONArray(Arrays.asList(uuidsArray));
            Log.d(TAG, "_GetListOfDevices: Building JSONObject");
            JSONObject dataUuidsJSON = new JSONObject();

            try {
                Log.d(TAG, "_GetListOfDevices: Try inserting uuuidsJSON array in the JSONObject");
                dataUuidsJSON.put("data", uuidsJSON);
            } catch (JSONException e) {
                Log.e(TAG, "_GetListOfDevices: JSONException");
                e.printStackTrace();
            }

            jsonListString = dataUuidsJSON.toString();
            Log.d(TAG, "_GetListOfDevices: sending found devices in JSON: " + jsonListString);
        } else {
            jsonListString = "NO DEVICE FOUND";
            Log.d(TAG, "_GetListOfDevices: no device was found");
        }

        return jsonListString;
    }

    public String _GetPairedDevices() {
        String list = "";
        Set<BluetoothDevice> pairedDevices = _mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            list += (device.getName()+" ");
            _mDevice.add(device);
            Log.d("_GetPairedDevices", device.getName()+" added to _mDevice");
        }

        Log.d("_GetPairedDevices","_mDevice size is " + _mDevice.size());
        return list;
    }

    public boolean _ConnectPeripheralAtIndex(int peripheralIndex) {
        Log.d(TAG,"_ConnectPeripheralAtIndex: " + peripheralIndex);
        BluetoothDevice device = _mDevice.get(peripheralIndex);

        _mDeviceAddress = device.getAddress();
        _mDeviceName = device.getName();

        Intent gattServiceIntent = new Intent(_unityActivity, HM10Service.class);
        _unityActivity.bindService(gattServiceIntent, _mServiceConnection, _unityActivity.BIND_AUTO_CREATE);

        return true;
    }

    public boolean _ConnectPeripheral(String peripheralID) {
        Log.d(TAG,"_ConnectPeripheral: " + peripheralID);
        for (BluetoothDevice device : _mDevice) {
            if (device.getAddress().equals(peripheralID)){
                _mDeviceAddress = device.getAddress();
                _mDeviceName = device.getName();

                Intent gattServiceIntent = new Intent(_unityActivity, HM10Service.class);
                _unityActivity.bindService(gattServiceIntent, _mServiceConnection, _unityActivity.BIND_AUTO_CREATE);

                return true;
            }
        }
        return false;
    }

    public byte[] _GetData() {
        Log.d(TAG,"_GetData: ");
        return _dataRx;
    }

    public void _SendData(String data) {
        Log.d(TAG,"_SendData: ");
        BluetoothGattCharacteristic characteristic = _map.get(HM10Service.HM_10_UUID_CHARAC);
        Log.d(TAG, "Set data in the _characteristicTx");
        //byte[] tx = hexStringToByteArray(data);
        byte[] tx = data.getBytes();
        characteristic.setValue(tx);
        Log.d(TAG, "characteristic.setValue OK!");

        //Log.d(TAG, "Write _characteristicTx in the _mBluetoothLeService: " + tx[0] + " " + tx[1] + " " + tx[2]);
        if (_mBluetoothLeService==null) {
            Log.d(TAG, "_mBluetoothLeService is null");
        }
        _mBluetoothLeService.writeCharacteristic(characteristic);
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
}
