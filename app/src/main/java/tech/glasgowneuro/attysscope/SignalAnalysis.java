package tech.glasgowneuro.attysscope;

/**
 * Created by bp1 on 12/02/17.
 */

public class SignalAnalysis {

    private int maxdata;
    private float[] data;
    private int nData = 0;

    SignalAnalysis(int _maxata) {
        maxdata = _maxata;
        data = new float[maxdata];
    }

    public void addData(float _data) {
        if (nData < maxdata) {
            data[nData] = _data;
            nData++;
        }
    }

    public void reset() {
        nData = 0;
    }

    public int getNdata() {
        return nData;
    }

    public boolean bufferFull() {
        return nData == maxdata;
    }

    public float getRMS() {
        float r = 0;
        if (nData > 0) {
            for (int i = 0; i < nData; i++) {
                float f = data[i];
                r = r + f * f;
            }
            r = r / nData;
            r = (float) Math.sqrt(r);
        }
        return r;
    }

    public float getPeakToPeak() {
        float min = 1E10F;
        float max = -1E10F;
        if (nData > 0) {
            for (int i = 0; i < nData; i++) {
                float f = data[i];
                if (f>max) max = f;
                if (f<min) min = f;
            }
        } else {
            max = 0;
            min = 0;
        }
        return max - min;
    }
}
