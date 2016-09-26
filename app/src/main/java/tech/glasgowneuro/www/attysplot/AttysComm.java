/**
 * Copyright 2016 Bernd Porr, mail@berndporr.me.uk
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p>
 * Modified code from:
 * https://developer.android.com/guide/topics/connectivity/bluetooth.html
 * <p>
 * Modified code from:
 * https://developer.android.com/guide/topics/connectivity/bluetooth.html
 * <p>
 * Modified code from:
 * https://developer.android.com/guide/topics/connectivity/bluetooth.html
 * <p>
 * Modified code from:
 * https://developer.android.com/guide/topics/connectivity/bluetooth.html
 */

/**
 * Modified code from:
 * https://developer.android.com/guide/topics/connectivity/bluetooth.html
 */

package tech.glasgowneuro.www.attysplot;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.ParcelUuid;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Scanner;
import java.util.UUID;


/**
 * Created by Bernd Porr on 14/08/16.
 */
public class AttysComm extends Thread {


    ///////////////////////////////////////////////////////////////////
    // ADC sampling rate and for the whole system
    public final static byte ADC_RATE_125HZ = 0;
    public final static byte ADC_RATE_250HZ = 1;
    public final static byte ADC_RATE_500Hz = 2;
    public final static byte ADC_DEFAULT_RATE = ADC_RATE_250HZ;
    // array of the sampling rates converting the index
    // to the actual sampling rate
    public final static int[] ADC_SAMPLINGRATE = {125, 250, 500, 1000};
    // the actual sampling rate in terms of the sampling rate index
    private byte adc_rate_index = ADC_DEFAULT_RATE;
    public void setAdc_samplingrate_index(byte idx) {
        adc_rate_index = idx;
    }
    // get the sampling rate in Hz (not index number)
    public int getSamplingRateInHz() {
        return ADC_SAMPLINGRATE[adcSamplingRate];
    }
    public byte getAdc_samplingrate_index() {
        return adc_rate_index;
    }



    ////////////////////////////////////////////////////////////////////////////
    // ADC gain
    // the strange numbering scheme comes from the ADC's numbering
    // scheme. Index=0 is really a gain factor of 6
    // On the ATttys we refer to channel 1 and 2 which are 0 and 1 here for
    // indexing.
    public final static byte ADC_GAIN_6 = 0;
    public final static byte ADC_GAIN_1 = 1;
    public final static byte ADC_GAIN_2 = 2;
    public final static byte ADC_GAIN_3 = 3;
    public final static byte ADC_GAIN_4 = 4;
    public final static byte ADC_GAIN_8 = 5;
    public final static byte ADC_GAIN_12 = 6;
    // mapping between index and actual gain
    public final static int[] ADC_GAIN_FACTOR = {6, 1, 2, 3, 4, 8, 12};
    // the voltage reference of the ADC in volts
    public final static float ADC_REF = 2.42F;
    public float getADCFullScaleRange(int channel) {
        return ADC_REF / ADC_GAIN_FACTOR[adcGainRegister[channel]];
    }
    public void setAdc0_gain_index(byte idx) {
        adc0_gain_index = idx;
    }
    public void setAdc1_gain_index(byte idx) {
        adc1_gain_index = idx;
    }
    // initial gain factor is 6 for both channels
    private byte adc0_gain_index = 0;
    private byte adc1_gain_index = 0;



    /////////////////////////////////////////////////////////////////////
    // Bias currents for resistance measurement
    // selectable bias current index numbers for the ADC inputs
    // used to measure resistance
    public final static byte ADC_CURRENT_6NA = 0;
    public final static byte ADC_CURRENT_22NA = 1;
    public final static byte ADC_CURRENT_6UA = 2;
    public final static byte ADC_CURRENT_22UA = 3;
    private byte current_index = 0;
    private byte current_mask = 0;
    // sets the bias current which can be switched on
    public void setBiasCurrent(byte currIndex) {
        current_index = currIndex;
    }
    // gets the bias current as in index
    public byte getBiasCurrent() {
        return current_index;
    }
    // switches the currents on
    public void enableCurrents(boolean pos_ch1, boolean neg_ch1, boolean pos_ch2) {
        current_mask = 0;
        if (pos_ch1) {
            current_mask = (byte) (current_mask | (byte) 0b00000001);
        }
        if (neg_ch1) {
            current_mask = (byte) (current_mask | (byte) 0b00000010);
        }
        if (pos_ch2) {
            current_mask = (byte) (current_mask | (byte) 0b00000100);
        }
    }



