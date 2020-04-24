package com.kong.openhaldex;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

public class ManageModes extends AppCompatActivity {

    Mode new_mode = new Mode();
    ArrayList<Mode> ModeList;
    LinearLayout lockpoint_container;
    EditText editText;
    boolean allow_overwrite = false;
    Mode existingMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Add new mode");
        setContentView(R.layout.activity_manage_modes);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        editText = findViewById(R.id.add_mode_name_text);
        lockpoint_container = findViewById(R.id.lockpoint_container);

        if (getIntent().getIntExtra("request_code", 0) == 1){
            allow_overwrite = true;
            existingMode = (Mode)(getIntent().getSerializableExtra("existingMode"));
            editText.setText(existingMode.name);
            for (LockPoint lockPoint :
                    existingMode.lockPoints) {
                LockpointView lockpointView = new LockpointView(this, lockPoint);
                lockpoint_container.addView(lockpointView, lockpoint_container.getChildCount() - 1);
                lockpointView.onFinishInflate();
            }
            lockpoint_container.removeViewAt(0);
        }else {
            allow_overwrite = false;
        }

        ModeList = (ArrayList<Mode>)(getIntent().getSerializableExtra("modeList"));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case android.R.id.home:
                onBackPressed();
                break;
        }
        return true;
    }

    @Override
    public void onBackPressed()
    {
        // Send the inputs back to MainActivity
        Intent intent = new Intent();
        Bundle b = new Bundle();
        b.putSerializable("old_mode", existingMode);
        intent.putExtras(b);
        setResult(RESULT_CANCELED, intent);
        finish();
        super.onBackPressed();
    }

    public void add_lockpoint_button_click(View view){
        if (lockpoint_container.getChildCount() > 10) {
            Toast.makeText(getApplicationContext(), "10 points maximum",Toast.LENGTH_SHORT).show();
        } else{
            LockpointView lockpointView = new LockpointView(this);
            lockpointView.onFinishInflate();
            lockpoint_container.addView(lockpointView, lockpoint_container.getChildCount() - 1);
        }
    }

    public void remove_lockpoint_button_click(View view){

        if (lockpoint_container.getChildCount() > 2){
            lockpoint_container.removeViewAt(lockpoint_container.getChildCount() - 2);
        }
    }

    public void add_mode_save_button_click(View view) {

        // Validate user inputs
        // Name
        String new_mode_name = editText.getText().toString();
        boolean name_exists = false;
        for (Mode m : ModeList) {
            if (m.name.equals(new_mode_name)) {
                name_exists = true;
                break;
            }
        }

        if (name_exists && !allow_overwrite){
            Toast.makeText(getApplicationContext(),String.format("Mode '%s' already exists!", new_mode_name),Toast.LENGTH_SHORT).show();
            return;
        }else if (new_mode_name.equals("")){
            Toast.makeText(getApplicationContext(),"Name must not be blank!",Toast.LENGTH_SHORT).show();
            return;
        }

        new_mode.name = new_mode_name;
        new_mode.editable = true;

        for (int i = 0; i < (lockpoint_container.getChildCount() - 1); i++){
            new_mode.lockPoints.add(((LockpointView)(lockpoint_container.getChildAt(i))).lockPoint);
        }

        // Send the inputs back to MainActivity
        Intent intent = new Intent();
        Bundle b = new Bundle();
        b.putSerializable("new_mode", new_mode);
        b.putSerializable("old_mode", existingMode);
        intent.putExtras(b);
        setResult(RESULT_OK, intent);
        finish();
    }
}