package com.github.jrejaud.jelling;

import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by jrejaud on 10/4/15.
 */
public class BluetoothSocketConnectedThread extends Thread {
    private final BluetoothSocket mSocket;
    private final InputStream inputStream;
    private final OutputStream outputStream;

    protected BluetoothSocketConnectedThread(Context context, BluetoothSocket socket) {
        jellingListener = (JellingListener) context;
        Log.d(Jelling.TAG, "Setting up socket stream");
        mSocket = socket;
        InputStream tmpIn = null;
        OutputStream tmpOut = null;

        // Get the input and output streams, using temp objects because
        // member streams are final
        try {
            tmpIn = socket.getInputStream();
            tmpOut = socket.getOutputStream();
        } catch (IOException e) { }

        inputStream = tmpIn;
        outputStream = tmpOut;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[1024];
        int bytes;

        while (true) {
            try {
                bytes = inputStream.read(buffer);
                String message = new String(buffer,0,bytes);
                jellingListener.receiveBluetoothMessage(message);

            } catch (IOException e) {
                e.printStackTrace();

            }
        }
    }

    protected void write(String message) {
        byte[] bytes = message.getBytes();
        try {
            Log.d(Jelling.TAG,"Sending BT message: "+message);
            outputStream.write(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void cancel() {
        try {
            mSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private JellingListener jellingListener;

    private interface JellingListener {
        void receiveBluetoothMessage(String message);
    }
}
