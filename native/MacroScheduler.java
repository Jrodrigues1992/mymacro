package com.jean.mymacro;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

public class MacroScheduler {

    /** Garante que o servico em segundo plano esta ligado. */
    public static void ensureRunning(Context c) {
        try {
            ContextCompat.startForegroundService(c.getApplicationContext(), new Intent(c.getApplicationContext(), MacroService.class));
        } catch (Throwable e) {
            MacroEngine.log(c, "Nao foi possivel iniciar o servico: " + e.getMessage());
        }
    }

    private static PendingIntent pi(Context c, String id) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setData(Uri.parse("mymacro://alarm/" + Uri.encode(id)));
        i.putExtra("id", id);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c, 0, i, flags);
    }

    static long nextTime(JSONObject t) {
        int hour = t.optInt("hour", 8), minute = t.optInt("minute", 0);
        JSONArray days = t.optJSONArray("days");
        long now = System.currentTimeMillis();
        for (int d = 0; d <= 7; d++) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, d);
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE, minute);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            if (cal.getTimeInMillis() <= now) continue;
            if (days == null || days.length() == 0) return cal.getTimeInMillis();
            int dow = cal.get(Calendar.DAY_OF_WEEK);
            for (int k = 0; k < days.length(); k++) if (days.optInt(k) == dow) return cal.getTimeInMillis();
        }
        return -1;
    }

    public static void scheduleAll(Context ctx) {
        Context c = ctx.getApplicationContext();
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        // cancela os anteriores
        try {
            JSONArray prev = new JSONArray(MacroEngine.sp(c).getString("alarm_ids", "[]"));
            for (int i = 0; i < prev.length(); i++) am.cancel(pi(c, prev.getString(i)));
        } catch (Exception e) { /* ignora */ }
        JSONArray ids = new JSONArray();
        JSONArray ms = MacroEngine.load(c);
        for (int i = 0; i < ms.length(); i++) {
            JSONObject m = ms.optJSONObject(i);
            if (m == null || !m.optBoolean("enabled", true)) continue;
            JSONObject t = m.optJSONObject("trigger");
            if (t == null || !"time".equals(t.optString("type"))) continue;
            long at = nextTime(t);
            if (at < 0) continue;
            String id = m.optString("id");
            PendingIntent p = pi(c, id);
            try {
                if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
                }
            } catch (SecurityException e) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
            }
            ids.put(id);
        }
        MacroEngine.sp(c).edit().putString("alarm_ids", ids.toString()).apply();
    }
}
