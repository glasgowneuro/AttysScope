/**
 * Copyright 2016 Bernd Porr, mail@berndporr.me.uk
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

package tech.glasgowneuro.www.attysplot;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Created by bp1 on 10/08/16.
 */
public class RealtimePlotView extends SurfaceView implements SurfaceHolder.Callback {

    private int xpos = 0;
    private int nLeft = 0;
    private SurfaceHolder holder;
    private float[] minData = null;
    private float[] maxData = null;
    private int nMaxChannels = 0;
    private float[][] ypos = null;
    Paint paint = new Paint();
    Paint paintBlack = new Paint();
    Paint paintXCoord = new Paint();
    Paint paintYCoord = new Paint();
    Paint paintLabel = new Paint();
    Canvas canvas;
    private int gap = 10;
    private int xtic = 250;

    private void init() {
        xpos = 0;
        holder = getHolder();
        paint.setColor(Color.WHITE);
        paintBlack.setColor(Color.BLACK);
        paintXCoord.setColor(Color.argb(128, 0, 255, 0));
        paintYCoord.setColor(Color.argb(64, 0, 128, 0));
        paintLabel.setColor(Color.argb(128, 0, 255, 0));
    }

    public void resetX() {
        xpos = 0;
    }

    public RealtimePlotView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public RealtimePlotView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RealtimePlotView(Context context) {
        super(context);
        init();
    }

    public void setMaxChannels(int n) {
        nMaxChannels = n;
        minData = new float[n];
        maxData = new float[n];
        for (int i = 0; i < n; i++) {
            minData[i] = -1;
            maxData[i] = 1;
        }
    }

    private void initYpos(int width) {
        ypos = new float[nMaxChannels][width + gap + 1];
        xpos = 0;
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    public void surfaceCreated(SurfaceHolder holder) {
        setWillNotDraw(false);
        initYpos(getWidth());
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        initYpos(width);
    }

    public void startAddSamples(int n) {
        int width = getWidth();
        nLeft = n;
        Rect rect = null;
        int xr = xpos + n + gap;
        if (xr > (width - 1)) {
            xr = width - 1;
        }
        rect = new Rect(xpos, 0, xr, getHeight());
        if (holder != null) {
            canvas = holder.lockCanvas(rect);
        } else {
            canvas = null;
        }
    }

    public void stopAddSamples() {
        if (holder != null) {
            if (canvas != null) {
                holder.unlockCanvasAndPost(canvas);
            }
        }
    }


    public void addSamples(float[] newData, float[] minV, float[] maxV, float[] ytick,
                           String[] label) {
        int width = getWidth();
        int height = getHeight();

        int nCh = newData.length;
        if (nCh == 0) return;

        float base = height / nCh;

        if (ypos == null) initYpos(width);

        if (nMaxChannels == 0) return;
        Surface surface = holder.getSurface();
        if (surface.isValid()) {
            Rect rect = new Rect(xpos, 0, xpos + gap, height);
            if (canvas != null) {
                paintLabel.setTextSize(canvas.getHeight()/30);
                canvas.drawRect(rect, paintBlack);
                for (int i = 0; i < nCh; i++) {
                    float dy = (float) base / (float) (maxV[i] - minV[i]);
                    float yZero = base * (i + 1) - ((0 - minV[i]) * dy);
                    float yTmp = base * (i + 1) - ((newData[i] - minV[i]) * dy);
                    float yTmpTicPos = base * (i + 1) - ((ytick[i] - minV[i]) * dy);
                    float yTmpTicNeg = base * (i + 1) - ((-ytick[i] - minV[i]) * dy);
                    ypos[i][xpos + 1] = yTmp;
                    canvas.drawPoint(xpos, yZero, paintXCoord);
                    if ((xpos % 2) == 0) {
                        canvas.drawPoint(xpos, yTmpTicPos, paintXCoord);
                        canvas.drawPoint(xpos, yTmpTicNeg, paintXCoord);
                    }
                    if ((xpos % xtic) == 0) {
                        canvas.drawLine(xpos, 0, xpos, height, paintYCoord);
                    }
                    canvas.drawLine(xpos, ypos[i][xpos], xpos + 1, ypos[i][xpos + 1], paint);
                    canvas.drawText(label[i], 0F, yZero - 1, paintLabel);
                }
            }
            xpos++;
            nLeft--;
            if (xpos >= (width - 1)) {
                xpos = 0;
                if (holder != null) {
                    if (canvas != null) {
                        holder.unlockCanvasAndPost(canvas);
                        canvas = null;
                    }
                }
                rect = new Rect(xpos, 0, nLeft + gap, getHeight());
                if (holder != null) {
                    canvas = holder.lockCanvas(rect);
                } else {
                    canvas = null;
                }
            }
        }
    }


    @Override
    protected void onDraw(Canvas canvas) {
    }

}
