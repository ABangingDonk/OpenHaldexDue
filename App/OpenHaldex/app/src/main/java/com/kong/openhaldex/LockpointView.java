package com.kong.openhaldex;

import android.content.Context;
import android.text.Editable;
import android.text.Layout;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class LockpointView extends LinearLayout {
    public LockPoint lockPoint = new LockPoint(0,0,0);
    private SeekBar seekBar;
    private TextView lockPercent;
    private EditText speed;

    public LockpointView(Context context){
        super(context);
        initialiseViews(context);
    }

    public LockpointView(Context context, LockPoint _lockPoint){
        super(context);
        lockPoint = _lockPoint;
        initialiseViews(context);
    }

    public LockpointView(Context context, AttributeSet attributeSet){
        super(context, attributeSet);
        initialiseViews(context);
    }

    public LockpointView(Context context, AttributeSet attributeSet, int defStyle){
        super(context, attributeSet, defStyle);
        initialiseViews(context);
    }

    private void initialiseViews(Context context){
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.lockpoint_view, this);
    }

    public void speed_on_edit(View view){
        EditText editText = (EditText)view;
        lockPoint.speed = Float.parseFloat(editText.getText().toString());
    }

    @Override
    protected void onFinishInflate(){
        super.onFinishInflate();

        lockPercent = findViewById(R.id.lock_percent_textview);
        lockPercent.setText(String.valueOf((int)lockPoint.lock));

        speed = findViewById(R.id.speed_edittext);
        speed.setHint(String.valueOf(lockPoint.speed));
        speed.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                lockPoint.speed = Float.parseFloat(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        seekBar = findViewById(R.id.lock_percent_seekbar);
        seekBar.setProgress((int)(lockPoint.lock / 10));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                lockPercent.setText(String.valueOf(progress * 10));
                lockPoint.lock = progress * 10;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

    }
}
