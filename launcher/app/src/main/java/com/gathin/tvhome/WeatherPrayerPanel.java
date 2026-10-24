package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lightweight weather and prayer-time overlays; no extra libraries required. */
final class WeatherPrayerPanel {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String WEATHER_URL =
        "https://api.open-meteo.com/v1/forecast?latitude=2.85&longitude=101.50"
        + "&current=temperature_2m,relative_humidity_2m,weather_code"
        + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
        + "&forecast_days=4&timezone=Asia%2FKuala_Lumpur";
    private static final String PRAYER_URL =
        "https://www.e-solat.gov.my/index.php?r=esolatApi/takwimsolat&period=today&zone=SGR03";

    private WeatherPrayerPanel() {}

    static void attach(Activity activity, FrameLayout root) {
        int margin = dp(activity, 24);
        LinearLayout weather = panel(activity);
        TextView weatherText = text(activity, "CUACA\nMemuatkan cuaca…", 14);
        weather.addView(weatherText);
        LinearLayout stack = new LinearLayout(activity);
        stack.setOrientation(LinearLayout.HORIZONTAL);
        stack.setGravity(Gravity.TOP);
        stack.setFocusable(false);
        stack.setClickable(false);
        stack.addView(weather, new LinearLayout.LayoutParams(dp(activity, 190),
                FrameLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout prayer = panel(activity);
        TextView prayerText = text(activity, "WAKTU SOLAT · KUALA LANGAT\nMemuatkan waktu solat…", 14);
        prayer.addView(prayerText);
        LinearLayout.LayoutParams prayerLp = new LinearLayout.LayoutParams(dp(activity, 390),
                FrameLayout.LayoutParams.WRAP_CONTENT);
        prayerLp.leftMargin = dp(activity, 12);
        stack.addView(prayer, prayerLp);

        FrameLayout.LayoutParams stackLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.RIGHT);
        stackLp.setMargins(0, dp(activity, 22), margin, 0);
        root.addView(stack, stackLp);

        IO.execute(() -> {
            String w = loadWeather();
            MAIN.post(() -> weatherText.setText(w));
        });
        IO.execute(() -> {
            String p = loadPrayer();
            MAIN.post(() -> prayerText.setText(p));
        });
    }

    private static LinearLayout panel(Activity a) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(a, 16), dp(a, 12), dp(a, 16), dp(a, 12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x883078C8);
        bg.setCornerRadius(dp(a, 18));
        bg.setStroke(dp(a, 1), 0x997FD8FF);
        box.setBackground(bg);
        box.setElevation(dp(a, 5));
        box.setFocusable(false);
        box.setClickable(false);
        return box;
    }

    private static TextView text(Activity a, String value, int size) {
        TextView t = new TextView(a);
        t.setText(value);
        t.setTextColor(Color.WHITE);
        t.setTextSize(size);
        t.setLineSpacing(dp(a, 3), 1f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setShadowLayer(2f, 0f, 1f, 0xAA000000);
        return t;
    }

    private static int dp(Activity a, int n) {
        return Math.round(n * a.getResources().getDisplayMetrics().density);
    }

    private static String get(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(7000);
        c.setReadTimeout(7000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "GlassTV/2.4");
        try {
            int status = c.getResponseCode();
            InputStream in = status >= 200 && status < 300 ? c.getInputStream() : c.getErrorStream();
            if (in == null) throw new Exception("HTTP " + status);
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) out.append(line);
            }
            if (status < 200 || status >= 300) throw new Exception("HTTP " + status);
            return out.toString();
        } finally {
            c.disconnect();
        }
    }

    private static String loadWeather() {
        try {
            JSONObject root = new JSONObject(get(WEATHER_URL));
            JSONObject now = root.getJSONObject("current");
            JSONObject daily = root.getJSONObject("daily");
            JSONArray dates = daily.getJSONArray("time");
            JSONArray codes = daily.getJSONArray("weather_code");
            JSONArray highs = daily.getJSONArray("temperature_2m_max");
            JSONArray lows = daily.getJSONArray("temperature_2m_min");
            StringBuilder b = new StringBuilder("CUACA · KUALA LANGAT\n");
            b.append(weatherName(now.optInt("weather_code", -1))).append("  ")
             .append(Math.round(now.optDouble("temperature_2m", 0))).append("°C")
             .append("  ·  Kelembapan ").append(Math.round(now.optDouble("relative_humidity_2m", 0))).append("%\n\n");
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat out = new SimpleDateFormat("EEE", new Locale("ms", "MY"));
            for (int i = 0; i < dates.length() && i < 4; i++) {
                Date date = in.parse(dates.getString(i));
                String day = date == null ? dates.getString(i) : out.format(date);
                b.append(day).append("  ").append(weatherName(codes.optInt(i, -1)))
                 .append("  ").append(Math.round(lows.optDouble(i, 0))).append("°–")
                 .append(Math.round(highs.optDouble(i, 0))).append("°C\n");
            }
            return b.toString().trim();
        } catch (Exception e) {
            return "CUACA · KUALA LANGAT\nCuaca belum dapat dimuatkan.\nSemak sambungan internet.";
        }
    }

    private static String loadPrayer() {
        try {
            JSONObject root = new JSONObject(get(PRAYER_URL));
            JSONArray arr = root.optJSONArray("prayerTime");
            if (arr == null || arr.length() == 0) {
                JSONObject data = root.optJSONObject("data");
                if (data != null) arr = data.optJSONArray("prayerTime");
            }
            if (arr == null || arr.length() == 0) throw new Exception("No prayerTime");
            JSONObject p = arr.getJSONObject(0);
            String date = p.optString("date", "");
            StringBuilder b = new StringBuilder("WAKTU SOLAT · KUALA LANGAT\n");
            if (!date.isEmpty()) b.append(date).append('\n');
            b.append("Subuh   ").append(time(p, "fajr")).append('\n')
             .append("Syuruk  ").append(time(p, "syuruk")).append('\n')
             .append("Zohor   ").append(time(p, "dhuhr")).append('\n')
             .append("Asar    ").append(time(p, "asr")).append('\n')
             .append("Maghrib ").append(time(p, "maghrib")).append('\n')
             .append("Isyak   ").append(time(p, "isha"));
            return b.toString();
        } catch (Exception e) {
            return "WAKTU SOLAT · KUALA LANGAT\nWaktu solat belum dapat dimuatkan.\nSemak sambungan internet.";
        }
    }

    private static String time(JSONObject o, String key) {
        String v = o.optString(key, "--:--");
        return v.length() >= 5 ? v.substring(0, 5) : v;
    }

    private static String weatherName(int code) {
        if (code == 0) return "Cerah";
        if (code == 1 || code == 2) return "Cerah berawan";
        if (code == 3) return "Mendung";
        if (code == 45 || code == 48) return "Berkabus";
        if (code >= 51 && code <= 57) return "Renyai";
        if (code >= 61 && code <= 67) return "Hujan";
        if (code >= 71 && code <= 77) return "Salji";
        if (code >= 80 && code <= 82) return "Hujan lebat";
        if (code >= 85 && code <= 86) return "Salji lebat";
        if (code >= 95) return "Ribut petir";
        return "Keadaan cuaca";
    }
}