    //////////////////////////////////////////////////////////////////////////////
    // selectable different input mux settings
    // for the ADC channels
    public final static byte ADC_MUX_NORMAL = 0;
    public final static byte ADC_MUX_SHORT = 1;
    public final static byte ADC_MUX_SUPPLY = 3;
    public final static byte ADC_MUX_TEMPERATURE = 4;
    public final static byte ADC_MUX_TEST_SIGNAL = 5;
    public final static byte ADC_MUX_ECG_EINTHOVEN = 6;
    private byte adc0_mux_index = ADC_MUX_NORMAL;
    private byte adc1_mux_index = ADC_MUX_NORMAL;
    public void setAdc0_mux_index(byte idx) {
        adc0_mux_index = idx;
    }
    public void setAdc1_mux_index(byte idx) {
        adc1_mux_index = idx;
    }



    ///////////////////////////////////////////////////////////////////////////////
    // accelerometer
    public final static byte ACCEL_2G = 0;
    public final static byte ACCEL_4G = 1;
    public final static byte ACCEL_8G = 2;
    public final static byte ACCEL_16G = 3;
    private final static float oneG = 9.80665F;
    public final static float[] ACCEL_FULL_SCALE = {2*oneG, 4*oneG, 8*oneG, 16*oneG}; // m/s^2
    private byte accel_full_scale_index = ACCEL_16G;
    public float getAccelFullScaleRange() {
        return ACCEL_FULL_SCALE[accel_full_scale_index];
    }
    public void setAccel_full_scale_index(byte idx) {
        accel_full_scale_index = idx;
    }



    ////////////////////////////////////////////////////////////////////////////////
    // gyroscope
    public final static float[] GYRO_FULL_SCALE = {250, 500, 1000, 2000}; //DPS
    public final static byte GYRO_250DPS = 0;
    public final static byte GYRO_500DPS = 1;
    public final static byte GYRO_1000DPS = 2;
    public final static byte GYRO_2000DPS = 3;
    private byte gyro_full_scale_index = GYRO_2000DPS;
    public float getGyroFullScaleRange() {
        return GYRO_FULL_SCALE[gyro_full_scale_index];
    }
    public void setGyro_full_scale_index(byte idx) {
        gyro_full_scale_index = idx;
    }

    public final static float MAG_FULL_SCALE = 4800.0E-6F; // TESLA
    public float getMagFullScaleRange() {
        return MAG_FULL_SCALE;
    }


    /////////////////////////////////////////////////////
    // data separator when saving it to the SD card
    public final static byte DATA_SEPARATOR_SPACE = 0;
    public final static byte DATA_SEPARATOR_COMMA = 1;
    public final static byte DATA_SEPARATOR_TAB = 2;
    private byte data_separator = DATA_SEPARATOR_SPACE;

    public void setDataSeparator(byte s) {
        data_separator = s;
    }

    public byte getDataSeparator() {
        return data_separator;
    }


    ////////////////////////////////////////////////
    // timestamp stuff as double
    // note this might drift in the long run
    public void setTimestamp(double ts) {
        timestamp = ts;
    }

    public double getTimestamp() {
        return timestamp;
    }


    ////////////////////////////////////////////////
    // sample counter
    private long sampleNumber = 0;

    public long getSampleNumber() {
        return sampleNumber;
    }

    public void setSampleNumber(long sn) {
        sampleNumber = sn;
    }


