package tech.glasgowneuro.attys2sciencejournal;

import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.DeadObjectException;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

import tech.glasgowneuro.attyscomm.AttysComm;
import tech.glasgowneuro.attysplot.Highpass;
import tech.glasgowneuro.attysplot.R;
import uk.me.berndporr.iirj.Butterworth;

import com.google.android.apps.forscience.whistlepunk.api.scalarinput.IDeviceConsumer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorConnector;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorConsumer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorDiscoverer;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorObserver;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.ISensorStatusListener;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.SensorAppearanceResources;
import com.google.android.apps.forscience.whistlepunk.api.scalarinput.SensorBehavior;

import java.util.Set;

public class Attys2ScienceJournal extends Service {
    public static final String DEVICE_ID = "AttysDevice";
    public static final String SENSOR_PREF_NAME = "attys_sensors";
    private static final String TAG = "Attys2ScienceJournal";
    private static AttysComm attysComm = null;
    private static ISensorObserver[] observer = null;
    private static ISensorStatusListener[] listener = null;
    private static double timestamp = 0;
    private static double samplingInterval = 0;

    private final static SensorAppearanceResources[] sensorAppearanceResources =
            new SensorAppearanceResources[AttysComm.NCHANNELS];

    private final static SensorBehavior[] behaviour =
            new SensorBehavior[AttysComm.NCHANNELS];

    private float[] gainFactor = {
            1, 1, 1, // acceleration
            1E6F, 1E6F, 1E6F, // magnetic field
            1, 1 // adc channels
    };

    private Highpass[] highpass = null;
    private Butterworth[] notch = null;

