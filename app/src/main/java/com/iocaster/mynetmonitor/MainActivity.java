package com.iocaster.mynetmonitor;

import android.content.Context;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private NetMonitor mNetMon;
    private boolean mMobileActive = false;
    private boolean mWifiActive = false;
    private boolean mEthernetActive = false;

//    public int mPlayerState = 0;
//    public static final int PLAYER_STATE_IDLE = 0;
//    public static final int PLAYER_STATE_INITIALIZED = 1;
//    public static final int PLAYER_STATE_PREPARING = 2;
//    public static final int PLAYER_STATE_PREPARED = 3;
//    public static final int PLAYER_STATE_STARTED = 4;
//    public static final int PLAYER_STATE_STOPPED = 5;
//    public static final int PLAYER_STATE_PAUSED = 6;
//    public static final int PLAYER_STATE_PLAYBACK_COMPLETED = 7;


    public static final int MSG_NOTICE_NEW_NETWORK = 1;
    public static final int MSG_UPDATE_LOG_TXT = 2;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            //super.handleMessage(msg);
            switch(msg.what) {
                case MSG_NOTICE_NEW_NETWORK :
                    Network activeNetwork = mNetMon.getActiveNetwork();
                    //mNetMon.printAllInetAddress(activeNetwork);   //Don't enable this line or App will be blocked if internet isn't available
                    mNetMon.printAllInterfaceIP();
                    NetworkInfo ni = mNetMon.getNetworkInfo(activeNetwork);
                    int netType = ni.getType();

                    if(activeNetwork != null) {
                        tvActiveNetworkId.setText( "network = " + activeNetwork.toString());
                        switch(netType) {
                            case ConnectivityManager.TYPE_WIFI:
                                mWifiActive = true;
                                List<String> wifiIps = mNetMon.getWifiIPAddress();
                                //String wifiIp = mNetMon.getIPAddress("wlan0");
                                for( int i=0; i<wifiIps.size(); i++ ) {
                                    tvActiveNetworkId.setText(tvActiveNetworkId.getText() + "\n ip = " + wifiIps.get(i) );
                                }
                                break;
                            case ConnectivityManager.TYPE_MOBILE:
                                mMobileActive = true;
                                List<String> mobileIps = mNetMon.getMobileIPAddress("rmnet_data1");
                                //String mobileIp = mNetMon.getIPAddress("dummy0");
                                for( int i=0; i<mobileIps.size(); i++ ) {
                                    tvActiveNetworkId.setText(tvActiveNetworkId.getText() + "\n ip = " + mobileIps.get(i) );
                                }
                                break;
                            case ConnectivityManager.TYPE_ETHERNET:
                                mEthernetActive = true;
                                String ethIp = mNetMon.getIPAddress("eth0");
                                tvActiveNetworkId.setText( tvActiveNetworkId.getText() + ", ip = " + ethIp+ " (ethernet)");
                                break;
                        }

                        if( msg.obj.equals(activeNetwork) ) {
                            //check internet is alive
                            boolean isInetAlive = mNetMon.isInternetAvailable();
                            if( isInetAlive )
                                setInternetColorOn();
                            else
                                setInternetColorOff();
                            appendLogText("\nInternet Alive("+ isInetAlive + ") on Network " + msg.obj + " ...\n");
                        }
                    } else {
                        tvActiveNetworkId.setText("No active network !!!");
                    }
                    break;
                case MSG_UPDATE_LOG_TXT :
                    String logStr = (String) msg.obj;
                    appendLogText( logStr );
                    break;
            }
        }
    };



    ImageView ivWifi, ivInternet, ivData;
    TextView tvActiveNetworkId;
    TextView tvLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ivWifi = findViewById(R.id.iconWifi);
        ivInternet = findViewById(R.id.iconInternet);
        ivData = findViewById(R.id.iconData);

        tvActiveNetworkId = findViewById(R.id.textView4ActiveNetworkId);
        tvLog = findViewById(R.id.textView4Log);

        setWifiColorOff();
        setInternetColorOff();
        setDataColorOff();

        //enable scrollable with android:scrollbars="vertical"
        tvLog.setMovementMethod(new ScrollingMovementMethod());

        if( mNetMon == null ) {
            mNetMon = new NetMonitor(this);
            setupNetMonCallback();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if( mNetMon != null ) mNetMon.release();
    }

    private void setupNetMonCallback() {
        mNetMon.setCallback( new NetMonitor.Callback() {
            @Override
            public void onValidatedNetwork(int netType, Network network) {
                switch( netType ) {
                    case NetMonitor.NET_TYPE_UNKNOWN:
                        Log.d(TAG, "--> onValidatedNetwork() : NetMonitor.NET_TYPE_UNKNOWN");
                        break;
                    case NetMonitor.NET_TYPE_WIFI:
                        setWifiColorOn();
                        Log.d(TAG, "--> onValidatedNetwork() : NetMonitor.NET_TYPE_WIFI");
                        break;
                    case NetMonitor.NET_TYPE_MOBILE:
                        setDataColorOn();
                        Log.d(TAG, "--> onValidatedNetwork() : NetMonitor.NET_TYPE_MOBILE");
                        break;
                }
                Message msg = new Message();
                msg.what = MSG_NOTICE_NEW_NETWORK;
                msg.obj = network;
                mHandler.sendMessage( msg );

                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo ni = cm.getNetworkInfo(network);
                String str = new String("--> onValidatedNetwork() : New NetworkID = " + network + " info = " + ni);
                Message msg2 = new Message();
                msg2.what = MSG_UPDATE_LOG_TXT;
                msg2.obj = str;
                mHandler.sendMessage( msg2 );
            }

            @Override
            public void onLostNetwork(int netType, Network network) {
                switch( netType ) {
                    case NetMonitor.NET_TYPE_UNKNOWN:
                        Log.d(TAG, "--> onLostNetwork() : NetMonitor.NET_TYPE_UNKNOWN");
                        break;
                    case NetMonitor.NET_TYPE_WIFI:
                        mWifiActive = false;
                        setWifiColorOff();
                        Log.d(TAG, "--> onLostNetwork() : NetMonitor.NET_TYPE_WIFI");
                        List<String> wifiIps = mNetMon.getWifiIPAddress();
                        for( int i=0; i<wifiIps.size(); i++ ) {
                            Log.d(TAG, "WiFi IP = " + wifiIps.get(i));
                        }
                        break;
                    case NetMonitor.NET_TYPE_MOBILE:
                        mMobileActive = false;
                        setDataColorOff();
                        Log.d(TAG, "--> onLostNetwork() : NetMonitor.NET_TYPE_MOBILE");
                        List<String> mobileIps = mNetMon.getMobileIPAddress("rmnet_data1");
                        for( int i=0; i<mobileIps.size(); i++ ) {
                            Log.d(TAG, "Mobile IP = " + mobileIps.get(i));
                        }
                        break;
                }

                if( !mMobileActive && !mWifiActive && !mEthernetActive ) {
                    setInternetColorOff();
                }

                String str = new String("--> onLostNetwork() : New NetworkID = " + network + " netType = " + mNetMon.getNetTypeName(netType) );
                Message msg2 = new Message();
                msg2.what = MSG_UPDATE_LOG_TXT;
                msg2.obj = str;
                mHandler.sendMessage( msg2 );
            }
        });
    }

    private void setWifiColorOff() {
        ivWifi.setBackgroundColor(Color.rgb(150, 150, 150));    //light gray
    }
    private void setWifiColorOn() {
        ivWifi.setBackgroundColor(Color.rgb(0, 255, 0));    //green
    }

    private void setInternetColorOff() {
        ivInternet.setBackgroundColor(Color.rgb(255, 0, 0));    //red
    }
    private void setInternetColorOn() {
        ivInternet.setBackgroundColor(Color.rgb(0, 255, 0));    //green
    }

    private void setDataColorOff() {
        ivData.setBackgroundColor(Color.rgb(150, 150, 150));    //light gray
    }
    private void setDataColorOn() {
        ivData.setBackgroundColor(Color.rgb(0, 255, 0));    //green
    }

    private void setLogText(String logString ) {
        tvLog.setText("");
        String[] strs = logString.split(",");
        for( String str : strs ) {
            tvLog.setText( tvLog.getText() + "\n" + str );
        }
    }

    private void appendLogText(String logString ) {
        //tvLog.setText("");
        String[] strs = logString.split(",");
        for( String str : strs ) {
            tvLog.setText( tvLog.getText() + "\n" + str );
            tvLog.invalidate();
        }
    }

    //This function is called by buttons of activity_main.xml.
    public void onClickButton( View view ) {
        switch( view.getId()) {
            case R.id.button4GetActiveNetwork: //getActiveNetwork
//                Network activeNetwork = mNetMon.getActiveNetwork();
//                mNetMon.printAllInetAddress(activeNetwork);
//
//                if(activeNetwork != null)
//                    tvActiveNetworkId.setText( activeNetwork.toString() );
//                else
//                    tvActiveNetworkId.setText( "No active network !!!" );
                Message msg = new Message();
                msg.what = MSG_NOTICE_NEW_NETWORK;
                msg.obj = mNetMon.getActiveNetwork();
                mHandler.sendMessage( msg );
                break;

            case R.id.button4ClearLog:
                tvLog.setText("");  //clear log window
                break;
        }
    }

    //The socket APIs should be called in a thread not in the main UI thread
    // or android.os.NetworkOnMainThreadException
    private void tryNetwork2(Network network) {
        if(network == null) return;

        final Network thisNet = network;
        new Thread() {
            public void run() {
                String host = "www.google.com";
                Socket socket = null;
                try {
                    socket = thisNet.getSocketFactory().createSocket();
                    socket.connect(new InetSocketAddress(host, 443), 10000);
                    Log.i(TAG, "--> tryNetwork2() : Validated " + thisNet + " " + " host=" + host);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                        cm.reportNetworkConnectivity(thisNet, true);
                        Log.i(TAG, "--> tryNetwork2() : Reported " + thisNet);
                    }
                } catch (IOException ex) {
                    Log.e(TAG, ex.toString());
                    Log.i(TAG, "--> tryNetwork2() : No connectivity " + thisNet);
                } finally {
                    if (socket != null)
                        try {
                            socket.close();
                        } catch (IOException ex) {
                            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                        }
                }
            }
        }.start();
    }


}
