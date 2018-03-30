package eu.siacs.conversations.ui.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.RequiresApi;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.services.NotificationService;

public class NotificationChannelHelper {

    private static final String CONVERSATION_NOTIFICATION_SHARED_PREF_NAME = "conversation_notif_shared_pref_name";
    private static final String CONVERSATION_NOTIFICATION_SHARED_PREF = "conversation_notif_shared_pref";
    private static final String CONVERSATION_QUIET_NOTIFICATION_SHARED_PREF = "conversation_quiet_notif_shared_pref";

    private static final String CONVERSATION_NOTIFICATION_CHANNEL_ID = "conversation";
    private static final String CONVERSATION_CHANNEL_NAME = "Unread Conversations";
    private static final String CONVERSATION_QUIET_NOTIFICATION_CHANNEL_ID = "quite_conversation";
    private static final String CONVERSATION_QUIET_CHANNEL_NAME = "Unread Conversations (Quite Hours)";

    public static final String FOREGROUND_NOTIFICATION_CHANNEL_ID = "foreground";
    private static final String FOREGROUND_CHANNEL_NAME = "Connected Accounts";
    public static final String UPDATE_ERROR_NOTIFICATION_CHANNEL_ID = "update_error";
    private static final String UPDATE_ERROR_CHANNEL_NAME = "Errors";
    public static final String FILE_ADDING_NOTIFICATION_CHANNEL_ID = "adding_file";
    private static final String FILE_ADDING_CHANNEL_NAME = "Video Compressing Updates";
    public static final String EXPORT_LOGS_NOTIFICATION_CHANNEL_ID = "exporting_logs";
    private static final String EXPORT_LOGS_CHANNEL_NAME = "Logs Export Updates";

    public static void createNotificationChannels(Context context, boolean delete) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            createConversationNotificationChannel(context, false, delete);
            createConversationNotificationChannel(context, true, delete);
            createNotificationChannel(context, FOREGROUND_NOTIFICATION_CHANNEL_ID, FOREGROUND_CHANNEL_NAME);
            createNotificationChannel(context, UPDATE_ERROR_NOTIFICATION_CHANNEL_ID, UPDATE_ERROR_CHANNEL_NAME);
            createNotificationChannel(context, FILE_ADDING_NOTIFICATION_CHANNEL_ID, FILE_ADDING_CHANNEL_NAME);
            createNotificationChannel(context, EXPORT_LOGS_NOTIFICATION_CHANNEL_ID, EXPORT_LOGS_CHANNEL_NAME);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private static void createNotificationChannel(Context context, String channelId, String channelName) {
        NotificationChannel defaultChannel = new NotificationChannel(channelId,
                channelName, NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager mNotifyManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (mNotifyManager == null) {
            return;
        }
        switch(channelId) {
            case FOREGROUND_NOTIFICATION_CHANNEL_ID:
                defaultChannel.setImportance(Config.SHOW_CONNECTED_ACCOUNTS ? NotificationManager.IMPORTANCE_DEFAULT : NotificationManager.IMPORTANCE_LOW);
                break;
            case UPDATE_ERROR_NOTIFICATION_CHANNEL_ID:
                defaultChannel.setImportance(NotificationManager.IMPORTANCE_LOW);
                break;
        }
        mNotifyManager.createNotificationChannel(defaultChannel);
    }

    public static String getPreviousNotificationChannelId(Context context, boolean isQuiet) {
        String key = !isQuiet ? CONVERSATION_NOTIFICATION_SHARED_PREF : CONVERSATION_QUIET_NOTIFICATION_SHARED_PREF;
        SharedPreferences sharedPref = context.getSharedPreferences(CONVERSATION_NOTIFICATION_SHARED_PREF_NAME, Context.MODE_PRIVATE);
        return sharedPref.getString(key, null);
    }

    private static void getNextNotificationChannelId(Context context, boolean isQuiet) {
        SharedPreferences sharedPref = context.getSharedPreferences(CONVERSATION_NOTIFICATION_SHARED_PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        String key = !isQuiet ? CONVERSATION_NOTIFICATION_SHARED_PREF : CONVERSATION_QUIET_NOTIFICATION_SHARED_PREF;
        String value = generateNextNotificationChannelId(context, isQuiet);
        editor.putString(key, value);
        editor.apply();
    }

    private static String generateNextNotificationChannelId(Context context, boolean isQuiet) {
        if (getPreviousNotificationChannelId(context, isQuiet) == null) {
            return !isQuiet ? CONVERSATION_NOTIFICATION_CHANNEL_ID : CONVERSATION_QUIET_NOTIFICATION_CHANNEL_ID;
        }
        return getPreviousNotificationChannelId(context, isQuiet) + Integer.toString(1);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private static void createConversationNotificationChannel(Context context, boolean isQuiet, boolean delete) {
        NotificationManager mNotifyManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (mNotifyManager == null) {
            return;
        }
        if (getPreviousNotificationChannelId(context, isQuiet) == null) {
            getNextNotificationChannelId(context, isQuiet);
        }
        if (delete) {
            mNotifyManager.deleteNotificationChannel(getPreviousNotificationChannelId(context, isQuiet));
            getNextNotificationChannelId(context, isQuiet);
        }
        String channelId = getPreviousNotificationChannelId(context, isQuiet);
        String channelName = !isQuiet ? CONVERSATION_CHANNEL_NAME : CONVERSATION_QUIET_CHANNEL_NAME;
        NotificationChannel defaultChannel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT);
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final Resources resources = context.getResources();
        final String ringtone = preferences.getString("notification_ringtone", resources.getString(R.string.notification_ringtone));
        final boolean vibrate = preferences.getBoolean("vibrate_on_notification", resources.getBoolean(R.bool.vibrate_on_notification));
        final boolean led = preferences.getBoolean("led", resources.getBoolean(R.bool.led));
        final boolean headsup = preferences.getBoolean("notification_headsup", resources.getBoolean(R.bool.headsup_notifications));
        if (!isQuiet) {
            if (vibrate) {
                final int dat = 70;
                final long[] pattern = {0, 3 * dat, dat, dat};
                defaultChannel.setVibrationPattern(pattern);
            } else {
                defaultChannel.setVibrationPattern(new long[]{0});
            }
            Uri uri = Uri.parse(ringtone);
            try {
                defaultChannel.setSound(NotificationService.fixRingtoneUri(context, uri),
                        new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT).setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED).build());
            } catch (SecurityException e) {
                Log.d(Config.LOGTAG, "unable to use custom notification sound " + uri.toString());
            }
            defaultChannel.setImportance(headsup ? NotificationManager.IMPORTANCE_HIGH : NotificationManager.IMPORTANCE_DEFAULT);
        }
        defaultChannel.setLightColor(ContextCompat.getColor(context, R.color.primary500));
        if (led) {
            defaultChannel.enableLights(true);
        }
        mNotifyManager.createNotificationChannel(defaultChannel);
    }
}
