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
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import tech.glasgowneuro.attyscomm.AttysComm;
import tech.glasgowneuro.attyscomm.AttysService;
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

    MenuItem menuItemPref = null;
    MenuItem menuItemEnterFilename = null;
    MenuItem menuItemChangeFolder = null;

    MenuItem menuItemRec = null;

    private AttysService attysService = null;
    private byte samplingRate = AttysComm.ADC_RATE_250HZ;

    private UpdatePlotTask updatePlotTask = null;

    private static final String TAG = "AttysScope";

    final String NOTIFICATION_CH = "recorder";
    final int NOTIFICATION_ID = (new Random()).nextInt();

    private ForegroundBroadcastReceiver foregroundBroadcastReceiver = null;

    private final static String FOREGROUND = "tech.glasgowneuro.attysscope2.FOREGROUND";
    private PendingIntent fgPendingIntent = null;

    public Ch2Converter ch2Converter = new Ch2Converter();

    private final Butterworth[] highpass = new Butterworth[2];
    private final Butterworth[] iirNotch = new Butterworth[2];
    private final float[] gain = new float[AttysComm.NCHANNELS];
    private final boolean[] invert = new boolean[AttysComm.NCHANNELS];
    private final int[] actualChannelIdx = new int[AttysComm.NCHANNELS];

    private final double notchBW = 2.5; // Hz
    private final int notchOrder = 2;
    private float powerlineHz = 50;
    private float highpass1Hz = 0.1F;
    private float highpass2Hz = 0.1F;

    private final int RINGBUFFERSIZE = 1024;
    private final float[][] ringBuffer = new float[RINGBUFFERSIZE][AttysComm.NCHANNELS];
    private int inPtr = 0;
    private int outPtr = 0;
    private long timestamp = 0;

    public void addFilteredSample(float[] sample) {
        System.arraycopy(sample, 0, ringBuffer[inPtr], 0, sample.length);
        inPtr++;
        if (inPtr == RINGBUFFERSIZE) {
            inPtr = 0;
        }
    }

    private boolean showAcc = false;
    private boolean showMag = false;
    private boolean showCh1 = true;
    private boolean showCh2 = true;

    private float ch1Div = 1;
    private float ch2Div = 1;

    private final float magTick = 1000.0E-6F; //1000uT

    private final float accTick = AttysComm.oneG; // 1G

    private int timebase = 1;

    private int tbCtr = 1;

    private int theChannelWeDoAnalysis = 0;

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

    String[] labels = {
            "Acc x", "Acc y", "Acc z",
            "Mag x", "Mag y", "Mag z",
            "ADC 1", "ADC 2"};

    String[] units = new String[AttysComm.NCHANNELS];

    private String dataFilename = null;
    public static byte dataSeparator = 0;
    public static Uri directoryUri = null;
    public static String LASTURI = "lasturi";

    private static final String ATTYS_SUBDIR = "attys";

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
        private final String[] units = {"V", "V", "V", "\u2126", "\u2126", "\u00b0C", "\u00b0C"};

        // current settings
        private final int[] currentIndex = {0, 1, 2, 1, 2, 2, -1};

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
                    return attysService.getAttysComm().getADCFullScaleRange(1);
                case 3:
                    return 81E6F - Rbaseline;
                case 4:
                    return 300000 - Rbaseline;
                case 5:
                    return 100;
                case 6:
                    return 1000;
            }
            return attysService.getAttysComm().getADCFullScaleRange(1);
        }

        // min range for plotting
        public float getMinRange() {
            switch (rule) {
                case 0:
                case 1:
                case 2:
                    return -attysService.getAttysComm().getADCFullScaleRange(1);
                case 3:
                case 4:
                    return 0;
                case 5:
                    return -20;
                case 6:
                    return -100;
            }
            return -attysService.getAttysComm().getADCFullScaleRange(1);
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


    private ServiceConnection serviceConnection = null;

    private void startAttysService() {
        final Intent intent = new Intent(getBaseContext(), AttysService.class);
        serviceConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder service) {
                Log.d(TAG, "Attys service connected. Starting now.");
                startService(intent);
                AttysService.AttysBinder binder = (AttysService.AttysBinder) service;
                attysService = binder.getService();
                if (null == attysService) {
                    Log.e(TAG, "attysService=null in onServiceConnected");
                    return;
                }
                attysService.createAttysComm();
                initAll();
                if (attysService.getAttysComm() != null) {
                    attysService.getAttysComm().start();
                }
            }

            public void onServiceDisconnected(ComponentName className) {
                if (attysService != null) {
                    attysService.stop();
                }
                attysService = null;
            }
        };
        Log.d(TAG, "Binding Player service");
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }


    private void stopAttysService() {
        dataRecorder.stopRec();
        if (serviceConnection == null) return;
        if (attysService != null) {
            attysService.stop();
        }
        unbindService(serviceConnection);
        stopService(new Intent(getBaseContext(), AttysService.class));
    }


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
                            if (attysService != null) {
                                attysService.stop();
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
            small = small + String.format(Locale.getDefault(), " d=%d,%d", gpio0, gpio1);
            if (largeText != null) {
                largeText = String.format("%s: ", labels[theChannelWeDoAnalysis]) + largeText;
            }
            if (infoView != null) {
                if (attysService.getAttysComm() != null) {
                    final String lt = largeText;
                    final String st = small;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String rectxt = null;
                            if (dataRecorder.isRecording()) {
                                rectxt = "RECORDING: " + dataFilename;
                            }
                            infoView.drawText(lt, st, rectxt);
                        }
                    });
                }
            }
        }

        private void doAnalysis(float v) {

            switch (textAnnotation) {
                case NONE:
                    int interval = attysService.getAttysComm().getSamplingRateInHz();
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

        public void run() {

            if (attysService.getAttysComm() != null) {
                if (attysService.getAttysComm().hasFatalError()) {
                    // Log.d(TAG,String.format("No bluetooth connection"));
                    return;
                }
            }
            if (attysService.getAttysComm() != null) {
                if (!attysService.getAttysComm().hasActiveConnection()) return;
            }

            int n = 0;
            int tmpOutPtr = outPtr;
            while (inPtr != tmpOutPtr) {
                tmpOutPtr++;
                n++;
                if (tmpOutPtr == RINGBUFFERSIZE) {
                    tmpOutPtr = 0;
                }
            }

            if (!realtimePlotView.startAddSamples(n)) return;

            final int nCh = AttysComm.NCHANNELS;
            if (attysService.getAttysComm() != null) {
                final float[] tmpSample = new float[nCh];
                final float[] tmpMin = new float[nCh];
                final float[] tmpMax = new float[nCh];
                final float[] tmpTick = new float[nCh];
                final String[] tmpLabels = new String[nCh];
                if (realtimePlotView != null) {
                    for (int i = 0; ((i < n) && (attysService.getAttysComm() != null)); i++) {
                        final float[] sample = new float[nCh];
                        System.arraycopy(ringBuffer[outPtr], 0, sample, 0, ringBuffer[outPtr].length);
                        outPtr++;
                        if (outPtr == RINGBUFFERSIZE) {
                            outPtr = 0;
                        }
                        for (int j = 0; j < nCh; j++) {
                            float v = sample[j];
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

                        int nRealChN = 0;
                        if (showCh1) {
                            if (attysService.getAttysComm() != null) {
                                tmpMin[nRealChN] = -attysService.getAttysComm().getADCFullScaleRange(0);
                                tmpMax[nRealChN] = attysService.getAttysComm().getADCFullScaleRange(0);
                                ch1Div = 1.0F / gain[AttysComm.INDEX_Analogue_channel_1];
                                if (attysService.getAttysComm().getADCFullScaleRange(0) < 1) {
                                    ch1Div = ch1Div / 10;
                                }
                                tmpTick[nRealChN] = ch1Div * gain[AttysComm.INDEX_Analogue_channel_1];
                                tmpLabels[nRealChN] = labels[AttysComm.INDEX_Analogue_channel_1];
                                actualChannelIdx[nRealChN] = AttysComm.INDEX_Analogue_channel_1;
                                tmpSample[nRealChN++] = sample[AttysComm.INDEX_Analogue_channel_1] * gain[AttysComm.INDEX_Analogue_channel_1];
                            }
                        }
                        if (showCh2) {
                            if (attysService.getAttysComm() != null) {
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
                            if (attysService.getAttysComm() != null) {
                                float min = -attysService.getAttysComm().getAccelFullScaleRange();
                                float max = attysService.getAttysComm().getAccelFullScaleRange();

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
                            if (attysService.getAttysComm() != null) {
                                for (int k = 0; k < 3; k++) {
                                    if (attysService.getAttysComm() != null) {
                                        tmpMin[nRealChN] = -attysService.getAttysComm().getMagFullScaleRange();
                                    }
                                    if (attysService.getAttysComm() != null) {
                                        tmpMax[nRealChN] = attysService.getAttysComm().getMagFullScaleRange();
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
                    if (realtimePlotView != null) {
                        realtimePlotView.stopAddSamples();
                    }
                }
            }
        }
    }


    public class ForegroundBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Intent i = new Intent(getBaseContext(), AttysScope.class);
            i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(i);
        }
    }


    private void initNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CH,
                    getString(R.string.app_name),
                    importance);
            channel.setDescription(getString(R.string.app_name));
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (null != nm) {
                nm.createNotificationChannel(channel);
                Log.d(TAG, "Created notification channel");
            } else {
                Log.d(TAG, "Could not create a notification channel");
            }
        }

        fgPendingIntent = PendingIntent.getBroadcast(
                getBaseContext(),
                0,
                new Intent(FOREGROUND),
                PendingIntent.FLAG_IMMUTABLE);

        if (null == foregroundBroadcastReceiver) {
            foregroundBroadcastReceiver = new ForegroundBroadcastReceiver();
            getBaseContext().registerReceiver(
                    foregroundBroadcastReceiver,
                    new IntentFilter(FOREGROUND));
        }
    }

    private void showNotification(double timestamp) {
        if (null == fgPendingIntent) return;
        if (null == dataRecorder.uri) return;
        if (!(dataRecorder.isRecording())) return;

        Log.v(TAG, "Notification: showNotification called.");

        final String message = dataRecorder.uri.getLastPathSegment() +
                String.format(Locale.US, ": %d sec", (int) Math.round(timestamp));

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(
                getApplicationContext(),
                NOTIFICATION_CH);

        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSmallIcon(R.drawable.ic_attys)
                .setContentTitle(message)
                .setContentIntent(fgPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
        Log.v(TAG, "Notification is being shown");
    }

    private void hideNotification() {
        Log.d(TAG, "Hiding notifications");
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.cancelAll();
    }


    @Override
    public void onBackPressed() {
        Log.d(TAG, "Back button pressed");
        stopAnimation();
        if (!(dataRecorder.isRecording())) {
            if (null != attysService) {
                attysService.stop();
            }
        }
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startActivity(startMain);
    }

    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), isGranted -> {
                // isGranted is a map of the permissions (Strings) to boolean values.
                if (isGranted.containsValue(false)) {
                    finish();
                }
                if (AttysComm.findAttysBtDevice() == null) {
                    noAttysFoundAlert();
                }
            });

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void requestPermissionsAndroid12() {
        final String[] ANDROID_12_PERMISSIONS = new String[]{
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
        };
        for(String p:ANDROID_12_PERMISSIONS) {
            if (!(ContextCompat.checkSelfPermission(getBaseContext(), p) ==
                    PackageManager.PERMISSION_GRANTED)) {
                requestPermissionLauncher.launch(ANDROID_12_PERMISSIONS);
                return;
            }
        }
    }


    private void requestBTpermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissionsAndroid12();
        } else {
            if (AttysComm.findAttysBtDevice() == null) {
                noAttysFoundAlert();
            }
        }
    }

    /**
     * Called when the activity is first created.
     */
    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

        setContentView(R.layout.activity_plot_window);

        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);

        progress = findViewById(R.id.indeterminateBar);

        for (int i = 0; i < 2; i++) {
            highpass[i] = null;
            iirNotch[i] = null;
        }

        int nChannels = AttysComm.NCHANNELS;
        for (int i = 0; i < nChannels; i++) {
            // set it to 1st ADC channel
            actualChannelIdx[i] = AttysComm.INDEX_Analogue_channel_1;
            gain[i] = 1;
            if ((i >= AttysComm.INDEX_Magnetic_field_X) && (i <= AttysComm.INDEX_Magnetic_field_Z)) {
                gain[i] = 20;
            }
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

        requestBTpermissions();

        startAttysService();

        initNotification();
    }

    private void highpass1on() {
        synchronized (highpass) {
            highpass[0] = new Butterworth();
            highpass[0].highPass(HIGHPASSORDER, attysService.getAttysComm().getSamplingRateInHz(), highpass1Hz);
        }
    }


    private void highpass1off() {
        synchronized (highpass) {
            highpass[0] = null;
        }
    }


    private void highpass2on() {
        synchronized (highpass) {
            highpass[1] = new Butterworth();
            highpass[1].highPass(HIGHPASSORDER, attysService.getAttysComm().getSamplingRateInHz(), highpass2Hz);
        }
    }


    private void highpass2off() {
        synchronized (highpass) {
            highpass[1] = null;
        }
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
                        finish();
                    }
                })
                .setNeutralButton("www.attys.tech", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String url = "https://www.attys.tech";
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri.parse(url));
                        startActivity(i);
                        finish();
                    }
                })
                .setNegativeButton("Exit", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        finish();
                    }
                })
                .show();
    }


    private final AttysComm.DataListener dataListener = new AttysComm.DataListener() {
        @Override
        public void gotData(long samplenumber, float[] data) {

            data[AttysComm.INDEX_Analogue_channel_2] = ch2Converter.convert(data[AttysComm.INDEX_Analogue_channel_2]);

            float adc1 = data[AttysComm.INDEX_Analogue_channel_1];
            synchronized (iirNotch) {
                if (iirNotch[0] != null) {
                    adc1 = (float) iirNotch[0].filter(adc1);
                }
            }
            synchronized (highpass) {
                if (highpass[0] != null) {
                    adc1 = (float) highpass[0].filter(adc1);
                }
            }


            float adc2 = data[AttysComm.INDEX_Analogue_channel_2];
            synchronized (iirNotch) {
                if (iirNotch[1] != null) {
                    adc2 = (float) iirNotch[1].filter(adc2);
                }
            }
            synchronized (highpass) {
                if (highpass[1] != null) {
                    adc2 = (float) highpass[1].filter(adc2);
                }
            }

            dataRecorder.saveData(samplenumber, data, adc1, adc2);

            data[AttysComm.INDEX_Analogue_channel_1] = adc1;
            data[AttysComm.INDEX_Analogue_channel_2] = adc2;
            addFilteredSample(data);

            if ((timestamp % 250) == 0) {
                showNotification((double) samplenumber /
                        attysService.getAttysComm().getSamplingRateInHz());
            }

            timestamp++;
        }
    };


    void initAll() {
        Log.d(TAG, "Starting to init all the settings.");

        if (null == attysService) return;
        if (null == attysService.getAttysComm()) return;

        attysService.getAttysComm().disableRingbuffer();
        attysService.getAttysComm().registerMessageListener(messageListener);
        attysService.getAttysComm().registerDataListener(dataListener);

        signalAnalysis = new SignalAnalysis(attysService.getAttysComm().getSamplingRateInHz());

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

        ecg_rr_det = new ECG_rr_det(attysService.getAttysComm().getSamplingRateInHz(), powerlineHz);

        ecg_rr_det.setRrListener(new ECG_rr_det.RRlistener() {
            @Override
            public void haveRpeak(long samplenumber,
                                  float bpm,
                                  float unfiltbmp,
                                  double amplitude,
                                  double confidence) {
                if (updatePlotTask != null) {
                    if (textAnnotation == TextAnnotation.ECG) {
                        updatePlotTask.annotatePlot(String.format(Locale.US, "%03d BPM", (int) bpm));
                    }
                }
                if (heartRateFragment != null) {
                    heartRateFragment.addValue(bpm);
                }
            }
        });

        startAnimation();

    }


    public void startAnimation() {
        if (null != timer) return;
        timer = new Timer();
        updatePlotTask = new UpdatePlotTask();
        updatePlotTask.resetAnalysis();
        timer.schedule(updatePlotTask, 0, REFRESH_IN_MS);
        Log.d(TAG, "Timer started");
        attysService.getAttysComm().resetRingbuffer();
    }

    private void stopAnimation() {

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

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Destroy!");
        }

        stopAnimation();

        if (alertDialog != null) {
            if (alertDialog.isShowing()) {
                alertDialog.dismiss();
            }
        }
        alertDialog = null;

        stopAttysService();
    }

    @Override
    public void onPause() {
        super.onPause();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onPause");
        }

        if (null == attysService) return;

        stopAnimation();

        if (!(dataRecorder.isRecording())) {
            if (null != attysService) {
                attysService.stop();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (null == attysService) return;

        Log.d(TAG, "onResume");
        if (!(dataRecorder.isRecording())) {
            getsetAttysPrefs();
            attysService.start();
        }

        startAnimation();

        if (null != updatePlotTask) {
            updatePlotTask.resetAnalysis();
        }
    }


    static final int CHOOSE_DIR_CODE = 1;
    static final int PICK_FILE_CODE = 2;

    static Uri getUri2Filename(Activity activity, String filename, int dataSeparator) throws IOException {
        if (null == activity) throw new IOException();
        DocumentFile documentTree = DocumentFile.fromTreeUri(activity.getApplicationContext(), AttysScope.directoryUri);
        if (null == documentTree) throw new IOException();
        DocumentFile documentFile = documentTree.createFile(AttysScope.getMimeType(), filename.trim());
        if (null == documentFile) throw new IOException();
        return documentFile.getUri();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == CHOOSE_DIR_CODE
                && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                directoryUri = resultData.getData();
                SharedPreferences prefs = PreferenceManager
                        .getDefaultSharedPreferences(this);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(LASTURI, directoryUri.toString());
                editor.apply();
                ContentResolver resolver = getContentResolver();
                resolver.takePersistableUriPermission(directoryUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                Log.d(TAG, "URI=" + directoryUri);
            }
        }
        if (requestCode == PICK_FILE_CODE
                && resultCode == Activity.RESULT_OK) {
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_STREAM, resultData.getData());
            sendIntent.setType("text/*");
            startActivity(Intent.createChooser(sendIntent, "Send your files"));
        }
    }

    private static void triggerRequestDirectoryAccess(Activity activity) {

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        activity.startActivityForResult(intent, CHOOSE_DIR_CODE);
    }

    static public void checkDirPermissions(Activity activity) {
        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(activity);
        final ContentResolver resolver = activity.getContentResolver();
        final List<UriPermission> lou = resolver.getPersistedUriPermissions();
        for (UriPermission permission : lou) {
            Log.d(TAG, "Persistent permission: " + permission.getUri().toString());
            final String lasturistring = prefs.getString(LASTURI, null);
            if (null != lasturistring) {
                Uri lasturi = Uri.parse(lasturistring);
                if ((lasturi.compareTo(permission.getUri()) == 0) && (permission.isWritePermission())) {
                    directoryUri = permission.getUri();
                    Log.d(TAG, "Found a previous persistent permission and it's writeable: " +
                            directoryUri.toString());
                }
            }
        }
        if (null == directoryUri) {
            triggerRequestDirectoryAccess(activity);
        }
    }

    private void enterFilename() {

        if (dataRecorder.isRecording()) return;

        checkDirPermissions(this);

        final EditText filenameEditText = new EditText(this);
        filenameEditText.setSingleLine(true);

        filenameEditText.setHint("");
        filenameEditText.setText(dataFilename);

        new AlertDialog.Builder(this)
                .setTitle("Enter filename")
                .setMessage("Enter the filename of the data textfile")
                .setView(filenameEditText)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dataFilename = filenameEditText.getText().toString();
                        dataFilename = fixFilename(dataFilename);
                        Toast.makeText(getApplicationContext(),
                                "Press rec to record to '" + dataFilename + "'",
                                Toast.LENGTH_SHORT).show();
                        setRecColour(Color.GREEN);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                })
                .show();
    }

    private void shareData() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("text/*");
        startActivityForResult(intent, PICK_FILE_CODE);
    }

    private void setRecColour(int c) {
        if (null == menuItemRec) return;
        SpannableString s = new SpannableString(menuItemRec.getTitle());
        s.setSpan(new ForegroundColorSpan(c), 0, s.length(), 0);
        menuItemRec.setTitle(s);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu_attysscope, menu);

        menuItemHighpass1 = menu.findItem(R.id.Ch1toggleDC);
        menuItemHighpass2 = menu.findItem(R.id.Ch2toggleDC);

        menuItemMains1 = menu.findItem(R.id.Ch1notch);
        menuItemMains2 = menu.findItem(R.id.Ch2notch);

        menuItemRec = menu.findItem(R.id.toggleRec);
        setRecColour(Color.GRAY);

        menuItemEnterFilename = menu.findItem(R.id.enterFilename);
        menuItemPref = menu.findItem(R.id.preferences);
        menuItemChangeFolder = menu.findItem(R.id.changefolder);

        return true;
    }

    private void enableMenuitems(boolean doit) {
        menuItemPref.setEnabled(doit);
        menuItemEnterFilename.setEnabled(doit);
        menuItemChangeFolder.setEnabled(doit);
    }

    private void toggleRec() {
        if (dataRecorder.isRecording()) {
            dataRecorder.stopRec();
            dataFilename = null;
            hideNotification();
            enableMenuitems(true);
            setRecColour(Color.GRAY);
        } else {
            if (dataFilename != null) {
                Uri uri = Uri.EMPTY;
                try {
                    uri = getUri2Filename(this, dataFilename, dataSeparator);
                    dataRecorder.startRec(uri);
                    enableMenuitems(false);
                    setRecColour(Color.RED);
                } catch (Exception e) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Could not open data file: " + e.getMessage());
                    }
                    setRecColour(Color.GRAY);
                    Toast.makeText(getApplicationContext(),
                            "Could not save the file.", Toast.LENGTH_SHORT).show();
                    dataFilename = null;
                }
            } else {
                Toast.makeText(getApplicationContext(),
                        "To record enter a filename first", Toast.LENGTH_SHORT).show();
                setRecColour(Color.GRAY);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {

            case R.id.preferences:
                if (!(dataRecorder.isRecording())) {
                    Intent intent = new Intent(this, PrefsActivity.class);
                    startActivity(intent);
                }
                return true;

            case R.id.toggleRec:
                toggleRec();
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
                if (!(dataRecorder.isRecording())) {
                    enterFilename();
                }
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
                synchronized (iirNotch) {
                    final int idx = 0;
                    if (iirNotch[idx] == null) {
                        iirNotch[idx] = new Butterworth();
                        iirNotch[idx].bandStop(
                                notchOrder,
                                attysService.getAttysComm().getSamplingRateInHz(),
                                powerlineHz,
                                notchBW);
                    } else {
                        iirNotch[idx] = null;
                    }
                    item.setChecked(iirNotch[idx] != null);
                }
                return true;

            case R.id.Ch2notch:
                synchronized (iirNotch) {
                    final int idx = 1;
                    if (iirNotch[idx] == null) {
                        iirNotch[idx] = new Butterworth();
                        iirNotch[idx].bandStop(notchOrder,
                                attysService.getAttysComm().getSamplingRateInHz(), powerlineHz, notchBW);
                    } else {
                        iirNotch[idx] = null;
                    }
                    item.setChecked(iirNotch[idx] != null);
                }
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
                if (attysService.getAttysComm() != null) {
                    amplitudeFragment.setSamplingrate(attysService.getAttysComm().getSamplingRateInHz());
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
                if (attysService.getAttysComm() != null) {
                    fourierFragment.setSamplingrate(attysService.getAttysComm().getSamplingRateInHz());
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

            case R.id.changefolder:
                if (dataRecorder.isRecording()) return true;
                triggerRequestDirectoryAccess(this);
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


    synchronized private void getsetAttysPrefs() {
        byte mux;

        if (null == attysService) return;
        if (null == attysService.getAttysComm()) return;

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
        attysService.getAttysComm().setAdc1_gain_index(gain0);
        attysService.getAttysComm().setAdc0_mux_index(mux);
        byte gain1 = (byte) (Integer.parseInt(prefs.getString("ch2_gainpref", "0")));
        attysService.getAttysComm().setAdc2_gain_index(gain1);
        attysService.getAttysComm().setAdc1_mux_index(mux);

        int ch2_option = Integer.parseInt(prefs.getString("ch2_options", "-1"));
        ch2Converter.setRule(ch2_option);
        int currentIndex = ch2Converter.getCurrentIndex();
        if (currentIndex < 0) {
            attysService.getAttysComm().enableCurrents(false, false, false);
        } else {
            attysService.getAttysComm().setBiasCurrent((byte) currentIndex);
            attysService.getAttysComm().enableCurrents(false, false, true);
        }

        dataSeparator = (byte) (Integer.parseInt(prefs.getString("data_separator", "0")));
        Log.d(TAG, "Data separator = " + dataSeparator + ", suff:" + getFileSuffix());

        int fullscaleAcc = Integer.parseInt(prefs.getString("accFullscale", "1"));

        attysService.getAttysComm().setAccel_full_scale_index((byte) fullscaleAcc);

        powerlineHz = Float.parseFloat(prefs.getString("powerline", "50"));
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "powerline=" + powerlineHz);
        }

        samplingRate = (byte) Integer.parseInt(prefs.getString("samplingrate", "1"));
        if (samplingRate < 0) samplingRate = 0;
        BluetoothDevice bluetoothDevice = attysService.getAttysComm().getBluetoothDevice();
        if (null != bluetoothDevice) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                if (bluetoothDevice.getName().contains("ATTYS2")) {
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

        attysService.getAttysComm().setAdc_samplingrate_index(samplingRate);

        highpass1Hz = Float.parseFloat(prefs.getString("highpass1", "0.1"));
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "highpass1=" + highpass1Hz);
        }
        highpass2Hz = Float.parseFloat(prefs.getString("highpass2", "0.1"));
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "highpass2=" + highpass2Hz);
        }

        System.arraycopy(AttysComm.CHANNEL_UNITS, 0, units, 0, AttysComm.NCHANNELS);

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

    public final static byte DATA_SEPARATOR_TAB = 0;
    public final static byte DATA_SEPARATOR_COMMA = 1;
    public final static byte DATA_SEPARATOR_SPACE = 2;


    static char getDataSeparatorChar() {
        char s = ' ';
        switch (dataSeparator) {
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
        return s;
    }


    static String getMimeType() {
        String s = "text/dat";
        switch (dataSeparator) {
            case DATA_SEPARATOR_SPACE:
                s = "text/dat";
                break;
            case DATA_SEPARATOR_COMMA:
                s = "text/csv";
                break;
            case DATA_SEPARATOR_TAB:
                s = "text/tsv";
                break;
        }
        return s;
    }


    static String getFileSuffix() {
        String s = ".dat";
        switch (dataSeparator) {
            case DATA_SEPARATOR_SPACE:
                s = ".dat";
                break;
            case DATA_SEPARATOR_COMMA:
                s = ".csv";
                break;
            case DATA_SEPARATOR_TAB:
                s = ".tsv";
                break;
        }
        return s;
    }


    static String fixFilename(String dataFilename) {
        dataFilename = dataFilename.replaceAll("[^a-zA-Z0-9.-]", "_");
        if (!dataFilename.contains(".")) {
            dataFilename = dataFilename + getFileSuffix();
        }
        return dataFilename;
    }


    public class DataRecorder {
        /////////////////////////////////////////////////////////////
        // saving data into a file

        private PrintWriter textdataFileStream = null;
        private Uri uri = null;

        // starts the recording
        public void startRec(Uri _uri) throws Exception {
            uri = _uri;
            if (null == uri) return;
            try {
                Log.d(TAG,"Starting recording. URI = "+uri.toString());
                textdataFileStream = new PrintWriter(Objects.requireNonNull(getContentResolver().openOutputStream(uri)));
                attysService.getAttysComm().resetSampleCounter();
                Log.d(TAG,"textdataFileStream = "+textdataFileStream);
            } catch (Exception e) {
                textdataFileStream = null;
                Log.d(TAG,"Could not start recording. URI = "+uri.toString(),e);
                throw e;
            }
        }

        // stops it
        public void stopRec() {
            if (textdataFileStream != null) {
                textdataFileStream.close();
                textdataFileStream = null;
            }
        }

        public boolean isRecording() {
            return (textdataFileStream != null);
        }

        public void saveData(long sampleNo, float[] data,float adc1,float adc2) {
            if (textdataFileStream == null) return;

            char s = getDataSeparatorChar();

            String tmp = String.format(Locale.US, "%e%c", (double) sampleNo / (double) attysService.getAttysComm().getSamplingRateInHz(), s);
            for (int i=0;i<AttysComm.NCHANNELS;i++) {
                if (i < AttysComm.INDEX_GPIO0) {
                    tmp = tmp + String.format(Locale.US, "%e%c", data[i], s);
                } else {
                    tmp = tmp + String.format(Locale.US, "%d%c", (int)(data[i]), s);
                }
            }
            tmp = tmp + String.format(Locale.US, "%e%c", adc1, s);
            tmp = tmp + String.format(Locale.US, "%e", adc2);

            if (textdataFileStream != null) {
                textdataFileStream.format(Locale.US, "%s\n", tmp);
            }
        }
    }

    private final DataRecorder dataRecorder = new DataRecorder();

}
