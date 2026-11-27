package com.spendwise.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateManager {

    private static final String TAG = "SpendWiseUpdate";

    public static class UpdateInfo {
        public int versionCode;
        public String versionName;
        public String apkUrl;
        public String releaseNotes;

        public UpdateInfo(int versionCode, String versionName, String apkUrl, String releaseNotes) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.apkUrl = apkUrl;
            this.releaseNotes = releaseNotes;
        }
    }

    public interface UpdateCallback {
        void onUpdateAvailable(UpdateInfo info);
        void onNoUpdate();
        void onError(String error);
    }

    public static void checkForUpdate(final Context context, final String serverBaseUrl, final UpdateCallback callback) {
        new Thread(() -> {
            java.util.List<String> candidates = new java.util.ArrayList<>();
            if (serverBaseUrl != null && !serverBaseUrl.trim().isEmpty()) {
                String cleanBase = serverBaseUrl.endsWith("/") ? serverBaseUrl.substring(0, serverBaseUrl.length() - 1) : serverBaseUrl.trim();
                candidates.add(cleanBase);
            }
            if (!candidates.contains("https://krinalpixster-sketch.github.io/spendwise-app")) {
                candidates.add(0, "https://krinalpixster-sketch.github.io/spendwise-app");
            }
            if (!candidates.contains("https://github.com/krinalpixster-sketch/spendwise-app/raw/main")) {
                candidates.add(0, "https://github.com/krinalpixster-sketch/spendwise-app/raw/main");
            }
            if (!candidates.contains("https://raw.githubusercontent.com/krinalpixster-sketch/spendwise-app/main")) {
                candidates.add(0, "https://raw.githubusercontent.com/krinalpixster-sketch/spendwise-app/main");
            }
            if (!candidates.contains("https://careers-ignored-memo-badge.trycloudflare.com")) {
                candidates.add("https://careers-ignored-memo-badge.trycloudflare.com");
            }
            if (!candidates.contains("http://192.168.0.107:8502")) {
                candidates.add("http://192.168.0.107:8502");
            }
            if (!candidates.contains("http://192.168.0.85:8502")) {
                candidates.add("http://192.168.0.85:8502");
            }
            if (!candidates.contains("http://192.168.0.185:8502")) {
                candidates.add("http://192.168.0.185:8502");
            }
            if (!candidates.contains("http://192.168.0.173:8502")) {
                candidates.add("http://192.168.0.173:8502");
            }

            // Probe adjacent IPs around the phone's own Wi-Fi IP
            String phoneIp = getPhoneIpAddress();
            if (phoneIp != null && phoneIp.contains(".")) {
                int lastDot = phoneIp.lastIndexOf('.');
                String prefix = phoneIp.substring(0, lastDot + 1);
                try {
                    int myHost = Integer.parseInt(phoneIp.substring(lastDot + 1));
                    for (int offset : new int[]{1, -1, 2, -2, 3, -3, 4, -4, 5, -5, 23, -23}) {
                        int h = myHost + offset;
                        if (h > 0 && h < 255) {
                            String cIp = "http://" + prefix + h + ":8502";
                            if (!candidates.contains(cIp)) candidates.add(cIp);
                        }
                    }
                } catch (Exception ignored) {}
            }

            boolean connectedAny = false;
            String lastError = null;

            for (String base : candidates) {
                try {
                    URL url = new URL(base + "/version.json");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setInstanceFollowRedirects(true);
                    conn.setConnectTimeout(4000);
                    conn.setReadTimeout(4000);
                    conn.setRequestMethod("GET");

                    int respCode = conn.getResponseCode();
                    if (respCode == HttpURLConnection.HTTP_MOVED_TEMP || respCode == HttpURLConnection.HTTP_MOVED_PERM || respCode == 307 || respCode == 308) {
                        String newLoc = conn.getHeaderField("Location");
                        if (newLoc != null && !newLoc.isEmpty()) {
                            conn.disconnect();
                            conn = (HttpURLConnection) new URL(newLoc).openConnection();
                            conn.setInstanceFollowRedirects(true);
                            conn.setConnectTimeout(4000);
                            conn.setReadTimeout(4000);
                            respCode = conn.getResponseCode();
                        }
                    }

                    if (respCode == 200) {
                        connectedAny = true;
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        reader.close();

                        JSONObject json = new JSONObject(sb.toString());
                        int serverVersionCode = json.optInt("versionCode", 1);
                        String serverVersionName = json.optString("versionName", "1.0.0");
                        String releaseNotes = json.optString("releaseNotes", "Performance improvements & bug fixes.");
                        String apkUrl = json.optString("apkUrl", base + "/SpendWise.apk");

                        // If a fallback URL worked, update settings in DB
                        try {
                            SpendWiseDbHelper db = new SpendWiseDbHelper(context);
                            if (!base.equals(db.getServerUrl())) {
                                db.setServerUrl(base);
                            }
                        } catch (Exception ignored) {}

                        PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                        int currentVersionCode = pInfo.versionCode;

                        if (serverVersionCode > currentVersionCode) {
                            final UpdateInfo info = new UpdateInfo(serverVersionCode, serverVersionName, apkUrl, releaseNotes);
                            new Handler(Looper.getMainLooper()).post(() -> callback.onUpdateAvailable(info));
                            return;
                        } else {
                            new Handler(Looper.getMainLooper()).post(callback::onNoUpdate);
                            return;
                        }
                    }
                } catch (Exception e) {
                    lastError = e.getMessage();
                    Log.d(TAG, "Update check candidate failed for " + base + ": " + e.getMessage());
                }
            }

            if (!connectedAny) {
                final String finalErr = (serverBaseUrl != null ? serverBaseUrl : "server") + (lastError != null ? " (" + lastError + ")" : "");
                new Handler(Looper.getMainLooper()).post(() -> callback.onError(finalErr));
            } else {
                new Handler(Looper.getMainLooper()).post(callback::onNoUpdate);
            }
        }).start();
    }

    public static void showUpdateDialog(final Activity activity, final UpdateInfo info) {
        new AlertDialog.Builder(activity)
                .setTitle("🚀 Update Available (v" + info.versionName + ")")
                .setMessage(info.releaseNotes + "\n\nWould you like to update directly inside the app?")
                .setPositiveButton("Update Now", (dialog, which) -> downloadAndInstallApk(activity, info))
                .setNegativeButton("Later", null)
                .show();
    }

    private static void downloadAndInstallApk(final Activity activity, final UpdateInfo info) {
        // Create custom progress view
        final ProgressBar pb = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        pb.setIndeterminate(false);
        pb.setMax(100);
        pb.setProgress(0);

        final TextView tvStatus = new TextView(activity);
        tvStatus.setText("Starting download...");
        tvStatus.setPadding(0, 20, 0, 0);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(activity);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 20);
        layout.addView(pb);
        layout.addView(tvStatus);

        final AlertDialog progressDialog = new AlertDialog.Builder(activity)
                .setTitle("Downloading SpendWise v" + info.versionName)
                .setView(layout)
                .setCancelable(false)
                .create();

        progressDialog.show();

        new Thread(() -> {
            try {
                URL url = new URL(info.apkUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(15000);
                conn.connect();

                int code = conn.getResponseCode();
                if (code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM || code == 307 || code == 308) {
                    String redirectUrl = conn.getHeaderField("Location");
                    if (redirectUrl != null && !redirectUrl.isEmpty()) {
                        conn.disconnect();
                        url = new URL(redirectUrl);
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setInstanceFollowRedirects(true);
                        conn.setConnectTimeout(8000);
                        conn.setReadTimeout(15000);
                        conn.connect();
                    }
                }

                int fileLength = conn.getContentLength();
                InputStream input = conn.getInputStream();

                final File apkFile = new File(activity.getCacheDir(), "SpendWise_update.apk");
                if (apkFile.exists()) {
                    apkFile.delete();
                }

                FileOutputStream output = new FileOutputStream(apkFile);
                byte[] data = new byte[4096];
                long total = 0;
                int count;

                while ((count = input.read(data)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        final int progress = (int) (total * 100 / fileLength);
                        activity.runOnUiThread(() -> {
                            pb.setProgress(progress);
                            tvStatus.setText("Downloading: " + progress + "%");
                        });
                    }
                    output.write(data, 0, count);
                }

                output.flush();
                output.close();
                input.close();

                activity.runOnUiThread(() -> {
                    progressDialog.dismiss();
                    installApk(activity, apkFile);
                });

            } catch (final Exception e) {
                Log.e(TAG, "Download error", e);
                activity.runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(activity, "Update download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private static void installApk(Activity activity, File apkFile) {
        if (!apkFile.exists()) {
            Toast.makeText(activity, "APK file missing", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check Unknown sources permission for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(activity)
                        .setTitle("Install Permission Required ⚙️")
                        .setMessage("Android requires permission to update SpendWise.\n\n1. Tap 'Enable in Settings' below\n2. Turn ON 'Allow from this source'\n3. Return here and tap 'Install Now'")
                        .setPositiveButton("Enable in Settings", (d, w) -> {
                            Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + activity.getPackageName()));
                            activity.startActivity(settingsIntent);
                        })
                        .setNeutralButton("Install Now", (d, w) -> installApk(activity, apkFile))
                        .setNegativeButton("Cancel", null)
                        .show();
                return;
            }
        }

        // Launch package installer using AppFileProvider content URI
        Uri contentUri = Uri.parse("content://com.spendwise.app.fileprovider/" + apkFile.getName());
        Intent installIntent = new Intent(Intent.ACTION_VIEW);
        installIntent.setDataAndType(contentUri, "application/vnd.android.package-archive");
        installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            activity.startActivity(installIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch installer", e);
            Toast.makeText(activity, "Could not launch installer: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static String getPhoneIpAddress() {
        try {
            java.util.List<java.net.NetworkInterface> interfaces = java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces());
            for (java.net.NetworkInterface intf : interfaces) {
                java.util.List<java.net.InetAddress> addrs = java.util.Collections.list(intf.getInetAddresses());
                for (java.net.InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
