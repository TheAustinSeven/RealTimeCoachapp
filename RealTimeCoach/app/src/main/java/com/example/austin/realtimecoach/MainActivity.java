package com.example.austin.realtimecoach;

import android.app.Activity;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.bluetooth.BluetoothServerSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import android.app.ListActivity;
import android.widget.ListView;
import android.widget.Toast;
import java.util.Timer;
import java.util.TimerTask;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MainActivity extends ListActivity {
    /* Get Default Adapter */


    private static BluetoothAdapter  _bluetooth = BluetoothAdapter.getDefaultAdapter();
    public static ArrayList<Player> players = new ArrayList<>();
    /* request BT enable */
    private static final int  REQUEST_ENABLE      = 0x1;
    /* request BT discover */
    private static final int  REQUEST_DISCOVERABLE  = 0x2;
    static BluetoothDevice device;
    static ArrayList<Short> xbees=new ArrayList<>();
    static Menu menu;
    static MenuItem item;
    static MenuItem item2;
    boolean iteme=false;
    boolean item2e=false;
    private static final String TAG = MainActivity.class.getSimpleName();
    static boolean checker=false;
    static BluetoothSocket socket;
    static OutputStream output;
    static InputStream input;
    static Thread workerThread;
    static byte[] readBuffer;
    static int readBufferPosition;
    static int counter;
    static volatile boolean stopWorker;
    static short[][] data;
    private Handler _handler = new Handler();
    static Player change;
    static boolean breaker=true;
    static boolean menu_created=false;
    static Thread xbeeFinder;
    static String[] dataCatcher=new String[1024];
    static int index=0;
    static boolean first=true;
    static int spot=0;
    static int numAddresses;
    static int accessIndex=0;
    static int crossaccessIndex = 0;
    static Thread connector;
    static Thread updater;
    static byte leftover;
    static ByteBuffer buffer;
    static Thread dataUpdater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
    @Override
    protected void onResume() {
        super.onResume();
        connector = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    establishConnection();
                    first = false;
                    item2e = true;
                }
                catch(Exception e) {
                    e.printStackTrace();
                }
            }
        });
        beginListenForData();
        updater = new Thread(new Runnable() {
            @Override
            public void run() {
                    try {
                        while (true) {
                            updateData();
                        }
                    }
                    catch(Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        if(menu_created) {
            runOnUiThread(new Thread() {
                public void run() {
                    try {
                        while (true) {
                            showPlayers();
                            Thread.sleep(1000);
                        }
                    }
                    catch (InterruptedException e) {
                        Thread.interrupted();
                    }
                }
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    public void onEnableButtonClicked()
    {
        //Intent enabler = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        //startActivityForResult(enabler, REQUEST_ENABLE);
        //enable

        if(isBluetoothEnabled())
        {
            try
            {
                closeBT();
                first=true;
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
            _bluetooth.disable();
            iteme=false;
            checker=false;
            item2e=false;
        }
        else
        {
            _bluetooth.enable();
            Intent enabler = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enabler, 0);
            iteme=true;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        menu_created=true;
        return true;

    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
        if(isBluetoothEnabled())
        {
            iteme=true;
        }
        else
        {
            iteme=false;
        }
        item = menu.findItem(R.id.action_add_BT);
        item.setVisible(iteme);
        item2 = menu.findItem(R.id.action_add_player);
        item2.setVisible(item2e);
        super.onPrepareOptionsMenu(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        //noinspection SimplifiableIfStatement
        switch (item.getItemId())
        {
            case R.id.action_add_BT:
                Intent enabler = new Intent(this, DiscoveryActivity.class);
                startActivity(enabler);
                return true;
            case R.id.action_toggle_BT:
                onEnableButtonClicked();
                return true;
            case R.id.action_add_player:
                Intent addPlayer = new Intent(this, AddPlayerActivity.class);
                startActivity(addPlayer);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }

        //return super.onOptionsItemSelected(item);
    }


    protected void onListItemClick(ListView l, View v, int position, long id)
    {
        change = players.get(position);
        spot=0;
        Intent editor = new Intent(this, UpdatePlayerActivity.class);
        startActivity(editor);
    }

    public static void addPlayer(Player player)
    {
        players.add(player);
    }

    public boolean isBluetoothEnabled()
    {
        return _bluetooth.isEnabled();
    }

    public static void establishConnection() throws IOException
    {
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //Standard SerialPortService ID
        socket = device.createRfcommSocketToServiceRecord(uuid);
        socket.connect();
        output = socket.getOutputStream();
        input = socket.getInputStream();

    }
    static void beginListenForData()
    {
        final Handler handler = new Handler();
        final byte delimiter = 59;
        final byte stopper = 33;

        data = new short[numAddresses][10];
        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        workerThread = new Thread(new Runnable()
        {
            public void run()
            {
                while(!Thread.currentThread().isInterrupted() && !stopWorker)
                {
                    boolean isleftover = false;

                    ByteBuffer bb;
                    try
                    {
                        int bytesAvailable = input.available();
                        if(bytesAvailable > 0)
                        {
                            byte[] packetBytes = new byte[bytesAvailable];
                            counter=input.read(packetBytes);
                            for(int i=0;i<counter;i++)
                            {
                                try
                                {
                                    byte b = packetBytes[i];
                                    if (b == delimiter) {
                                        byte[] encodedBytes;
                                        if(isleftover) {
                                            encodedBytes = new byte[readBufferPosition+1];
                                            System.arraycopy(readBuffer, 0, encodedBytes, 1, encodedBytes.length - 1);
                                            encodedBytes[0] = leftover;
                                        }
                                        else {
                                            encodedBytes = new byte[readBufferPosition];
                                            System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                        }
                                        synchronized(data){
                                            if(encodedBytes.length % 2 == 1) {
                                                leftover = encodedBytes[encodedBytes.length-1];
                                                isleftover = true;
                                            }
                                            for(int j = crossaccessIndex; j < encodedBytes.length; j = j + 2) {
                                                bb = ByteBuffer.allocate(2);
                                                bb.order(ByteOrder.LITTLE_ENDIAN);
                                                bb.put(encodedBytes[j]);
                                                bb.put(encodedBytes[j+1]);
                                                data[accessIndex][j] = bb.getShort(0);
                                                if(crossaccessIndex == 9) {
                                                    crossaccessIndex = 0;
                                                    if(accessIndex == numAddresses-1){
                                                        accessIndex = 0;
                                                    }
                                                    else{
                                                        accessIndex++;
                                                    }
                                                }
                                                else {
                                                    crossaccessIndex++;
                                                }
                                            }

                                        }

                                        readBufferPosition = 0;

                                        index++;
                                    }
                                    else if (b==stopper) {
                                        stopWorker=true;
                                    }
                                    else
                                    {
                                        readBuffer[readBufferPosition++] = b;
                                    }
                                }
                                catch(ArrayIndexOutOfBoundsException e)
                                {
                                    e.printStackTrace();
                            }
                        }
                        }
                    }
                    catch (IOException ex)
                    {
                        stopWorker = true;
                    }
                }
            }
        });

        workerThread.start();
    }

    public static void updateData() {
        dataUpdater = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true)
                {
                    if (!(data == null)) {
                        synchronized (data){
                            for(short[] s:data){

                            }
                        }
                        //TODO: parse the data into meaningful bites so that it can be passed to the player objects without issue.
                    } else {

                    }
                }
            }
        });dataUpdater.start();
    }
    /*
    public static void obtain_valid_addresses()
    {
        xbees=new ArrayList<>();

        xbeeFinder = new Thread(new Runnable() {
            public void run() {
                try {
                    int lastCut=0;
                    Timer timer = new Timer();
                    sendData("!!!");
                    beginListenForData();
                    while (data.length() == 0 && breaker) {
                        timer.schedule(new Ender(), 1000);
                    }
                    timer.cancel();
                    for (int i = 0; i < data.length(); i++) {
                        if (data.charAt(i) == ',') {
                            xbees.add(Integer.parseInt(data.substring(lastCut + 1, i)));
                            lastCut = i;
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }
    */

    public static boolean isValidAddress(int x)
    {
        boolean check=false;
        for(Short value:xbees)
        {
            if(value.intValue()==x)
            {
                check=true;
            }
        }
        breaker=true;
        return check;
    }

    void closeBT() throws IOException
    {
        stopWorker = true;
        output.close();
        input.close();
        socket.close();
        Context context = getApplicationContext();
        CharSequence text = "Bluetooth Closed.";
        int duration = Toast.LENGTH_SHORT;

        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    protected boolean showPlayers()
    {
        Collections.sort(players);
        Log.d("EF-BTBee", ">>showDevices");
        final UserItemAdapter adapter = new UserItemAdapter(this, R.layout.rtc_line_layout, players);
        _handler.post(new Runnable() {
            public void run()
            {
                setListAdapter(adapter);
            }
        });
        return true;
    }

    static void sendData(short data) throws IOException
    {
        buffer = ByteBuffer.allocate(2);
        buffer.putShort(data);
        output.write(buffer.array());
    }

    static class Ender extends TimerTask {
        public void run() {
            breaker=false;
        }
    }
}