    private void setAppearance() {

        for (int i = 0; i < AttysComm.NCHANNELS; i++) {
            sensorAppearanceResources[i] = new SensorAppearanceResources();
            sensorAppearanceResources[i].units = AttysComm.CHANNEL_UNITS[i];
            if ((i >= AttysComm.INDEX_Magnetic_field_X) && (i <= AttysComm.INDEX_Magnetic_field_Z)) {
               sensorAppearanceResources[i].units = "\u00b5"+sensorAppearanceResources[i].units;
            }
            sensorAppearanceResources[i].shortDescription = AttysComm.CHANNEL_DESCRIPTION[i];
            behaviour[i] = new SensorBehavior();
            behaviour[i].shouldShowSettingsOnConnect = false;
        }

        switch (ADC1Settings.getIndexForMode(Attys2ScienceJournal.this)) {
            case ADC1Settings.MODE_AC_MV:
            case ADC1Settings.MODE_DC_MV:
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Ch1 BIO mode: mV instead of V");
                }
                sensorAppearanceResources[AttysComm.INDEX_Analogue_channel_1].units =
                        "m" + AttysComm.CHANNEL_UNITS[AttysComm.INDEX_Analogue_channel_1];
                break;
            case ADC1Settings.MODE_AC_UV:
            case ADC1Settings.MODE_DC_UV:
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Ch1 BIO mode: µV instead of V");
                }
                sensorAppearanceResources[AttysComm.INDEX_Analogue_channel_1].units =
                        "µ" + AttysComm.CHANNEL_UNITS[AttysComm.INDEX_Analogue_channel_1];
                break;
            case ADC1Settings.MODE_AC:
            case ADC1Settings.MODE_DC:
            default:
                sensorAppearanceResources[AttysComm.INDEX_Analogue_channel_1].units =
                        AttysComm.CHANNEL_UNITS[AttysComm.INDEX_Analogue_channel_1];
                break;
        }

        switch (ADC2Settings.getIndexForMode(Attys2ScienceJournal.this)) {
            case ADC2Settings.MODE_AC_MV:
            case ADC2Settings.MODE_DC_MV:
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Ch1 BIO mode: mV instead of V");
                }
                sensorAppearanceResources[AttysComm.INDEX_Analogue_channel_2].units =
                        "m" + AttysComm.CHANNEL_UNITS[AttysComm.INDEX_Analogue_channel_2];
                break;
            case ADC2Settings.MODE_AC_UV:
            case ADC2Settings.MODE_DC_UV:
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Ch1 BIO mode: µV instead of V");
                }
                sensorAppearanceResources[AttysComm.INDEX_Analogue_channel_2].units =
                        "µ" + AttysComm.CHANNEL_UNITS[AttysComm.INDEX_Analogue_channel_2];
                break;
            case ADC2Settings.MODE_RESISTANCE:
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Ch1 R mode: Ohm instead of V");
                }
                sensorAppearanceResources[AttysComm.INDEX_Analogue_channel_2].units =
                        "Ohm";
                break;
            case ADC2Settings.MODE_AC:
            case ADC2Settings.MODE_DC:
            default:
                sensorAppearanceResources[AttysComm.INDEX_Analogue_channel_2].units =
                        AttysComm.CHANNEL_UNITS[AttysComm.INDEX_Analogue_channel_2];
                break;
        }

        // acc
        sensorAppearanceResources[AttysComm.INDEX_Acceleration_X].iconId = R.drawable.ic_attys_acc_x;
        sensorAppearanceResources[AttysComm.INDEX_Acceleration_Y].iconId = R.drawable.ic_attys_acc_y;
        sensorAppearanceResources[AttysComm.INDEX_Acceleration_Z].iconId = R.drawable.ic_attys_acc_z;

        // mag
        sensorAppearanceResources[AttysComm.INDEX_Magnetic_field_X].iconId = R.drawable.ic_attys_acc_x;
        sensorAppearanceResources[AttysComm.INDEX_Magnetic_field_Y].iconId = R.drawable.ic_attys_acc_y;
        sensorAppearanceResources[AttysComm.INDEX_Magnetic_field_Z].iconId = R.drawable.ic_attys_acc_z;

        // ADC
        sensorAppearanceResources[AttysComm.INDEX_Analogue_channel_1].iconId = R.drawable.ic_attys_channel1_bold;
        sensorAppearanceResources[AttysComm.INDEX_Analogue_channel_2].iconId = R.drawable.ic_attys_channel2_bold;

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "ch1/dim=" + sensorAppearanceResources[AttysComm.INDEX_Analogue_channel_1].units);
        }
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "ch2/dim=" + sensorAppearanceResources[AttysComm.INDEX_Analogue_channel_2].units);
        }
    }

    @Override
    public void onCreate() {
        // we create an array of observers and listeners for every sensor
        // of the attys, This then allows to propagate error conditions
        // to all sensors at the same time (i.e. connection loss) and
        // check when we can close the bluetooth connection once all
        // sensors have been disconnected.
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Creating service");
        }
        observer = new ISensorObserver[AttysComm.NCHANNELS];
        listener = new ISensorStatusListener[AttysComm.NCHANNELS];
        for (int i = 0; i < AttysComm.NCHANNELS; i++) {
            observer[i] = null;
            listener[i] = null;
        }
        highpass = new Highpass[2];
        notch = new Butterworth[2];
        for (int i = 0; i < 2; i++) {
            highpass[i] = new Highpass();
            notch[i] = null;
        }
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Destroying service");
        }
        killAttys();
    }


    private synchronized void startObservingSync(final int sensorIndex,
                                                 final ISensorObserver theObserver,
                                                 final ISensorStatusListener theListener,
                                                 String settingsKey) throws RemoteException {

        // we keep all open listeners and observers in an array so
        // that they can be served at once from the Attys callback
        // when a new set of samples arrives
        theListener.onSensorConnecting();

        listener[sensorIndex] = theListener;
        observer[sensorIndex] = theObserver;


        int adc1Mode = ADC1Settings.getIndexForMode(Attys2ScienceJournal.this);
        final int adc2Mode = ADC2Settings.getIndexForMode(Attys2ScienceJournal.this);

        int[] adcpowerline = {
                ADC1Settings.getIndexForPowerline(Attys2ScienceJournal.this),
                ADC2Settings.getIndexForPowerline(Attys2ScienceJournal.this)
        };

        BluetoothDevice bluetoothDevice = findPairedAttys();
        if (bluetoothDevice == null) {
            theListener.onSensorError("No paired Attys available");
            return;
        }

        // are we the first sensor? Then let's start a proper connection
        if ((attysComm == null)) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "First sensor:" + sensorIndex + " we open the connection!");
                Log.d(TAG, "Service hash: " + getClass().hashCode());
            }
            theListener.onSensorConnecting();

            attysComm = new AttysComm(bluetoothDevice);
            attysComm.setAdc_samplingrate_index(AttysComm.ADC_RATE_125HZ);
            samplingInterval = 1000.0 / attysComm.getSamplingRateInHz();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "SamplingInterval: " + samplingInterval);
            }
            attysComm.setAccel_full_scale_index(AttysComm.ACCEL_16G);

            attysComm.setAdc1_gain_index(AttysComm.ADC_GAIN_1);
            attysComm.setAdc2_gain_index(AttysComm.ADC_GAIN_1);

            for (int i = 0; i < 2; i++) {
                switch (adcpowerline[i]) {
                    case ADC1Settings.POWERLINE_FILTER_50HZ:
                        notch[i] = new Butterworth();
                        notch[i].bandStop(2, (double) attysComm.getSamplingRateInHz(), 50, 5);
                        break;
                    case ADC1Settings.POWERLINE_FILTER_60HZ:
                        notch[i] = new Butterworth();
                        notch[i].bandStop(2, (double) attysComm.getSamplingRateInHz(), 60, 5);
                        break;
                    default:
                        notch[i] = null;
                }
                highpass[i].setAlpha(1.0F / attysComm.getSamplingRateInHz());
            }
            switch (adc1Mode) {
                case ADC1Settings.MODE_AC:
                case ADC1Settings.MODE_AC_MV:
                case ADC1Settings.MODE_AC_UV:
                    highpass[0].setActive(true);
                    break;
                case ADC1Settings.MODE_DC:
                case ADC1Settings.MODE_DC_MV:
                case ADC1Settings.MODE_DC_UV:
                default:
                    highpass[0].setActive(false);
                    break;
            }
            switch (adc2Mode) {
                case ADC2Settings.MODE_AC:
                case ADC2Settings.MODE_AC_MV:
                case ADC2Settings.MODE_AC_UV:
                    highpass[1].setActive(true);
                    break;
                case ADC2Settings.MODE_DC:
                case ADC2Settings.MODE_DC_MV:
                case ADC2Settings.MODE_DC_UV:
                default:
                    highpass[1].setActive(false);
                    break;
            }
            switch (adc1Mode) {
                case ADC1Settings.MODE_AC_MV:
                case ADC1Settings.MODE_DC_MV:
                    attysComm.setAdc1_gain_index(AttysComm.ADC_GAIN_6);
                    gainFactor[AttysComm.INDEX_Analogue_channel_1] = 1000;
                    break;
                case ADC1Settings.MODE_AC_UV:
                case ADC1Settings.MODE_DC_UV:
                    attysComm.setAdc1_gain_index(AttysComm.ADC_GAIN_12);
                    gainFactor[AttysComm.INDEX_Analogue_channel_1] = 1000000;
                    break;
                default:
                    attysComm.setAdc1_gain_index(AttysComm.ADC_GAIN_1);
                    gainFactor[AttysComm.INDEX_Analogue_channel_1] = 1;
                    break;
            }

            switch (adc2Mode) {
                case ADC2Settings.MODE_AC_MV:
                case ADC2Settings.MODE_DC_MV:
                    attysComm.setAdc2_gain_index(AttysComm.ADC_GAIN_6);
                    gainFactor[AttysComm.INDEX_Analogue_channel_2] = 1000;
                    break;
                case ADC1Settings.MODE_AC_UV:
                case ADC1Settings.MODE_DC_UV:
                    attysComm.setAdc2_gain_index(AttysComm.ADC_GAIN_12);
                    gainFactor[AttysComm.INDEX_Analogue_channel_2] = 1000000;
                    break;
                case ADC2Settings.MODE_RESISTANCE:
                    attysComm.setAdc2_gain_index(AttysComm.ADC_GAIN_1);
                    attysComm.setBiasCurrent(AttysComm.ADC_CURRENT_22UA);
                    attysComm.enableCurrents(false, false, true);
                    break;
                default:
                    attysComm.enableCurrents(false, false, false);
                    attysComm.setAdc2_gain_index(AttysComm.ADC_GAIN_1);
                    gainFactor[AttysComm.INDEX_Analogue_channel_2] = 1;
                    break;
            }

            AttysComm.DataListener dataListener = new AttysComm.DataListener() {
                @Override
                public void gotData(long samplenumber, float[] data) {
                    // Log.d(TAG, String.format("Got data: timestamp=%d",
                    //                                       timestamp));
                    if (timestamp == 0) {
                        timestamp = (double) System.currentTimeMillis();
                    }
                    for (int i = 0; i < AttysComm.NCHANNELS; i++) {
                        if (observer[i] != null) {
                            try {
                                float v = data[i];
                                if (i == AttysComm.INDEX_Analogue_channel_1) {
                                    if (notch[0] != null) {
                                        v = (float) notch[0].filter((double) v);
                                    }
                                    v = highpass[0].filter(v);
                                    //Log.d(TAG, String.format("%f,%f",
                                    //        timestamp, v));
                                }
                                if (i == AttysComm.INDEX_Analogue_channel_2) {
                                    if (notch[1] != null) {
                                        v = (float) notch[1].filter((double) v);
                                        //Log.d(TAG,""+v);
                                    }
                                    v = highpass[1].filter(v);
                                    if (adc2Mode == ADC2Settings.MODE_RESISTANCE) {
                                        v = v / 22E-6F;
                                    }
                                }
                                try {
                                    observer[i].onNewData(
                                            Math.round(timestamp),
                                            v * gainFactor[i]);
                                } catch (DeadObjectException e) {
                                    killAttys();
                                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                                        Log.d(TAG, "onNewData DeadObject:", e);
                                    }
                                }
                            } catch (RemoteException e) {
                                if (Log.isLoggable(TAG, Log.ERROR)) {
                                    Log.e(TAG, "onNewData exception:", e);
                                }
                            }
                        }
                    }

                    double timeNow = System.currentTimeMillis();
                    // let see if we drift apart which happens because
                    // the clock in the Attys might be slightly
                    // faster or slower
                    // so if the timestamp is lagging behind the
                    // system time we speed up our timestamp a bit
                    double timeDiff = timeNow - timestamp;

                    // let's gently stay in sync with the system
                    // time but let the ADC clock dominate the
                    // timing because we know that they arrive at
                    // the sampling rate (+/- a small drift)
                    double offset = 0.1 * timeDiff / ((double) attysComm.getSamplingRateInHz());

                    // Log.d(TAG, String.format("offset=%f,timeDiff=%f", offset, timeDiff));

                    timestamp = timestamp + samplingInterval + offset;
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
        } else {
            // all good already
            theListener.onSensorConnected();
        }
    }


    synchronized void stopObservingSync(int sensorIndex) throws RemoteException {
        if (listener[sensorIndex] != null) {
            listener[sensorIndex].onSensorDisconnected();
            listener[sensorIndex] = null;
        }
        observer[sensorIndex] = null;

        // check if we observe something on another sensor
        for (int i = 0; i < AttysComm.NCHANNELS; i++) {
            if (observer[i] != null) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Keep connection alive.");
                }
                return;
            }
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Shutting down the connection.");
        }
        killAttys();
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
            Log.d(TAG, "findPairedAttys");
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

    private void killAttys() {
        if (attysComm != null) {
            attysComm.cancel();
            try {
                attysComm.join();
            } catch (InterruptedException e) {
            }
            attysComm = null;
        }
        timestamp = 0;
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Attys killed");
        }
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

                BluetoothDevice bluetoothDevice = findPairedAttys();

                if (bluetoothDevice == null) {
                    return;
                }

                // added just a dummy
                PendingIntent settingsIntent = PendingIntent.getActivity(
                        getApplicationContext(),
                        0,
                        new Intent(),
                        PendingIntent.FLAG_UPDATE_CURRENT);

                setAppearance();

                for (int i = 0; i < AttysComm.NCHANNELS; i++) {
                    String loggingID = "".format("Attys,%d,%s",
                            i, AttysComm.CHANNEL_DESCRIPTION[i]);
                    String channelDescr = "ATTYS " + AttysComm.CHANNEL_DESCRIPTION[i];
                    behaviour[i].loggingId = loggingID;
                    behaviour[i].settingsIntent = settingsIntent;
                    if (i == AttysComm.INDEX_Analogue_channel_1) {
                        behaviour[i].settingsIntent =
                                ADC1Settings.getPendingIntent(
                                        Attys2ScienceJournal.this);
                    }
                    if (i == AttysComm.INDEX_Analogue_channel_2) {
                        behaviour[i].settingsIntent =
                                ADC2Settings.getPendingIntent(
                                        Attys2ScienceJournal.this);
                    }
                    c.onSensorFound("" + i, // sensorAddress = ch index number
                            channelDescr, // name
                            behaviour[i],
                            sensorAppearanceResources[i]);
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

                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "Start observing on sensorID:" + sensorIndex);
                        }

                        if (observer[sensorIndex] != null) return;
                        if (listener[sensorIndex] != null) return;

                        startObservingSync(sensorIndex, theObserver, theListener, settingsKey);

                    }

                    @Override
                    public void stopObserving(String sensorId) throws RemoteException {
                        int sensorIndex = Integer.valueOf(sensorId);

                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, String.format("shutting down sensor %d.", sensorIndex));
                        }

                        stopObservingSync(sensorIndex);
                    }
                };
            }
        };
    }
}
