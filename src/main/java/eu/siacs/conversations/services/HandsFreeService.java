/*
HandsFree mechanism added by Mark Burton
Copyright (c) Mark Burton 2016

This file is Licensed under the terms of the GNU General Public License Version 3.
See LICENSE file for details


Functionality:
The HandsFree service provides a speakMessage service, which subsequently can offer the user the possibility to reply using speech recognition.

The service will respect the users interruptions settings (e.g. Do Not Disturb)
It will also mute and/or stop vice recognition on phone calls
    (Both of these services need permissions)
Finally it tries to 'play nicely' with other audio users on the device.

 */

package eu.siacs.conversations.services;

import eu.siacs.conversations.ui.ConversationFragment;

import android.content.Intent;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.widget.Toast;
import android.app.NotificationManager;

import java.util.concurrent.ScheduledThreadPoolExecutor;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;


public class HandsFreeService {
    private Handler mVoiceHandler = new Handler();
    private ScheduledThreadPoolExecutor mTTSHandler = new ScheduledThreadPoolExecutor(0);
    private ConversationFragment mConversationFragment;
    private SpeechRecognizer mRecognizer = null;
    private TextToSpeech mTextToSpeech = null;
    private mUtteranceProgressListener mutteranceProgressListener = null;
    private Context mContext;
    private boolean ttsBound = false;
    private boolean onPhone = false;
    private boolean isListening = false;
    private boolean isTalking = false;
    private mRecognitionListener mrecognitionListener = new mRecognitionListener();
    private final Semaphore available = new Semaphore(1, true);
    private AudioManager am = null;
    private NotificationManager notificationManager = null;
    private enum FocusState {None, Requested, Accepted}

    private FocusState focusState=FocusState.None;
    private AudioManager.OnAudioFocusChangeListener mOnAudioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            Log.d(Config.LOGTAG,"Audio Focus "+focusChange);
            if (focusChange>0) focusState=FocusState.Accepted;
            if (focusChange < 0 && focusState==FocusState.Accepted) stopListening();
        }
    };

    public HandsFreeService(final Context context) {
        mContext = context;
        available.acquireUninterruptibly();
        mutteranceProgressListener = new mUtteranceProgressListener();
        mTextToSpeech = new TextToSpeech(mContext, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    ttsBound = true;
                    mTextToSpeech.setOnUtteranceProgressListener(mutteranceProgressListener);
                }
                available.release();
            }
        });
        TelephonyManager TelephonyMgr = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        TelephonyMgr.listen(new MyPhoneStateListener(), PhoneStateListener.LISTEN_CALL_STATE);

        am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public void onRegisterConversationFragment(ConversationFragment fragment) {

        mConversationFragment = fragment;
        handsFreePrompt();
    }

    public void onActivateConversationFragement(ConversationFragment fragment) {

        mConversationFragment = fragment;

        endListening();

        mRecognizer = SpeechRecognizer.createSpeechRecognizer(mContext);
        mRecognizer.setRecognitionListener(mrecognitionListener);

        handsFreePrompt();
    }

    public void handsFreePrompt() {
        checkBluetooth();
        if (mTextToSpeech != null && mRecognizer != null) {
            String prompt=PreferenceManager.getDefaultSharedPreferences(mContext).getString("handsfree_prompt", "");
            if (prompt.isEmpty()) {
                checkBluetooth();
                if (mRecognizer != null) {
                    mVoiceHandler.postDelayed(mOfferVoice, 500);
                }
            } else {
                speakMessage(prompt);
            }
        }
    }


    public void speakMessage(String str) {
        class OneShotTask implements Runnable {
            String message;

            OneShotTask(String s) {
                message = s;
            }

            public void run() {
                if (mTextToSpeech != null && ttsBound) {
//                    stopListening();

                    if (am != null) {
                        if (am.requestAudioFocus(mOnAudioFocusChangeListener, AudioManager.STREAM_NOTIFICATION, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                            focusState=FocusState.Requested;
                            Log.d(Config.LOGTAG,"Got Audio Focus for speech");
                        }
                    }

    //                Log.d(Config.LOGTAG, "Waiting to speak.");
    //                Log.d(Config.LOGTAG, "available permits " + available.availablePermits());
                    available.acquireUninterruptibly();
    //                Log.d(Config.LOGTAG, "available permits " + available.availablePermits());
                    Log.d(Config.LOGTAG, "Speaking message " + message);
                    if (available.availablePermits() != 0) throw new AssertionError();
                    if (isListening) throw new AssertionError();
                    if (isTalking) throw new AssertionError();
                    isTalking = true;
                    Bundle params = new Bundle();
                    params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "Conversation");
                    mTextToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, params, "Conversation");

                }
            }
        }

        checkBluetooth();

        boolean dnd = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            dnd = (notificationManager.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL);
        }
        if ((str.length() > 0) && mTextToSpeech != null && !onPhone && !dnd && (mRecognizer != null || PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("ttsnotify_enabled", true))) {
            int max_length = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(mContext).getString("max_tts_length", "100"));
            final String msg;
            if (max_length > 0) {
                msg = str.substring(0, Math.min(max_length, str.length()));
            } else {
                msg = str;
            }
