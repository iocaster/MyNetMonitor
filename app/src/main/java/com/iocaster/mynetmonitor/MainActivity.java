package com.iocaster.mynetmonitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static final int MSG_UPDATE_ACTIVE_NETWORK_DETAIL = 1;
    public static final int MSG_UPDATE_LOG_TXT = 9;

    final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            //super.handleMessage(msg);
            switch(msg.what) {
                case MSG_UPDATE_ACTIVE_NETWORK_DETAIL :
                    String detailStr = (String) msg.obj;
                    setActiveNetworkDetail( detailStr );
                    break;
                case MSG_UPDATE_LOG_TXT :
                    String logStr = (String) msg.obj;
                    appendLogText( logStr );
                    break;
            }
        }
    };

    TextView tvActiveNetworkId;
    TextView tvActiveNetworkDetail;
    TextView tvLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvActiveNetworkId = findViewById(R.id.textView4ActiveNetworkId);
        tvActiveNetworkDetail = findViewById(R.id.textView4ActiveNetworkDetail);
        tvLog = findViewById(R.id.textView4Log);

        //enable scrollable with android:scrollbars="vertical"
        tvActiveNetworkDetail.setMovementMethod(new ScrollingMovementMethod());
        tvLog.setMovementMethod(new ScrollingMovementMethod());

        setNetworkMonitorCallback();
    }

    @Override
    protected void onResume() {
        super.onResume();

//        Network activeNetwork = getActiveNetwork();
//        printAllInetAddress(activeNetwork);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        cm.unregisterNetworkCallback(networkMonitorCallback);
    }

    private void setActiveNetworkDetail(String logString ) {
        tvActiveNetworkDetail.setText("");
        String[] strs = logString.split(",");
        for( String str : strs ) {
            tvActiveNetworkDetail.setText( tvActiveNetworkDetail.getText() + "\n" + str );
        }
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
                Network activeNetwork = getActiveNetwork();
                printAllInetAddress(activeNetwork);

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

    //require <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    /*
     * 로그 메세지 예:
     * --> active network = 174
     * --> inetAddresses.length = 2
     * --> inet = www.google.com/2404:6800:4004:80b::2004
     * --> inet = www.google.com/216.58.197.132
     */
    private Network getActiveNetwork() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            Network activeNet = (cm == null ? null : cm.getActiveNetwork());
            Log.d(TAG, "--> active network = " + activeNet);

            tryNetwork2(activeNet);

            return activeNet;
        }
        return null;
    }

    private void setNetworkMonitorCallback() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        cm.registerNetworkCallback(
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                        /* 더 많은 고려 대상 capaility 들 :
                         * NET_CAPABILITY_FOREGROUND
                         * NET_CAPABILITY_NOT_SUSPENDED
                         * NET_CAPABILITY_NOT_CONGESTED
                         */
                networkMonitorCallback);	//networkMonitorCallback 은 아래쪽 구현 예 참고
    }

    ConnectivityManager.NetworkCallback networkMonitorCallback = new ConnectivityManager.NetworkCallback() {
//        private Map<Network, Long> validated = new HashMap<>();

        //참고: https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/connectivity/NetworkMonitor.java

        @Override
        public void onAvailable(Network network) {
            //super.onAvailable(network);

            Log.d(TAG, "--> onAvailable() : Enter...");
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getNetworkInfo(network);
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            Log.i(TAG, "    Available network " + network + " " + ni);
            Log.i(TAG, "    Capabilities=" + capabilities);
            //checkConnectivity(network, ni, capabilities);	//--> 아래에 구현되어 있음.
            checkConnectivity2(network, ni, capabilities);	//--> 아래에 구현되어 있음.

            //setLogText("Available network " + network + " " + ni + "\n Capabilities=" + capabilities);
            String logStr = new String("\n--> onAvailable() : Available network " + network + " " + ni + "\n Capabilities=" + capabilities);
            Message msg = new Message();
            msg.what = MSG_UPDATE_ACTIVE_NETWORK_DETAIL;
            msg.obj = logStr;
            mHandler.sendMessage( msg );

            //String logStr = new String("--> onAvailable() : Available network " + network + " " + ni + "\n Capabilities=" + capabilities);
            Message msg2 = new Message();
            msg2.what = MSG_UPDATE_LOG_TXT;
            msg2.obj = logStr;
            mHandler.sendMessage( msg2 );
        }

        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
            //super.onCapabilitiesChanged(network, capabilities);

            Log.d(TAG, "--> onCapabilitiesChanged() : Enter...");
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getNetworkInfo(network);
            Log.i(TAG, "    New capabilities network " + network + " " + ni);
            Log.i(TAG, "    Capabilities=" + capabilities);
            //checkConnectivity(network, ni, capabilities);	//--> 아래에 구현되어 있음.
            checkConnectivity2(network, ni, capabilities);	//--> 아래에 구현되어 있음.

            String logStr = new String("\n--> onCapabilitiesChanged() : New capabilities network " + network + " " + ni + "\n Capabilities=" + capabilities);
            Message msg = new Message();
            msg.what = MSG_UPDATE_LOG_TXT;
            msg.obj = logStr;
            mHandler.sendMessage( msg );
        }

        @Override
        public void onLosing(Network network, int maxMsToLive) {
            //super.onLosing(network, maxMsToLive);

            Log.d(TAG, "--> onLosing() : Enter...");
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getNetworkInfo(network);
            Log.i(TAG, "    Losing network " + network + " within " + maxMsToLive + " ms " + ni);

            String logStr = new String("\n--> onLosing() : Losing network " + network + " " + ni);
            Message msg = new Message();
            msg.what = MSG_UPDATE_LOG_TXT;
            msg.obj = logStr;
            mHandler.sendMessage( msg );
        }

        @Override
        public void onLost(Network network) {
            //super.onLost(network);

            Log.d(TAG, "--> onLost() : Enter...");
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getNetworkInfo(network);
            Log.i(TAG, "    Lost network " + network + " " + ni);

            String logStr = new String("\n--> onLost() : Lost network " + network + " " + ni);
            Message msg = new Message();
            msg.what = MSG_UPDATE_LOG_TXT;
            msg.obj = logStr;
            mHandler.sendMessage( msg );

//            synchronized (validated) {
//                validated.remove(network);
//            }
        }

        @Override
        public void onUnavailable() {
            //super.onUnavailable();

            Log.d(TAG, "--> onUnavailable() : Enter...");
            Log.i(TAG, "    No networks available");

            String logStr = new String("\n--> onUnavailable() : No networks available");
            Message msg = new Message();
            msg.what = MSG_UPDATE_LOG_TXT;
            msg.obj = logStr;
            mHandler.sendMessage( msg );
        }


        @Override
        public void onBlockedStatusChanged(@NonNull Network network, boolean blocked) {
            super.onBlockedStatusChanged(network, blocked);
            Log.d(TAG, "--> onBlockedStatusChanged() : network = " + network + " blocked = " + blocked);
        }

        @Override
        public void onLinkPropertiesChanged(@NonNull Network network, @NonNull LinkProperties linkProperties) {
            super.onLinkPropertiesChanged(network, linkProperties);
            Log.d(TAG, "--> onLinkPropertiesChanged() ..." );
        }

        private void checkConnectivity2(Network network, NetworkInfo ni, NetworkCapabilities capabilities) {
            if (ni != null && capabilities != null &&
                    ni.getDetailedState() != NetworkInfo.DetailedState.SUSPENDED &&
                    ni.getDetailedState() != NetworkInfo.DetailedState.BLOCKED &&
                    ni.getDetailedState() != NetworkInfo.DetailedState.DISCONNECTED &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {

                /*
                 * NOTE : In this case - NET_CAPABILITY_VALIDATED - the network is always working.
                 *        No need to check with isNetworkAvailable().
                 */
                tryNetwork(network);
                //tryNetwork2(network);
            }
        }

        private void checkConnectivity(Network network, NetworkInfo ni, NetworkCapabilities capabilities) {
            if (ni != null && capabilities != null &&
                    ni.getDetailedState() != NetworkInfo.DetailedState.SUSPENDED &&
                    ni.getDetailedState() != NetworkInfo.DetailedState.BLOCKED &&
                    //ni.getDetailedState() != NetworkInfo.DetailedState.DISCONNECTED &&
                    ni.getDetailedState() == NetworkInfo.DetailedState.CONNECTED &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                    !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {

                /*
                 * NOTE : Not always the network is working in this case - !NET_CAPABILITY_VALIDATED -
                 *        even though isNetworkAvailable() returns true.
                 */
                boolean isNetAvailable = isNetworkAvailable2();
                Log.i(TAG, "--> isNetAvailable = " + isNetAvailable);

                tryNetwork(network);

//                synchronized (validated) {
//                    if (validated.containsKey(network) &&
//                            validated.get(network) + 20 * 1000 > new Date().getTime()) {
//                        Log.i(TAG, "--> Already validated " + network + " " + ni);
//                        return;
//                    }
//                }

                /*
                 * Validating 로그 - 실패 예:
                 * I/MainActivity: --> Validating 176 [type: WIFI[], state: CONNECTED/CONNECTED, reason: (unspecified), extra: "U+NetB1D7-HOAM2G", failover: false, available: true, roaming: false, network type: 1, apn type: , subtype: 0, VZWSubtype: 0] host=www.google.com
                 * E/MainActivity: java.net.ConnectException: failed to connect to www.google.com/2404:6800:4004:80b::2004 (port 443) from /:: (port 0) after 10000ms: connect failed: ENETUNREACH (Network is unreachable)
                 * I/MainActivity: --> No connectivity 176 [type: WIFI[], state: CONNECTED/CONNECTED, reason: (unspecified), extra: "U+NetB1D7-HOAM2G", failover: false, available: true, roaming: false, network type: 1, apn type: , subtype: 0, VZWSubtype: 0]
                 */
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                String host = prefs.getString("validate", "www.google.com");
                Log.i(TAG, "--> Validating " + network + " " + ni + " host=" + host);

                Socket socket = null;
                try {
                    socket = network.getSocketFactory().createSocket();
                    socket.connect(new InetSocketAddress(host, 443), 10000);
                    Log.i(TAG, "--> Validated " + network + " " + ni + " host=" + host);
//                    synchronized (validated) {
//                        validated.put(network, new Date().getTime());
//                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                        cm.reportNetworkConnectivity(network, true);
                        Log.i(TAG, "--> Reported " + network + " " + ni);
                    }
                } catch (IOException ex) {
                    Log.e(TAG, ex.toString());
                    Log.i(TAG, "--> No connectivity " + network + " " + ni);
                } finally {
                    if (socket != null)
                        try {
                            socket.close();
                        } catch (IOException ex) {
                            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                        }
                }
            }
        }

        private void tryNetwork(Network network) {
            String host = "www.iocaster.com";
            Socket socket = null;
            try {
                socket = network.getSocketFactory().createSocket();
                socket.connect(new InetSocketAddress(host, 80), 10000);
                Log.i(TAG, "--> tryNetwork() : Validated " + network + " " + " host=" + host);
//                synchronized (validated) {
//                    validated.put(network, new Date().getTime());
//                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                    cm.reportNetworkConnectivity(network, true);
                    Log.i(TAG, "--> tryNetwork() : Reported " + network);
                }
            } catch (IOException ex) {
                Log.e(TAG, ex.toString());
                Log.i(TAG, "--> tryNetwork() : No connectivity " + network);
            } finally {
                if (socket != null)
                    try {
                        socket.close();
                    } catch (IOException ex) {
                        Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                    }
            }
        }

    };

    //The socket APIs should be called in a thread not in the main UI thread
    // or android.os.NetworkOnMainThreadException
    private void tryNetwork2(Network network) {
        if(network == null) return;

        final Network thisNet = network;
        new Thread() {
            public void run() {
                String host = "www.iocaster.com";
                Socket socket = null;
                try {
                    socket = thisNet.getSocketFactory().createSocket();
                    socket.connect(new InetSocketAddress(host, 80), 10000);
                    Log.i(TAG, "--> tryNetwork2() : Validated " + thisNet + " " + " host=" + host);
//                    synchronized (validated) {
//                        validated.put(network, new Date().getTime());
//                    }
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


    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = cm.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private boolean isNetworkAvailable2() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = cm.getActiveNetworkInfo();
        //return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        boolean connected = activeNetworkInfo != null && activeNetworkInfo.isConnected();

        NetworkCapabilities capabilities = cm.getNetworkCapabilities( cm.getActiveNetwork() );
        boolean validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);

        return connected && validated;
    }

    //require : <uses-permission android:name="android.permission.INTERNET" />
    // and don't have to use getAllByName() inside the main UI Thread or
    // you will face the android.os.NetworkOnMainThreadException.
    private void printAllInetAddress( Network network ) {
        if(network == null) return;

        final Network thisNet = network;
        new Thread() {
            public void run() {
                try {
                    InetAddress[] inetAddresses = thisNet.getAllByName("www.google.com");
                    Log.d(TAG, "--> inetAddresses.length = " + inetAddresses.length);
                    for( InetAddress inet : inetAddresses ) {
                        Log.d(TAG, "--> inet = " + inet);
                    }
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }


}