    /////////////////////////////////////
    // data listener
    // provides the data with the sample number as long
    // the data array contains all the data:
    public final static String[] CHANNEL_DESCRIPTION = {
            "Acceleration X",
            "Acceleration Y",
            "Acceleration Z",
            "Rotation X",
            "Rotation Y",
            "Rotation Z",
            "Magnetic field X",
            "Magnetic field Y",
            "Magnetic field Z",
            "Analogue channel 1",
            "Analogue channel 2"
    };

    // 11 channels will be always sent back
    public final static int NCHANNELS = 11;

    public interface DataListener {
        void gotData(long samplenumber, float[] data);
    }

    private DataListener dataListener = null;

    public void registerDataListener(DataListener l) {
        dataListener = l;
    }

    public void unregisterDataListener() {
        dataListener = null;
    }


    ///////////////////////////////////////////////////////////////////////
    // message listener
    // sends error/success messages back
    // for MessageListener
    // here are the messages:
    public final static int MESSAGE_CONNECTED = 0;
    public final static int MESSAGE_ERROR = 1;
    public final static int MESSAGE_RETRY = 2;
    public final static int MESSAGE_CONFIGURE = 3;
    public final static int MESSAGE_STARTED_RECORDING = 4;
    public final static int MESSAGE_STOPPED_RECORDING = 5;

    public interface MessageListener {
        void haveMessage(int msg);
    }

    private MessageListener messageListener = null;

    public void registerMessageListener(MessageListener m) {
        messageListener = m;
    }

    public void unregisterMessageListener() {
        messageListener = null;
    }


    ////////////////////////////////////////////
    // connection info
    public boolean hasActiveConnection() {
        return isConnected;
    }
    public boolean hasFatalError() {
        return fatalError;
    }


    /////////////////////////////////////////////////
    // ringbuffer keeping data for chunk-wise plotting
    public float[] getSampleFromBuffer() {
        if (inPtr != outPtr) {
            float[] sample = null;
            if (ringBuffer != null) {
                sample = ringBuffer[outPtr];
            }
            outPtr++;
            if (outPtr == nMem) {
                outPtr = 0;
            }
            return sample;
        } else {
            return null;
        }
    }

    public boolean isSampleAvilabale() {
        return (inPtr != outPtr);
    }

    public int getNumSamplesAvilable() {
        int n = 0;
        int tmpOutPtr = outPtr;
        while (inPtr != tmpOutPtr) {
            tmpOutPtr++;
            n++;
            if (tmpOutPtr == nMem) {
                tmpOutPtr = 0;
            }
        }
        return n;
    }


    /////////////////////////////////////////////////////////////
    // saving data into a file
    // starts the recording
    public java.io.FileNotFoundException startRec(File file) {
        try {
            textdataFileStream = new PrintWriter(file);
            messageListener.haveMessage(MESSAGE_STARTED_RECORDING);
        } catch (java.io.FileNotFoundException e) {
            textdataFileStream = null;
            return e;
        }
        return null;
    }
    // stops it
    public void stopRec() {
        if (textdataFileStream != null) {
            textdataFileStream.close();
            messageListener.haveMessage(MESSAGE_STOPPED_RECORDING);
            textdataFileStream = null;
        }
    }
    // are we recording?
    public boolean isRecording() {
        return (textdataFileStream != null);
    }



    /////////////////////////////////////////////////
    // Constructor: takes the bluetooth device as an argument
    // it then tries to connect to the Attys
    public AttysComm(BluetoothDevice device) {

        adcMuxRegister = new byte[2];
        adcMuxRegister[0] = 0;
        adcMuxRegister[1] = 0;
        adcGainRegister = new byte[2];
        adcGainRegister[0] = 0;
        adcGainRegister[1] = 0;
        adcCurrNegOn = new boolean[2];
        adcCurrNegOn[0] = false;
        adcCurrNegOn[1] = false;
        adcCurrPosOn = new boolean[2];
        adcCurrPosOn[0] = false;
        adcCurrNegOn[1] = false;

        connectThread = new ConnectThread(device);
        // let's try to connect
        // b/c it's blocking we do it in another thread
        connectThread.start();

    }