//            stopListening();
            mTTSHandler.schedule(new OneShotTask(msg), 300, TimeUnit.MILLISECONDS);
        }
    }

    class mUtteranceProgressListener extends UtteranceProgressListener {
        @Override
        public void onStart(String utteranceId) {
        }

        @Override
        public void onError(String utteranceId) {
            this.onDone(utteranceId);
            Log.d(Config.LOGTAG, "Utterance " + utteranceId + " Error");
        }

        @Override
        public void onDone(String utteranceId) {
         //   Log.d(Config.LOGTAG, "Utterance " + utteranceId + " Completed");
            if (isTalking) {
                isTalking = false;
                if (am != null) {
                    am.abandonAudioFocus(mOnAudioFocusChangeListener);
                    focusState=FocusState.None;
                }
                available.release();
           //     Log.d(Config.LOGTAG, "speaking done");
           //     Log.d(Config.LOGTAG, "available permits " + available.availablePermits());

                checkBluetooth();

                if (mRecognizer != null) {
                    int delay = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(mContext).getString("prompt_delay", "1000"));
                    mVoiceHandler.postDelayed(mOfferVoice, delay);
                }
            }
        }
    }

    private void checkBluetooth() {
        if (PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("bluetoothonly_enabled", false)) {
            Log.d(Config.LOGTAG,"Bluetooth mode");
            if (am != null) {
                if (mRecognizer == null && am.isBluetoothA2dpOn() && mConversationFragment != null) {
                    mRecognizer = SpeechRecognizer.createSpeechRecognizer(mContext);
                    mRecognizer.setRecognitionListener(mrecognitionListener);
                }
                if (mRecognizer != null && !am.isBluetoothA2dpOn()) {
                    endListening();
                }
            }
        }
    }

    private void endListening() {
        stopListening();
        if (mRecognizer != null) {
            mRecognizer.destroy();
            mRecognizer = null;
        }
        //mTextToSpeech.shutdown();
        //mTextToSpeech=null;
    }

    private void stopListening() {
        Log.d(Config.LOGTAG, "Request stop listening");

        if (isListening) {
            if (mRecognizer != null) {
                mRecognizer.stopListening();
            }
            isListening = false;
            if (am != null) {
                am.abandonAudioFocus(mOnAudioFocusChangeListener);
                focusState=FocusState.None;
            }
            available.release();

          //  Log.d(Config.LOGTAG, "stopped listening");
          //  Log.d(Config.LOGTAG, "available permits " + available.availablePermits());
        }
    }


    private Runnable mOfferVoice = new Runnable() {
        public void run() {
            if (mRecognizer != null && !onPhone && !isListening) {
          //      Log.d(Config.LOGTAG, "Waiting to listen");
          //      Log.d(Config.LOGTAG, "available permits " + available.availablePermits());
                available.acquireUninterruptibly();
                Log.d(Config.LOGTAG, "Starting to listen");
                if (available.availablePermits() != 0) throw new AssertionError();
                if (isListening) throw new AssertionError();
                if (isTalking) throw new AssertionError();
                isListening = true;
                if (am != null) {
                    if (am.requestAudioFocus(mOnAudioFocusChangeListener, AudioManager.STREAM_NOTIFICATION, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        focusState=FocusState.Requested;
                        Log.d(Config.LOGTAG,"Got Audio Focus for recognition");
                    }
                }
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
                }
                intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

                mrecognitionListener.reset();

                mRecognizer.startListening(intent);
            }
        }
    };

    class mRecognitionListener implements RecognitionListener {
        public String baseText=null;
        private java.util.concurrent.locks.ReentrantLock mLock=new java.util.concurrent.locks.ReentrantLock();
        public void reset() {
            mLock.lock();
            baseText=null;
            mLock.unlock();
        }
        private void updateBaseText() {
            mLock.lock();
            if (baseText == null) {
                if (mConversationFragment != null) {
                    baseText = mConversationFragment.currentText();
                    if (baseText.length() != 0 && !baseText.endsWith(" ")) {
                        baseText = baseText + " ";
                    }
                    Log.d(Config.LOGTAG,"Basetext is"+baseText);
                }
            }
            mLock.unlock();
        }

        public void onReadyForSpeech(Bundle params) {
//            Log.d(Config.LOGTAG, "Speech Ready");
        }

        public void onBeginningOfSpeech() {
            updateBaseText();
        }

        public void onRmsChanged(float rmsdB) {
//            Log.d(Config.LOGTAG, "Speech RMS change");
        }

        public void onBufferReceived(byte[] buffer) {
        }

        public void onEndOfSpeech() {
            //           Log.d(Config.LOGTAG, "Speech End of speech");
        }

        public void onError(int error) {
            Log.d(Config.LOGTAG, "speech Recognition error " + error);
            switch (error) {
                case 6:
                case 3:
                case 1:
                case 2:
                case 4:
                    stopListening();
                    break;
                case 8:
                    stopListening();
                    mVoiceHandler.postDelayed(mOfferVoice, 1000);
                    break;
                case 5:
                case 7:
                    break;
                case 9:
                    Toast.makeText(mContext, mContext.getString(R.string.no_record_voice_permission), Toast.LENGTH_SHORT).show();
                    stopListening();
                default:
                    break;

            }
        }

        private String processText(String str) {
            String result=str
                    .replaceAll("(?i)\\s*\\b" + PreferenceManager.getDefaultSharedPreferences(mContext).getString("handsfree_send", "") + "$", "")
                    .replaceAll("(?i)\\s*\\b[^ ]+\\s+" + PreferenceManager.getDefaultSharedPreferences(mContext).getString("handsfree_delete", "") + "\\b", "");
            for (String replacements : PreferenceManager.getDefaultSharedPreferences(mContext).getString("handsfree_user_replacements", "").split("\\n")) {
                String r[]=replacements.split("->");
                if (r.length==2 && r[0].length()>0) {
                    result=result.replaceAll("(?i)\\b" + r[0], r[1]);
                }
            }
            return result;
        }

        public void onResults(Bundle results) {
            updateBaseText();

            String str = "";
      //      Log.d(Config.LOGTAG, "speech recognition onResult");
            // it's possible that an error occurred, but we kept going, in which case we have already released the remit
            stopListening();

            ArrayList data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (data != null) {
                for (int i = 0; i < data.size(); i++) {
                    Log.d(Config.LOGTAG, "speech Recognition result " + data.get(i));
                    str += data.get(i);
                }
            }


            if (str.matches("(?i)^" + PreferenceManager.getDefaultSharedPreferences(mContext).getString("handsfree_stop", "") + "$")) {
                mConversationFragment.replaceText("");
                endListening();
            } else if (str.matches("(?i)^" + PreferenceManager.getDefaultSharedPreferences(mContext).getString("handsfree_clear", "") + "$")) {
                mConversationFragment.replaceText("");
                mVoiceHandler.postDelayed(mOfferVoice, 500);
            } else if (str.matches("(?i)\\s*\\b" + PreferenceManager.getDefaultSharedPreferences(mContext).getString("handsfree_send", "") + "$")) {
                mConversationFragment.replaceText(processText(baseText + str));
                mConversationFragment.sendMessage();
            } else {
                String newText = processText(baseText + str);
                mConversationFragment.replaceText(newText);
                speakMessage(newText);
//                mTextTospeech.speak(newText, TextToSpeech.QUEUE_FLUSH, null, "Conversation");
//				mVoiceHandler.postDelayed(mOfferVoice, 500);
            }
            baseText = "";
            //mText.setText("results: "+String.valueOf(data.size()));
        }

        public void onPartialResults(Bundle results) {
            updateBaseText();

            String str = "";
            ArrayList data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (data != null) {
                for (int i = 0; i < data.size(); i++) {
             //       Log.d(Config.LOGTAG, "speech Recognition partial result " + data.get(i));
                    str += data.get(i);
                }
            }

            mConversationFragment.replaceText(processText(baseText + str));

        }

        public void onEvent(int eventType, Bundle params) {
            Log.d(Config.LOGTAG, "onEvent : " + eventType);
        }
    }


    private class MyPhoneStateListener extends PhoneStateListener {

        public void onCallStateChanged(int state, String incomingNumber) {

            switch (state) {
                case TelephonyManager.CALL_STATE_IDLE:
                    Log.d("DEBUG", "IDLE");
                    onPhone = false;
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    Log.d("DEBUG", "OFFHOOK");
                    onPhone = true;
                    stopListening();
                    break;
                case TelephonyManager.CALL_STATE_RINGING:
                    Log.d("DEBUG", "RINGING");
                    onPhone = true;
                    stopListening();
                    break;
            }
        }

    }

}
