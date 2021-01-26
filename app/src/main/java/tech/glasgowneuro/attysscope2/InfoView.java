package tech.glasgowneuro.attysscope2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

/**
 * Overlay which prints all the infos on the screen in a semi transparent
 * scope look.
 */
public class InfoView extends View {

    static private String TAG = "InfoView";

    static private Paint paintLarge = new Paint();
    static private Paint paintSmall = new Paint();
    static private int textHeight = 0;
    static private String largeText;
    static private String smallText;
    static Rect bounds = new Rect();

    public InfoView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public InfoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public InfoView(Context context) {
        super(context);
        init();
    }

    private void init() {
        paintLarge.setColor(Color.argb(128, 0, 255, 0));
        paintSmall.setColor(Color.argb(128, 0, 255, 0));
    }

    public int getInfoHeight() {
        return textHeight;
    }

    public void resetInfoHeight() { textHeight = 0;}

    public void drawText(String _largeText, String _smallText, boolean recording) {
        largeText = _largeText;
        smallText = _smallText;
        if (recording) {
            paintSmall.setColor(Color.argb(128, 255, 255, 0));
        } else {
            paintSmall.setColor(Color.argb(128, 0, 255, 0));
        }
        invalidate();
        //Log.d(TAG,String.format("textHeight=%d",textHeight));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int yLarge = 0;
        int xLarge = 0;
        int width = getWidth();
        int txtDiv = 25;
        do {
            paintSmall.setTextSize(getHeight() / txtDiv);
            if (null != smallText) {
                paintSmall.getTextBounds(smallText + "|y`", 0, smallText.length(), bounds);
            }
            txtDiv++;
        } while ((width - (bounds.width() * 10 / 9)) < 0);
        int y2 = bounds.height();
        if (largeText != null) {
            if (largeText.length()>0) {
                int txtDivTmp = 7;
                do {
                    paintLarge.setTextSize(getHeight() / txtDivTmp);
                    paintLarge.getTextBounds(largeText+"|y`", 0, largeText.length(), bounds);
                    xLarge = width - (bounds.width() * 10 / 9);
                    txtDivTmp++;
                } while (xLarge < 0);
                String dummyText = "1.2424Vpp";
                paintLarge.getTextBounds(dummyText, 0, dummyText.length(), bounds);
                yLarge = bounds.height();
                canvas.drawText(largeText, xLarge, yLarge + y2 * 10 / 9, paintLarge);
            }
        }
        if (null != smallText) {
            canvas.drawText(smallText, width / 100, y2, paintSmall);
        }
        if ((y2+yLarge)>textHeight) {
            textHeight = y2 + yLarge;
        }
    }
}
