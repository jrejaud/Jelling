package com.github.jrejaud.jelling;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/**
 * Created by jrejaud on 10/4/15.
 */
public class Jelling {

    private static Jelling ourInstance = new Jelling();

    public static Jelling getInstance() {
        return ourInstance;
    }

    private Jelling() {
    }

    private BluetoothAdapter mBluetoothAdapter;
    private Context context;
    private int REQUEST_ENABLE_BT = 13;
    protected static String TAG = Jelling.class.getSimpleName();
    private Semaphore bluetoothAdapterEnabled = new Semaphore(0,true);
    private boolean bluetoothAdapterReady = false;
    private BluetoothDevice desiredDevice;
    private String desiredDeviceName;
    private String desiredDeviceUUID;
    private BluetoothSocket bluetoothSocket;
    private BluetoothSocketConnectedThread bluetoothSocketConnectedThread;


    //Start the bluetooth setup process
    public void setupBluetooth(Context context) {
        setupBluetoothAdapter(context);
    }

    private void setupBluetoothAdapter(Context context) {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.context = context;
        if (mBluetoothAdapter == null) {
            showErrorAndClose("This device does not support bluetooth");
            return;
        }
        checkIfBluetoothAdapterIsEnabled();
    }

    private void checkIfBluetoothAdapterIsEnabled() {
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            ((Activity)context).startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }
        bluetoothAdapterReady = true;
        bluetoothAdapterEnabled.release();
    }

    public void connectToDeviceByNameAndUUID(final String deviceName, String UUID) {
        desiredDeviceUUID = UUID;
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (!bluetoothAdapterReady) {
                    Log.d(TAG,"Bluetooth Adapter isn't ready yet, waiting for it to get setup before starting search for "+deviceName);
                    try {
                        bluetoothAdapterEnabled.acquire();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                //See if desired desiredDevice is already in paired desiredDevice list
                BluetoothDevice existingPairedDevice = checkPairedDevicesForDevice(deviceName);
                //If it is in the list, then all is good!
                if (existingPairedDevice!=null) {
                    desiredDevice = existingPairedDevice;
                    desiredDeviceHasBeenFound();
                } else {
                    desiredDeviceName = deviceName;
                    startDeviceDiscovery(deviceName);
                }

            }
        }).start();
    }

    private BluetoothDevice checkPairedDevicesForDevice(String deviceName) {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size()<1) {
            Log.d(TAG, "No paired bluetooth devices, start search for " + deviceName);
            return null;
        }
        for (BluetoothDevice device : pairedDevices) {
            if (checkDevice(device, deviceName)) {
                Log.d(TAG, deviceName+" found!");
                return device;
            }
        }
        return null;
    }

    private void desiredDeviceHasBeenFound() {
        Log.d(TAG, desiredDevice.getName() + " has been successfully found!");
        context.unregisterReceiver(bluetoothDeviceDiscoveryReceiver);
        mBluetoothAdapter.cancelDiscovery();

        if (desiredDeviceUUID==null) {
            Log.d(TAG,"You need to set a UUID String!");
            return;
        }

        UUID uuid = UUID.fromString(desiredDeviceUUID);
        BluetoothSocket tmp = null;
        try {
            Log.d(TAG,"Trying to connect to "+desiredDevice.getName()+" "+desiredDeviceUUID);
            tmp = desiredDevice.createRfcommSocketToServiceRecord(uuid);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "Socket to "+desiredDevice.getName()+" acquired");
        BluetoothSocket bluetoothSocket = tmp;
        new connectSocketTask().execute(bluetoothSocket);
    }

    private class connectSocketTask extends AsyncTask<BluetoothSocket,Void,BluetoothSocket> {
        @Override
        protected BluetoothSocket doInBackground(BluetoothSocket... params) {
            BluetoothSocket socket = params[0];
            try {
                Log.d(TAG,"Attempting to connect socket...");
                socket.connect();
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    socket.close();
                } catch (IOException e1) { }
                return null;
            }
            Log.d(TAG, "Socket Connected");
            return socket;
        }

        @Override
        protected void onPostExecute(BluetoothSocket socket) {
            if (socket==null) {
                String message = "Connected to "+desiredDeviceName+", but no bluetooth server is running on it";
                Log.d(TAG, message);
                return;
            }
            bluetoothSocket = socket;
            connectBluetoothSocket();
        }

    }

    private void connectBluetoothSocket() {
        bluetoothSocketConnectedThread = new BluetoothSocketConnectedThread(context,bluetoothSocket);
        bluetoothSocketConnectedThread.start();
    }

    private boolean checkDevice(BluetoothDevice foundDevice, String desiredDeviceName) {
        if (foundDevice != null && foundDevice.getName() != null && foundDevice.getName().equals(desiredDeviceName)) {
            return true;
        }
        return false;
    }

    private void startDeviceDiscovery(String deviceName) {
        Log.d(TAG, "Couldn't find "+deviceName+" in paired devices, starting desiredDevice discovery");
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        context.registerReceiver(bluetoothDeviceDiscoveryReceiver,filter);
        mBluetoothAdapter.startDiscovery();
    }

    private BroadcastReceiver bluetoothDeviceDiscoveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.d(TAG,"Device discovered: "+device.getName()+" "+device.getAddress());
                if (checkDevice(device,desiredDeviceName)) {
                    desiredDevice = device;
                    desiredDeviceHasBeenFound();
                }
            }
        }
    };

    public void sendMessage(String message) {
        if (desiredDevice==null) {
            Log.d(TAG,"Cannot send a message, another device hasn't been found yet!");
            return;
        }

        if (bluetoothSocketConnectedThread==null) {
            Log.d(TAG, "Bluetooth connected thread is not ready yet, need to connect to laptop to send messages");
            return;
        }

        bluetoothSocketConnectedThread.write(message);
    }

    //Disconnect the bluetooth thing
    public void disconnect() {
        try {
            context.unregisterReceiver(bluetoothDeviceDiscoveryReceiver);
        } catch (IllegalArgumentException e) {}
        mBluetoothAdapter.cancelDiscovery();
        if (bluetoothSocketConnectedThread!=null) {
            bluetoothSocketConnectedThread.cancel();
        }
    }


    private void showErrorAndClose(String errorMessage) {
        Log.e(TAG, errorMessage);
        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show();
        ((Activity) context).finish();
    }


}