    ///////////////////////////////////////////////////////
    // from here it's private
    private BluetoothSocket mmSocket;
    private Scanner inScanner = null;
    private boolean doRun;
    private float[][] ringBuffer = null;
    final private int nMem = 1000;
    private int inPtr = 0;
    private int outPtr = 0;
    private ConnectThread connectThread = null;
    private boolean isConnected = false;
    private InputStream mmInStream = null;
    private OutputStream mmOutStream = null;
    private static final String TAG = "AttysComm";
    private boolean fatalError = false;
    private byte[] adcMuxRegister = null;
    private int adcSamplingRate = ADC_DEFAULT_RATE;
    private byte[] adcGainRegister = null;
    private boolean[] adcCurrNegOn = null;
    private boolean[] adcCurrPosOn = null;
    private byte expectedTimestamp = 0;
    private PrintWriter textdataFileStream = null;
    private double timestamp = 0.0; // in secs



    private class ConnectThread extends Thread {
        private BluetoothSocket mmSocket;
        private boolean connectionEstablished;
        BluetoothDevice bluetoothDevice = null;
        // standard SPP uid
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

        public BluetoothSocket getBluetoothSocket() {
            return mmSocket;
        }

        public boolean hasActiveConnection() {
            return connectionEstablished;
        }

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            bluetoothDevice = device;
            connectionEstablished = false;

            if (device == null) {
                if (Log.isLoggable(TAG, Log.ERROR)) {
                    Log.e(TAG, "Bluetooth device is null.");
                }
            }
        }

