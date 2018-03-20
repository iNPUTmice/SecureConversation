package eu.siacs.conversations.ui;

import android.app.Dialog;
import android.support.annotation.NonNull;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.app.AlertDialog;

import eu.siacs.conversations.R;
import eu.siacs.conversations.utils.TorServiceUtils;

public class DownloadOrbotDialog extends DialogFragment {

    private static final String ARG_CANCELABLE = StartOrbotDialog.class.getCanonicalName() + ".ARG_CANCELABLE";

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setCancelable(false);
    }

    /**
     * Public factory method to get dialog instances.
     *
     * @param cancelable If 'true', the dialog can be cancelled by the user input (BACK button, touch outside...)
     * @return New dialog instance, ready to show.
     */
    public static DownloadOrbotDialog newInstance(boolean cancelable) {
        DownloadOrbotDialog fragment = new DownloadOrbotDialog();
        Bundle args = new Bundle();
        args.putBoolean(ARG_CANCELABLE, cancelable);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        boolean cancelable = getArguments().getBoolean(ARG_CANCELABLE, false);
        setCancelable(cancelable);

        AlertDialog.Builder downloadDialog = new AlertDialog.Builder(getActivity());
        downloadDialog.setTitle(R.string.install_orbot_dialog_title);
        downloadDialog.setMessage(R.string.install_orbot_dialog_description);
        downloadDialog.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
                TorServiceUtils.downloadOrbot(getActivity());
            }
        });
        downloadDialog.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
            }
        });

        return downloadDialog.show();
    }

    @Override
    public void onDestroyView() {
        Dialog dialog = getDialog();
        if (dialog != null && getRetainInstance()) {
            dialog.setDismissMessage(null);
        }
        super.onDestroyView();
    }
}
