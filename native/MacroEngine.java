package com.jean.mymacro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.telephony.SmsManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MacroEngine {
    static final String PREFS = "mymacro";
    static final String CH_ALERT = "mymacro_alerts";
    private static boolean torchOn = false;

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static JSONArray load(Context c) {
        try {
            return new JSONArray(sp(c).getString("macros", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public static void save(Context c, String json) {
        sp(c).edit().putString("macros", json).apply();
    }

    public static String loadLog(Context c) {
        return sp(c).getString("log", "[]");
    }

    public static synchronized void log(Context c, String msg) {
        try {
            JSONArray old = new JSONArray(sp(c).getString("log", "[]"));
            JSONArray n = new JSONArray();
            JSONObject o = new JSONObject();
            o.put("t", System.currentTimeMillis());
            o.put("m", msg);
            n.put(o);
            for (int i = 0; i < old.length() && i < 59; i++) n.put(old.get(i));
            sp(c).edit().putString("log", n.toString()).apply();
        } catch (Exception e) {
            // ignora
        }
    }

    public static void clearLog(Context c) {
        sp(c).edit().putString("log", "[]").apply();
    }

    public static JSONObject find(Context c, String id) {
        JSONArray ms = load(c);
        for (int i = 0; i < ms.length(); i++) {
            JSONObject m = ms.optJSONObject(i);
            if (m != null && id.equals(m.optString("id"))) return m;
        }
        return null;
    }

    /** Dispara macros cujo gatilho e do tipo informado. */
    public static void fire(Context c, String type, String param) {
        JSONArray ms = load(c);
        for (int i = 0; i < ms.length(); i++) {
            JSONObject m = ms.optJSONObject(i);
            if (m == null || !m.optBoolean("enabled", true)) continue;
            JSONObject t = m.optJSONObject("trigger");
            if (t == null || !type.equals(t.optString("type"))) continue;
            String want = t.optString("device", "").trim();
            if (type.startsWith("bt_") && want.length() > 0) {
                if (param == null || !param.toLowerCase().contains(want.toLowerCase())) continue;
            }
            runMacro(c, m);
        }
    }

    public static void fireBattery(Context c, int prev, int cur) {
        JSONArray ms = load(c);
        for (int i = 0; i < ms.length(); i++) {
            JSONObject m = ms.optJSONObject(i);
            if (m == null || !m.optBoolean("enabled", true)) continue;
            JSONObject t = m.optJSONObject("trigger");
            if (t == null || !"battery_below".equals(t.optString("type"))) continue;
            int lv = t.optInt("level", 20);
            if (prev > lv && cur <= lv) runMacro(c, m);
        }
    }

    public static void runMacro(final Context ctx, final JSONObject m) {
        final Context c = ctx.getApplicationContext();
        final String name = m.optString("name", "macro");
        final JSONArray actions = m.optJSONArray("actions");
        new Thread(new Runnable() {
            @Override
            public void run() {
                log(c, "Executando: " + name);
                if (actions == null) return;
                for (int i = 0; i < actions.length(); i++) {
                    JSONObject a = actions.optJSONObject(i);
                    if (a == null) continue;
                    try {
                        runAction(c, a);
                    } catch (Throwable e) {
                        log(c, "Erro em '" + a.optString("type") + "': " + e.getClass().getSimpleName() + " " + e.getMessage());
                    }
                }
                log(c, "Concluida: " + name);
            }
        }).start();
    }

    private static void startActivity(Context c, Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    static void runAction(Context c, JSONObject a) throws Exception {
        String type = a.optString("type");
        if ("notify".equals(type)) {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new NotificationChannel(CH_ALERT, "Alertas das macros", NotificationManager.IMPORTANCE_HIGH));
                b = new Notification.Builder(c, CH_ALERT);
            } else {
                b = new Notification.Builder(c);
            }
            b.setSmallIcon(android.R.drawable.ic_dialog_info);
            b.setContentTitle(a.optString("title", "MyMacro"));
            b.setContentText(a.optString("text", ""));
            b.setAutoCancel(true);
            nm.notify((int) (System.currentTimeMillis() % 100000) + 10, b.build());
        } else if ("open_app".equals(type)) {
            Intent i = c.getPackageManager().getLaunchIntentForPackage(a.optString("pkg"));
            if (i == null) throw new Exception("app nao encontrado");
            startActivity(c, i);
        } else if ("open_url".equals(type)) {
            String u = a.optString("url");
            if (!u.contains("://")) u = "https://" + u;
            startActivity(c, new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
        } else if ("sms".equals(type)) {
            if (c.checkSelfPermission("android.permission.SEND_SMS") != PackageManager.PERMISSION_GRANTED)
                throw new Exception("sem permissao de SMS");
            SmsManager sm = Build.VERSION.SDK_INT >= 31 ? c.getSystemService(SmsManager.class) : SmsManager.getDefault();
            String txt = a.optString("text", "");
            java.util.ArrayList<String> parts = sm.divideMessage(txt);
            sm.sendMultipartTextMessage(a.optString("number"), null, parts, null, null);
        } else if ("vibrate".equals(type)) {
            Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            long ms = a.optLong("ms", 500);
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(ms);
        } else if ("flashlight".equals(type)) {
            CameraManager cm = (CameraManager) c.getSystemService(Context.CAMERA_SERVICE);
            String mode = a.optString("mode", "toggle");
            boolean on = "on".equals(mode) || ("toggle".equals(mode) && !torchOn);
            cm.setTorchMode(cm.getCameraIdList()[0], on);
            torchOn = on;
        } else if ("ringer".equals(type)) {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            String mode = a.optString("mode", "normal");
            int r = "silent".equals(mode) ? AudioManager.RINGER_MODE_SILENT
                    : "vibrate".equals(mode) ? AudioManager.RINGER_MODE_VIBRATE : AudioManager.RINGER_MODE_NORMAL;
            am.setRingerMode(r);
        } else if ("settings".equals(type)) {
            String target = a.optString("target", "wifi");
            Intent i;
            if ("bluetooth".equals(target)) i = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            else if (Build.VERSION.SDK_INT >= 29) i = new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY);
            else i = new Intent(Settings.ACTION_WIFI_SETTINGS);
            startActivity(c, i);
        } else if ("wait".equals(type)) {
            Thread.sleep(Math.min(a.optLong("ms", 1000), 600000L));
        } else if ("taps".equals(type)) {
            TapAccessibilityService svc = TapAccessibilityService.instance;
            if (svc == null) throw new Exception("acessibilidade desligada");
            JSONArray st = a.optJSONArray("strokes");
            if (st == null) return;
            for (int i = 0; i < st.length(); i++) {
                JSONObject s = st.optJSONObject(i);
                if (s == null) continue;
                long delay = Math.min(s.optLong("delay", 300), 30000L);
                if (i == 0) delay = Math.max(delay, 400);
                Thread.sleep(delay);
                final CountDownLatch latch = new CountDownLatch(1);
                long dur = Math.max(1, s.optLong("dur", 60));
                boolean ok = svc.stroke((float) s.optDouble("x1"), (float) s.optDouble("y1"),
                        (float) s.optDouble("x2"), (float) s.optDouble("y2"), dur, new Runnable() {
                            @Override
                            public void run() { latch.countDown(); }
                        });
                if (ok) latch.await(dur + 3000, TimeUnit.MILLISECONDS);
            }
        }
    }
}
