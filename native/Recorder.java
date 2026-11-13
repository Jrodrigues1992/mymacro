package com.jean.mymacro;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

public class Recorder {
    public interface Done { void onDone(JSONArray strokes); }

    private static Recorder cur;
    private final Context ctx;
    private final Done done;
    private final WindowManager wm;
    private View capture;
    private TextView stopBtn;
    private final JSONArray strokes = new JSONArray();
    private long lastUp = 0;
    private float dx, dy, lx, ly;
    private long downT;
    private final Handler h = new Handler(Looper.getMainLooper());

    private Recorder(Context c, Done d) {
        ctx = c.getApplicationContext();
        done = d;
        wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
    }

    public static boolean active() { return cur != null; }

    public static void start(final Context c, final Done d) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (cur != null) return;
                cur = new Recorder(c, d);
                cur.show();
            }
        });
    }

    private int type() {
        return Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private void show() {
        capture = new View(ctx) {
            @Override
            public boolean onTouchEvent(MotionEvent e) {
                handle(e);
                return true;
            }
        };
        capture.setBackgroundColor(Color.argb(30, 255, 0, 0));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        wm.addView(capture, lp);

        stopBtn = new TextView(ctx);
        stopBtn.setText("PARAR GRAVACAO");
        stopBtn.setTextColor(Color.WHITE);
        stopBtn.setBackgroundColor(Color.rgb(200, 30, 30));
        stopBtn.setPadding(48, 28, 48, 28);
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { stop(); }
        });
        WindowManager.LayoutParams lp2 = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        lp2.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp2.y = 120;
        wm.addView(stopBtn, lp2);
    }

    private void setTouchable(boolean on) {
        try {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) capture.getLayoutParams();
            if (on) lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            else lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            wm.updateViewLayout(capture, lp);
        } catch (Throwable e) { /* ignora */ }
    }

    private void handle(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            downT = System.currentTimeMillis();
            dx = e.getRawX();
            dy = e.getRawY();
            lx = dx;
            ly = dy;
        } else if (a == MotionEvent.ACTION_MOVE) {
            lx = e.getRawX();
            ly = e.getRawY();
        } else if (a == MotionEvent.ACTION_UP) {
            lx = e.getRawX();
            ly = e.getRawY();
            long now = System.currentTimeMillis();
            long dur = Math.max(50, now - downT);
            long delay = lastUp == 0 ? 0 : Math.max(0, downT - lastUp);
            lastUp = now;
            try {
                JSONObject s = new JSONObject();
                s.put("x1", Math.round(dx));
                s.put("y1", Math.round(dy));
                s.put("x2", Math.round(lx));
                s.put("y2", Math.round(ly));
                s.put("dur", dur);
                s.put("delay", delay);
                strokes.put(s);
            } catch (Exception ex) { /* ignora */ }
            // repassa o toque para o app que esta por baixo
            final TapAccessibilityService svc = TapAccessibilityService.instance;
            if (svc != null) {
                setTouchable(false);
                boolean ok = svc.stroke(dx, dy, lx, ly, dur, new Runnable() {
                    @Override
                    public void run() {
                        h.postDelayed(new Runnable() {
                            @Override
                            public void run() { if (cur == Recorder.this) setTouchable(true); }
                        }, 80);
                    }
                });
                if (!ok) setTouchable(true);
            }
        }
    }

    private void stop() {
        try { wm.removeView(capture); } catch (Throwable e) { /* ignora */ }
        try { wm.removeView(stopBtn); } catch (Throwable e) { /* ignora */ }
        cur = null;
        if (done != null) done.onDone(strokes);
    }
}
