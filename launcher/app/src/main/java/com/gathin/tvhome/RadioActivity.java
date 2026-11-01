package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Malaysian internet radio list (Radio Browser directory) with simple playback. */
public class RadioActivity extends Activity {
    private static final String API = "https://de1.api.radio-browser.info/json/stations/search"
            + "?countrycode=MY&hidebroken=true&order=clickcount&reverse=true&limit=60";
    private static final int ACCENT = 0xFFFF8A00;

    // Kept static so playback continues after the screen is closed.
    private static MediaPlayer player;
    private static String playingUrl;
    private static String playingName;
    private static WeakReference<RadioActivity> current;
    private static List<Station> cache;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout list;
    private TextView status;

    private static final class Station {
        String name;
        String url;
        String codec;
        int bitrate;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        current = new WeakReference<>(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xF2101014);
        root.setPadding(dp(40), dp(28), dp(40), dp(20));

        TextView title = new TextView(this);
        title.setText("Radio");
        title.setTextColor(ACCENT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        status = new TextView(this);
        status.setTextColor(0xFFB0B0B8);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        status.setPadding(0, dp(4), 0, dp(10));
        status.setText(playingName != null
                ? "Sedang dimainkan: " + playingName : "Memuatkan senarai stesen...");
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        if (cache != null) {
            show(cache);
            return;
        }
        new Thread(() -> {
            try {
                final List<Station> got = parse(get(API));
                handler.post(() -> {
                    if (isDestroyed()) return;
                    cache = got;
                    show(got);
                });
            } catch (final Exception e) {
                handler.post(() -> {
                    if (!isDestroyed()) status.setText("Tak dapat ambil senarai: " + e.getMessage());
                });
            }
        }, "radio-list").start();
    }

    @Override
    protected void onDestroy() {
        if (current != null && current.get() == this) current = null;
        super.onDestroy();
    }

    private void show(List<Station> stations) {
        list.removeAllViews();
        if (playingName == null) {
            status.setText(stations.isEmpty() ? "Tiada stesen" : "Pilih stesen dan tekan OK");
        }
        View first = row("Henti radio", "Hentikan siaran yang sedang dimainkan", v -> {
            stopPlayer();
            say("Radio dihentikan");
        });
        for (final Station s : stations) {
            String sub = s.codec + (s.bitrate > 0 ? "  " + s.bitrate + " kbps" : "");
            row(s.name, sub, v -> toggle(s));
        }
        first.requestFocus();
    }

    private void toggle(Station s) {
        if (player != null && s.url.equals(playingUrl)) {
            stopPlayer();
            say("Radio dihentikan");
            return;
        }
        stopPlayer();
        playingUrl = s.url;
        playingName = s.name;
        say("Menyambung: " + s.name + "...");
        try {
            MediaPlayer p = new MediaPlayer();
            p.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            p.setDataSource(s.url);
            p.setOnPreparedListener(mp -> {
                mp.start();
                say("Sedang dimainkan: " + playingName);
            });
            p.setOnErrorListener((mp, what, extra) -> {
                stopPlayer();
                say("Tak dapat main strim ini (" + what + "). Cuba stesen lain.");
                return true;
            });
            player = p;
            p.prepareAsync();
        } catch (Exception e) {
            stopPlayer();
            say("Ralat: " + e.getMessage());
        }
    }

    private static void stopPlayer() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
        playingUrl = null;
        playingName = null;
    }

    private static void say(String msg) {
        RadioActivity a = current == null ? null : current.get();
        if (a != null && !a.isDestroyed()) a.status.setText(msg);
    }

    private View row(String main, String sub, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setOnClickListener(click);
        r.setPadding(dp(16), dp(10), dp(16), dp(10));
        StateListDrawable bg = new StateListDrawable();
        GradientDrawable f = new GradientDrawable();
        f.setColor(0x33FFFFFF);
        f.setCornerRadius(dp(12));
        f.setStroke(dp(2), ACCENT);
        bg.addState(new int[]{android.R.attr.state_focused}, f);
        bg.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        r.setBackground(bg);

        TextView a = new TextView(this);
        a.setText(main);
        a.setTextColor(Color.WHITE);
        a.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        a.setTypeface(Typeface.DEFAULT_BOLD);
        TextView c = new TextView(this);
        c.setText(sub);
        c.setTextColor(0xFFB0B0B8);
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        r.addView(a);
        r.addView(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        list.addView(r, lp);
        return r;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private static String get(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "GlassTV");
        if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
        InputStream in = c.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        c.disconnect();
        return out.toString("UTF-8");
    }

    private static List<Station> parse(String json) throws Exception {
        JSONArray arr = new JSONArray(json);
        List<Station> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String name = o.optString("name", "").trim();
            String url = o.optString("url_resolved", "");
            if (url.isEmpty()) url = o.optString("url", "");
            if (name.isEmpty() || url.isEmpty()) continue;
            if (!seen.add(name.toLowerCase(Locale.ROOT))) continue;
            Station s = new Station();
            s.name = name;
            s.url = url;
            s.codec = o.optString("codec", "");
            s.bitrate = o.optInt("bitrate", 0);
            out.add(s);
        }
        return out;
    }
}
