package tech.glasgowneuro.www.attysplot;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * Created by Bernd Porr on 01/10/16.
 */

public class Attys2ScienceJournalADCSettings extends Activity {

    private static final String TAG="AttysADCSettings";

    public static final String[] adc_modes = {"AC Voltage", "DC Voltage", "Resistance", "ECG/EMG/bio"};
    public static final int MODE_AC = 0;
    public static final int MODE_DC = 1;
    public static final int MODE_R = 2;
    public static final int MODE_BIO = 3;

    public static final String[] powerline_filter = {"off", "remove 50Hz", "remove 60Hz"};
    public static final int POWERLINE_FILTER_OFF = 0;
    public static final int POWERLINE_FILTER_50HZ = 1;
    public static final int POWERLINE_FILTER_60HZ = 2;

    private static final String PREF_KEY_MODE_PREFIX = "attys_adc_mode_";
    private static final String PREF_KEY_POWERLINE_PREFIX = "attys_adc_powerline_";

    private static final String SENSOR_PREF_NAME = "attys_sensors";

    private static final String EXTRA_SENSOR_ADC_CH = "attys_adc_ch";

    public static PendingIntent getPendingIntent(Context context, int adcChannel) {
        int flags = 0;
        Intent intent = new Intent(context, Attys2ScienceJournalADCSettings.class);
        intent.putExtra(EXTRA_SENSOR_ADC_CH, adcChannel);
        return PendingIntent.getActivity(context, adcChannel, intent, flags);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG,"onCreate called");

        setTitle("Attys ADC configuration");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.adc_settings);

        Bundle extras = getIntent().getExtras();
        final int sensorCh = extras.getInt(EXTRA_SENSOR_ADC_CH);
        TextView header = (TextView) findViewById(R.id.header);
        header.setText("Channel " + (sensorCh+1) + ":");

        Spinner spinnerMode = (Spinner) findViewById(R.id.mode_spinner);
        spinnerMode.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                adc_modes));
        spinnerMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SharedPreferences prefs = getSensorPreferences(Attys2ScienceJournalADCSettings.this);
                prefs.edit().putInt(getModePrefKey(sensorCh), position).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        spinnerMode.setSelection(getIndexForMode(sensorCh, this));


        Spinner spinnerPowerline = (Spinner) findViewById(R.id.powerline_spinner);
        spinnerPowerline.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                new String[]{"off", "remove 50Hz", "remove 60Hz"}));
        spinnerPowerline.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SharedPreferences prefs = getSensorPreferences(Attys2ScienceJournalADCSettings.this);
                prefs.edit().putInt(getPowerlinePrefKey(sensorCh), position).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        spinnerPowerline.setSelection(getIndexForPowerline(sensorCh, this));


    }

    public static int getIndexForMode(int channel, Context context) {
        return getSensorPreferences(context).getInt(getModePrefKey(channel), 0);
    }

    public static int getIndexForPowerline(int channel, Context context) {
        return getSensorPreferences(context).getInt(getPowerlinePrefKey(channel), 0);
    }

    private static String getModePrefKey(int channel) {
        return PREF_KEY_MODE_PREFIX + channel;
    }

    private static String getPowerlinePrefKey(int channel) {
        return PREF_KEY_POWERLINE_PREFIX + channel;
    }

    private static SharedPreferences getSensorPreferences(Context context) {
        return context.getSharedPreferences(SENSOR_PREF_NAME, Context.MODE_PRIVATE);
    }
}
