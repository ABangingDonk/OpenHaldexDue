package com.kong.openhaldex;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements DeleteModeFragment.DialogListener {

    private boolean bt_connected = false;
    private BluetoothDevice device;
    private BluetoothSocket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private final UUID PORT_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");//Serial Port Service ID
    private int selected_mode_button;
    private static char out_data[] = {0xff, 0, 0};
    private static char in_data[] = {0, 0};
    private static char haldex_lock = 0x7f;
    private static char haldex_status = 0;

    Handler rx = new Handler();
    int rx_delay = 50;
    Runnable runnable;

    public ArrayList<Mode> ModeList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        //getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_main);

        // Try to read our private XML to get our list of modes
        try{
            _getModes();
        } catch (Exception e) {
            // If something went wrong then write the builtin XML
            _create_modesXML();
            try {
                // And try to read the XML modes again
                _getModes();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        // Create the mode buttons
        _createModeButtons();
    }

    public void delete_button_click(View v){
        DeleteModeFragment deleteModeFragment = new DeleteModeFragment();
        FragmentTransaction ft;

        CharSequence[] modeNames = new CharSequence[ModeList.size()];
        for (int i = 0; i < ModeList.size(); i++){
            modeNames[i] = ModeList.get(i).name;
        }
        Bundle b = new Bundle();
        b.putCharSequenceArray("modeNames", modeNames);

        deleteModeFragment.setArguments(b);

        ft = getSupportFragmentManager().beginTransaction();
        Fragment prev = getSupportFragmentManager().findFragmentByTag("dialog");
        if (prev != null){
            ft.remove(prev);
        }
        ft.addToBackStack(null);

        deleteModeFragment.show(ft, "dialog");
    }

    public void add_button_click(View v){
        Intent intent = new Intent(this, ManageModes.class);
        Bundle b = new Bundle();
        b.putSerializable("modeList", ModeList);
        intent.putExtras(b);
        startActivityForResult(intent, 1);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode){
            case 1:
                // Handle save
                if (resultCode == RESULT_OK){
                    Mode new_mode = (Mode)data.getSerializableExtra("mode");
                    // Add new_mode to the private XML

                    Toast.makeText(getApplicationContext(),String.format("'%s' added", new_mode.name),Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    @Override
    public void onFinishEditDialog(int index) {
        Mode deletedMode = ModeList.get(index);
        if (!deletedMode.editable){
            Toast.makeText(getApplicationContext(), String.format("Mode '%s' cannot be deleted", deletedMode.name),Toast.LENGTH_SHORT).show();
        }
        else{
            Toast.makeText(getApplicationContext(), String.format("Mode '%s' has been deleted", deletedMode.name),Toast.LENGTH_SHORT).show();
        }
    }

    private View.OnClickListener modeOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mode_button_click(v);
        }
    };

    public void mode_button_click(View view){
        ToggleButton previous_selection = findViewById(selected_mode_button);
        if(selected_mode_button != view.getId())
        {
            previous_selection.setChecked(false);
            selected_mode_button = view.getId();
            //out_data[1] = (char)Integer.parseInt((String)view.getTag());
        }
        else
        {
            previous_selection.setChecked(true);
        }
    }

    private void _createModeButtons(){
        for (Mode mode:ModeList) {
            ToggleButton button = new ToggleButton(this);
            button.setId(View.generateViewId());
            button.setTextOn(mode.name);
            button.setTextOff(mode.name);
            button.setText(mode.name);
            button.setMinHeight(175);
            button.setOnClickListener(modeOnClickListener);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            button.setLayoutParams(params);
            LinearLayout mode_button_container = findViewById(R.id.mode_button_container);
            mode_button_container.addView(button);
        }
        // Select the first mode in the list
        LinearLayout mode_button_container = findViewById(R.id.mode_button_container);
        selected_mode_button = mode_button_container.getChildAt(0).getId();
        ToggleButton button = findViewById(selected_mode_button);
        button.setChecked(true);
    }

    private void _getModes() throws XmlPullParserException, IOException {
        XmlPullParserFactory pullParserFactory;

        pullParserFactory = XmlPullParserFactory.newInstance();
        XmlPullParser parser = pullParserFactory.newPullParser();

        InputStream inputStream = openFileInput("modes.xml");
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,false);
        parser.setInput(inputStream, null);

        ModeList = parseXML(parser);
    }

    private ArrayList<Mode> parseXML(XmlPullParser parser) throws XmlPullParserException, IOException {
        ArrayList<Mode> ret = null;
        int eventType = parser.getEventType();
        Mode mode = null;

        while (eventType != XmlPullParser.END_DOCUMENT){
            String element_name;
            switch (eventType){
                case XmlPullParser.START_DOCUMENT:
                    ret = new ArrayList<Mode>();
                    break;
                case XmlPullParser.START_TAG:
                    element_name = parser.getName();
                    if (element_name.equals("mode")){
                        mode = new Mode();
                        mode.editable = parser.getAttributeValue(null,"editable").equals("true");
                    } else if (mode != null){
                        if (element_name.equals("name")){
                            mode.name = parser.nextText();
                        } else if (element_name.equals("lockpoint")){
                            LockPoint lockPoint = new LockPoint();
                            lockPoint.speed=Float.parseFloat(parser.getAttributeValue(null,"speed"));
                            lockPoint.lock=Float.parseFloat(parser.getAttributeValue(null,"lock"));
                            lockPoint.intensity=Float.parseFloat(parser.getAttributeValue(null,"intensity"));
                            mode.lockPoints.add(lockPoint);
                        }
                    }
                    break;
                case XmlPullParser.END_TAG:
                    element_name = parser.getName();
                    if (element_name.equals("mode") && mode != null){
                        ret.add(mode);
                        mode = null;
                    }
            }
            eventType = parser.next();
        }
        return ret;
    }

    private void _create_modesXML(){
        try{
            AssetManager assetManager = getAssets();
            InputStream input = assetManager.open("builtin_modes.xml");
            int size = input.available();
            byte[] buffer = new byte[size];
            input.read(buffer);
            input.close();

            FileOutputStream outputStream = openFileOutput("modes.xml", Context.MODE_PRIVATE);
            outputStream.write(buffer);
            outputStream.close();
        }catch(IOException e){
            e.printStackTrace();
        }
    }



    public void connect_button_click(View view){
        ToggleButton button = findViewById(view.getId());
        if(button.isChecked())
        {
            bt_connected = startBT();
            if(!bt_connected)
            {
                button.setChecked(false);
            }
            else
            {
                rx.postDelayed(runnable = new Runnable() {
                    @Override
                    public void run() {
                        if(receiveData() > 0)
                        {
                            ProgressBar haldex_status_bar = findViewById(R.id.lock_percent_bar);
                            TextView haldex_status_label = findViewById(R.id.lock_percent_label);

                            haldex_status = in_data[0];
                            haldex_status &= 0x8;
                            haldex_lock = in_data[1];

                            haldex_status_bar.setProgress(haldex_lock);
                            if(haldex_status != 0)
                            {
                                haldex_status_label.setText(String.format("ERROR: 0x%1$02X", (int)haldex_status));
                                haldex_status_bar.setBackgroundColor(0xff888888);
                            }
                            else
                            {
                                haldex_status_label.setText(String.format(Locale.ENGLISH,"%1$02d%%", (int)haldex_lock));
                            }
                        }
                        rx.postDelayed(runnable, rx_delay);
                    }
                }, rx_delay);
            }
        }
        else
        {
            if(bt_connected && stopBT())
            {
                bt_connected = false;
            }
            rx.removeCallbacks(runnable);
        }
    }

    private int receiveData(){
        int rx_count = 0;
        try {
            while(inputStream.available() > 0){
                if(inputStream.read() == 0xff){
                    in_data[0] = (char)inputStream.read();
                    char new_lock_val = (char)inputStream.read();
                    in_data[1] = ((new_lock_val & 0x80) != 0) ? (char)((new_lock_val & 0x7f) * (100.0/70.0))
                                                              : 0;
                    rx_count += 3;
                }
                else
                {
                    rx_count++;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if(rx_count > 0){
            sendData();
        }

        return rx_count;
    }

    private void sendData(){
        try{
            for(int i = 0; i < out_data.length; i++)
            {
                outputStream.write(out_data[i]);
            }
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    private boolean startBT(){
        boolean connected = false;
        BluetoothAdapter bluetoothAdapter;

        try {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }catch (Exception e){
            e.printStackTrace();
            Toast.makeText(getApplicationContext(),"Device doesn't support Bluetooth",Toast.LENGTH_SHORT).show();
            return connected;
        }

        if(bluetoothAdapter == null){
            Toast.makeText(getApplicationContext(),"Device doesn't support Bluetooth",Toast.LENGTH_SHORT).show();
            return connected;
        }

        if(!bluetoothAdapter.isEnabled()){
            Intent enableAdapter = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableAdapter, 0);
        }

        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();

        if(bondedDevices.isEmpty()) {

            Toast.makeText(getApplicationContext(),"Please Pair the Device first",Toast.LENGTH_SHORT).show();
            return connected;

        } else {
            boolean device_found = false;
            for (BluetoothDevice iterator : bondedDevices) {
                if(iterator.getName().equals("OpenHaldex") || iterator.getAddress().equals("20:16:10:25:56:93")) {
                    device = iterator; //device is an object of type BluetoothDevice
                    device_found = true;
                    break;
                }
            }
            if(!device_found){
                Toast.makeText(getApplicationContext(),"OpenHaldex master not found",Toast.LENGTH_SHORT).show();
                return connected;
            }
        }

        try{
            socket = device.createRfcommSocketToServiceRecord(PORT_UUID);
            socket.connect();
            connected = true;
        }catch (IOException e){
            e.printStackTrace();
        }

        if(connected){
            try{
                outputStream = socket.getOutputStream();
            }catch (IOException e){
                e.printStackTrace();
            }
            try{
                inputStream = socket.getInputStream();
            }catch (IOException e){
                e.printStackTrace();
            }
        }
        return connected;
    }

    private boolean stopBT(){
        try{
            socket = device.createRfcommSocketToServiceRecord(PORT_UUID);
            inputStream.close();
            outputStream.close();
            socket.close();
        }catch (IOException e){
            e.printStackTrace();
            return false;
        }
        return true;
    }
}