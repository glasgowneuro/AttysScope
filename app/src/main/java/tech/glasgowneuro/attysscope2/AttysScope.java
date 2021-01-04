/**
 * Copyright 2016-2017 Bernd Porr, mail@berndporr.me.uk
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
 **/

package tech.glasgowneuro.attysscope2;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;

import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import tech.glasgowneuro.attyscomm.AttysComm;
import uk.me.berndporr.iirj.Butterworth;

public class AttysScope extends AppCompatActivity {

    private Timer timer = null;
    // screen refresh rate
    private final int REFRESH_IN_MS = 50;

    private final int HIGHPASSORDER = 2;

    private RealtimePlotView realtimePlotView = null;
    private InfoView infoView = null;

    // Fragments
    AmplitudeFragment amplitudeFragment = null;
    FourierFragment fourierFragment = null;
    HeartratePlotFragment heartRateFragment = null;

    // Menu checkboxes
    MenuItem menuItemHighpass1 = null;
    MenuItem menuItemHighpass2 = null;
    MenuItem menuItemMains1 = null;
    MenuItem menuItemMains2 = null;

    private AttysComm attysComm = null;
    private BluetoothDevice btAttysDevice = null;
    private byte samplingRate = AttysComm.ADC_RATE_250HZ;

    private UpdatePlotTask updatePlotTask = null;

    private static final String TAG = "AttysScope";

    public Ch2Converter ch2Converter = new Ch2Converter();

    private Butterworth[] highpass = null;
    private float[] gain;
    private Butterworth[] iirNotch;
    private double notchBW = 2.5; // Hz
    private int notchOrder = 2;
    private boolean[] invert;
    private float powerlineHz = 50;
    private float highpass1Hz = 0.1F;
    private float highpass2Hz = 0.1F;


    private boolean showAcc = false;
    private boolean showMag = false;
    private boolean showCh1 = true;
    private boolean showCh2 = true;

    private float ch1Div = 1;
    private float ch2Div = 1;

    private float magTick = 1000.0E-6F; //1000uT

    private float accTick = AttysComm.oneG; // 1G

    private int timebase = 1;

    private int tbCtr = 1;

    private int theChannelWeDoAnalysis = 0;

    private int[] actualChannelIdx;

    private int gpio0 = 0;
    private int gpio1 = 0;

    public enum TextAnnotation {
        NONE,
        PEAKTOPEAK,
        RMS,
        ECG
    }

    private SignalAnalysis signalAnalysis = null;

    private ECG_rr_det ecg_rr_det = null;

    int ygapForInfo = 0;

    private TextAnnotation textAnnotation = TextAnnotation.PEAKTOPEAK;

    // debugging the ECG detector, commented out for production
    //double ecgDetOut;

    private int timestamp = 0;

    String[] labels = {
            "Acc x", "Acc y", "Acc z",
            "Mag x", "Mag y", "Mag z",
            "ADC 1", "ADC 2"};

    String[] units = new String[AttysComm.NCHANNELS];

    private String dataFilename = null;
    private byte dataSeparator = 0;

    private static final String ATTYS_SUBDIR = "attys";

    static final File ATTYSDIR =
            new File(Environment.getExternalStorageDirectory().getPath(), ATTYS_SUBDIR);

    ProgressBar progress = null;

    AlertDialog alertDialog = null;

    // converts Ch2 data from its voltage units to resistance, temperature etc
    public class Ch2Converter {
        // the series resistance on the input pins
        final float Rbaseline = 55000;
        // rule -1 means: do nothing
        private int rule = -1;
        // cold junction tempterature for thermocouple
        float coldJunctionT = 20;

        // units as strings fo the different settings
        private String[] units = {"V", "V", "V", "\u2126", "\u2126", "\u00b0C", "\u00b0C"};

        // current settings
        private int[] currentIndex = {0, 1, 2, 1, 2, 2, -1};

        void setRule(int _rule) {
            rule = _rule;
        }

        int getRule() {
            return rule;
        }

        // converts the voltage into another unit
        public float convert(float v) {
            switch (rule) {
                case 0:
                    // 0.006uA
                case 1:
                    // 0.022uA
                case 2:
                    // 6uA
                    return v;
                case 3:
                    // resistance, 0 - 81MOhm
                    return v / 0.022E-6F - Rbaseline;
                case 4:
                    // resistance 0 - 300KOhm
                    return v / 6E-6F - Rbaseline;
                case 5:
                    // temperature, TFPTL15L5001FL2B -  PTC Thermistor, 5 kohm
                    double rt = v / 6E-6 - Rbaseline;
                    double r25 = 5000.0;
                    double t = 28.54 * Math.pow(rt / r25, 3) - 158.5 * Math.pow(rt / r25, 2) +
                            474.8 * (rt / r25) - 319.85;
                    return (float) t;
                case 6:
                    // temperature, K type thermocouple
                    return v / 39E-6F + coldJunctionT;
            }
            return v;
        }


        // the unit as a String
        public String getUnit() {
            if (rule < 0) return "V";
            return units[rule];
        }

        // max range for plotting
        public float getMaxRange() {
            switch (rule) {
                case 0:
                case 1:
                case 2:
                    return attysComm.getADCFullScaleRange(1);
                case 3:
                    return 81E6F - Rbaseline;
                case 4:
                    return 300000 - Rbaseline;
                case 5:
                    return 100;
                case 6:
                    return 1000;
            }
            return attysComm.getADCFullScaleRange(1);
        }

