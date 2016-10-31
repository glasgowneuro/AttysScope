package tech.glasgowneuro.attys2sciencejournal;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import tech.glasgowneuro.attysplot.R;

/**
 * Settings for the ADC channel 1
 */

public class ADC1Settings extends Activity {

    private static final String TAG="AttysADC1Settings";

    public static final String[] adc_modes = {
            "DC/Volt", "AC/Volt",
            "DC/mV", "AC/mV (BIO)",
            "DC/µV", "AC/µV" };
    public static final int MODE_DC = 0;
    public static final int MODE_AC = 1;
    public static final int MODE_DC_MV = 2;
    public static final int MODE_AC_MV = 3;
    public static final int MODE_DC_UV = 4;
    public static final int MODE_AC_UV = 5;

    public static final String[] powerline_filter = {"off", "remove 50Hz", "remove 60Hz"};
    public static final int POWERLINE_FILTER_OFF = 0;
    public static final int POWERLINE_FILTER_50HZ = 1;
    public static final int POWERLINE_FILTER_60HZ = 2;

    private static final String PREF_KEY_MODE = "attys_adc1_mode";
    private static final String PREF_KEY_POWERLINE = "attys_adc1_powerline";

    public static PendingIntent getPendingIntent(Context context) {
        int flags = 0;
        Intent intent = new Intent(context, ADC1Settings.class);
        return PendingIntent.getActivity(context, 0, intent, flags);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        setTitle("Attys ADC configuration");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.adc_settings);

        TextView header = (TextView) findViewById(R.id.header);
        header.setText("Channel 1:");

        Spinner spinnerMode = (Spinner) findViewById(R.id.mode_spinner);
        spinnerMode.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                adc_modes));
        spinnerMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SharedPreferences prefs = getSensorPreferences(ADC1Settings.this);
                prefs.edit().putInt(PREF_KEY_MODE, position).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        spinnerMode.setSelection(getIndexForMode(this));


        Spinner spinnerPowerline = (Spinner) findViewById(R.id.powerline_spinner);
        spinnerPowerline.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                powerline_filter));
        spinnerPowerline.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                SharedPreferences prefs = getSensorPreferences(ADC1Settings.this);
                prefs.edit().putInt(PREF_KEY_POWERLINE, position).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        spinnerPowerline.setSelection(getIndexForPowerline(this));


    }

    public static int getIndexForMode(Context context) {
        return getSensorPreferences(context).getInt(PREF_KEY_MODE, MODE_DC);
    }

    public static int getIndexForPowerline(Context context) {
        return getSensorPreferences(context).getInt(PREF_KEY_POWERLINE, POWERLINE_FILTER_OFF);
    }

    private static SharedPreferences getSensorPreferences(Context context) {
        return context.getSharedPreferences(
                Attys2ScienceJournal.SENSOR_PREF_NAME,
                Context.MODE_PRIVATE);
    }
}