        // this is all a bit of a mystery / woodoo
        // the connect command is terribly unreliable so we try out
        // different strategies
        public void run() {

            if (bluetoothDevice == null) return;
            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                mmSocket = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(uuid);
            } catch (Exception ex) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Could not get rfComm socket:", ex);
                }
                try {
                    mmSocket.close();
                } catch (Exception closeExeption) {
                }
                ;
                mmSocket = null;
                messageListener.haveMessage(MESSAGE_ERROR);
                return;
            }

            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Got rfComm socket!");
            }

            try {
                sleep(100);
            } catch (Exception esleep) {
            }
            ;

            if (mmSocket != null) {
                try {
                    if (mmSocket != null) {
                        mmSocket.connect();
                    }
                } catch (IOException connectException) {

                    // connection failed
                    messageListener.haveMessage(MESSAGE_RETRY);

                    try {
                        mmSocket.close();
                    } catch (IOException e1) {}

                    // let's just wait a bit
                    try {
                        sleep(100);
                    } catch (InterruptedException e1) {}

                    // let's try to connect
                    try {
                        if (mmSocket != null) {
                            mmSocket.connect();
                        }
                    } catch (IOException e2) {

                        try {
                            sleep(100);
                        } catch (InterruptedException e3) {
                        }

                        try {
                            if (Log.isLoggable(TAG, Log.DEBUG)) {
                                Log.d(TAG, "Last resort: we try the hidden API");
                            }
                            mmSocket.close();
                            mmSocket = null;
                            Method createMethod = bluetoothDevice.getClass().
                                    getMethod("createInsecureRfcommSocket", new Class[]{int.class});
                            mmSocket = (BluetoothSocket) createMethod.invoke(bluetoothDevice, 1);
                        } catch (Exception e) {
                            if (Log.isLoggable(TAG, Log.ERROR)) {
                                Log.e(TAG, "Could not get non-UUID based bluetooth socket!", e);
                            }
                            mmSocket = null;
                            return;
                        }

                        try {
                            sleep(100);
                        } catch (InterruptedException e3) {
                        }

                        try {
                            if (mmSocket != null) {
                                mmSocket.connect();
                            }
                        } catch (IOException e4) {

                            try {
                                mmSocket.close();
                                mmSocket = null;
                            } catch (IOException e) {
                            }

                            connectionEstablished = false;
                            fatalError = true;
                            if (Log.isLoggable(TAG, Log.DEBUG)) {
                                Log.d(TAG, "Could not establish connection to Attys: " +
                                        e4.getMessage());
                            }
                            messageListener.haveMessage(MESSAGE_ERROR);

                            return;
                        }
                    }
                }
                connectionEstablished = true;
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "Connected to socket!");
                }
            }
        }

        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                if (mmSocket != null) {
                    mmSocket.close();
                }
            } catch (IOException e) {
            }
            mmSocket = null;
        }
    }


    private void stopADC() {
        String s = "\r\n\r\n\r\nx=0\r";
        byte[] bytes = s.getBytes();
        for (int j = 0; j < 100; j++) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Trying to stop the data acquisition. Attempt #" + (j + 1) + ".");
            }
            try {
                if (mmOutStream != null) {
                    mmOutStream.flush();
                    mmOutStream.write(bytes);
                    mmOutStream.flush();
                }
            } catch (IOException e) {
                if (Log.isLoggable(TAG, Log.ERROR)) {
                    Log.e(TAG, "Could not send 'x=0' (=stop requ) to the Attys:" + e.getMessage());
                }
            }
            for (int i = 0; i < 100; i++) {
                if (inScanner != null) {
                    if (inScanner.hasNextLine()) {
                        if (inScanner != null) {
                            String l = inScanner.nextLine();
                            if (l.equals("OK")) {
                                if (Log.isLoggable(TAG, Log.DEBUG)) {
                                    Log.d(TAG, "ADC stopped. Now in command mode.");
                                }
                                return;
                            } else {
                                yield();
                            }
                        }
                    }
                }
            }
        }
        if (Log.isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, "Could not detect OK after x=0!");
        }
    }


    private void startADC() {
        String s = "x=1\r";
        byte[] bytes = s.getBytes();
        try {
            if (mmOutStream != null) {
                mmOutStream.flush();
                mmOutStream.write(13);
                mmOutStream.write(10);
                mmOutStream.write(bytes);
                mmOutStream.flush();
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "ADC started. Now acquiring data.");
                }
            }
        } catch (IOException e) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "Could not send x=1 to the Attys.");
            }
        }
    }


    private void sendSyncCommand(String s) {
        byte[] bytes = s.getBytes();

        try {
            if (mmOutStream != null) {
                mmOutStream.flush();
                mmOutStream.write(10);
                mmOutStream.write(13);
                mmOutStream.write(bytes);
                mmOutStream.write(13);
                mmOutStream.flush();
            } else {
                return;
            }
        } catch (IOException e) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "Could not write to stream.");
            }
        }
        for (int j = 0; j < 100; j++) {
            if (inScanner != null) {
                if (inScanner.hasNextLine()) {
                    if (inScanner != null) {
                        String l = inScanner.nextLine();
                        if (l.equals("OK")) {
                            if (Log.isLoggable(TAG, Log.DEBUG)) {
                                Log.d(TAG, "Sent successfully '" + s + "' to the Attys.");
                            }
                            return;
                        } else {
                            try {
                                sleep(10);
                            } catch (InterruptedException e) {
                            }
                        }
                    }
                }
            }
        }
        if (Log.isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, "ATTYS hasn't replied with OK after command: " + s + ".");
        }
    }


    private void sendSamplingRate() {
        sendSyncCommand("r=" + adc_rate_index);
    }

    private void sendFullscaleGyroRange() {
        sendSyncCommand("g=" + gyro_full_scale_index);
    }

    private void sendFullscaleAccelRange() {
        sendSyncCommand("t=" + accel_full_scale_index);
    }

    private void sendCurrentMask() {
        sendSyncCommand("c=" + current_mask);
    }
    private void sendBiasCurrent() {
        sendSyncCommand("i=" + current_index);
    }

    private void sendGainMux(int channel, byte gain, byte mux) {
        int v = (mux & 0x0f) | ((gain & 0x0f) << 4);
        switch (channel) {
            case 0:
                sendSyncCommand("a=" + v);
                break;
            case 1:
                sendSyncCommand("b=" + v);
                break;
        }
        adcGainRegister[channel] = gain;
        adcMuxRegister[channel] = mux;
    }

    private void setADCGain(int channel, byte gain) {
        sendGainMux(channel, gain, adcMuxRegister[channel]);
    }

    private void setADCMux(int channel, byte mux) {
        sendGainMux(channel, adcGainRegister[channel], mux);
    }

    private boolean sendInit() {
        stopADC();
        // switching to base64 encoding
        sendSyncCommand("d=1");
        sendSamplingRate();
        sendFullscaleGyroRange();
        sendFullscaleAccelRange();
        sendGainMux(0, adc0_gain_index, adc0_mux_index);
        sendGainMux(1, adc1_gain_index, adc1_mux_index);
        sendCurrentMask();
        sendBiasCurrent();
        startADC();
        return true;
    }

    private void saveData(float[] data) {
        char s = ' ';
        switch (data_separator) {
            case DATA_SEPARATOR_SPACE:
                s = ' ';
                break;
            case DATA_SEPARATOR_COMMA:
                s = ',';
                break;
            case DATA_SEPARATOR_TAB:
                s = 9;
                break;
        }
        if (textdataFileStream != null) {
            textdataFileStream.format("%f%c", timestamp, s);
            for (int i = 0; i < (data.length - 1); i++) {
                if (textdataFileStream != null) {
                    textdataFileStream.format("%f%c", data[i], s);
                }
            }
            if (textdataFileStream != null) {
                textdataFileStream.format("%f\n", data[data.length - 1]);
            }
        }
    }


    public void run() {

        long[] data = new long[12];
        byte[] raw = null;

        try {
            // let's wait until we are connected
            if (connectThread != null) {
                connectThread.join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            isConnected = false;
        }

        if (connectThread != null) {
            if (!connectThread.hasActiveConnection()) {
                isConnected = false;
                return;
            }
        } else {
            return;
        }

        try {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Getting streams");
            }
            mmSocket = connectThread.getBluetoothSocket();
            if (mmSocket != null) {
                mmInStream = mmSocket.getInputStream();
                mmOutStream = mmSocket.getOutputStream();
                inScanner = new Scanner(mmInStream);
                ringBuffer = new float[nMem][NCHANNELS];
                isConnected = true;
            }
        } catch (IOException e) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "Could not get streams");
            }
            isConnected = false;
            fatalError = true;
            mmInStream = null;
            mmOutStream = null;
            inScanner = null;
            ringBuffer = null;
            messageListener.haveMessage(MESSAGE_ERROR);
        }

        // we only enter in the main loop if we have connected
        doRun = isConnected;

        messageListener.haveMessage(MESSAGE_CONFIGURE);
        sendInit();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Starting main data acquistion loop");
        }
        messageListener.haveMessage(MESSAGE_CONNECTED);
        // Keep listening to the InputStream until an exception occurs
        while (doRun) {
            try {
                // Read from the InputStream
                if ((inScanner != null) && inScanner.hasNextLine()) {
                    // get a line from the Attys
                    String oneLine;
                    if (inScanner != null) {
                        oneLine = inScanner.nextLine();
                    } else {
                        return;
                    }
                    if (!oneLine.equals("OK")) {
                        // we have a real sample
                        try {
                            raw = Base64.decode(oneLine, Base64.DEFAULT);

                            // adc data def there
                            if (raw.length > 9) {
                                for (int i = 0; i < 2; i++) {
                                    long v = (raw[i * 4] & 0xff)
                                            | ((raw[i * 4 + 1] & 0xff) << 8)
                                            | ((raw[i * 4 + 2] & 0xff) << 16);
                                    data[9 + i] = v;
                                }
                            }

                            // all data def there
                            if (raw.length > 27) {
                                for (int i = 0; i < 9; i++) {
                                    long v = (raw[10 + i * 2] & 0xff)
                                            | ((raw[10 + i * 2 + 1] & 0xff) << 8);
                                    data[i] = v;
                                }
                            }

                            // check that the timestamp is the expected one
                            byte ts = 0;
                            if (raw.length > 8) {
                                ts = raw[9];
                                if (Math.abs(ts - expectedTimestamp) == 1) {
                                    Log.d(TAG, String.format("sample lost"));
                                    ts++;
                                }
                            }
                            // update timestamp
                            expectedTimestamp = ++ts;

                        } catch (Exception e) {
                            // this is triggered if the base64 is too short or any data is too short
                            // this leads to data processed from the previous sample instead
                            if (Log.isLoggable(TAG, Log.DEBUG)) {
                                Log.d(TAG, "reception error: " + oneLine);
                            }
                            expectedTimestamp++;
                        }

                        // acceleration
                        for (int i = 0; i < 3; i++) {
                            float norm = 0x8000;
                            try {
                                ringBuffer[inPtr][i] = ((float) data[i] - norm) / norm *
                                        getAccelFullScaleRange();
                            } catch (Exception e) {
                                ringBuffer[inPtr][i] = 0;
                            }
                        }

                        // gyroscope
                        for (int i = 3; i < 6; i++) {
                            float norm = 0x8000;
                            try {
                                ringBuffer[inPtr][i] = ((float) data[i] - norm) / norm *
                                        getGyroFullScaleRange();
                            } catch (Exception e) {
                                ringBuffer[inPtr][i] = 0;
                            }
                        }

                        // magnetometer
                        for (int i = 6; i < 9; i++) {
                            float norm = 0x8000;
                            try {
                                ringBuffer[inPtr][i] = ((float) data[i] - norm) / norm *
                                        MAG_FULL_SCALE;
                            } catch (Exception e) {
                                ringBuffer[inPtr][i] = 0;
                            }
                        }

                        for (int i = 9; i < 11; i++) {
                            float norm = 0x800000;
                            try {
                                ringBuffer[inPtr][i] = ((float) data[i] - norm) / norm *
                                        ADC_REF / ADC_GAIN_FACTOR[adcGainRegister[i - 9]];
                            } catch (Exception e) {
                                ringBuffer[inPtr][i] = 0;
                            }
                        }

                        if (textdataFileStream != null) {
                            saveData(ringBuffer[inPtr]);
                        }

                        if (dataListener != null) {
                            dataListener.gotData(sampleNumber, ringBuffer[inPtr]);
                        }

                        timestamp = timestamp + 1.0 / getSamplingRateInHz();
                        sampleNumber++;
                        inPtr++;
                        if (inPtr == nMem) {
                            inPtr = 0;
                        }

                    } else {
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "OK caught from the Attys");
                        }
                    }
                }
            } catch (Exception e) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Stream lost or closing.", e);
                }
                break;
            }
        }
    }

    /* Call this from the main activity to shutdown the connection */
    public void cancel() {
        if (connectThread != null) {
            connectThread.cancel();
        }
        doRun = false;
        if (inScanner != null) {
            inScanner.close();
        }
        if (mmInStream != null) {
            try {
                mmInStream.close();
            } catch (IOException e) {
            }
        }
        if (mmOutStream != null) {
            try {
                mmOutStream.close();
            } catch (IOException e) {
            }
        }
        if (mmSocket != null) {
            try {
                mmSocket.close();
            } catch (IOException e) {
            }
        }
        mmSocket = null;
        isConnected = false;
        fatalError = false;
        mmInStream = null;
        mmOutStream = null;
        inScanner = null;
        ringBuffer = null;
        connectThread = null;
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Stopped data acquisition. All streams have been shut down successfully.");
        }
    }
}