        // min range for plotting
        public float getMinRange() {
            switch (rule) {
                case 0:
                case 1:
                case 2:
                    return -attysComm.getADCFullScaleRange(1);
                case 3:
                case 4:
                    return 0;
                case 5:
                    return -20;
                case 6:
                    return -100;
            }
            return -attysComm.getADCFullScaleRange(1);
        }

        // ticks of the coordiante system
        public float getTick() {
            switch (rule) {
                case 0:
                case 1:
                case 2:
                    return 1;
                case 3:
                    return 10000000;
                case 4:
                    return 100000;
                case 5:
                    return 10;
                case 6:
                    return 100;
            }
            return 1;
        }

        // switches on/off the excitation current
        // -1 means it's off
        public int getCurrentIndex() {
            if (rule < 0) {
                return -1;
            }
            return currentIndex[rule];
        }
    }

    public class DataRecorder {
        /////////////////////////////////////////////////////////////
        // saving data into a file

        public final static byte DATA_SEPARATOR_TAB = 0;
        public final static byte DATA_SEPARATOR_COMMA = 1;
        public final static byte DATA_SEPARATOR_SPACE = 2;

        private PrintWriter textdataFileStream = null;
        private File textdataFile = null;
        private byte data_separator = DataRecorder.DATA_SEPARATOR_TAB;
        private File file = null;
        private long sampleNo = 0;
        private boolean gpioLogging = false;

        // starts the recording
        public void startRec(File _file) throws java.io.FileNotFoundException {
            file = _file;
            try {
                textdataFileStream = new PrintWriter(file);
                textdataFile = file;
                messageListener.haveMessage(AttysComm.MESSAGE_STARTED_RECORDING);
            } catch (java.io.FileNotFoundException e) {
                textdataFileStream = null;
                textdataFile = null;
                throw e;
            }
            sampleNo = 0;
        }

        // stops it
        public void stopRec() {
            if (textdataFileStream != null) {
                textdataFileStream.close();
                messageListener.haveMessage(AttysComm.MESSAGE_STOPPED_RECORDING);
                textdataFileStream = null;
                textdataFile = null;
                if (file != null) {
                    Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    Uri contentUri = Uri.fromFile(file);
                    mediaScanIntent.setData(contentUri);
                    sendBroadcast(mediaScanIntent);
                }
            }
        }

        // are we recording?
        public boolean isRecording() {
            return (textdataFileStream != null);
        }

        public File getFile() {
            return textdataFile;
        }

        public void setDataSeparator(byte s) {
            data_separator = s;
        }

        public void setGPIOlogging(boolean g) { gpioLogging = g; }

        private void saveData(float[] data_unfilt, float[] data_filt) {
            if (textdataFile == null) return;
            if (textdataFileStream == null) return;

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
            String tmp = String.format(Locale.US, "%f%c", (double) sampleNo / (double) attysComm.getSamplingRateInHz(), s);
            for (float aData_unfilt : data_unfilt) {
                tmp = tmp + String.format(Locale.US, "%f%c", aData_unfilt, s);
            }
            tmp = tmp + String.format(Locale.US, "%f%c", data_filt[AttysComm.INDEX_Analogue_channel_1], s);
            tmp = tmp + String.format(Locale.US, "%f", data_filt[AttysComm.INDEX_Analogue_channel_2]);

            if (gpioLogging) {
                tmp = tmp + String.format(Locale.US, "%c%f", s, data_filt[AttysComm.INDEX_GPIO0]);
                tmp = tmp + String.format(Locale.US, "%c%f", s, data_filt[AttysComm.INDEX_GPIO1]);
            }

            if (textdataFileStream != null) {
                textdataFileStream.format(Locale.US, "%s\n", tmp);
            }
            sampleNo++;
        }
    }


    DataRecorder dataRecorder = new DataRecorder();


