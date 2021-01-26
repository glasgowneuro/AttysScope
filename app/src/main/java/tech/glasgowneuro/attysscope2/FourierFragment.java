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

import androidx.annotation.NonNull;
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
import android.widget.Toast;
import android.widget.ToggleButton;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Objects;

import tech.glasgowneuro.attyscomm.AttysComm;
import tech.glasgowneuro.attyscomm.AttysService;

/**
 * Fourier Transform Fragment
 */

public class FourierFragment extends Fragment {

    private String TAG = "FourierFragment";

    private int channel = AttysComm.INDEX_Analogue_channel_1;

    private SimpleXYSeries spectrumSeries = null;

    private XYPlot spectrumPlot = null;

    private static String[] MAXYTXT = {"auto range", "1", "0.5", "0.1", "0.05", "0.01", "0.005", "0.001", "0.0005", "0.0001", "0.00005", "0.00001"};

    private String[] units = new String[AttysComm.NCHANNELS];

    void setUnits(String[] _units) {
        System.arraycopy(_units, 0, units, 0, AttysComm.NCHANNELS);
    }

    private FourierTransformRunnable fourierTransformRunnable = null;

    private int samplingRate = 250;

    private boolean ready = false;

    private boolean acceptData = false;

    private static final int BUFFERSIZE = 256;

    private double[] values = new double[BUFFERSIZE];

    private int nValues = 0;

    private String dataFilename = null;

    private byte dataSeparator = AttysService.DataRecorder.DATA_SEPARATOR_TAB;

