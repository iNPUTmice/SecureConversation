package eu.siacs.conversations.ui;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.prefs.PreferenceChangeListener;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;

public class SettingsFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener {
    public static final String PREF_THEME_KEY = "theme";
    public static final String PREF_QUICKACTION_KEY = "quick_action";
    public static final String PREF_RINGTONE_KEY = "notification_ringtone";
    public static final String PREF_ACCEPTFILESIZE_KEY = "auto_accept_file_size";
    public static final String PREF_COMPRESS_KEY = "picture_compression";
    private Preference themePreference;
    private Preference quickactionPreference;
    private Preference ringtonePreference;
    private Preference acceptfilesizePreference;
    private Preference compressPreference;
    private SharedPreferences preferences;
	//http://stackoverflow.com/questions/16374820/action-bar-home-button-not-functional-with-nested-preferencescreen/16800527#16800527
	private void initializeActionBar(PreferenceScreen preferenceScreen) {
		final Dialog dialog = preferenceScreen.getDialog();

		if (dialog != null) {
			View homeBtn = dialog.findViewById(android.R.id.home);

			if (homeBtn != null) {
				View.OnClickListener dismissDialogClickListener = new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						dialog.dismiss();
					}
				};

				ViewParent homeBtnContainer = homeBtn.getParent();

				if (homeBtnContainer instanceof FrameLayout) {
					ViewGroup containerParent = (ViewGroup) homeBtnContainer.getParent();
					if (containerParent instanceof LinearLayout) {
						((LinearLayout) containerParent).setOnClickListener(dismissDialogClickListener);
					} else {
						((FrameLayout) homeBtnContainer).setOnClickListener(dismissDialogClickListener);
					}
				} else {
					homeBtn.setOnClickListener(dismissDialogClickListener);
				}
			}
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.preferences);
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

        themePreference=findPreference(PREF_THEME_KEY);
        themePreference.setSummary(getResources().getString(getThemeSecondaryText(preferences.getString(PREF_THEME_KEY,""))));
        quickactionPreference=findPreference(PREF_QUICKACTION_KEY);
        quickactionPreference.setSummary(getResources().getString(getQuickActionSecondaryText(preferences.getString(PREF_QUICKACTION_KEY,""))));
        ringtonePreference=findPreference(PREF_RINGTONE_KEY);
        ringtonePreference.setSummary(getRingtoneSecondaryText());
        acceptfilesizePreference=findPreference(PREF_ACCEPTFILESIZE_KEY);
        acceptfilesizePreference.setSummary(getResources().getString(getAcceptFilesSecondaryText(preferences.getString(PREF_ACCEPTFILESIZE_KEY,""))));
        compressPreference=findPreference(PREF_COMPRESS_KEY);
        compressPreference.setSummary(getResources().getString(getCompressSecondaryText(preferences.getString(PREF_COMPRESS_KEY,""))));
        // Remove from standard preferences if the flag ONLY_INTERNAL_STORAGE is not true
		if (!Config.ONLY_INTERNAL_STORAGE) {
			PreferenceCategory mCategory = (PreferenceCategory) findPreference("security_options");
			Preference mPref1 = findPreference("clean_cache");
			Preference mPref2 = findPreference("clean_private_storage");
			mCategory.removePreference(mPref1);
			mCategory.removePreference(mPref2);
		}

	}

    @Override
    public void onResume() {
        super.onResume();
        themePreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                themePreference.setSummary(getResources().getString(getThemeSecondaryText((String) newValue)));
                return true;
            }
        });

        quickactionPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                quickactionPreference.setSummary(getResources().getString(getQuickActionSecondaryText((String) newValue)));
                return true;
            }
        });

        ringtonePreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                ringtonePreference.setSummary(getRingtoneSecondaryText());
                return true;
            }
        });

        acceptfilesizePreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                acceptfilesizePreference.setSummary(getResources().getString(getAcceptFilesSecondaryText((String) newValue)));
                return true;
            }
        });

        compressPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                compressPreference.setSummary(getResources().getString(getCompressSecondaryText((String) newValue)));
                return true;
            }
        });

        themePreference.setSummary(getResources().getString(getThemeSecondaryText(preferences.getString("theme",""))));
        quickactionPreference.setSummary(getResources().getString(getQuickActionSecondaryText(preferences.getString(PREF_QUICKACTION_KEY,""))));
        ringtonePreference.setSummary(getRingtoneSecondaryText());
        acceptfilesizePreference.setSummary(getResources().getString(getAcceptFilesSecondaryText(preferences.getString(PREF_ACCEPTFILESIZE_KEY,""))));
        compressPreference.setSummary(getResources().getString(getCompressSecondaryText(preferences.getString(PREF_COMPRESS_KEY,""))));

    }

	@Override
	public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
		super.onPreferenceTreeClick(preferenceScreen, preference);
		if (preference instanceof PreferenceScreen) {
			initializeActionBar((PreferenceScreen) preference);
		}
		return false;
	}


    @Override
    public boolean onPreferenceChange(Preference preference, Object o) {
        if(preference.getKey().equals(PREF_THEME_KEY)) {
            themePreference.setSummary(getResources().getString(getThemeSecondaryText((String) o)));
        } else if (preference.getKey().equals(PREF_QUICKACTION_KEY)) {
            quickactionPreference.setSummary(getResources().getString(getQuickActionSecondaryText((String) o)));
        } else if (preference.getKey().equals(PREF_RINGTONE_KEY)) {
            ringtonePreference.setSummary(getRingtoneSecondaryText());
        } else if (preference.getKey().equals(PREF_ACCEPTFILESIZE_KEY)) {
            acceptfilesizePreference.setSummary(getResources().getString(getAcceptFilesSecondaryText((String) o)));
        } else if (preference.getKey().equals(PREF_COMPRESS_KEY)) {
            compressPreference.setSummary(getResources().getString(getCompressSecondaryText((String) o)));
        }
        return false;
    }

    public int getThemeSecondaryText(String type) {
        if(type.equals("light")) {
            return R.string.pref_theme_light;
        } else if(type.equals("dark")) {
            return R.string.pref_theme_dark;
        }

        return 0;
    }

    public int getQuickActionSecondaryText(String type) {
        if(type.equals("none")) {
            return R.string.none;
        } else if(type.equals("recent")) {
            return R.string.recently_used;
        } else if(type.equals("photo")) {
            return R.string.attach_take_picture;
        } else if(type.equals("picture")) {
            return R.string.attach_choose_picture;
        } else if(type.equals("voice")) {
            return R.string.attach_record_voice;
        }

        return 0;
    }

    public int getAcceptFilesSecondaryText(String type) {
        if(type.equals("0")) {
            return R.string.never;
        } else if(type.equals("262144")) {
            return R.string.KiB256;
        } else if(type.equals("524288")) {
            return R.string.KiB512;
        } else if(type.equals("1048576")) {
            return R.string.MiB1;
        } else if(type.equals("5242880")) {
            return R.string.MiB5;
        } else if(type.equals("10485760")) {
            return R.string.MiB10;
        }

        return 0;
    }

    public int getCompressSecondaryText(String type) {
        if(type.equals("never")) {
            return R.string.never;
        } else if(type.equals("auto")) {
            return R.string.automatically;
        } else if(type.equals("always")) {
            return R.string.always;
        }

        return 0;
    }

    public String getRingtoneSecondaryText() {
        String ringtoneString = preferences.getString(PREF_RINGTONE_KEY,"");
        if(!ringtoneString.equals("")) {
            Ringtone ringtone = RingtoneManager.getRingtone(getActivity(), Uri.parse(ringtoneString));
            return ringtone.getTitle(getActivity());
        }
        return "None";
    }


}
