package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Manchester United fixtures and latest results (ESPN public API), in Malaysia time. */
public class ManUtdActivity extends Activity {
    private static final String BASE = "https://site.api.espn.com/apis/site/v2/sports/soccer/eng.1/teams/360/schedule";
    private static final String TEAM_ID = "360";
    private static final int RED = 0xFFDA291C;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout list;
    private TextView status;

    private static final class Match {
        long time;
        String opponent;
        boolean home;
        boolean done;
        String own;
        String opp;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xF2101014);
        root.setPadding(dp(40), dp(28), dp(40), dp(20));

        TextView title = new TextView(this);
        title.setText("Manchester United");
        title.setTextColor(RED);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        status = new TextView(this);
        status.setText("Memuatkan jadual...");
        status.setTextColor(0xFFB0B0B8);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        status.setPadding(0, dp(4), 0, dp(10));
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        new Thread(() -> {
            try {
                final List<Match> up = parse(get(BASE + "?fixture=true"));
                final List<Match> past = parse(get(BASE));
                handler.post(() -> { if (!isDestroyed()) show(up, past); });
            } catch (final Exception e) {
                handler.post(() -> {
                    if (!isDestroyed()) status.setText("Tak dapat ambil jadual: " + e.getMessage());
                });
            }
        }, "manutd").start();
    }

    private void show(List<Match> up, List<Match> past) {
        list.removeAllViews();
        Collections.sort(up, new Comparator<Match>() {
            public int compare(Match a, Match b) { return Long.compare(a.time, b.time); }
        });
        Collections.sort(past, new Comparator<Match>() {
            public int compare(Match a, Match b) { return Long.compare(b.time, a.time); }
        });
        long now = System.currentTimeMillis();
        SimpleDateFormat fmt = new SimpleDateFormat("EEE d MMM  HH:mm", new Locale("ms", "MY"));
        fmt.setTimeZone(TimeZone.getTimeZone("Asia/Kuala_Lumpur"));

        View first = null;
        int shown = 0;
        header("Perlawanan akan datang");
        for (Match m : up) {
            if (m.done || m.time < now - 3 * 3600 * 1000L) continue;
            String line = m.home ? "Man United  v  " + m.opponent : m.opponent + "  v  Man United";
            View r = row(line, fmt.format(new Date(m.time)) + "  (waktu Malaysia)");
            if (first == null) first = r;
            if (++shown >= 10) break;
        }
        int total = shown;

        shown = 0;
        header("Keputusan terkini");
        for (Match m : past) {
            if (!m.done) continue;
            String line;
            if (m.own != null && m.opp != null) {
                line = m.home
                        ? "Man United  " + m.own + " - " + m.opp + "  " + m.opponent
                        : m.opponent + "  " + m.opp + " - " + m.own + "  Man United";
            } else {
                line = m.home ? "Man United  v  " + m.opponent : m.opponent + "  v  Man United";
            }
            View r = row(line, fmt.format(new Date(m.time)));
            if (first == null) first = r;
            if (++shown >= 3) break;
        }
        total += shown;
        status.setText(total == 0 ? "Tiada data perlawanan" : "Data: ESPN");
        if (first != null) first.requestFocus();
    }

    private void header(String text) {
        TextView h = new TextView(this);
        h.setText(text);
        h.setTextColor(RED);
        h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setPadding(dp(16), dp(14), 0, dp(6));
        list.addView(h);
    }

    private View row(String main, String sub) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setFocusable(true);
        r.setPadding(dp(16), dp(10), dp(16), dp(10));
        StateListDrawable bg = new StateListDrawable();
        GradientDrawable f = new GradientDrawable();
        f.setColor(0x33FFFFFF);
        f.setCornerRadius(dp(12));
        f.setStroke(dp(2), RED);
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

    private static Date parseDate(String s) {
        String[] pats = {"yyyy-MM-dd'T'HH:mm'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'"};
        for (String p : pats) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(p, Locale.US);
                f.setTimeZone(TimeZone.getTimeZone("UTC"));
                return f.parse(s);
            } catch (Exception ignored) { }
        }
        return null;
    }

    private static String scoreOf(JSONObject c) {
        Object s = c.opt("score");
        if (s == null || s == JSONObject.NULL) return null;
        if (s instanceof JSONObject) {
            JSONObject o = (JSONObject) s;
            return o.optString("displayValue", o.optString("value", ""));
        }
        return String.valueOf(s);
    }

    private static List<Match> parse(String json) throws Exception {
        List<Match> out = new ArrayList<>();
        JSONArray events = new JSONObject(json).optJSONArray("events");
        if (events == null) return out;
        for (int i = 0; i < events.length(); i++) {
            JSONObject ev = events.optJSONObject(i);
            if (ev == null) continue;
            JSONArray comps = ev.optJSONArray("competitions");
            JSONObject comp = comps == null ? null : comps.optJSONObject(0);
            if (comp == null) continue;
            JSONArray cs = comp.optJSONArray("competitors");
            if (cs == null) continue;
            Date d = parseDate(ev.optString("date", comp.optString("date", "")));
            if (d == null) continue;
            Match m = new Match();
            m.time = d.getTime();
            for (int j = 0; j < cs.length(); j++) {
                JSONObject c = cs.optJSONObject(j);
                if (c == null) continue;
                JSONObject team = c.optJSONObject("team");
                String id = team != null ? team.optString("id", "") : "";
                if (id.isEmpty()) id = c.optString("id", "");
                if (TEAM_ID.equals(id)) {
                    m.home = "home".equals(c.optString("homeAway"));
                    m.own = scoreOf(c);
                } else {
                    m.opponent = team != null
                            ? team.optString("displayName", team.optString("name", "?")) : "?";
                    m.opp = scoreOf(c);
                }
            }
            if (m.opponent == null) continue;
            JSONObject st = comp.optJSONObject("status");
            JSONObject type = st == null ? null : st.optJSONObject("type");
            m.done = type != null && type.optBoolean("completed", false);
            out.add(m);
        }
        return out;
    }
}
