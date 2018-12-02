package com.kong.openhaldex;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;
import java.lang.Integer;

public class MainActivity extends AppCompatActivity {

    private boolean bt_connected = false;
    private BluetoothDevice device;
    private BluetoothSocket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private final UUID PORT_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");//Serial Port Service ID
    private int selected_mode_button = R.id.stock_button;
    private static char out_data[] = {0xff, 0, 0};
    private static char in_data[] = {0, 0};
    private static char haldex_lock = 0x7f;
    private static char haldex_status = 0;

    Handler rx = new Handler();
    int rx_delay = 50;
    Runnable runnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        //getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_main);
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
                                haldex_status_label.setText(String.format("%1$02d%%", (int)haldex_lock));
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

    public void mode_button_click(View view){
        ToggleButton previous_selection = findViewById(selected_mode_button);
        if(selected_mode_button != view.getId())
        {
            previous_selection.setChecked(false);
            selected_mode_button = view.getId();
            out_data[1] = (char)Integer.parseInt((String)view.getTag());
        }
        else
        {
            previous_selection.setChecked(true);
        }
    }
}