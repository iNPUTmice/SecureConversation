package eu.siacs.conversations.ui;
import android.content.Intent;
import android.os.Bundle;
import eu.siacs.conversations.Config;
import android.support.v7.app.AppCompatActivity;

import org.jitsi.meet.sdk.JitsiMeetView;

public class JitsiMeet extends AppCompatActivity {
    private JitsiMeetView view;



    @Override
    public void onBackPressed() {
        if (!JitsiMeetView.onBackPressed()) {
            // Invoke the default handler if it wasn't handled by React.
            super.onBackPressed();

        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        view = new JitsiMeetView(this);

        Intent intentJitsiMeet = getIntent();
        String room = intentJitsiMeet.getStringExtra("room");
        Bundle config = new Bundle();
        config.putBoolean("startWithAudioMuted", false);
        config.putBoolean("startWithVideoMuted", true);
        Bundle urlObject = new Bundle();
        urlObject.putBundle("config", config);
        urlObject.putString("url", Config.JITSIMEET_BASEURL + room);
        view.loadURLObject(urlObject);

        setContentView(view);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        JitsiMeetView.onHostDestroy(this);
        view.dispose();
        view = null;
        finish();
    }

    @Override
    public void onNewIntent(Intent intent) {
        JitsiMeetView.onNewIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        JitsiMeetView.onHostResume(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        JitsiMeetView.onHostPause(this);
        finish();
    }
}