    public void setSamplingrate(int _samplingrate) {
        samplingRate = _samplingrate;
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "creating Fragment");
        }

        if (container == null) {
            return null;
        }

        View view = inflater.inflate(R.layout.spectrumfragment, container, false);

        // setup the APR Levels plot:
        spectrumPlot = view.findViewById(R.id.spectrum_PlotView);
        spectrumPlot.setTitle("Frequency spectrum");
        ToggleButton toggleButtonDoRecord = view.findViewById(R.id.spectrum_doRecord);
        toggleButtonDoRecord.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    nValues = 0;
                    acceptData = true;
                } else {
                    acceptData = false;
                }
            }
        });
        toggleButtonDoRecord.setChecked(true);
        Button saveButton = view.findViewById(R.id.spectrum_Save);
        saveButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                saveSpectrum();
            }
        });
        Spinner spinnerChannel = view.findViewById(R.id.spectrum_channel);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(Objects.requireNonNull(getContext()),
                android.R.layout.simple_spinner_dropdown_item,
                AttysComm.CHANNEL_DESCRIPTION_SHORT);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerChannel.setAdapter(adapter);
        spinnerChannel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                channel = position;
                spectrumSeries.setTitle(AttysComm.CHANNEL_DESCRIPTION[channel]);
                spectrumPlot.setRangeLabel(units[channel]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        spinnerChannel.setBackgroundResource(android.R.drawable.btn_default);
        spinnerChannel.setSelection(AttysComm.INDEX_Analogue_channel_1);

        spectrumSeries = new SimpleXYSeries(" ");

        for (int i = 0; i <= (BUFFERSIZE / 2); i++) {
            spectrumSeries.addLast(i * samplingRate / BUFFERSIZE, 0);
        }

        spectrumPlot.addSeries(spectrumSeries,
                new LineAndPointFormatter(
                        Color.rgb(100, 255, 255), null, null, null));

        Paint paint = new Paint();
        paint.setColor(Color.argb(128, 0, 255, 0));
        spectrumPlot.getGraph().setDomainGridLinePaint(paint);
        spectrumPlot.getGraph().setRangeGridLinePaint(paint);

        spectrumPlot.setDomainLabel("f/Hz");
        spectrumPlot.setRangeLabel(" ");

        spectrumPlot.setRangeLowerBoundary(0, BoundaryMode.FIXED);
        spectrumPlot.setRangeUpperBoundary(1, BoundaryMode.AUTO);
        spectrumPlot.setRangeLabel(units[channel]);

        spectrumPlot.setDomainLowerBoundary(0, BoundaryMode.FIXED);
        spectrumPlot.setDomainUpperBoundary(samplingRate/2, BoundaryMode.FIXED);

        XYGraphWidget.LineLabelRenderer lineLabelRendererY = new XYGraphWidget.LineLabelRenderer() {
            @Override
            public void drawLabel(Canvas canvas,
                                  XYGraphWidget.LineLabelStyle style,
                                  Number val, float x, float y, boolean isOrigin) {
                Rect bounds = new Rect();
                style.getPaint().getTextBounds("a", 0, 1, bounds);
                drawLabel(canvas, String.format("%04.6f ", val.floatValue()),
                        style.getPaint(), x + bounds.width() / 2, y + bounds.height(), isOrigin);
            }
        };

        spectrumPlot.getGraph().setLineLabelRenderer(XYGraphWidget.Edge.LEFT, lineLabelRendererY);
        XYGraphWidget.LineLabelStyle lineLabelStyle = spectrumPlot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.LEFT);
        Rect bounds = new Rect();
        String dummyTxt = String.format("%04.5f ", 1000.000597558899);
        lineLabelStyle.getPaint().getTextBounds(dummyTxt, 0, dummyTxt.length(), bounds);
        spectrumPlot.getGraph().setMarginLeft(bounds.width());

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

        spectrumPlot.getGraph().setLineLabelRenderer(XYGraphWidget.Edge.BOTTOM, lineLabelRendererX);

        spectrumSeries.setTitle(AttysComm.CHANNEL_DESCRIPTION[channel]);

        final Screensize screensize = new Screensize(Objects.requireNonNull(getActivity()).getWindowManager());
        if (screensize.isTablet()) {
            spectrumPlot.setDomainStep(StepMode.INCREMENT_BY_VAL, 25);
        } else {
            spectrumPlot.setDomainStep(StepMode.INCREMENT_BY_VAL, 50);
        }

        Spinner spinnerMaxY = view.findViewById(R.id.spectrum_maxy);
        ArrayAdapter<String> adapter1 = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, MAXYTXT);
        adapter1.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMaxY.setAdapter(adapter1);
        spinnerMaxY.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    spectrumPlot.setRangeUpperBoundary(1, BoundaryMode.AUTO);
                    spectrumPlot.setRangeStep(StepMode.INCREMENT_BY_PIXELS, 50);
                } else {
                    float maxy = Float.parseFloat(MAXYTXT[position]);
                    if (screensize.isTablet()) {
                        spectrumPlot.setRangeStep(StepMode.INCREMENT_BY_VAL, maxy/10);
                    } else {
                        spectrumPlot.setRangeStep(StepMode.INCREMENT_BY_VAL, maxy/2);
                    }
                    spectrumPlot.setRangeUpperBoundary(maxy, BoundaryMode.FIXED);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        spinnerMaxY.setBackgroundResource(android.R.drawable.btn_default);
        spinnerMaxY.setSelection(0);

        fourierTransformRunnable = new FourierTransformRunnable();

        Thread fourierTransformThread = new Thread(fourierTransformRunnable);
        fourierTransformThread.start();

        ready = true;

        return view;

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ready = false;
        acceptData = false;
        fourierTransformRunnable.cancel();
    }


    private void writeSpectrumfile() throws IOException {

        PrintWriter aepdataFileStream;

        if (dataFilename == null) return;

        File file;

        try {
            file = new File(AttysScope.ATTYSDIR, dataFilename.trim());
            file.createNewFile();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Saving spectrum to " + file.getAbsolutePath());
            }
            aepdataFileStream = new PrintWriter(file);
        } catch (java.io.FileNotFoundException e) {
            throw e;
        }

        char s = ' ';
        switch (dataSeparator) {
            case AttysService.DataRecorder.DATA_SEPARATOR_SPACE:
                s = ' ';
                break;
            case AttysService.DataRecorder.DATA_SEPARATOR_COMMA:
                s = ',';
                break;
            case AttysService.DataRecorder.DATA_SEPARATOR_TAB:
                s = 9;
                break;
        }

        for (int i = 0; i < spectrumSeries.size(); i++) {
            aepdataFileStream.format("%e%c%e%c\n",
                    spectrumSeries.getX(i).floatValue(), s,
                    spectrumSeries.getY(i).floatValue(), s);
            if (aepdataFileStream.checkError()) {
                throw new IOException("file write error");
            }
        }

        aepdataFileStream.close();

        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri contentUri = Uri.fromFile(file);
        mediaScanIntent.setData(contentUri);
        Objects.requireNonNull(getActivity()).sendBroadcast(mediaScanIntent);
    }


    private void saveSpectrum() {

        final EditText filenameEditText = new EditText(getContext());
        filenameEditText.setSingleLine(true);

        final int REQUEST_EXTERNAL_STORAGE = 1;
        String[] PERMISSIONS_STORAGE = {
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        int permission = ActivityCompat.checkSelfPermission(Objects.requireNonNull(getContext()), Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    Objects.requireNonNull(getActivity()),
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }

        filenameEditText.setHint("");
        filenameEditText.setText(dataFilename);

        new AlertDialog.Builder(getContext())
                .setTitle("Saving the Fourier spectrum")
                .setMessage("Enter the filename of the data textfile")
                .setView(filenameEditText)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dataFilename = filenameEditText.getText().toString();
                        dataFilename = dataFilename.replaceAll("[^a-zA-Z0-9.-]", "_");
                        if (!dataFilename.contains(".")) {
                            switch (dataSeparator) {
                                case AttysService.DataRecorder.DATA_SEPARATOR_COMMA:
                                    dataFilename = dataFilename + ".csv";
                                    break;
                                case AttysService.DataRecorder.DATA_SEPARATOR_SPACE:
                                    dataFilename = dataFilename + ".dat";
                                    break;
                                case AttysService.DataRecorder.DATA_SEPARATOR_TAB:
                                    dataFilename = dataFilename + ".tsv";
                            }
                        }
                        try {
                            writeSpectrumfile();
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


    synchronized void addValue(final float[] sample) {
        if (!ready) return;
        if (nValues < BUFFERSIZE) {
            values[nValues] = sample[channel];
            nValues++;
        }
    }


    private class FourierTransformRunnable implements Runnable {

        boolean doRun = true;

        void cancel() {
            doRun = false;
        }

        public void run() {

            FastFourierTransformer fastFourierTransformer =
                    new FastFourierTransformer(DftNormalization.STANDARD);

            while (doRun) {

                try {
                    Thread.sleep(1250);
                } catch (Exception e) {
                    Log.v(TAG,"Sleep:",e);
                }

                if (ready && acceptData && (nValues == BUFFERSIZE)) {

                    Complex[] spectrum = fastFourierTransformer.transform(
                            values,
                            TransformType.FORWARD
                    );

                    if (!doRun) return;

                    if (spectrumSeries != null) {
                        for (int i = 0; (i < spectrumSeries.size()) && doRun; i++) {
                            spectrumSeries.setX(Math.round((float) i * samplingRate / (values.length - 1)), i);
                            spectrumSeries.setY(spectrum[i].divide(values.length).abs(), i);
                        }
                    }
                    if (channel<AttysComm.INDEX_Analogue_channel_1) {
                        if (spectrumSeries != null) {
                            spectrumSeries.setY(0, 0);
                        }
                    }
                    if ((spectrumPlot != null) && doRun) {
                        spectrumPlot.redraw();
                    }
                    nValues = 0;
                }
            }
        }
    }
}
