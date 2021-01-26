package tech.glasgowneuro.attysscope2;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import tech.glasgowneuro.attyscomm.AttysComm;
import tech.glasgowneuro.attyscomm.AttysService;

/**
 * RMS / pp Fragment
 */

public class AmplitudeFragment extends Fragment {

    String TAG = "AmplitudeFragment";

    int channel = AttysComm.INDEX_Analogue_channel_1;

    final int nSampleBufferSize = 100;

    private boolean isRMSmode = false;

    private Spinner spinnerMaxY;

    private static String[] MAXYTXT = {
            "auto range", "1E8", "1E7", "1E6", "1E5", "1E4", "500", "50", "20", "10", "5", "2", "1", "0.5", "0.1", "0.05",
            "0.01", "0.005", "0.001", "0.0005", "0.0001"};

    private static String[] WINDOW_LENGTH = {"0.1 sec", "0.2 sec", "0.5 sec", "1 sec", "2 sec", "5 sec", "10 sec"};

    private int DEFAULT_WINDOW_LENGTH = 3;

    private int windowLength = 100;

    private SimpleXYSeries amplitudeHistorySeries;
    private SimpleXYSeries amplitudeFullSeries = null;

    private XYPlot amplitudePlot = null;

    private TextView amplitudeReadingText = null;

    private ToggleButton toggleButtonDoRecord;

    private ToggleButton toggleButtonRMS_pp;

    private Button resetButton;

    private Button saveButton;

    private Spinner spinnerChannel;

    private Spinner spinnerWindow;

    private SignalAnalysis signalAnalysis = null;

    private String[] units = new String[AttysComm.NCHANNELS];

    public void setUnits(String [] _units) {
        for(int i=0;i<AttysComm.NCHANNELS;i++) {
            units[i] = _units[i];
        }
    }

    View view = null;

    int samplingRate = 250;

    int step = 0;

    float current_stat_result = 0;

    boolean ready = false;

    boolean acceptData = false;

    private String dataFilename = null;

    private byte dataSeparator = AttysScope.DataRecorder.DATA_SEPARATOR_TAB;

    public void setSamplingrate(int _samplingrate) {
        samplingRate = _samplingrate;
    }

    public void reset() {
        ready = false;

        signalAnalysis = new SignalAnalysis(windowLength);

        step = 0;

        int n = amplitudeHistorySeries.size();
        for (int i = 0; i < n; i++) {
            amplitudeHistorySeries.removeLast();
        }
        amplitudeFullSeries = new SimpleXYSeries(" ");

        if (isRMSmode) {
            amplitudeHistorySeries.setTitle(units[channel] + " RMS");
        } else {
            amplitudeHistorySeries.setTitle(units[channel] + " pp");
        }
        amplitudePlot.setRangeLabel(units[channel]);
        amplitudePlot.setTitle(" ");

        amplitudePlot.redraw();

        ready = true;
    }


