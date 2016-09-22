package tech.glasgowneuro.www.attysplot;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.apps.forscience.whistlepunk.api.scalarinput.IDeviceConsumer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorConnector;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorConsumer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorObserver;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorStatusListener;

import java.util.Set;

public class Attys2ScienceJournal extends Service {
    public static final String DEVICE_ID = "AttysDevice";
    private static final String TAG = "Attys2ScienceJournal";
    private static AttysComm attysComm = null;
    private static ISensorObserver[] observer = null;
    private static ISensorStatusListener[] listener = null;
    private static BluetoothDevice bluetoothDevice = null;
    private static long timestamp = 0;

    @Override
    public void onCreate() {
        // we create an array of observers and listeners for every sensor
        // of the attys, This then allows to propagate error conditions
        // to all sensors at the same time (i.e. connection loss) and
        // check when we can close the bluetooth connection once all
        // sensors have been disconnected.
        observer = new ISensorObserver[AttysComm.NCHANNELS];
        listener = new ISensorStatusListener[AttysComm.NCHANNELS];
        for (int i = 0; i < AttysComm.NCHANNELS; i++) {
            observer[i] = null;
            listener[i] = null;
        }
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        if (attysComm != null) {
            // we cancel sync the connection
            attysComm.cancel();
            try {
                attysComm.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            attysComm = null;
        }
    }

    private ISensorDiscoverer.Stub mDiscoverer = null;

    @Nullable
    @Override
    public ISensorDiscoverer.Stub onBind(Intent intent) {
        return getDiscoverer();
    }

    public ISensorDiscoverer.Stub getDiscoverer() {
        if (mDiscoverer == null) {
            mDiscoverer = createDiscoverer();
        }
        return mDiscoverer;
    }

    private BluetoothDevice findPairedAttys() {

        BluetoothAdapter BA = BluetoothAdapter.getDefaultAdapter();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Finding paired attys");
        }

        if (BA == null) {
            if (Log.isLoggable(TAG, Log.WARN)) {
                Log.w(TAG, "No bluetooth adapter!");
            }
            return null;
        }

        Set<BluetoothDevice> pairedDevices;
        pairedDevices = BA.getBondedDevices();

        if (pairedDevices == null) {
            if (Log.isLoggable(TAG, Log.WARN)) {
                Log.w(TAG, "No paired Attys available.");
            }
            return null;
        }

        for (BluetoothDevice bt : pairedDevices) {
            String b = bt.getName();
            if (b.startsWith("GN-ATTYS")) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Found an Attys as a paired device");
                }
                return bt;
            }
        }
        return null;
    }


    private ISensorDiscoverer.Stub createDiscoverer() {
        return new ISensorDiscoverer.Stub() {
            @Override
            public String getName() throws RemoteException {
                return "AllAttys";
            }

            @Override
            public void scanDevices(IDeviceConsumer c) throws RemoteException {
                c.onDeviceFound(DEVICE_ID, "Attys sensors", null);
            }

            @Override
            public void scanSensors(String deviceId, ISensorConsumer c) throws RemoteException {
                if (!DEVICE_ID.equals(deviceId)) {
                    return;
                }

                bluetoothDevice = findPairedAttys();

                if (bluetoothDevice == null) {
                    return;
                }

                for (int i = 0; i < AttysComm.NCHANNELS; i++) {
                    c.onSensorFound("" + i, "ATTYS " + AttysComm.CHANNEL_DESCRIPTION[i], null);
                }
            }

            @Override
            public ISensorConnector getConnector() throws RemoteException {
                return new ISensorConnector.Stub() {

                    @Override
                    public void startObserving(final String sensorId,
                                               final ISensorObserver theObserver,
                                               final ISensorStatusListener theListener,
                                               String settingsKey) throws RemoteException {

                        int sensorIndex = Integer.valueOf(sensorId);

                        // we keep all open listeners and observers in an array so
                        // that they can be served at once from the Attys callback
                        // when a new set of samples arrives
                        listener[sensorIndex] = theListener;
                        observer[sensorIndex] = theObserver;

                        bluetoothDevice = findPairedAttys();

                        if (bluetoothDevice == null) {
                            theListener.onSensorError("No paired Attys available");
                            return;
                        }

                        // are we the first sensor? Then let's start a proper connection
                        if (attysComm == null) {
                            attysComm = new AttysComm(bluetoothDevice);
                            attysComm.setAccel_full_scale_index(AttysComm.ACCEL_16G);
                            attysComm.setGyro_full_scale_index(AttysComm.GYRO_2000DPS);
                            attysComm.setAdc0_gain_index(AttysComm.ADC_GAIN_1);
                            attysComm.setAdc1_gain_index(AttysComm.ADC_GAIN_1);
                        }

                        AttysComm.DataListener dataListener = new AttysComm.DataListener() {
                            @Override
                            public void gotData(long samplenumber, float[] data) {
                                for (int i = 0; i < AttysComm.NCHANNELS; i++) {
                                    if (observer[i] != null) {
                                        try {
                                            if (timestamp == 0) {
                                                timestamp = System.currentTimeMillis();
                                            }
                                            observer[i].onNewData(timestamp, data[i]);
                                            timestamp = timestamp +
                                                    1000/((long)attysComm.getSamplingRateInHz());
                                            // Log.d(TAG,String.format("timestamp=%d",timestamp));
                                        } catch (RemoteException e) {
                                            Log.e(TAG, "onNewData exception:", e);
                                        }
                                    }
                                }
                            }
                        };

                        attysComm.registerDataListener(dataListener);

                        AttysComm.MessageListener messageListener = new AttysComm.MessageListener() {
                            @Override
                            public void haveMessage(int msg) {
                                switch (msg) {
                                    case AttysComm.MESSAGE_ERROR:
                                        for (int i = 0; i < AttysComm.NCHANNELS; i++) {
                                            try {
                                                if (listener[i] != null) {
                                                    listener[i].onSensorError("Attys connection problem");
                                                }
                                            } catch (RemoteException e) {
                                                if (Log.isLoggable(TAG, Log.ERROR)) {
                                                    Log.e(TAG, "Cannot report BT error to open science journal", e);
                                                }
                                            }
                                        }
                                        break;
                                    case AttysComm.MESSAGE_CONNECTED:
                                        for (int i = 0; i < AttysComm.NCHANNELS; i++) {
                                            try {
                                                if (listener[i] != null) {
                                                    listener[i].onSensorConnected();
                                                }
                                            } catch (RemoteException e) {
                                                if (Log.isLoggable(TAG, Log.ERROR)) {
                                                    Log.e(TAG, "Cannot announce connect", e);
                                                }
                                            }
                                        }
                                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                                            Log.d(TAG, "Connected");
                                        }
                                        break;
                                    case AttysComm.MESSAGE_CONFIGURE:
                                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                                            Log.d(TAG, "Configuring Attys");
                                        }
                                        break;
                                    case AttysComm.MESSAGE_RETRY:
                                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                                            Log.d(TAG, "Retrying to connect");
                                        }
                                        break;
                                }

                            }
                        };

                        attysComm.registerMessageListener(messageListener);

                        // this is async in the background and might take a second or two
                        attysComm.start();

                        theListener.onSensorConnected();

                    }

                    @Override
                    public void stopObserving(String sensorId) throws RemoteException {
                        int sensorIndex = Integer.valueOf(sensorId);

                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, String.format("shutting down sensor %d.", sensorIndex));
                        }

                        if (listener[sensorIndex] != null) {
                            listener[sensorIndex].onSensorDisconnected();
                            listener[sensorIndex] = null;
                        }
                        observer[sensorIndex] = null;

                        // check if we observe something on another sensor
                        for (int i = 0; i < AttysComm.NCHANNELS; i++) {
                            if (observer[i] != null) {
                                Log.d(TAG, String.format("Keep connection alive."));
                                return;
                            }
                        }

                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, String.format("Shutting down the connection."));
                        }
                        // we no longer need an active bluetooth connection so we kill it off
                        attysComm.cancel();
                    }
                };
            }
        };
    }
}
