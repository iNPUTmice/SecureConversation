package eu.siacs.conversations.ui;

import android.content.Context;
import android.os.AsyncTask;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.xmpp.jid.Jid;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class ExportLogsPreference extends Preference {
    private static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    private ProgressBar progressBar = null;
    private ExportLogsTask exportLogsTask = null;
    private int currentProgressBarProgress = -1;
    private int currentProgressBarMax = -1;

    public ExportLogsPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ExportLogsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ExportLogsPreference(Context context) {
        super(context);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.preference_export_logs, parent, false);
        progressBar = (ProgressBar) view.findViewById(R.id.progress_bar);
        if (exportLogsTask != null && exportLogsTask.getStatus() == AsyncTask.Status.RUNNING) {
            progressBar.setMax(currentProgressBarMax);
            progressBar.setProgress(currentProgressBarProgress);
            progressBar.setVisibility(View.VISIBLE);
        } else {
            progressBar.setVisibility(View.INVISIBLE);
        }
        return view;
    }

    protected void onClick() {
        if (exportLogsTask == null || exportLogsTask.getStatus() == AsyncTask.Status.FINISHED) {
            exportLogsTask = new ExportLogsTask();
            exportLogsTask.execute();
        }
    }

    private void setProgressBarProgress(int progress) {
        currentProgressBarProgress = progress;
        progressBar.setProgress(progress);
    }

    private void setProgressBarMax(int max) {
        currentProgressBarMax = max;
        progressBar.setMax(max);
    }

    private class ExportLogsTask extends AsyncTask<Void, Integer, Void> {

        final String DIRECTORY_STRING_FORMAT = FileBackend.getConversationsFileDirectory() + "/logs/%s/%s";
        final String MESSAGE_STRING_FORMAT = "(%s) %s: %s\n";

        DatabaseBackend databaseBackend = DatabaseBackend.getInstance(getContext());
        List<Account> accounts = databaseBackend.getAccounts();

        protected Void doInBackground(Void... _) {
            List<Conversation> conversations = databaseBackend.getConversations(Conversation.STATUS_AVAILABLE);
            conversations.addAll(databaseBackend.getConversations(Conversation.STATUS_ARCHIVED));

            setProgressBarMax(conversations.size());
            int progress = 0;

            for (Conversation conversation : conversations) {
                writeToFile(conversation);
                publishProgress(++progress);
            }
            return null;
        }

        private void writeToFile(Conversation conversation) {
            Jid accountJid = resolveAccountUuid(conversation.getAccountUuid());
            Jid contactJid = conversation.getJid();

            File dir = new File(String.format(DIRECTORY_STRING_FORMAT,
                    accountJid.toBareJid().toString(),
                    contactJid.toBareJid().toString()));
            dir.mkdirs();

            BufferedWriter bw = null;
            try {
                for (Message message : databaseBackend.getMessagesIterable(conversation)) {
                    if (message.getType() == Message.TYPE_TEXT || message.hasFileOnRemoteHost()) {
                        String date = simpleDateFormat.format(new Date(message.getTimeSent()));
                        if (bw == null) {
                            bw = new BufferedWriter(new FileWriter(new File(dir, date + ".txt")));
                        }
                        String jid = null;
                        switch (message.getStatus()) {
                            case Message.STATUS_RECEIVED:
                                jid = getMessageCounterpart(message);
                                break;
                            case Message.STATUS_SEND:
                                jid = accountJid.toBareJid().toString();
                                break;
                        }
                        if (jid != null) {
                            bw.write(String.format(MESSAGE_STRING_FORMAT, date, jid,
                                    message.getBody().replace("\\\n", "\\ \n").replace("\n", "\\ \n")));
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (bw != null) {
                        bw.close();
                    }
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }

        private Jid resolveAccountUuid(String accountUuid) {
            for (Account account : accounts) {
                if (account.getUuid().equals(accountUuid)) {
                    return account.getJid();
                }
            }
            return null;
        }

        private String getMessageCounterpart(Message message) {
            String trueCounterpart = (String) message.getContentValues().get(Message.TRUE_COUNTERPART);
            if (trueCounterpart != null) {
                return trueCounterpart;
            } else {
                return message.getCounterpart().toString();
            }
        }

        protected void onPreExecute() {
            setProgressBarProgress(0);
            progressBar.setVisibility(View.VISIBLE);
        }

        protected void onProgressUpdate(Integer... progress) {
            setProgressBarProgress(progress[0]);
        }

        protected void onPostExecute(Void _) {
            progressBar.setVisibility(View.INVISIBLE);
        }
    }
}