    /**
     * Called when the activity is first created.
     */
    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "creating Fragment");
        }

        if (container == null) {
            return null;
        }

        signalAnalysis = new SignalAnalysis(samplingRate);

        view = inflater.inflate(R.layout.amplitudefragment, container, false);

        // setup the APR Levels plot:
        amplitudePlot = view.findViewById(R.id.amplitude_PlotView);
        amplitudeHistorySeries = new SimpleXYSeries(" ");
        amplitudeReadingText = view.findViewById(R.id.amplitude_valueTextView);
        amplitudeReadingText.setText(String.format("%04d", 0));
        toggleButtonDoRecord = view.findViewById(R.id.amplitude_doRecord);
        toggleButtonDoRecord.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                acceptData = isChecked;
            }
        });
        toggleButtonDoRecord.setChecked(true);

        toggleButtonRMS_pp = view.findViewById(R.id.amplitude_rms_pp);
        toggleButtonRMS_pp.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                isRMSmode = isChecked;
                reset();
            }
        });
        toggleButtonRMS_pp.setChecked(false);

        resetButton = view.findViewById(R.id.amplitude_Reset);
        resetButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                reset();
            }
        });
        saveButton = view.findViewById(R.id.amplitude_Save);
        saveButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                saveAmplitude();
            }
        });

        spinnerChannel = view.findViewById(R.id.amplitude_channel);
        ArrayAdapter<String> adapterChannel = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_dropdown_item,
                AttysComm.CHANNEL_DESCRIPTION_SHORT);
        adapterChannel.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerChannel.setAdapter(adapterChannel);
        spinnerChannel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                channel = position;
                reset();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        spinnerChannel.setBackgroundResource(android.R.drawable.btn_default);
        spinnerChannel.setSelection(AttysComm.INDEX_Analogue_channel_1);


        spinnerWindow = view.findViewById(R.id.amplitude_window);
        ArrayAdapter<String> adapterWindow = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_dropdown_item,
                WINDOW_LENGTH);
        adapterWindow.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerWindow.setAdapter(adapterWindow);
        spinnerWindow.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                float winLen = Float.parseFloat(WINDOW_LENGTH[position].split(" ")[0]);
                windowLength = (int) (winLen * samplingRate);
                //Log.d(TAG, "winlen=" + windowLength);
                reset();
                if (amplitudePlot != null) {
                    amplitudePlot.setDomainStep(StepMode.INCREMENT_BY_VAL, 20 * winLen);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        spinnerWindow.setBackgroundResource(android.R.drawable.btn_default);
        spinnerWindow.setSelection(DEFAULT_WINDOW_LENGTH);


        amplitudePlot.addSeries(amplitudeHistorySeries,
                new LineAndPointFormatter(
                        Color.rgb(100, 255, 255), null, null, null));

        Paint paint = new Paint();
        paint.setColor(Color.argb(128, 0, 255, 0));
        amplitudePlot.getGraph().setDomainGridLinePaint(paint);
        amplitudePlot.getGraph().setRangeGridLinePaint(paint);

        amplitudePlot.setDomainLabel("t/sec");
        amplitudePlot.setRangeLabel(" ");

        amplitudePlot.setRangeLowerBoundary(0, BoundaryMode.FIXED);
        amplitudePlot.setRangeUpperBoundary(1, BoundaryMode.AUTO);

        XYGraphWidget.LineLabelRenderer lineLabelRendererY = new XYGraphWidget.LineLabelRenderer() {
            @Override
            public void drawLabel(Canvas canvas,
                                  XYGraphWidget.LineLabelStyle style,
                                  Number val, float x, float y, boolean isOrigin) {
                Rect bounds = new Rect();
                style.getPaint().getTextBounds("a", 0, 1, bounds);
                drawLabel(canvas, String.format("%04.5f ", val.floatValue()),
                        style.getPaint(), x + bounds.width() / 2, y + bounds.height(), isOrigin);
            }
        };

        amplitudePlot.getGraph().setLineLabelRenderer(XYGraphWidget.Edge.LEFT, lineLabelRendererY);
        XYGraphWidget.LineLabelStyle lineLabelStyle = amplitudePlot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.LEFT);
        Rect bounds = new Rect();
        String dummyTxt = String.format("%04.5f ", 100000.000599);
        lineLabelStyle.getPaint().getTextBounds(dummyTxt, 0, dummyTxt.length(), bounds);
        amplitudePlot.getGraph().setMarginLeft(bounds.width());

        XYGraphWidget.LineLabelRenderer lineLabelRendererX = new XYGraphWidget.LineLabelRenderer() {
            @Override
            public void drawLabel(Canvas canvas,
                                  XYGraphWidget.LineLabelStyle style,
                                  Number val, float x, float y, boolean isOrigin) {
                if (!isOrigin) {
                    Rect bounds = new Rect();
                    style.getPaint().getTextBounds("a", 0, 1, bounds);
                    drawLabel(canvas, String.format("%d", val.intValue()),
                            style.getPaint(), x + bounds.width() / 2, y + bounds.height(), isOrigin);
                }
            }
        };

        amplitudePlot.getGraph().setLineLabelRenderer(XYGraphWidget.Edge.BOTTOM, lineLabelRendererX);

        spinnerMaxY = view.findViewById(R.id.amplitude_maxy);
        ArrayAdapter<String> adapter1 = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, MAXYTXT);
        adapter1.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMaxY.setAdapter(adapter1);
        spinnerMaxY.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    amplitudePlot.setRangeUpperBoundary(1, BoundaryMode.AUTO);
                    amplitudePlot.setRangeLowerBoundary(0, BoundaryMode.AUTO);
                } else {
                    Screensize screensize = new Screensize(getActivity().getWindowManager());
                    amplitudePlot.setRangeLowerBoundary(0, BoundaryMode.FIXED);
                    if (screensize.isTablet()) {
                        amplitudePlot.setRangeStep(StepMode.INCREMENT_BY_VAL, Float.parseFloat(MAXYTXT[position]) / 10);
                    } else {
                        amplitudePlot.setRangeStep(StepMode.INCREMENT_BY_VAL, Float.parseFloat(MAXYTXT[position]) / 10);
                    }
                    amplitudePlot.setRangeUpperBoundary(Float.parseFloat(MAXYTXT[position]), BoundaryMode.FIXED);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        spinnerMaxY.setBackgroundResource(android.R.drawable.btn_default);
        spinnerMaxY.setSelection(0);

        reset();

        return view;

    }


    private void writeAmplitudefile() throws IOException {

        PrintWriter aepdataFileStream;

        if (dataFilename == null) return;

        File file;

        try {
            file = new File(AttysScope.ATTYSDIR, dataFilename.trim());
            file.createNewFile();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Saving amplitude data to " + file.getAbsolutePath());
            }
            aepdataFileStream = new PrintWriter(file);
        } catch (java.io.FileNotFoundException e) {
            throw e;
        }

        char s = ' ';
        switch (dataSeparator) {
            case AttysScope.DataRecorder.DATA_SEPARATOR_SPACE:
                s = ' ';
                break;
            case AttysScope.DataRecorder.DATA_SEPARATOR_COMMA:
                s = ',';
                break;
            case AttysScope.DataRecorder.DATA_SEPARATOR_TAB:
                s = 9;
                break;
        }

        for (int i = 0; i < amplitudeFullSeries.size(); i++) {
            aepdataFileStream.format("%e%c%e%c\n",
                    amplitudeFullSeries.getX(i).floatValue(), s,
                    amplitudeFullSeries.getY(i).floatValue(), s);
            if (aepdataFileStream.checkError()) {
                throw new IOException("file write error");
            }
        }

        aepdataFileStream.close();

        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri contentUri = Uri.fromFile(file);
        mediaScanIntent.setData(contentUri);
        getActivity().sendBroadcast(mediaScanIntent);
    }


    private void saveAmplitude() {

        final EditText filenameEditText = new EditText(getContext());
        filenameEditText.setSingleLine(true);

        final int REQUEST_EXTERNAL_STORAGE = 1;
        String[] PERMISSIONS_STORAGE = {
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        int permission = ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    getActivity(),
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }

        filenameEditText.setHint("");
        filenameEditText.setText(dataFilename);

        new AlertDialog.Builder(getContext())
                .setTitle("Saving fast/slow data")
                .setMessage("Enter the filename of the data textfile")
                .setView(filenameEditText)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dataFilename = filenameEditText.getText().toString();
                        dataFilename = dataFilename.replaceAll("[^a-zA-Z0-9.-]", "_");
                        if (!dataFilename.contains(".")) {
                            switch (dataSeparator) {
                                case AttysScope.DataRecorder.DATA_SEPARATOR_COMMA:
                                    dataFilename = dataFilename + ".csv";
                                    break;
                                case AttysScope.DataRecorder.DATA_SEPARATOR_SPACE:
                                    dataFilename = dataFilename + ".dat";
                                    break;
                                case AttysScope.DataRecorder.DATA_SEPARATOR_TAB:
                                    dataFilename = dataFilename + ".tsv";
                            }
                        }
                        try {
                            writeAmplitudefile();
                            Toast.makeText(getActivity(),
                                    "Successfully written '" + dataFilename + "' to the external memory",
                                    Toast.LENGTH_SHORT).show();
                        } catch (IOException e) {
                            Toast.makeText(getActivity(),
                                    "Write Error while saving '" + dataFilename + "' to the external memory",
                                    Toast.LENGTH_SHORT).show();
                            if (Log.isLoggable(TAG, Log.DEBUG)) {
                                Log.d(TAG, "Error saving file: ", e);
                            }
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                })
                .show();
    }


    public synchronized void addValue(final float[] sample) {
        if (!ready) return;
        if (!acceptData) return;
        if (signalAnalysis != null) {
            signalAnalysis.addData(sample[channel]);
            if (signalAnalysis.bufferFull()) {
                updateStats();
                signalAnalysis.reset();
            }
        }
    }


    private void updateStats() {

        double delta_t = (double) windowLength * (1.0 / samplingRate);

        if (isRMSmode) {
            if (signalAnalysis != null) {
                current_stat_result = signalAnalysis.getRMS();
            }
        } else {
            if (signalAnalysis != null) {
                current_stat_result = signalAnalysis.getPeakToPeak();
            }
        }

        if (amplitudeHistorySeries == null) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "amplitudeHistorySeries == null");
            }
            return;
        }

        // get rid the oldest sample in history:
        if (amplitudeHistorySeries.size() > nSampleBufferSize) {
            amplitudeHistorySeries.removeFirst();
        }

        int n = nSampleBufferSize - amplitudeHistorySeries.size();
        for (int i = 0; i < n; i++) {
            // add the latest history sample:
            amplitudeHistorySeries.addLast(step * delta_t, current_stat_result);
            step++;
        }

        // add the latest history sample:
        amplitudeHistorySeries.addLast(step * delta_t, current_stat_result);
        amplitudeFullSeries.addLast(step * delta_t, current_stat_result);
        step++;
        amplitudePlot.redraw();

        if (getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (amplitudeReadingText != null) {
                        if (isRMSmode) {
                            amplitudeReadingText.setText(String.format("%1.05f %s RMS", current_stat_result, units[channel]));
                        } else {
                            amplitudeReadingText.setText(String.format("%1.05f %s pp", current_stat_result, units[channel]));
                        }
                    }
                }
            });
        }
    }
}
