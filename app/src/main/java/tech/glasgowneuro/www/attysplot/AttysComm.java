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
 */

package tech.glasgowneuro.www.attysplot;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
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

    public final static int NCHANNELS = 8;

    // index numbers of the channels returned in the data array
    public static final int INDEX_Acceleration_X = 0;
    public static final int INDEX_Acceleration_Y = 1;
    public static final int INDEX_Acceleration_Z = 2;
    public static final int INDEX_Magnetic_field_X = 3;
    public static final int INDEX_Magnetic_field_Y = 4;
    public static final int INDEX_Magnetic_field_Z = 5;
    public static final int INDEX_Analogue_channel_1 = 6;
    public static final int INDEX_Analogue_channel_2 = 7;

    // descriptions the channels in text form
    public final static String[] CHANNEL_DESCRIPTION = {
            "Acceleration X",
            "Acceleration Y",
            "Acceleration Z",
            "Magnetic field X",
            "Magnetic field Y",
            "Magnetic field Z",
            "Analogue channel 1",
            "Analogue channel 2"
    };

    // units of the channels
    public final static String[] CHANNEL_UNITS = {
            "m/s^2",
            "m/s^2",
            "m/s^2",
            "T",
            "T",
            "T",
            "V",
            "V"
    };

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
        return ADC_SAMPLINGRATE[adc_rate_index];
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

    public void setAdc1_gain_index(byte idx) {
        adc0_gain_index = idx;
    }

    public void setAdc2_gain_index(byte idx) {
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
    public final static float oneG = 9.80665F; // m/s^2
    public final static float[] ACCEL_FULL_SCALE = {2 * oneG, 4 * oneG, 8 * oneG, 16 * oneG}; // m/s^2
    private byte accel_full_scale_index = ACCEL_16G;

    public float getAccelFullScaleRange() {
        return ACCEL_FULL_SCALE[accel_full_scale_index];
    }

    public void setAccel_full_scale_index(byte idx) {
        accel_full_scale_index = idx;
    }


    ///////////////////////////////////////////////////
    // magnetometer
    //
    public final static float MAG_FULL_SCALE = 4800.0E-6F; // TESLA

    public float getMagFullScaleRange() {
        return MAG_FULL_SCALE;
    }


    /////////////////////////////////////////////////////
    // data separator when saving it to the SD card
    public final static byte DATA_SEPARATOR_TAB = 0;
    public final static byte DATA_SEPARATOR_COMMA = 1;
    public final static byte DATA_SEPARATOR_SPACE = 2;
    private byte data_separator = DATA_SEPARATOR_TAB;

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
    public final static int MESSAGE_CONNECTING = 6;

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
    public AttysComm(BluetoothDevice _device) {

        bluetoothDevice = _device;

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

    }


    ///////////////////////////////////////////////////////
    // from here it's private
    private BluetoothSocket mmSocket = null;
    private Scanner inScanner = null;
    private boolean doRun = false;
    private float[][] ringBuffer = null;
    final private int nMem = 1000;
    private int inPtr = 0;
    private int outPtr = 0;
    private boolean isConnected = false;
    private InputStream mmInStream = null;
    private OutputStream mmOutStream = null;
    private static final String TAG = "AttysComm";
    private boolean fatalError = false;
    private byte[] adcMuxRegister = null;
    private byte[] adcGainRegister = null;
    private boolean[] adcCurrNegOn = null;
    private boolean[] adcCurrPosOn = null;
    private byte expectedTimestamp = 0;
    private boolean correctTimestampDifference = false;
    private PrintWriter textdataFileStream = null;
    private double timestamp = 0.0; // in secs
    private boolean connectionEstablished;
    private BluetoothDevice bluetoothDevice;

    // standard SPP uid
    UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    public void connectToAttys() throws IOException {

        connectionEstablished = false;
        mmSocket = null;

        if (bluetoothDevice == null) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "Bluetooth device is null.");
            }
            throw new IOException();
        }

        if (bluetoothDevice == null) throw new IOException();
        // Get a BluetoothSocket to connect with the given BluetoothDevice

        if (messageListener != null) {
            messageListener.haveMessage(MESSAGE_CONNECTING);
        }

        try {
            mmSocket = bluetoothDevice.createRfcommSocketToServiceRecord(uuid);
        } catch (Exception ex) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Could not get rfComm socket:", ex);
            }
            try {
                mmSocket.close();
            } catch (Exception closeExeption) {
            }
            mmSocket = null;
            throw new IOException(ex);
        }

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "Got rfComm socket!");
        }

        try {
            sleep(100);
        } catch (Exception esleep) {
        }

        if (mmSocket != null) {
            try {
                if (mmSocket != null) {
                    mmSocket.connect();
                }
            } catch (IOException connectException) {

                // connection failed
                if (messageListener != null) {
                    messageListener.haveMessage(MESSAGE_RETRY);
                }

                try {
                    if (mmSocket != null) {
                        mmSocket.close();
                    }
                } catch (IOException e1) {
                }

                // let's just wait a bit
                try {
                    sleep(100);
                } catch (InterruptedException e1) {
                }

                try {
                    mmSocket = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(uuid);
                } catch (Exception ex) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Could not get rfComm socket:", ex);
                    }
                    try {
                        if (mmSocket != null) {
                            mmSocket.close();
                        }
                    } catch (Exception closeExeption) {
                    }
                    mmSocket = null;
                    throw new IOException(ex);
                }

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
                        throw new IOException(e);
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
                            if (mmSocket != null) {
                                mmSocket.close();
                            }
                            mmSocket = null;
                        } catch (IOException e) {
                        }

                        connectionEstablished = false;
                        fatalError = true;
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "Could not establish connection to Attys: " +
                                    e4.getMessage());
                        }
                        throw new IOException(e4);
                    }
                }
            }
            connectionEstablished = true;
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Connected to socket!");
            }
        }
    }


    // brute force stop of the Attys
    // bluetooth is so terrible in full duplex that this is required!

    private synchronized void stopADC() throws IOException {
        String s = "\r\n\r\n\r\nx=0\r";
        byte[] bytes = s.getBytes();
        if (mmSocket != null) {
            if (!mmSocket.isConnected()) throw new IOException();
        }
        for (int j = 0; j < 100; j++) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Trying to stop the data acquisition. Attempt #" + (j + 1) + ".");
            }
            try {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "flushing stream");
                }
                if (mmOutStream == null) throw new IOException();
                mmOutStream.flush();
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Sending x=0");
                }
                if (mmOutStream == null) throw new IOException();
                mmOutStream.write(bytes);
                if (mmOutStream == null) throw new IOException();
                mmOutStream.flush();
            } catch (IOException e) {
                if (Log.isLoggable(TAG, Log.ERROR)) {
                    Log.e(TAG, "Could not send 'x=0' (=stop requ) to the Attys:" + e.getMessage());
                }
                throw new IOException(e);
            }
            if (inScanner == null) return;
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


    private synchronized void startADC() throws IOException {
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
            throw new IOException(e);
        }
    }


    private synchronized void sendSyncCommand(String s) throws IOException {
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
                Log.e(TAG, "Could not send: " + s);
            }
            throw new IOException(e);
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
        throw new IOException();
    }


    private synchronized void sendSamplingRate() throws IOException {
        sendSyncCommand("r=" + adc_rate_index);
    }

    private synchronized void sendFullscaleAccelRange() throws IOException {
        sendSyncCommand("t=" + accel_full_scale_index);
    }

    private synchronized void sendCurrentMask() throws IOException {
        sendSyncCommand("c=" + current_mask);
    }

    private synchronized void sendBiasCurrent() throws IOException {
        sendSyncCommand("i=" + current_index);
    }

    private synchronized void sendGainMux(int channel, byte gain, byte mux) throws IOException {
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

    private synchronized void setADCGain(int channel, byte gain) throws IOException {
        sendGainMux(channel, gain, adcMuxRegister[channel]);
    }

    private synchronized void setADCMux(int channel, byte mux) throws IOException {
        sendGainMux(channel, adcGainRegister[channel], mux);
    }

    private synchronized void sendInit() throws IOException {
        stopADC();
        // switching to base64 encoding
        sendSyncCommand("d=1");
        sendSamplingRate();
        sendFullscaleAccelRange();
        sendGainMux(0, adc0_gain_index, adc0_mux_index);
        sendGainMux(1, adc1_gain_index, adc1_mux_index);
        sendCurrentMask();
        sendBiasCurrent();
        startADC();
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
        String tmp = String.format("%f%c", timestamp, s);
        for (int i = 0; i < (data.length - 1); i++) {
            tmp = tmp + String.format("%f%c", data[i], s);
        }
        tmp = tmp + String.format("%f", data[data.length - 1]);

        if (textdataFileStream != null) {
            textdataFileStream.format("%s\n",tmp);
        }
    }


    public void run() {

        long[] data = new long[NCHANNELS];
        byte[] raw = null;
        float[] sample = new float[NCHANNELS];

        int nTrans = 1;

        try {
            connectToAttys();
            if (mmSocket != null) {
                mmInStream = mmSocket.getInputStream();
                mmOutStream = mmSocket.getOutputStream();
                inScanner = new Scanner(mmInStream);
                ringBuffer = new float[nMem][NCHANNELS];
                isConnected = true;
            }
        } catch (IOException e) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "Could not get streams", e);
            }
            isConnected = false;
            fatalError = true;
            mmInStream = null;
            mmOutStream = null;
            inScanner = null;
            ringBuffer = null;
            if (messageListener != null) {
                messageListener.haveMessage(MESSAGE_ERROR);
            }
            return;
        }

        // we only enter in the main loop if we have connected
        doRun = isConnected;

        if (messageListener != null) {
            messageListener.haveMessage(MESSAGE_CONFIGURE);
        }

        try {
            sendInit();
        } catch (IOException e) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "Attys init failure");
            }
            if (messageListener != null) {
                messageListener.haveMessage(MESSAGE_ERROR);
            }
            return;
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Starting main data acquistion loop");
        }
        if (messageListener != null) {
            messageListener.haveMessage(MESSAGE_CONNECTED);
        }
        // Keep listening to the InputStream until an exception occurs
        while (doRun) {
            try {
                // Read from the InputStream
                if ((inScanner != null) && inScanner.hasNextLine()) {
                    // get a line from the Attys
                    String oneLine;
                    if (inScanner != null) {
                        oneLine = inScanner.nextLine();
                        //Log.d(TAG, oneLine);
                    } else {
                        return;
                    }
                    if (!oneLine.equals("OK")) {
                        // we have a real sample
                        try {
                            raw = Base64.decode(oneLine, Base64.DEFAULT);

                            for (int i = 0; i < 2; i++) {
                                long v = (raw[i * 3] & 0xff)
                                        | ((raw[i * 3 + 1] & 0xff) << 8)
                                        | ((raw[i * 3 + 2] & 0xff) << 16);
                                data[INDEX_Analogue_channel_1 + i] = v;
                            }

                            for (int i = 0; i < 6; i++) {
                                long v = (raw[8 + i * 2] & 0xff)
                                        | ((raw[8 + i * 2 + 1] & 0xff) << 8);
                                data[i] = v;
                            }

                            /**
                             Log.d(TAG,String.format("%d,%d,%d, %d,%d,%d, %d,%d",
                             data[0],data[1],data[2],
                             data[3],data[4],data[5],
                             data[6],data[7]));
                             **/

                            // check that the timestamp is the expected one
                            byte ts = 0;
                            nTrans = 1;
                            if (raw.length > 8) {
                                ts = raw[7];
                                if ((ts - expectedTimestamp) > 0) {
                                    if (correctTimestampDifference) {
                                        nTrans = 1 + (ts - expectedTimestamp);
                                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                                            Log.d(TAG, String.format("Timestamp=%s,expected=%d",
                                                    ts, expectedTimestamp));
                                        }
                                    } else {
                                        correctTimestampDifference = true;
                                    }
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
                        for (int i = AttysComm.INDEX_Acceleration_X;
                             i <= AttysComm.INDEX_Acceleration_Z; i++) {
                            float norm = 0x8000;
                            try {
                                sample[i] = ((float) data[i] - norm) / norm *
                                        getAccelFullScaleRange();
                            } catch (Exception e) {
                                Log.d(TAG, "Acc conv err");
                                sample[i] = 0;
                            }
                        }

                        // magnetometer
                        for (int i = AttysComm.INDEX_Magnetic_field_X;
                             i <= AttysComm.INDEX_Magnetic_field_Z; i++) {
                            float norm = 0x8000;
                            try {
                                sample[i] = ((float) data[i] - norm) / norm *
                                        MAG_FULL_SCALE;
                                //Log.d(TAG,"i="+i+","+sample[i]);
                            } catch (Exception e) {
                                Log.d(TAG, "Mag conv err");
                                sample[i] = 0;
                            }
                        }

                        for (int i = AttysComm.INDEX_Analogue_channel_1;
                             i <= AttysComm.INDEX_Analogue_channel_2; i++) {
                            float norm = 0x800000;
                            try {
                                sample[i] = ((float) data[i] - norm) / norm *
                                        ADC_REF / ADC_GAIN_FACTOR[adcGainRegister[i
                                        - AttysComm.INDEX_Analogue_channel_1]];
                            } catch (Exception e) {
                                sample[i] = 0;
                            }
                        }

                        // in case a sample has been lost
                        for (int j = 0; j < nTrans; j++) {
                            if (textdataFileStream != null) {
                                saveData(sample);
                            }

                            if (dataListener != null) {
                                dataListener.gotData(sampleNumber, sample);
                            }
                            if (ringBuffer != null) {
                                System.arraycopy(sample, 0, ringBuffer[inPtr], 0, sample.length);
                            }
                            timestamp = timestamp + 1.0 / getSamplingRateInHz();
                            sampleNumber++;
                            inPtr++;
                            if (inPtr == nMem) {
                                inPtr = 0;
                            }
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

    private synchronized void sendMasterReset() {
        String s = "\r\n\r\n\r\nm=1\r";
        byte[] bytes = s.getBytes();
        try {
            mmOutStream.write(bytes);
        } catch (IOException e) {
        }
    }

    /* Call this from the main activity to shutdown the connection */
    public synchronized void cancel() {
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
            } catch (Exception e) {
            }
        }
        mmSocket = null;
        isConnected = false;
        fatalError = false;
        mmInStream = null;
        mmOutStream = null;
        inScanner = null;
        ringBuffer = null;
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Stopped data acquisition. All streams have been shut down successfully.");
        }
    }
}
