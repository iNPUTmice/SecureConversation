package eu.siacs.conversations.ui;

import android.app.Dialog;
import android.support.annotation.NonNull;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.app.AlertDialog;

import eu.siacs.conversations.R;
import eu.siacs.conversations.utils.TorServiceUtils;

public class StartOrbotDialog extends DialogFragment {

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
    public static StartOrbotDialog newInstance(boolean cancelable) {
        StartOrbotDialog fragment = new StartOrbotDialog();
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

        AlertDialog.Builder startDialog = new AlertDialog.Builder(getActivity());
        startDialog.setTitle(R.string.start_orbot_dialog_title);
        startDialog.setMessage(R.string.start_orbot_dialog_description);
        startDialog.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
                TorServiceUtils.startOrbot(getActivity());
            }
        });
        startDialog.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
            }
        });

        return startDialog.show();
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
