package tech.glasgowneuro.attysscope2;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.Objects;

import tech.glasgowneuro.attyscomm.AttysComm;

/**
 * Fourier Transform Fragment
 */

public class FourierFragment extends Fragment {

    private final String TAG = "FourierFragment";

    private int channel = AttysComm.INDEX_Analogue_channel_1;

    private SimpleXYSeries spectrumSeries = null;

    private XYPlot spectrumPlot = null;

    private static final String[] MAXYTXT = {"auto range", "1", "0.5", "0.1", "0.05", "0.01", "0.005", "0.001", "0.0005", "0.0001", "0.00005", "0.00001"};

    private final String[] units = new String[AttysComm.NCHANNELS];

    void setUnits(String[] _units) {
        System.arraycopy(_units, 0, units, 0, AttysComm.NCHANNELS);
    }

    private FourierTransformRunnable fourierTransformRunnable = null;

    private int samplingRate = 250;

    private boolean ready = false;

    private boolean acceptData = false;

    private static final int BUFFERSIZE = 256;

    private final double[] values = new double[BUFFERSIZE];

    private int nValues = 0;

    private String dataFilename = null;

    // private byte dataSeparator = AttysService.DataRecorder.DATA_SEPARATOR_TAB;
    private final byte dataSeparator = AttysScope.DATA_SEPARATOR_TAB;

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
                drawLabel(canvas, String.format(Locale.US,"%04.6f ", val.floatValue()),
                        style.getPaint(), x + (float)bounds.width() / 2, y + bounds.height(), isOrigin);
            }
        };

        spectrumPlot.getGraph().setLineLabelRenderer(XYGraphWidget.Edge.LEFT, lineLabelRendererY);
        XYGraphWidget.LineLabelStyle lineLabelStyle = spectrumPlot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.LEFT);
        Rect bounds = new Rect();
        String dummyTxt = String.format(Locale.US,"%04.5f ", 1000.000597558899);
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
                    drawLabel(canvas, String.format(Locale.US,"%d", val.intValue()),
                            style.getPaint(), x + (float)bounds.width() / 2, y + bounds.height(), isOrigin);
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


    private void writeSpectrumfile(String dataFilename, SimpleXYSeries simpleXYSeries) throws IOException {

        PrintWriter fftFileStream;

        Uri uri = Uri.EMPTY;

        uri = AttysScope.getUri2Filename(getActivity(),dataFilename,dataSeparator);
        fftFileStream = new PrintWriter(Objects.requireNonNull(getActivity().getContentResolver().openOutputStream(uri)));

        char s = AttysScope.getDataSeparatorChar();

        for (int i = 0; i < simpleXYSeries.size(); i++) {
            fftFileStream.format("%e%c%e%c\n",
                    simpleXYSeries.getX(i).floatValue(), s,
                    simpleXYSeries.getY(i).floatValue(), s);
            if (fftFileStream.checkError()) {
                throw new IOException("file write error");
            }
        }

        fftFileStream.close();

    }


    private void saveSpectrum() {

        final EditText filenameEditText = new EditText(getContext());
        final SimpleXYSeries savedSpectrumSeries = new SimpleXYSeries(" ");

        for (int i = 0; i <= (BUFFERSIZE / 2); i++) {
            savedSpectrumSeries.addLast(spectrumSeries.getX(i), spectrumSeries.getY(i));
        }

        AttysScope.checkDirPermissions(getActivity());

        filenameEditText.setSingleLine(true);

        filenameEditText.setHint("");
        filenameEditText.setText(dataFilename);

        new AlertDialog.Builder(Objects.requireNonNull(getContext()))
                .setTitle("Saving the Fourier spectrum")
                .setMessage("Enter the filename of the data textfile")
                .setView(filenameEditText)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dataFilename = filenameEditText.getText().toString();
                        dataFilename = AttysScope.fixFilename(dataFilename);
                        try {
                            writeSpectrumfile(dataFilename,savedSpectrumSeries);
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

        private final Object sleeper = new Object();

        void cancel() {
            doRun = false;
            synchronized (sleeper) {
                sleeper.notify();
            }
        }

        public void run() {

            FastFourierTransformer fastFourierTransformer =
                    new FastFourierTransformer(DftNormalization.STANDARD);

            while (doRun) {

                try {
                    synchronized (sleeper) {
                        sleeper.wait(1250);
                    }
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