    AttysComm.MessageListener messageListener = new AttysComm.MessageListener() {
        @Override
        public void haveMessage(final int msg) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    switch (msg) {
                        case AttysComm.MESSAGE_ERROR:
                            Toast.makeText(getApplicationContext(),
                                    "Bluetooth connection problem", Toast.LENGTH_SHORT).show();
                            if (attysComm != null) {
                                attysComm.stop();
                            }
                            progress.setVisibility(View.GONE);
                            finish();
                            break;
                        case AttysComm.MESSAGE_CONNECTED:
                            progress.setVisibility(View.GONE);
                            break;
                        case AttysComm.MESSAGE_RETRY:
                            Toast.makeText(getApplicationContext(),
                                    "Bluetooth - trying to connect. Please be patient.",
                                    Toast.LENGTH_SHORT).show();
                            break;
                        case AttysComm.MESSAGE_STARTED_RECORDING:
                            Toast.makeText(getApplicationContext(),
                                    "Started recording data to external storage.",
                                    Toast.LENGTH_SHORT).show();
                            break;
                        case AttysComm.MESSAGE_STOPPED_RECORDING:
                            Toast.makeText(getApplicationContext(),
                                    "Finished recording data to external storage.",
                                    Toast.LENGTH_SHORT).show();
                            break;
                        case AttysComm.MESSAGE_CONNECTING:
                            progress.setVisibility(View.VISIBLE);
                    }
                }
            });
        }
    };


    private class UpdatePlotTask extends TimerTask {

        private String m_unit = "";

        private void resetAnalysis() {
            realtimePlotView.resetX();
            if (textAnnotation == TextAnnotation.NONE) {
                annotatePlot(null);
                return;
            }
            signalAnalysis.reset();
            ecg_rr_det.reset();

            m_unit = units[theChannelWeDoAnalysis];

            if ((theChannelWeDoAnalysis == AttysComm.INDEX_Magnetic_field_X) ||
                    (theChannelWeDoAnalysis == AttysComm.INDEX_Magnetic_field_Y) ||
                    (theChannelWeDoAnalysis == AttysComm.INDEX_Magnetic_field_Z)) {
                m_unit = "\u00b5" + m_unit;
            }

            annotatePlot("---------------");
        }

        public void annotatePlot(String largeText) {
            String small = String.format(Locale.getDefault(), "%d sec/div, ", timebase);
            if (showCh1) {
                small = small + String.format(Locale.getDefault(), "ADC1 = %1.04fV/div (X%d), ", ch1Div,
                        (int) gain[AttysComm.INDEX_Analogue_channel_1]);
            }
            if (showCh2) {
                small = small + String.format(Locale.getDefault(), "ADC2 = %1.04f%s/div (X%d), ", ch2Div,
                        ch2Converter.getUnit(), (int) gain[AttysComm.INDEX_Analogue_channel_2]);
            }
            if (showAcc) {
                small = small + String.format(Locale.getDefault(), "ACC = %dG/div, ", Math.round(accTick / AttysComm.oneG));
            }
            if (showMag) {
                small = small + String.format(Locale.getDefault(), "MAG = %d\u00b5T/div, ", Math.round(magTick / 1E-6));
            }
            if (dataRecorder.isRecording()) {
                small = small + " !!RECORDING to:" + dataFilename;
            }
            small = small + String.format(Locale.getDefault(), " d=%d,%d", gpio0, gpio1);
            if (largeText != null) {
                largeText = String.format("%s: ", labels[theChannelWeDoAnalysis]) + largeText;
            }
            if (infoView != null) {
                if (attysComm != null) {
                    final String lt = largeText;
                    final String st = small;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            infoView.drawText(lt, st, dataRecorder.isRecording());
                        }
                    });
                }
            }
        }

        private void doAnalysis(float v) {

            switch (textAnnotation) {
                case NONE:
                    int interval = attysComm.getSamplingRateInHz();
                    if ((timestamp % interval) == 0) {
                        annotatePlot(null);
                    }
                    break;
                case RMS:
                    signalAnalysis.addData(v);
                    if (signalAnalysis.bufferFull()) {
                        annotatePlot(String.format(Locale.getDefault(), "%1.05f%s RMS",
                                signalAnalysis.getRMS(),
                                m_unit));
                        signalAnalysis.reset();
                    }
                    break;
                case PEAKTOPEAK:
                    signalAnalysis.addData(v);
                    if (signalAnalysis.bufferFull()) {
                        annotatePlot(String.format(Locale.getDefault(), "%1.05f%s pp",
                                signalAnalysis.getPeakToPeak(),
                                m_unit));
                        signalAnalysis.reset();
                    }
                    break;
            }
        }

        public synchronized void run() {

            if (attysComm != null) {
                if (attysComm.hasFatalError()) {
                    // Log.d(TAG,String.format("No bluetooth connection"));
                    return;
                }
            }
            if (attysComm != null) {
                if (!attysComm.hasActiveConnection()) return;
            }

            int nCh = 0;
            if (attysComm != null) nCh = attysComm.NCHANNELS;
            if (attysComm != null) {
                float[] tmpSample = new float[nCh];
                float[] tmpMin = new float[nCh];
                float[] tmpMax = new float[nCh];
                float[] tmpTick = new float[nCh];
                float[] sample = new float[attysComm.NCHANNELS];
                String[] tmpLabels = new String[nCh];
                int n = attysComm.getNumSamplesAvilable();
                if (realtimePlotView != null) {
                    if (!realtimePlotView.startAddSamples(n)) return;
                    for (int i = 0; ((i < n) && (attysComm != null)); i++) {
                        float[] sample_unfilt = null;
                        if (attysComm != null) {
                            sample_unfilt = attysComm.getSampleFromBuffer();
                        }
                        if (sample_unfilt != null) {
                            // debug ECG detector
                            // sample[AttysComm.INDEX_Analogue_channel_2] = (float)ecgDetOut;
                            timestamp++;

                            System.arraycopy(sample_unfilt, 0, sample, 0, nCh);

                            sample[AttysComm.INDEX_Analogue_channel_2] = ch2Converter.convert(sample[AttysComm.INDEX_Analogue_channel_2]);

                            if (iirNotch[AttysComm.INDEX_Analogue_channel_1] != null) {
                                sample[AttysComm.INDEX_Analogue_channel_1] =
                                        (float) iirNotch[AttysComm.INDEX_Analogue_channel_1].filter((double) sample_unfilt[AttysComm.INDEX_Analogue_channel_1]);
                            }

                            if (iirNotch[AttysComm.INDEX_Analogue_channel_2] != null) {
                                sample[AttysComm.INDEX_Analogue_channel_2] =
                                        (float) iirNotch[AttysComm.INDEX_Analogue_channel_2].filter((double) sample_unfilt[AttysComm.INDEX_Analogue_channel_2]);
                            }

                            for (int j = 0; j < nCh; j++) {
                                float v = sample[j];
                                if (j >= AttysComm.INDEX_Analogue_channel_1) {
                                    if (highpass[j] != null) {
                                        v = (float) highpass[j].filter((double) v);
                                    }
                                }
                                if (invert[j]) {
                                    v = -v;
                                }
                                if (j == theChannelWeDoAnalysis) {
                                    doAnalysis(v);
                                }
                                sample[j] = v;
                            }

                            gpio0 = (int) sample[AttysComm.INDEX_GPIO0];
                            gpio1 = (int) sample[AttysComm.INDEX_GPIO1];

                            ecg_rr_det.detect(sample[AttysComm.INDEX_Analogue_channel_1]);

                            if (amplitudeFragment != null) {
                                amplitudeFragment.addValue(sample);
                            }

                            if (fourierFragment != null) {
                                fourierFragment.addValue(sample);
                            }

                            dataRecorder.saveData(sample_unfilt, sample);

                            int nRealChN = 0;
                            if (showCh1) {
                                if (attysComm != null) {
                                    tmpMin[nRealChN] = -attysComm.getADCFullScaleRange(0);
                                    tmpMax[nRealChN] = attysComm.getADCFullScaleRange(0);
                                    ch1Div = 1.0F / gain[AttysComm.INDEX_Analogue_channel_1];
                                    if (attysComm.getADCFullScaleRange(0) < 1) {
                                        ch1Div = ch1Div / 10;
                                    }
                                    tmpTick[nRealChN] = ch1Div * gain[AttysComm.INDEX_Analogue_channel_1];
                                    tmpLabels[nRealChN] = labels[AttysComm.INDEX_Analogue_channel_1];
                                    actualChannelIdx[nRealChN] = AttysComm.INDEX_Analogue_channel_1;
                                    tmpSample[nRealChN++] = sample[AttysComm.INDEX_Analogue_channel_1] * gain[AttysComm.INDEX_Analogue_channel_1];
                                }
                            }
                            if (showCh2) {
                                if (attysComm != null) {
                                    tmpMin[nRealChN] = ch2Converter.getMinRange();
                                    tmpMax[nRealChN] = ch2Converter.getMaxRange();
                                    ch2Div = ch2Converter.getTick() / gain[AttysComm.INDEX_Analogue_channel_2];
                                    tmpTick[nRealChN] = ch2Converter.getTick();
                                    tmpLabels[nRealChN] = labels[AttysComm.INDEX_Analogue_channel_2];
                                    actualChannelIdx[nRealChN] = AttysComm.INDEX_Analogue_channel_2;
                                    tmpSample[nRealChN++] = sample[AttysComm.INDEX_Analogue_channel_2] * gain[AttysComm.INDEX_Analogue_channel_2];
                                }
                            }
                            if (showAcc) {
                                if (attysComm != null) {
                                    float min = -attysComm.getAccelFullScaleRange();
                                    float max = attysComm.getAccelFullScaleRange();

                                    for (int k = 0; k < 3; k++) {
                                        tmpMin[nRealChN] = min;
                                        tmpMax[nRealChN] = max;
                                        tmpTick[nRealChN] = gain[k] * accTick;
                                        tmpLabels[nRealChN] = labels[k];
                                        actualChannelIdx[nRealChN] = k;
                                        tmpSample[nRealChN++] = sample[k] * gain[k];
                                    }
                                }
                            }
                            if (showMag) {
                                if (attysComm != null) {
                                    for (int k = 0; k < 3; k++) {
                                        if (attysComm != null) {
                                            tmpMin[nRealChN] = -attysComm.getMagFullScaleRange();
                                        }
                                        if (attysComm != null) {
                                            tmpMax[nRealChN] = attysComm.getMagFullScaleRange();
                                        }
                                        tmpLabels[nRealChN] = labels[k + 3];
                                        actualChannelIdx[nRealChN] = k + 3;
                                        tmpTick[nRealChN] = magTick;
                                        tmpSample[nRealChN++] = sample[k + 3] * gain[k + 3];
                                    }
                                }
                            }
                            if (infoView != null) {
                                ygapForInfo = infoView.getInfoHeight();
//                                if ((Log.isLoggable(TAG, Log.DEBUG)) && (ygapForInfo > 0)) {
//                                    Log.d(TAG, "ygap=" + ygapForInfo);
//                                }
                            }
                            if (realtimePlotView != null) {
                                tbCtr--;
                                if (tbCtr < 1) {
                                    realtimePlotView.addSamples(Arrays.copyOfRange(tmpSample, 0, nRealChN),
                                            Arrays.copyOfRange(tmpMin, 0, nRealChN),
                                            Arrays.copyOfRange(tmpMax, 0, nRealChN),
                                            Arrays.copyOfRange(tmpTick, 0, nRealChN),
                                            Arrays.copyOfRange(tmpLabels, 0, nRealChN),
                                            ygapForInfo);
                                    tbCtr = timebase;
                                }
                            }
                        }
                    }
                    if (realtimePlotView != null) {
                        realtimePlotView.stopAddSamples();
                    }
                }
            }
        }
    }


    @Override
    public void onBackPressed() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Back button pressed");
        }
        killAttysComm();
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startActivity(startMain);
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (!ATTYSDIR.exists()) {
            ATTYSDIR.mkdirs();
        }

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

        setContentView(R.layout.activity_plot_window);

        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);

        progress = findViewById(R.id.indeterminateBar);

        int nChannels = AttysComm.NCHANNELS;
        highpass = new Butterworth[nChannels];
        gain = new float[nChannels];
        iirNotch = new Butterworth[nChannels];
        invert = new boolean[nChannels];
        actualChannelIdx = new int[nChannels];
        for (int i = 0; i < nChannels; i++) {
            highpass[i] = null;
            iirNotch[i] = null;
            // set it to 1st ADC channel
            actualChannelIdx[i] = AttysComm.INDEX_Analogue_channel_1;
            gain[i] = 1;
            if ((i >= AttysComm.INDEX_Magnetic_field_X) && (i <= AttysComm.INDEX_Magnetic_field_Z)) {
                gain[i] = 20;
            }
        }
    }

    // this is called whenever the app is starting or re-starting
    @Override
    public void onStart() {
        super.onStart();

        startDAQ();

    }


    @Override
    public void onResume() {
        super.onResume();

        updatePlotTask.resetAnalysis();

    }


    private void highpass1on() {
        highpass[AttysComm.INDEX_Analogue_channel_1] = new Butterworth();
        highpass[AttysComm.INDEX_Analogue_channel_1].highPass(HIGHPASSORDER, attysComm.getSamplingRateInHz(), highpass1Hz);
    }


    private void highpass1off() {
        highpass[AttysComm.INDEX_Analogue_channel_1] = null;
    }


    private void highpass2on() {
        highpass[AttysComm.INDEX_Analogue_channel_2] = new Butterworth();
        highpass[AttysComm.INDEX_Analogue_channel_2].highPass(HIGHPASSORDER, attysComm.getSamplingRateInHz(), highpass2Hz);
    }


    private void highpass2off() {
        highpass[AttysComm.INDEX_Analogue_channel_2] = null;
    }


    private void checkMenuItems() {
        if (menuItemHighpass1 != null) {
            if (menuItemHighpass1.isChecked()) {
                highpass1on();
            } else {
                highpass1off();
            }
        } else {
            // default
            highpass1on();
        }

        if (menuItemHighpass2 != null) {
            if (ch2Converter.getRule() >= 0) {
                menuItemHighpass2.setChecked(false);
                textAnnotation = TextAnnotation.RMS;
            }
            if (menuItemHighpass2.isChecked()) {
                highpass2on();
            } else {
                highpass2off();
            }
        } else {
            // default
            highpass2on();
        }
    }


    private void noAttysFoundAlert() {
        alertDialog = new AlertDialog.Builder(this)
                .setTitle("No Attys found or bluetooth disabled")
                .setMessage("Before you can use the Attys you need to pair it with this device.")
                .setPositiveButton("Configure bluetooth", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Intent i = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                        startActivity(i);
                    }
                })
                .setNegativeButton("Exit", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        finish();
                    }
                })
                .show();
    }


    public void startDAQ() {

        btAttysDevice = AttysComm.findAttysBtDevice();
        if (btAttysDevice == null) {
            noAttysFoundAlert();
        }

        attysComm = new AttysComm(btAttysDevice);
        attysComm.registerMessageListener(messageListener);

        getsetAttysPrefs();

        checkMenuItems();

        if (showCh1) {
            theChannelWeDoAnalysis = AttysComm.INDEX_Analogue_channel_1;
        } else if (showCh2) {
            theChannelWeDoAnalysis = AttysComm.INDEX_Analogue_channel_2;
        } else if (showAcc) {
            theChannelWeDoAnalysis = AttysComm.INDEX_Acceleration_X;
        } else if (showMag) {
            theChannelWeDoAnalysis = AttysComm.INDEX_Magnetic_field_X;
        }

        realtimePlotView = findViewById(R.id.realtimeplotview);
        realtimePlotView.setMaxChannels(15);
        realtimePlotView.init();

        realtimePlotView.registerTouchEventListener(
                new RealtimePlotView.TouchEventListener() {
                    @Override
                    public void touchedChannel(int chNo) {
                        try {
                            theChannelWeDoAnalysis = actualChannelIdx[chNo];
                            updatePlotTask.resetAnalysis();
                        } catch (Exception e) {
                            if (Log.isLoggable(TAG, Log.ERROR)) {
                                Log.e(TAG, "Exception in the TouchEventListener (BUG!):", e);
                            }
                        }
                    }
                });

        infoView = findViewById(R.id.infoview);

        signalAnalysis = new SignalAnalysis(attysComm.getSamplingRateInHz());

        attysComm.start();

        ecg_rr_det = new ECG_rr_det(attysComm.getSamplingRateInHz(), powerlineHz);

        ecg_rr_det.setRrListener(new ECG_rr_det.RRlistener() {
            @Override
            public void haveRpeak(long samplenumber,
                                  float bpm,
                                  float unfiltbmp,
                                  double amplitude,
                                  double confidence) {
                if (updatePlotTask != null) {
                    if (textAnnotation == TextAnnotation.ECG) {
                        updatePlotTask.annotatePlot(String.format("%03d BPM", (int) bpm));
                    }
                }
                if (heartRateFragment != null) {
                    heartRateFragment.addValue(bpm);
                }
            }
        });


        timer = new Timer();
        updatePlotTask = new UpdatePlotTask();
        updatePlotTask.resetAnalysis();
        timer.schedule(updatePlotTask, 0, REFRESH_IN_MS);
    }

    private void killAttysComm() {

        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Killed timer");
            }
        }

        if (updatePlotTask != null) {
            updatePlotTask.cancel();
            updatePlotTask = null;
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Killed update Plot Task");
            }
        }

        if (attysComm != null) {
            attysComm.stop();
            attysComm = null;
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Killed AttysComm");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Destroy!");
        }
        killAttysComm();
        if (alertDialog != null) {
            if (alertDialog.isShowing()) {
                alertDialog.dismiss();
            }
        }
        alertDialog = null;
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Restarting");
        }
        killAttysComm();
    }


    @Override
    public void onPause() {
        super.onPause();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Paused");
        }

    }


    @Override
    public void onStop() {
        super.onStop();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Stopped");
        }

        killAttysComm();
    }


    private void enterFilename() {

        final EditText filenameEditText = new EditText(this);
        filenameEditText.setSingleLine(true);

        final int REQUEST_EXTERNAL_STORAGE = 1;
        String[] PERMISSIONS_STORAGE = {
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }

        filenameEditText.setHint("");
        filenameEditText.setText(dataFilename);

        new AlertDialog.Builder(this)
                .setTitle("Enter filename")
                .setMessage("Enter the filename of the data textfile")
                .setView(filenameEditText)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dataFilename = filenameEditText.getText().toString();
                        dataFilename = dataFilename.replaceAll("[^a-zA-Z0-9.-]", "_");
                        if (!dataFilename.contains(".")) {
                            switch (dataSeparator) {
                                case DataRecorder.DATA_SEPARATOR_COMMA:
                                    dataFilename = dataFilename + ".csv";
                                    break;
                                case DataRecorder.DATA_SEPARATOR_SPACE:
                                    dataFilename = dataFilename + ".dat";
                                    break;
                                case DataRecorder.DATA_SEPARATOR_TAB:
                                    dataFilename = dataFilename + ".tsv";
                            }
                        }
                        Toast.makeText(getApplicationContext(),
                                "Press rec to record to '" + dataFilename + "'",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                })
                .show();
    }


    private void shareData() {

        final int REQUEST_EXTERNAL_STORAGE = 1;
        String[] PERMISSIONS_STORAGE = {
                Manifest.permission.READ_EXTERNAL_STORAGE
        };

        int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }

        final List files = new ArrayList();
        final String[] list = ATTYSDIR.list();
        if (list == null) return;
        for (String file : list) {
            if (files != null) {
                if (file != null) {
                    if (files != null) {
                        files.add(file);
                    }
                }
            }
        }


        final ListView listview = new ListView(this);
        ArrayAdapter adapter = new ArrayAdapter(this,
                android.R.layout.simple_list_item_multiple_choice,
                files);
        listview.setAdapter(adapter);
        listview.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                view.setSelected(true);
            }
        });

        new AlertDialog.Builder(this)
                .setTitle("Share")
                .setMessage("Select filename(s)")
                .setView(listview)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        SparseBooleanArray checked = listview.getCheckedItemPositions();
                        Intent sendIntent = new Intent();
                        sendIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
                        ArrayList<Uri> files = new ArrayList<>();
                        for (int i = 0; i < listview.getCount(); i++) {
                            if (checked.get(i)) {
                                String filename = list[i];
                                File fp = new File(ATTYSDIR, filename);
                                files.add(Uri.fromFile(fp));
                                if (Log.isLoggable(TAG, Log.DEBUG)) {
                                    Log.d(TAG, "filename=" + filename);
                                }
                            }
                        }
                        sendIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, files);
                        sendIntent.setType("text/*");
                        startActivity(Intent.createChooser(sendIntent, "Send your files"));
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                })
                .show();

        ViewGroup.LayoutParams layoutParams = listview.getLayoutParams();
        Screensize screensize = new Screensize(getWindowManager());
        layoutParams.height = screensize.getHeightInPixels() / 2;
        listview.setLayoutParams(layoutParams);

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu_attysscope, menu);

        menuItemHighpass1 = menu.findItem(R.id.Ch1toggleDC);
        menuItemHighpass2 = menu.findItem(R.id.Ch2toggleDC);

        menuItemMains1 = menu.findItem(R.id.Ch1notch);
        menuItemMains2 = menu.findItem(R.id.Ch2notch);

        checkMenuItems();

        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {

            case R.id.preferences:
                Intent intent = new Intent(this, PrefsActivity.class);
                startActivity(intent);
                return true;

            case R.id.toggleRec:
                if (dataRecorder.isRecording()) {
                    File file = dataRecorder.getFile();
                    dataRecorder.stopRec();
                    if (file != null) {
                        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                        Uri contentUri = Uri.fromFile(file);
                        mediaScanIntent.setData(contentUri);
                        sendBroadcast(mediaScanIntent);
                    }
                } else {
                    if (dataFilename != null) {
                        File file = new File(ATTYSDIR, dataFilename.trim());
                        dataRecorder.setDataSeparator(dataSeparator);
                        if (file.exists()) {
                            Toast.makeText(getApplicationContext(),
                                    "File exists already. Enter a different one.",
                                    Toast.LENGTH_LONG).show();
                            return true;
                        }
                        try {
                            dataRecorder.startRec(file);
                        } catch (Exception e) {
                            if (Log.isLoggable(TAG, Log.DEBUG)) {
                                Log.d(TAG, "Could not open data file: " + e.getMessage());
                            }
                            return true;
                        }
                        if (dataRecorder.isRecording()) {
                            if (Log.isLoggable(TAG, Log.DEBUG)) {
                                Log.d(TAG, "Saving to " + file.getAbsolutePath());
                            }
                        }
                    } else {
                        Toast.makeText(getApplicationContext(),
                                "To record enter a filename first", Toast.LENGTH_SHORT).show();
                    }
                }
                return true;

            case R.id.showCh1:
                showCh1 = !showCh1;
                item.setChecked(showCh1);
                return true;

            case R.id.showCh2:
                showCh2 = !showCh2;
                item.setChecked(showCh2);
                return true;

            case R.id.showaccelerometer:
                showAcc = !showAcc;
                item.setChecked(showAcc);
                return true;

            case R.id.showmagnetometer:
                showMag = !showMag;
                item.setChecked(showMag);
                return true;

            case R.id.enterFilename:
                enterFilename();
                return true;

            case R.id.Ch1toggleDC:
                item.setChecked(!item.isChecked());
                if (item.isChecked()) {
                    highpass1on();
                } else {
                    highpass1off();
                }
                return true;

            case R.id.Ch2toggleDC:
                item.setChecked(!item.isChecked());
                if (item.isChecked()) {
                    highpass2on();
                } else {
                    highpass2off();
                }
                return true;

            case R.id.Ch1notch:
                if (iirNotch[AttysComm.INDEX_Analogue_channel_1] == null) {
                    iirNotch[AttysComm.INDEX_Analogue_channel_1] = new Butterworth();
                    iirNotch[AttysComm.INDEX_Analogue_channel_1].bandStop(notchOrder,
                            attysComm.getSamplingRateInHz(), powerlineHz, notchBW);
                } else {
                    iirNotch[AttysComm.INDEX_Analogue_channel_1] = null;
                }
                item.setChecked(iirNotch[AttysComm.INDEX_Analogue_channel_1] != null);
                return true;

            case R.id.Ch2notch:
                if (iirNotch[AttysComm.INDEX_Analogue_channel_2] == null) {
                    iirNotch[AttysComm.INDEX_Analogue_channel_2] = new Butterworth();
                    iirNotch[AttysComm.INDEX_Analogue_channel_2].bandStop(notchOrder,
                            attysComm.getSamplingRateInHz(), powerlineHz, notchBW);
                } else {
                    iirNotch[AttysComm.INDEX_Analogue_channel_2] = null;
                }
                item.setChecked(iirNotch[AttysComm.INDEX_Analogue_channel_2] != null);
                return true;

            case R.id.Ch1invert:
                boolean a = invert[AttysComm.INDEX_Analogue_channel_1];
                a = !a;
                invert[AttysComm.INDEX_Analogue_channel_1] = a;
                item.setChecked(a);
                return true;

            case R.id.Ch2invert:
                a = invert[AttysComm.INDEX_Analogue_channel_2];
                a = !a;
                invert[AttysComm.INDEX_Analogue_channel_2] = a;
                item.setChecked(a);
                return true;

            case R.id.Ch1gain1:
            case R.id.Ch1gain2:
            case R.id.Ch1gain5:
            case R.id.Ch1gain10:
            case R.id.Ch1gain20:
            case R.id.Ch1gain50:
            case R.id.Ch1gain100:
            case R.id.Ch1gain200:
            case R.id.Ch1gain500:
            case R.id.Ch1gain1000:
            case R.id.Ch1gain2000:
                String t = item.getTitle().toString();
                int g = Integer.parseInt(t);
                gain[AttysComm.INDEX_Analogue_channel_1] = (float) g;
                Toast.makeText(getApplicationContext(),
                        String.format(Locale.getDefault(), "Channel 1 gain set to x%d", g), Toast.LENGTH_LONG).show();
                return true;

            case R.id.Ch2gain1:
            case R.id.Ch2gain2:
            case R.id.Ch2gain5:
            case R.id.Ch2gain10:
            case R.id.Ch2gain20:
            case R.id.Ch2gain50:
            case R.id.Ch2gain100:
            case R.id.Ch2gain200:
            case R.id.Ch2gain500:
            case R.id.Ch2gain1000:
            case R.id.Ch2gain2000:
                t = item.getTitle().toString();
                g = Integer.parseInt(t);
                Toast.makeText(getApplicationContext(),
                        String.format(Locale.getDefault(), "Channel 2 gain set to x%d", g), Toast.LENGTH_LONG).show();
                gain[AttysComm.INDEX_Analogue_channel_2] = (float) g;
                return true;

            case R.id.tb1:
            case R.id.tb2:
            case R.id.tb5:
            case R.id.tb10:
                t = item.getTitle().toString();
                g = Integer.parseInt(t);
                Toast.makeText(getApplicationContext(),
                        String.format(Locale.getDefault(), "Timebase set to %d secs/div", g), Toast.LENGTH_LONG).show();
                timebase = g;
                return true;

            case R.id.largeStatusOff:
                textAnnotation = TextAnnotation.NONE;
                updatePlotTask.resetAnalysis();
                infoView.resetInfoHeight();
                return true;

            case R.id.largeStatusPP:
                textAnnotation = TextAnnotation.PEAKTOPEAK;
                updatePlotTask.resetAnalysis();
                return true;

            case R.id.largeStatusRMS:
                textAnnotation = TextAnnotation.RMS;
                updatePlotTask.resetAnalysis();
                return true;

            case R.id.largeStatusBPM:
                textAnnotation = TextAnnotation.ECG;
                updatePlotTask.resetAnalysis();
                return true;

            case R.id.infoWindowAmplitude:
                deleteFragmentWindow();
                // Create a new Fragment to be placed in the activity layout
                amplitudeFragment = new AmplitudeFragment();
                amplitudeFragment.setUnits(units);
                if (attysComm != null) {
                    amplitudeFragment.setSamplingrate(attysComm.getSamplingRateInHz());
                } else {
                    amplitudeFragment = null;
                    return true;
                }
                // Add the fragment to the 'fragment_container' FrameLayout
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Adding Amplitude fragment");
                }
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.fragment_plot_container,
                                amplitudeFragment,
                                "amplitudeFragment")
                        .commit();
                showPlotFragment();
                return true;

            case R.id.infoWindowSpectrum:
                deleteFragmentWindow();
                // Create a new Fragment to be placed in the activity layout
                fourierFragment = new FourierFragment();
                fourierFragment.setUnits(units);
                if (attysComm != null) {
                    fourierFragment.setSamplingrate(attysComm.getSamplingRateInHz());
                } else {
                    fourierFragment = null;
                    return true;
                }
                // Add the fragment to the 'fragment_container' FrameLayout
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Adding Fourier fragment");
                }
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.fragment_plot_container,
                                fourierFragment,
                                "fourierFragment")
                        .commit();
                showPlotFragment();
                return true;

            case R.id.infoWindowHeartrate:
                deleteFragmentWindow();
                // Create a new Fragment to be placed in the activity layout
                heartRateFragment = new HeartratePlotFragment();
                // Add the fragment to the 'fragment_container' FrameLayout
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Adding Heartrate fragment");
                }
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.fragment_plot_container,
                                heartRateFragment,
                                "heartRateFragment")
                        .commit();
                showPlotFragment();
                return true;

            case R.id.infoWindowOff:
                deleteFragmentWindow();
                hidePlotFragment();
                return true;

            case R.id.filebrowser:
                shareData();
                return true;

            case R.id.sourcecode:
                String url = "https://github.com/glasgowneuro/AttysScope";
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }


    private void getsetAttysPrefs() {
        byte mux;

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Setting preferences");
        }
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        boolean ecg_mode = prefs.getBoolean("ECG_mode", false);
        if (ecg_mode) {
            mux = AttysComm.ADC_MUX_ECG_EINTHOVEN;
        } else {
            mux = AttysComm.ADC_MUX_NORMAL;
        }
        byte gain0 = (byte) (Integer.parseInt(prefs.getString("ch1_gainpref", "0")));
        attysComm.setAdc1_gain_index(gain0);
        attysComm.setAdc0_mux_index(mux);
        byte gain1 = (byte) (Integer.parseInt(prefs.getString("ch2_gainpref", "0")));
        attysComm.setAdc2_gain_index(gain1);
        attysComm.setAdc1_mux_index(mux);

        int ch2_option = Integer.parseInt(prefs.getString("ch2_options", "-1"));
        ch2Converter.setRule(ch2_option);
        int currentIndex = ch2Converter.getCurrentIndex();
        if (currentIndex < 0) {
            attysComm.enableCurrents(false, false, false);
        } else {
            attysComm.setBiasCurrent((byte) currentIndex);
            attysComm.enableCurrents(false, false, true);
        }

        byte data_separator = (byte) (Integer.parseInt(prefs.getString("data_separator", "0")));
        dataRecorder.setDataSeparator(data_separator);

        boolean withGPIO = prefs.getBoolean("GPIO_logging",false);
        dataRecorder.setGPIOlogging(withGPIO);

        int fullscaleAcc = Integer.parseInt(prefs.getString("accFullscale", "1"));

        attysComm.setAccel_full_scale_index((byte) fullscaleAcc);

        powerlineHz = Float.parseFloat(prefs.getString("powerline", "50"));
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "powerline=" + powerlineHz);
        }

        samplingRate = (byte) Integer.parseInt(prefs.getString("samplingrate", "1"));
        if (samplingRate < 0) samplingRate = 0;
        if (null != btAttysDevice) {
            if (null != btAttysDevice.getName()) {
                if (btAttysDevice.getName().contains("ATTYS2")) {
                    if (samplingRate > 2) {
                        samplingRate = 2;
                    }
                } else {
                    if (samplingRate > 1) {
                        samplingRate = 1;
                    }
                }
            }
        }
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("samplingrate", String.valueOf(samplingRate));
        editor.apply();

        attysComm.setAdc_samplingrate_index(samplingRate);

        highpass1Hz = Float.parseFloat(prefs.getString("highpass1", "0.1"));
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "highpass1=" + highpass1Hz);
        }
        highpass2Hz = Float.parseFloat(prefs.getString("highpass2", "0.1"));
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "highpass2=" + highpass2Hz);
        }

        for (int i = 0; i < AttysComm.NCHANNELS; i++) {
            units[i] = AttysComm.CHANNEL_UNITS[i];
        }
        units[AttysComm.INDEX_Analogue_channel_2] = ch2Converter.getUnit();

        if (amplitudeFragment != null) {
            amplitudeFragment.setUnits(units);
            amplitudeFragment.reset();
        }

    }


    private void showPlotFragment() {
        FrameLayout frameLayout = findViewById(R.id.mainplotlayout);
        frameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT, 1.0f));

        frameLayout = findViewById(R.id.fragment_plot_container);
        frameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT, 0.5f));

    }

    private void hidePlotFragment() {
        FrameLayout frameLayout = findViewById(R.id.mainplotlayout);
        frameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT, 0.0f));
    }


    private synchronized void deleteFragmentWindow() {
        List<Fragment> fragments = getSupportFragmentManager().getFragments();
        if (!(fragments.isEmpty())) {
            for (Fragment fragment : fragments) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    if (fragment != null) {
                        Log.d(TAG, "Removing fragment: " + fragment.getTag());
                    }
                }
                if (fragment != null) {
                    getSupportFragmentManager().beginTransaction().remove(fragment).commit();
                }
            }
        }
        amplitudeFragment = null;
        fourierFragment = null;
        heartRateFragment = null;
    }


}
