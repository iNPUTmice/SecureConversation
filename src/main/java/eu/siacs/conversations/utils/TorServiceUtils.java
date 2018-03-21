package eu.siacs.conversations.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.StringTokenizer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

public class TorServiceUtils {

    private final static String TAG = "TorUtils";
    private final static String URI_ORBOT = "org.torproject.android";
    private final static String TOR_BIN_PATH = "/data/data/org.torproject.android/app_bin/tor";

    private static final String ORBOT_PLAYSTORE_URI = "market://details?id=" + URI_ORBOT;
    private final static String ACTION_START_TOR = "org.torproject.android.START_TOR";

    private final static String SHELL_CMD_PS = "ps";
    private final static String SHELL_CMD_PIDOF = "pidof";

    private static int findOrbotProcessId() {
        int procId = -1;
        try {
            procId = findProcessIdWithPidOf(TOR_BIN_PATH);
            if (procId == -1)
                procId = findProcessIdWithPS(TOR_BIN_PATH);
        } catch (Exception e) {
            try {
                procId = findProcessIdWithPS(TOR_BIN_PATH);
            } catch (Exception e2) {
                Log.e(TAG, "Unable to get proc id for command: " + URLEncoder.encode(TOR_BIN_PATH), e2);
            }
        }
        return procId;
    }

    private static int findProcessIdWithPidOf(String command) throws Exception {
        int procId = -1;
        Runtime r = Runtime.getRuntime();
        String baseName = new File(command).getName();

        Process procPs = r.exec(new String[]{
                SHELL_CMD_PIDOF, baseName
        });

        BufferedReader reader = new BufferedReader(new InputStreamReader(procPs.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            try {
                procId = Integer.parseInt(line.trim());
                break;
            } catch (NumberFormatException e) {
                Log.e("TorServiceUtils", "unable to parse process pid: " + line, e);
            }
        }
        return procId;
    }

    private static int findProcessIdWithPS(String command) throws Exception {
        int procId = -1;
        Runtime r = Runtime.getRuntime();

        Process procPs = r.exec(SHELL_CMD_PS);

        BufferedReader reader = new BufferedReader(new InputStreamReader(procPs.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.contains(' ' + command)) {
                StringTokenizer st = new StringTokenizer(line, " ");
                st.nextToken();
                procId = Integer.parseInt(st.nextToken().trim());
                break;
            }
        }
        return procId;
    }

    public static boolean isOrbotInstalled(Context context) {
        PackageManager pm = context.getPackageManager();
        boolean installed;
        try {
            pm.getPackageInfo(URI_ORBOT, PackageManager.GET_ACTIVITIES);
            installed = true;
        } catch (PackageManager.NameNotFoundException e) {
            installed = false;
        }
        return installed;
    }

    public static boolean isOrbotStarted() {
        int procId = TorServiceUtils.findOrbotProcessId();
        return (procId != -1);
    }

    public static void downloadOrbot(Context context) {
        Uri uri = Uri.parse(ORBOT_PLAYSTORE_URI);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        context.startActivity(intent);
    }

    public static void startOrbot(Context context) {
        Intent launchIntent = new Intent(URI_ORBOT);
        launchIntent.setAction(ACTION_START_TOR);
        context.startActivity(launchIntent);
    }

    public static void downloadOrbot(Activity activity, int requestCode) {
        Uri uri = Uri.parse(ORBOT_PLAYSTORE_URI);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        activity.startActivityForResult(intent, requestCode);
    }

    public static void startOrbot(Activity activity, int requestCode) {
        Intent launchIntent = new Intent(URI_ORBOT);
        launchIntent.setAction(ACTION_START_TOR);
        activity.startActivityForResult(launchIntent, requestCode);
    }
}
