package com.iocaster.mynetmonitor;

import android.content.Context;
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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private NetMonitor mNetMon;

    public int mPlayerState = 0;
    public static final int PLAYER_STATE_IDLE = 0;
    public static final int PLAYER_STATE_INITIALIZED = 1;
    public static final int PLAYER_STATE_PREPARING = 2;
    public static final int PLAYER_STATE_PREPARED = 3;
    public static final int PLAYER_STATE_STARTED = 4;
    public static final int PLAYER_STATE_STOPPED = 5;
    public static final int PLAYER_STATE_PAUSED = 6;
    public static final int PLAYER_STATE_PLAYBACK_COMPLETED = 7;


    public static final int MSG_NOTICE_NEW_NETWORK = 1;
    public static final int MSG_UPDATE_LOG_TXT = 2;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            //super.handleMessage(msg);
            switch(msg.what) {
                case MSG_NOTICE_NEW_NETWORK :
                    Network activeNetwork = mNetMon.getActiveNetwork();
                    mNetMon.printAllInetAddress(activeNetwork);
                    NetworkInfo ni = mNetMon.getNetworkInfo(activeNetwork);
                    int netType = ni.getType();

                    if(activeNetwork != null) {
                        tvActiveNetworkId.setText(activeNetwork.toString());
                        switch(netType) {
                            case ConnectivityManager.TYPE_WIFI:
                                String wifiIp = mNetMon.getWifiIPAddress();
                                tvActiveNetworkId.setText( tvActiveNetworkId.getText() + " ip = " + wifiIp);
                                break;
                            case ConnectivityManager.TYPE_MOBILE:
                                String mobileIp = mNetMon.getMobileIPAddress();
                                tvActiveNetworkId.setText( tvActiveNetworkId.getText() + " ip = " + mobileIp);
                                break;
                            case ConnectivityManager.TYPE_ETHERNET:
                                break;
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


    TextView tvActiveNetworkId;
    TextView tvLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvActiveNetworkId = findViewById(R.id.textView4ActiveNetworkId);
        tvLog = findViewById(R.id.textView4Log);

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
                    case NetMonitor.MY_NET_TYPE_UNKNOWN:
                        Log.d(TAG, "--> onValidatedNetwork() : NetMonitor.MY_NET_TYPE_UNKNOWN");
                        break;
                    case NetMonitor.MY_NET_TYPE_WIFI:
                        Log.d(TAG, "--> onValidatedNetwork() : NetMonitor.MY_NET_TYPE_WIFI");
                        break;
                    case NetMonitor.MY_NET_TYPE_MOBILE:
                        Log.d(TAG, "--> onValidatedNetwork() : NetMonitor.MY_NET_TYPE_MOBILE");
                        break;
                }
                Message msg = new Message();
                msg.what = MSG_NOTICE_NEW_NETWORK;
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
                    case NetMonitor.MY_NET_TYPE_UNKNOWN:
                        Log.d(TAG, "--> onLostNetwork() : NetMonitor.MY_NET_TYPE_UNKNOWN");
                        break;
                    case NetMonitor.MY_NET_TYPE_WIFI:
                        Log.d(TAG, "--> onLostNetwork() : NetMonitor.MY_NET_TYPE_WIFI");
                        String wifiIp = mNetMon.getWifiIPAddress();
                        Log.d(TAG, "WiFi IP = " + wifiIp);
                        break;
                    case NetMonitor.MY_NET_TYPE_MOBILE:
                        Log.d(TAG, "--> onLostNetwork() : NetMonitor.MY_NET_TYPE_MOBILE");
                        String mobileIp = mNetMon.getMobileIPAddress();
                        Log.d(TAG, "Mobile IP = " + mobileIp);
                        break;
                }

                String str = new String("--> onLostNetwork() : New NetworkID = " + network + " netType = " + mNetMon.getNetTypeName(netType) );
                Message msg2 = new Message();
                msg2.what = MSG_UPDATE_LOG_TXT;
                msg2.obj = str;
                mHandler.sendMessage( msg2 );
            }
        });
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
        }
    }

    //This function is called by buttons of activity_main.xml.
    public void onClickButton( View view ) {
        switch( view.getId()) {
            case R.id.button4GetActiveNetwork: //getActiveNetwork
                Network activeNetwork = mNetMon.getActiveNetwork();
                mNetMon.printAllInetAddress(activeNetwork);

                if(activeNetwork != null)
                    tvActiveNetworkId.setText( activeNetwork.toString() );
                else
                    tvActiveNetworkId.setText( "No active network !!!" );
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
