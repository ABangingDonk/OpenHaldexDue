package com.kong.openhaldex;

import android.content.Context;
import android.text.Editable;
import android.text.Layout;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

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

    @Override
    protected void onFinishInflate(){
        super.onFinishInflate();

        lockPercent = findViewById(R.id.lock_percent_textview);
        lockPercent.setText(String.format(Locale.ENGLISH, "%d%%", (int)lockPoint.lock));

        speed = findViewById(R.id.speed_edittext);
        speed.setText(String.valueOf(lockPoint.speed));
        speed.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                EditText editText = (EditText)v;
                if (hasFocus){
                    editText.getText().clear();
                }
            }
        });
        speed.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!s.toString().equals("")){
                    int speed = Integer.parseInt(s.toString());
                    if (speed > 255){
                        lockPoint.speed = 255;
                    }
                    else{
                        lockPoint.speed = speed;
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!s.toString().equals("")){
                    int speed = Integer.parseInt(s.toString());
                    if (speed > 255){
                        s.clear();
                        s.append("255");
                    }
                }
            }
        });

        seekBar = findViewById(R.id.lock_percent_seekbar);
        seekBar.setProgress(lockPoint.lock / 10);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                lockPercent.setText(String.format(Locale.ENGLISH, "%d%%", (progress * 10)));
                lockPoint.lock = (byte)(progress * 10);
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
