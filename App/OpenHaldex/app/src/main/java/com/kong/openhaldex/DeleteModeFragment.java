package com.kong.openhaldex;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

public class DeleteModeFragment extends DialogFragment  {
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Select a mode to delete")
                .setItems(getArguments().getCharSequenceArray("modeNames"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        DialogListener listener = (DialogListener)getActivity();
                        listener.onFinishEditDialog(getArguments().getInt("returnID"), which);
                    }
                });
        // Create the AlertDialog object and return it
        return builder.create();
    }

    public interface DialogListener{
        void onFinishEditDialog(int source, int index);
    }
}