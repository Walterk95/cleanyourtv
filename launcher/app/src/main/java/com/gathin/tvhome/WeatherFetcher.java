package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The launcher's only network access: one small HTTPS request to api.open-meteo.com for the weather strip,
 * at most once an hour, on a background thread. The last good answer is kept in a file so the strip shows
 * at start-up and while the TV is offline. Nothing is sent but the place's coordinates and the number of
 * days. Weather data by Open-Meteo.com, CC BY 4.0.
 */
final class WeatherFetcher {
    private static final String TAG = "TvHome.Weather";
    private static final int TIMEOUT_MS = 10_000;
    private static final int MAX_BYTES = 64 * 1024;
    /** A cached answer older than this is not shown at all. */
    static final long MAX_CACHE_AGE_MS = 12L * 60 * 60 * 1000;

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "weather");
        t.setDaemon(true);
        return t;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private WeatherFetcher() { }

    /** A forecast and the time (epoch ms) it was fetched. */
    static final class Result {
        final WeatherForecast forecast;
        final long fetchedAt;

        Result(WeatherForecast forecast, long fetchedAt) {
            this.forecast = forecast;
            this.fetchedAt = fetchedAt;
        }
    }

    interface Callback {
        /** On the main thread; null when the request failed or the answer was unusable. */
        void onResult(Result result);
    }

    private static File cacheFile(Context ctx, WeatherConfig cfg) {
        return new File(ctx.getFilesDir(), cfg.cacheName());
    }

    /** The saved answer, or null when there is none, it is unreadable or it is older than 12 hours. */
    static Result readCache(Context ctx, WeatherConfig cfg) {
        File f = cacheFile(ctx, cfg);
        try {
            if (!f.isFile() || f.length() == 0 || f.length() > MAX_BYTES) return null;
            long at = f.lastModified();
            if (System.currentTimeMillis() - at > MAX_CACHE_AGE_MS) return null;
            byte[] bytes = new byte[(int) f.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                int n = 0, r;
                while (n < bytes.length && (r = in.read(bytes, n, bytes.length - n)) > 0) n += r;
            }
            return new Result(WeatherForecast.parse(new String(bytes, StandardCharsets.UTF_8), cfg.days), at);
        } catch (Exception e) {
            Log.w(TAG, "ignoring the cached forecast: " + e.getMessage());
            return null;
        }
    }

    static void fetch(Context ctx, WeatherConfig cfg, Callback callback) {
        final Context app = ctx.getApplicationContext();
        POOL.execute(() -> {
            Result result = null;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(cfg.url()).openConnection();
                c.setConnectTimeout(TIMEOUT_MS);
                c.setReadTimeout(TIMEOUT_MS);
                c.setRequestProperty("Accept", "application/json");
                int code = c.getResponseCode();
                if (code != 200) throw new IOException("HTTP " + code);
                String body;
                try (InputStream in = c.getInputStream()) {
                    body = readLimited(in);
                }
                WeatherForecast forecast = WeatherForecast.parse(body, cfg.days);
                saveCache(app, cfg, body);
                result = new Result(forecast, System.currentTimeMillis());
            } catch (Exception e) {
                Log.w(TAG, "weather request failed: " + e.getMessage());
            } finally {
                if (c != null) c.disconnect();
            }
            final Result r = result;
            MAIN.post(() -> callback.onResult(r));
        });
    }

    private static String readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > MAX_BYTES) throw new IOException("answer larger than 64 KB");
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Written next to its final name first, so a crash halfway never leaves a truncated cache. */
    private static void saveCache(Context ctx, WeatherConfig cfg, String body) {
        File f = cacheFile(ctx, cfg);
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        } catch (IOException e) {
            Log.w(TAG, "could not save the forecast: " + e.getMessage());
            return;
        }
        if (!tmp.renameTo(f)) tmp.delete();
    }
}
