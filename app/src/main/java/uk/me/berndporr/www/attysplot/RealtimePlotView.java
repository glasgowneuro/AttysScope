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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

/**
 * Created by bp1 on 10/08/16.
 */
public class RealtimePlotView extends SurfaceView implements SurfaceHolder.Callback {

    private int xpos = 0;
    private SurfaceHolder holder;
    private float[] minData = null;
    private float[] maxData = null;
    private int nMaxChannels = 0;
    private float[][] ypos = null;
    Paint paint = new Paint();
    Paint paintBlack = new Paint();
    Paint paintCoord = new Paint();
    Canvas canvas;
    private int gap = 10;
    private int xtic = 250;

    private void init() {
        xpos = 0;
        holder = getHolder();
        paint.setColor(Color.YELLOW);
        paintBlack.setColor(Color.BLACK);
        paintCoord.setColor(Color.argb(128,64,64,64));
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
        for(int i=0;i<n;i++) {
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
        Rect rect = null;
        if ((xpos >= (width - n))) {
            rect = new Rect(xpos, 0, width-1, getHeight());
            canvas = holder.lockCanvas(rect);
            canvas.drawRect(rect, paintBlack);
            holder.unlockCanvasAndPost(canvas);
            xpos = 0;
        }
        int xr = xpos + n + gap;
        rect = new Rect(xpos, 0, xpos + n + gap, getHeight());
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

    public void addSamples(float[] newData, float[] minV, float[] maxV) {
        int width = getWidth();
        int height = getHeight();

        int nCh = newData.length;
        if (nCh == 0) return;

        float base = height / nCh;

        if (ypos == null) initYpos(width);

        if (nMaxChannels == 0) return;
        Surface surface = holder.getSurface();
        if (surface.isValid()) {
            Rect rect = new Rect(xpos, 0, xpos+gap, height);
            if (canvas != null) {
                canvas.drawRect(rect, paintBlack);
                for (int i = 0; i < nCh; i++) {
                    float dy = (float) base / (float) (maxV[i] - minV[i]);
                    float yZero = base * (i + 1) - ((0 - minV[i]) * dy);
                    float yTmp = base * (i + 1) - ((newData[i] - minV[i]) * dy);
                    ypos[i][xpos + 1] = yTmp;
                    canvas.drawPoint(xpos, yZero, paintCoord);
                    if ((xpos%xtic) == 0) {
                        canvas.drawLine(xpos,0,xpos,height,paintCoord);
                    }
                    canvas.drawLine(xpos, ypos[i][xpos], xpos + 1, ypos[i][xpos + 1], paint);
                }
                xpos = xpos + 1;
            }
        }
    }


    @Override
    protected void onDraw(Canvas canvas) {
    }

}
