/**
 Copyright 2016 Bernd Porr, mail@berndporr.me.uk

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 **/

package uk.me.berndporr.www.attysplot;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CheckBox;
import android.widget.Toast;

//import com.google.android.gms.appindexing.Action;
//import com.google.android.gms.appindexing.AppIndex;
//import com.google.android.gms.common.api.GoogleApiClient;

import java.util.Arrays;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class AttysPlot extends AppCompatActivity {

    private Timer timer = null;

    private RealtimePlotView realtimePlotView = null;

    private CheckBox showAccelerometer, showGyroscope, showMagnetometer, showADC1, showADC2;

    private BluetoothAdapter BA;
    private AttysComm attysComm = null;
    private BluetoothDevice btAttysDevice = null;

    private static final String TAG = "AttysPlot";

    private Highpass [] highpass = null;
    private float [] gain;
    private IIR_notch[] iirNotch;
    private boolean[] invert;

    private boolean showAcc = true;
    private boolean showGyr = true;
    private boolean showMag = true;
    private boolean showCh1 = true;
    private boolean showCh2 = true;


    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case AttysComm.BT_ERROR:
                    Toast.makeText(getApplicationContext(),
                            "Bluetooth error", Toast.LENGTH_LONG).show();
                    finish();
                    break;
                case AttysComm.BT_CONNECTED:
                    Toast.makeText(getApplicationContext(),
                            "Bluetooth connected", Toast.LENGTH_SHORT).show();
                    break;
                case AttysComm.BT_CONFIGURE:
                    Toast.makeText(getApplicationContext(),
                            "Configuring Attys", Toast.LENGTH_SHORT).show();
                    break;
                case AttysComm.BT_RETRY:
                    Toast.makeText(getApplicationContext(),
                            "Bluetooth connection problems - trying again. Please be patient.",
                            Toast.LENGTH_LONG).show();
                    break;
            }
        }
    };


    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    // private GoogleApiClient client;






    private BluetoothDevice connect2Bluetooth() {

        Intent turnOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(turnOn, 0);

        BA = BluetoothAdapter.getDefaultAdapter();

        if (BA == null) {
            Log.d(TAG, "no bluetooth adapter!");
            finish();
        }

        Set<BluetoothDevice> pairedDevices;
        pairedDevices = BA.getBondedDevices();

        if (pairedDevices == null) {
            Log.d(TAG, "No paired devices available. Exiting.");
            finish();
        }

        for (BluetoothDevice bt : pairedDevices) {
            String b = bt.getName();
            Log.d(TAG, b);
            if (b.startsWith("GN-ATTYS")) {
                Log.d(TAG,"Found an Attys");
                return bt;
            }
        }
        return null;
    }


    private class UpdatePlotTask extends TimerTask {

        public void run() {

            if (attysComm != null) {
                if (attysComm.hasFatalError()) {
                    // Log.d(TAG,String.format("No bluetooth connection"));
                    handler.sendEmptyMessage(AttysComm.BT_ERROR);
                    return;
                }
            }
            if (attysComm != null) {
                if (!attysComm.hasActiveConnection()) return;
            }
            int nCh = 0;
            if (attysComm != null) nCh = attysComm.getnChannels();
            if (attysComm != null) {
                float[] tmpSample = new float[nCh];
                float[] tmpMin = new float[nCh];
                float[] tmpMax = new float[nCh];
                float[] tmpTick = new float[nCh];
                int n = attysComm.getNumSamplesAvilable();
                if (realtimePlotView != null) {
                    realtimePlotView.startAddSamples(n);
                    for (int i = 0; ((i < n) && (attysComm != null)); i++) {
                        float[] sample = attysComm.getSampleFromBuffer();
                        for (int j = 0; j < nCh; j++) {
                            float v = sample[j];
                            if (j > 8) {
                                v = highpass[j].filter(v);
                                v = iirNotch[j].filter(v);
                            }
                            v = v * gain[j];
                            if (invert[j]) {
                                sample[j] = -v;
                            } else {
                                sample[j] = v;
                            }
                        }
                        int nRealChN = 0;
                        int sn = 0;
                        if (showAcc) {
                            if (attysComm != null) {
                                float min = -attysComm.getAccelFullScaleRange();
                                float max = attysComm.getAccelFullScaleRange();

                                for (int k = 0; k < 3; k++) {
                                    tmpMin[nRealChN] = min;
                                    tmpMax[nRealChN] = max;
                                    tmpTick[nRealChN] = gain[k] * 1.0F; // 1G
                                    tmpSample[nRealChN++] = sample[k];
                                }
                            }
                        }
                        if (showGyr) {
                            if (attysComm != null) {
                                float min = -attysComm.getGyroFullScaleRange();
                                float max = attysComm.getGyroFullScaleRange();
                                for (int k = 0; k < 3; k++) {
                                    tmpMin[nRealChN] = min;
                                    tmpMax[nRealChN] = max;
                                    tmpTick[nRealChN] = gain[k + 3] * 1000.0F; // 1000DPS
                                    tmpSample[nRealChN++] = sample[k + 3];
                                }
                            }
                        }
                        if (showMag) {
                            if (attysComm != null) {
                                for (int k = 0; k < 3; k++) {
                                    tmpMin[nRealChN] = -attysComm.getMagFullScaleRange();
                                    tmpMax[nRealChN] = attysComm.getMagFullScaleRange();
                                    ;
                                    tmpTick[nRealChN] = gain[k + 6] * 1000.0E-6F; //1000uT
                                    tmpSample[nRealChN++] = sample[k + 6];
                                }
                            }
                        }
                        if (showCh1) {
                            if (attysComm != null) {
                                tmpMin[nRealChN] = -attysComm.getADCFullScaleRange(0);
                                tmpMax[nRealChN] = attysComm.getADCFullScaleRange(0);
                                tmpTick[nRealChN] = 0.001F * gain[9]; // 1mV
                                tmpSample[nRealChN++] = sample[9];
                            }
                        }
                        if (showCh2) {
                            if (attysComm != null) {
                                tmpMin[nRealChN] = -attysComm.getADCFullScaleRange(1);
                                tmpMax[nRealChN] = attysComm.getADCFullScaleRange(1);
                                tmpTick[nRealChN] = 0.001F * gain[10]; // 1mV
                                tmpSample[nRealChN++] = sample[10];
                            }
                        }
                        realtimePlotView.addSamples(Arrays.copyOfRange(tmpSample,0,nRealChN),
                                Arrays.copyOfRange(tmpMin,0,nRealChN),
                                Arrays.copyOfRange(tmpMax,0,nRealChN),
                                Arrays.copyOfRange(tmpTick,0,nRealChN));
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
        Log.d(TAG,String.format("Back button pressed"));
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

        setContentView(R.layout.activity_plot_window);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);

        btAttysDevice = connect2Bluetooth();
        if (btAttysDevice == null) {
            Context context = getApplicationContext();
            CharSequence text = "Could not find any paired Attys devices.";
            int duration = Toast.LENGTH_LONG;
            Toast toast = Toast.makeText(context, text, duration);
            toast.show();
            finish();
        }

        attysComm = new AttysComm(btAttysDevice,handler);

        int nChannels = attysComm.getnChannels();
        highpass = new Highpass[nChannels];
        gain = new float[nChannels];
        iirNotch = new IIR_notch[nChannels];
        invert = new boolean[nChannels];
        for(int i = 0;i<nChannels;i++) {
            highpass[i] = new Highpass();
            highpass[i].setAlpha(0.01F);
            iirNotch[i] = new IIR_notch();
            gain[i] = 1;
            // for magnetomter
            if (i > 5) gain[i] = 50;
            // initial gain for AD channels
            if (i > 8) gain[i] = 200;
        }

        attysComm.start();

        for(int i = 0;i<nChannels;i++) {
            iirNotch[i].setParameters((float) 50.0 / attysComm.getSamplingRateInHz(), 0.9F);
            iirNotch[i].setIsActive(true);
            highpass[i].setAlpha(1.0F/attysComm.getSamplingRateInHz());
        }

        realtimePlotView = (RealtimePlotView) findViewById(R.id.realtimeplotview);
        realtimePlotView.setMaxChannels(15);

        timer = new Timer();
        UpdatePlotTask updatePlotTask = new UpdatePlotTask();
        timer.schedule(updatePlotTask,0,300);

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
//        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu_attysplot, menu);

        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.toggleAcc:
                showAcc = !showAcc;
                item.setChecked(showAcc);
                return true;

            case R.id.toggleGyr:
                showGyr = !showGyr;
                item.setChecked(showGyr);
                return true;

            case R.id.toggleMag:
                showMag = !showMag;
                item.setChecked(showMag);
                return true;

            case R.id.toggleCh1:
                showCh1 = !showCh1;
                item.setChecked(showCh1);
                return true;

            case R.id.toggleCh2:
                showCh2 = !showCh2;
                item.setChecked(showCh2);
                return true;

            case R.id.Ch1toggleDC:
                boolean a = highpass[9].getIsActive();
                a = !a;
                item.setChecked(a);
                highpass[9].setActive(a);
                return true;

            case R.id.Ch2toggleDC:
                a = highpass[10].getIsActive();
                a = !a;
                item.setChecked(a);
                highpass[10].setActive(a);
                return true;

            case R.id.Ch1notch:
                a = iirNotch[9].getIsActive();
                a = !a;
                item.setChecked(a);
                iirNotch[9].setIsActive(a);
                return true;

            case R.id.Ch2notch:
                a = iirNotch[10].getIsActive();
                a = !a;
                item.setChecked(a);
                iirNotch[10].setIsActive(a);
                return true;

            case R.id.Ch1invert:
                a = invert[9];
                a = !a;
                invert[9] = a;
                item.setChecked(a);
                return true;

            case R.id.Ch2invert:
                a = invert[10];
                a = !a;
                invert[10] = a;
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
                String t = item.getTitle().toString();
                int g = Integer.parseInt(t);
                Log.d(TAG, String.format("g=%d",g));
                gain[9] = (float)g;
                Toast.makeText(getApplicationContext(),
                        String.format("Channel 1 gain set to x%d",g),Toast.LENGTH_LONG).show();
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
                t = item.getTitle().toString();
                g = Integer.parseInt(t);
                Toast.makeText(getApplicationContext(),
                        String.format("Channel 2 gain set to x%d",g),Toast.LENGTH_LONG).show();
                gain[10] = (float)g;
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }



    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.

   /**
        client.connect();
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "AttysPlot Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://www.berndporr.me.uk"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://uk.me.berndporr.www.attysplot/http/host/path")
        );
        AppIndex.AppIndexApi.start(client, viewAction);
        **/
    }

    private void killAttysComm() {
        if (attysComm != null) {
            attysComm.cancel();
            try {
                attysComm.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            attysComm = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.d(TAG, String.format("Destroy!"));
        killAttysComm();
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        Log.d(TAG, String.format("Restarting"));
        realtimePlotView.resetX();
        killAttysComm();
        attysComm = new AttysComm(btAttysDevice, handler);
        attysComm.start();
    }


    @Override
    public void onStop() {
        super.onStop();

        Log.d(TAG,String.format("Stopped"));

        killAttysComm();

        /**
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "AttysPlot Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://uk.me.berndporr.www.attysplot/http/host/path")
        );
        AppIndex.AppIndexApi.end(client, viewAction);
        client.disconnect();
         **/
    }
}
