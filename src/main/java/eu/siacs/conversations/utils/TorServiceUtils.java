package eu.siacs.conversations.utils;

import java.util.List;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import eu.siacs.conversations.entities.Account;

public class TorServiceUtils {

    private final static String URI_ORBOT = "org.torproject.android";
    private static final String ORBOT_PLAYSTORE_URI = "market://details?id=" + URI_ORBOT;
    private final static String ACTION_START_TOR = "org.torproject.android.START_TOR";

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

    public static boolean isOrbotStarted(Account account) {
        return !(account != null && account.getStatus() == Account.State.TOR_NOT_AVAILABLE);
    }

    public static boolean isOrbotStarted(List<Account> accountList) {
        for (Account account : accountList) {
            if (!isOrbotStarted(account)) {
                return false;
            }
        }
        return true;
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
        try {
            activity.startActivityForResult(intent, requestCode);
        } catch(ActivityNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void startOrbot(Activity activity, int requestCode) {
        Intent launchIntent = new Intent(URI_ORBOT);
        launchIntent.setAction(ACTION_START_TOR);
        activity.startActivityForResult(launchIntent, requestCode);
    }
}
