package com.example.ekb2011.locationmap;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    public final static int DEVICES_DIALOG = 1;
    public final static int ERROR_DIALOG = 2;
    public final static int QUIT_DIALOG = 3;
    public final int REQUEST_BLUETOOTH_ENABLE = 100;
    public static Context mContext;
    public AppCompatActivity activity;

    static BluetoothAdapter mBluetoothAdapter;
    BluetoothSocketWrapper mmSocket;
    BluetoothDevice mmDevice;
    InputStream mmInputStream;
    Thread workerThread = null;
    byte[] readBuffer;
    int readBufferPosition;
    volatile boolean stopWorker;
    static boolean isConnectionError = false;

    Intent intt=getIntent();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this;
        activity = this;

/*        final ImageButton imageButton2=(ImageButton)findViewById(R.id.imageButton2);
        imageButton2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent contact_intent=new Intent(MainActivity.this, GetContacts.class);
                MainActivity.this.startActivity(contact_intent);


            }
        });*/



        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            ErrorDialog("This device is not implement Bluetooth.");
            return;
        }
        if (!mBluetoothAdapter.isEnabled()) {
            Intent bluetooth_intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(bluetooth_intent, REQUEST_BLUETOOTH_ENABLE);
            return;
        } else {
            DeviceDialog();
        }

    }

    static public Set<BluetoothDevice> getPairedDevices() {
        return mBluetoothAdapter.getBondedDevices();
    }

    //뒤로가기 버튼 눌렀을 때 종료
    @Override
    public void onBackPressed() {
        doClose();
        super.onBackPressed();
    }

    //13. 백버튼이 눌러지거나, ConnectTask에서 예외발생시
    //데이터 수신을 위한 스레드를 종료시키고 CloseTask를 실행하여 입출력 스트림을 닫고,
    //소켓을 닫아 통신을 종료합니다.
    public void doClose() {
        if (workerThread != null) {
            workerThread.interrupt();
        }
        new CloseTask().execute();
        Log.e("BT", "close socket");
    }

    public void doConnect(BluetoothDevice device) {
        mmDevice = device;
        //Standard SerialPortService ID
        //블루투스 수신 센서 HC-06의 고유번호(UUID)
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

        try {
            // 4. 지정한 블루투스 장치에 대한 특정 UUID 서비스를 하기 위한 소켓을 생성합니다.
            // 여기선 시리얼 통신을 위한 UUID를 지정하고 있습니다.
            BluetoothSocket tmp;
            tmp = mmDevice.createRfcommSocketToServiceRecord(uuid);  //secure 모드 경우

            mmSocket = new NativeBluetoothSocket(tmp);
            // 5. 블루투스 장치 검색을 중단합니다.
            mBluetoothAdapter.cancelDiscovery();
            // 6. ConnectTask를 시작합니다.
            new ConnectTask().execute();
        } catch (IOException e) {
            Log.e("BT", e.toString(), e);
            ErrorDialog("Connect? " + e.toString());
        }
    }

    public class ConnectTask extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected void onPreExecute() {
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                //7. 블루투스 장치로 연결을 시도합니다.
                mmSocket.connect();
                //8. 소켓에 대한 출력 스트림을 가져옵니다.
                mmInputStream = mmSocket.getInputStream();
                //9. 데이터 수신을 대기하기 위한 스레드를 생성하여 입력스트림로부터의 데이터를 대기하다가
                //   들어오기 시작하면 버퍼에 저장합니다.
                //  '\n' 문자가 들어오면 지금까지 버퍼에 저장한 데이터를 UI에 출력하기 위해 핸들러를 사용합니다.
                beginListenForData();

            } catch (Throwable t) {
                Log.e("BT", "connect? " + t.getMessage());
                //try the fallback
                try {
                    mmSocket = new FallbackBluetoothSocket(mmSocket.getUnderlyingSocket());
                    Thread.sleep(500);
                    //재접속을 시도합니다.
                    mmSocket.connect();
                    //소켓에 대한 출력 스트림을 가져옵니다.
                    mmInputStream = mmSocket.getInputStream();
                    //데이터 수신을 대기하기 위한 스레드를 생성하여 입력스트림로부터의 데이터를 대기하다가
                    //들어오기 시작하면 버퍼에 저장합니다.
                    //'\n' 문자가 들어오면 지금까지 버퍼에 저장한 데이터를 UI에 출력하기 위해 핸들러를 사용합니다.
                    beginListenForData();
                    return null;
                } catch (FallbackException e1) {
                    Log.e("BT", "Could not initialize FallbackBluetoothSocket classes.", e1);
                    return false;
                } catch (InterruptedException e1) {
                    Log.e("BT", e1.getMessage(), e1);
                    return false;
                } catch (IOException e1) {
                    //재접속 실패한 경우...
                    Log.e("BT", "Fallback failed. Cancelling.", e1);
                    return false;
                }
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean isSucess) {
            if (!isSucess) {
                Log.e("BT", "Failed to connect to device");
                isConnectionError = true;
                ErrorDialog("Failed to connect to device");
            }
        }
    }

    public class CloseTask extends AsyncTask<Void, Void, Object> {
        @Override
        protected Object doInBackground(Void... params) {
            try {
                try {
                    mmInputStream.close();
                } catch (Throwable t) {
                    /*ignore*/
                }
                mmSocket.close();
            } catch (Throwable t) {
                return t;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Object result) {
            if (result instanceof Throwable) {
                Log.e("BT", result.toString(), (Throwable) result);
                ErrorDialog(result.toString());
            }
        }
    }

    public void DeviceDialog() {
        if (activity.isFinishing()) {
            return;
        }
        FragmentManager fm = MainActivity.this.getSupportFragmentManager();
        MyDialogFragment alertDialog = MyDialogFragment.newInstance(DEVICES_DIALOG, "");
        alertDialog.show(fm, "");
    }

    public void ErrorDialog(String text) {
        if (activity.isFinishing()) {
            return;
        }
        FragmentManager fm = MainActivity.this.getSupportFragmentManager();
        MyDialogFragment alertDialog = MyDialogFragment.newInstance(ERROR_DIALOG, text);
        alertDialog.show(fm, "");
    }


    public void QuitDialog(String text) {
        if (activity.isFinishing()) {
            return;
        }
        FragmentManager fm = MainActivity.this.getSupportFragmentManager();
        MyDialogFragment alertDialog = MyDialogFragment.newInstance(QUIT_DIALOG, text);
        alertDialog.show(fm, "");
    }


    void beginListenForData() {
        final Handler handler = new Handler(Looper.getMainLooper());
        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        workerThread = new Thread(new Runnable() {
            public void run() {
                while (!Thread.currentThread().isInterrupted() && !stopWorker) {
                    try {
                        int bytesAvailable = mmInputStream.available();
                        if (bytesAvailable > 0) {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for (int i = 0; i < bytesAvailable; i++) {
                                byte b = packetBytes[i];
                                if (b == '\n') {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "euc-kr");
                                    readBufferPosition = 0;
                                    handler.post(new Runnable() {
                                        public void run() {
                                            if (!data.isEmpty()) {
/*                                                AlertDialog.Builder builder=new AlertDialog.Builder(MainActivity.this);
                                                builder.setTitle("메시지 전송 여부")
                                                        .setMessage("사고 메시지를 전송하시겠습니까?")
                                                        .setCancelable(true)
                                                        .setPositiveButton("예", new DialogInterface.OnClickListener(){
                                                            @Override
                                                            public void onClick(DialogInterface dialog, int which) {
                                                                startLocationService();
                                                                finish();
                                                            }
                                                        })
                                                        .setNegativeButton("아니오", new DialogInterface.OnClickListener(){
                                                            @Override
                                                            public void onClick(DialogInterface dialog, int which) {
                                                                finish();
                                                            }
                                                        });
                                                try{
                                                    Thread.sleep(10000);
                                                }catch (InterruptedException ex){}*/
                                                startLocationService();
                                                finish();
                                            }
                                        }
                                    });
                                } else {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    } catch (IOException ex) {
                        stopWorker = true;
                    }
                }
            }
        });
        workerThread.start();
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == REQUEST_BLUETOOTH_ENABLE) {
            if (resultCode == RESULT_OK) {
                //BlueTooth is now Enabled
                DeviceDialog();
            }
            if (resultCode == RESULT_CANCELED) {
                QuitDialog("You need to enable bluetooth");
            }
        }
    }

    public interface BluetoothSocketWrapper {
        InputStream getInputStream() throws IOException;

        void connect() throws IOException;

        void close() throws IOException;

        BluetoothSocket getUnderlyingSocket();
    }


    public static class NativeBluetoothSocket implements BluetoothSocketWrapper {
        public BluetoothSocket socket;

        public NativeBluetoothSocket(BluetoothSocket tmp) {
            this.socket = tmp;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return socket.getInputStream();
        }

        @Override
        public void connect() throws IOException {
            socket.connect();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }

        @Override
        public BluetoothSocket getUnderlyingSocket() {
            return socket;
        }

    }

    public class FallbackBluetoothSocket extends NativeBluetoothSocket {
        public BluetoothSocket fallbackSocket;

        public FallbackBluetoothSocket(BluetoothSocket tmp) throws FallbackException {
            super(tmp);
            try {
                Class<?> clazz = tmp.getRemoteDevice().getClass();
                Class<?>[] paramTypes = new Class<?>[]{Integer.TYPE};
                Method m = clazz.getMethod("createRfcommSocket", paramTypes);
                Object[] params = new Object[]{Integer.valueOf(1)};
                fallbackSocket = (BluetoothSocket) m.invoke(tmp.getRemoteDevice(), params);
            } catch (Exception e) {
                throw new FallbackException(e);
            }
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return fallbackSocket.getInputStream();
        }

        @Override
        public void connect() throws IOException {
            fallbackSocket.connect();
        }

        @Override
        public void close() throws IOException {
            fallbackSocket.close();
        }
    }

    public static class FallbackException extends Exception {
        public static final long serialVersionUID = 1L;

        public FallbackException(Exception e) {
            super(e);
        }
    }


    /**
     * 위치 정보 확인을 위해 정의한 메소드
     */
    private void startLocationService() {
        // 위치 관리자 객체 참조
        LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        // 위치 정보를 받을 리스너 생성
        GPSListener gpsListener = new GPSListener();
        long minTime = 10000;
        float minDistance = 0;
        try {
            // GPS를 이용한 위치 요청
            manager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    minTime,
                    minDistance,
                    gpsListener);
            // 네트워크를 이용한 위치 요청
            manager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    minTime,
                    minDistance,
                    gpsListener);
            // 위치 확인이 안되는 경우에도 최근에 확인된 위치 정보 먼저 확인
            Location lastLocation = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastLocation != null) {
                Double latitude = lastLocation.getLatitude();
                Double longitude = lastLocation.getLongitude();
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setMessage("Latitude: "+latitude+"\n"+"Longitude: "+longitude+"\n")
                        .setPositiveButton("OK",null)
                        .create()
                        .show();
            }
        } catch (SecurityException ex) {
            ex.printStackTrace();
        }
    }

    private class GPSListener implements LocationListener {
        public void onLocationChanged(Location location) {

            Double latitude = location.getLatitude();
            Double longitude = location.getLongitude();

            String msg = "사고위치\n";
            msg += "https://www.google.co.kr/maps/place/" + latitude + "%20" + longitude;
/*            sendSMS(intt.getStringExtra("contact1"), msg);
            sendSMS(intt.getStringExtra("contact2"), msg);
            sendSMS(intt.getStringExtra("contact3"), msg);
            sendSMS(intt.getStringExtra("contact4"), msg);*/

            sendSMS("01041002071", msg);
            Log.i("GPSListener", msg);
            /*PHPRequest registerRequest = new PHPRequest(lat, lon, userNum, responseListener);
            RequestQueue queue = Volley.newRequestQueue(MainActivity.this);
            queue.add(registerRequest);*/
            finish();
        }

        public void onProviderDisabled(String provider) {
        }

        public void onProviderEnabled(String provider) {
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        public void sendSMS(String smsNumber, String smsText) {
            registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    switch (getResultCode()) {
                        case Activity.RESULT_OK:
                            Toast.makeText(mContext, "Sending Process has been completed", Toast.LENGTH_SHORT).show();
                            break;
                        case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                            Toast.makeText(mContext, "Sending Process Failed", Toast.LENGTH_SHORT).show();
                            break;
                        case SmsManager.RESULT_ERROR_NO_SERVICE:
                            Toast.makeText(mContext, "Not in service", Toast.LENGTH_SHORT).show();
                            break;
                        case SmsManager.RESULT_ERROR_RADIO_OFF:
                            Toast.makeText(mContext, "Radio is Turned Off", Toast.LENGTH_SHORT).show();
                            break;
                        case SmsManager.RESULT_ERROR_NULL_PDU:
                            Toast.makeText(mContext, "PDU Null", Toast.LENGTH_SHORT).show();
                            break;
                    }
                }
            }, new IntentFilter("SMS_SENT_ACTION"));
            registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    switch (getResultCode()) {
                        case Activity.RESULT_OK:
                            Toast.makeText(mContext, "Success", Toast.LENGTH_SHORT).show();
                            break;
                        case Activity.RESULT_CANCELED:
                            Toast.makeText(mContext, "Failed", Toast.LENGTH_SHORT).show();
                            break;
                    }
                }
            }, new IntentFilter("SMS_DELIVERED_ACTION"));
            SmsManager mSmsManager = SmsManager.getDefault();
            mSmsManager.sendTextMessage(smsNumber, null, smsText, null, null);
        }
    }
}