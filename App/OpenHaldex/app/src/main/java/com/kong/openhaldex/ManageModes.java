package com.kong.openhaldex;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

public class ManageModes extends AppCompatActivity {

    Mode new_mode = new Mode();
    ArrayList<Mode> ModeList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_modes);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        ModeList = (ArrayList<Mode>)getIntent().getSerializableExtra("modeList");
    }

    public void add_mode_save_button_click(View view) {

        // Validate user inputs
        // Name
        EditText editText = findViewById(R.id.add_mode_name_text);
        String new_mode_name = editText.getText().toString();
        boolean name_exists = false;
        for (Mode m : ModeList) {
            if (m.name.equals(new_mode_name)) {
                name_exists = true;
                break;
            }
        }

        if (name_exists){
            Toast.makeText(getApplicationContext(),String.format("Mode '%s' already exists!", new_mode_name),Toast.LENGTH_SHORT).show();
            return;
        }else if (new_mode_name.equals("")){
            Toast.makeText(getApplicationContext(),"Name must not be blank!",Toast.LENGTH_SHORT).show();
            return;
        }

        new_mode.name = new_mode_name;
        new_mode.editable = true;

        // Send the inputs back to MainActivity
        Intent intent = new Intent();
        Bundle b = new Bundle();
        b.putSerializable("mode", new_mode);
        intent.putExtras(b);
        setResult(RESULT_OK, intent);
        finish();
    }
}