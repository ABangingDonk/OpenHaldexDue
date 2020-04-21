package com.kong.openhaldex;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;

import static android.support.constraint.Constraints.TAG;

public class SetMinPedalFragment extends DialogFragment {
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        int threshold = getArguments().getInt("pedalThreshold");

        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        final EditText input = new EditText(this.getContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setPadding(19,5,19,5);
        Log.i(TAG, String.format("onCreateDialog: existing threshold: %s%%", String.valueOf(threshold)));
        input.setHint(String.valueOf(threshold));

        builder.setView(input);

        builder.setTitle("Set minimum pedal threshold for Haldex engagement");
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int retval) {
                DialogListener listener = (DialogListener)getActivity();
                listener.onFinishEditDialog(getArguments().getInt("returnID"), Integer.parseInt(input.getText().toString()));
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int retval) {
                dialog.cancel();
            }
        });
        // Create the AlertDialog object and return it
        return builder.create();
    }

    public interface DialogListener{
        void onFinishEditDialog(int source, int retval);
    }
}
