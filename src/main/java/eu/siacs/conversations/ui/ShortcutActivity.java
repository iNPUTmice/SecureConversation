package eu.siacs.conversations.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;

import eu.siacs.conversations.services.XmppConnectionService;

import static eu.siacs.conversations.ui.XmppActivity.EXTRA_ACCOUNT;

public class ShortcutActivity extends AppCompatActivity implements ServiceConnection {


    private static final int REQUEST_CODE_CHOOSE_CONTACT = 0xcafe;
    private XmppConnectionService xmppConnectionService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent service = new Intent(this, XmppConnectionService.class);
        startService(service);
        bindService(service, this, BIND_AUTO_CREATE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_CHOOSE_CONTACT && resultCode == RESULT_OK) {
            String account = data.getStringExtra(EXTRA_ACCOUNT);
            String contact = data.getStringExtra("contact");
            Intent shortcut = xmppConnectionService.getShortcutService().createShortcut(account, contact);
            setResult(RESULT_OK, shortcut);
            finish();
        }else {
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        xmppConnectionService = ((XmppConnectionService.XmppConnectionBinder) service).getService();
        Intent intent = new Intent(this, ChooseContactActivity.class);
        startActivityForResult(intent, REQUEST_CODE_CHOOSE_CONTACT);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(this);
    }
}
