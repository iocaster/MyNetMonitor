package com.iocaster.mynetmonitor;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetMonitor {
    private static final String TAG = "NetMonitor";

    public interface Callback {
        public void onValidatedNetwork(int netType, Network network);
        public void onLostNetwork(int netType, Network network);
    }
    private Callback mCallback;

    private Context mCtx;
    private Object lockObj = new Object();

    private int mNetType = NET_TYPE_UNKNOWN;
    public static final int NET_TYPE_UNKNOWN = 0;
    public static final int NET_TYPE_WIFI = 1;
    public static final int NET_TYPE_MOBILE = 2;
    public static final int NET_TYPE_ETHERNET = 3;
    public static final int NET_TYPE_MAX = 4;
    //public static enum eNetType {NET_TYPE_UNKNOWN, NET_TYPE_WIFI, NET_TYPE_MOBILE, NET_TYPE_ETHERNET, NET_TYPE_MAX};
    private static final String[] NET_TYPE_STRS = {
            "NET_TYPE_UNKNOWN",
            "NET_TYPE_WIFI",
            "NET_TYPE_MOBILE",
            "NET_TYPE_ETHERNET",
    };
    public String getNetTypeName(int netType) {
        return NET_TYPE_STRS[netType];
    }

    //private int[] mNetStates = { 0, 0, 0, 0 };   //for UNKNOWN, WIFI, MOBILE, ETHERNET
    private int[] mNetStates = new int[NET_TYPE_MAX];

    private static final int MY_NET_STATE_UNKNOWN = 0;
    private static final int MY_NET_STATE_AVAILABLE = 1;                //interface up state but internet isn't available state
    private static final int MY_NET_STATE_CAPABILITY_VALIDATED = 2;    //internet available state
    private static final int MY_NET_STATE_LOST = 3;                     //interface down state
    private static final String[] MY_NET_STATE_STRS = {
            "MY_NET_STATE_UNKNOWN",
            "MY_NET_STATE_AVAILABLE",
            "MY_NET_STATE_CAPABILITY_VALIDATED",
            "MY_NET_STATE_LOST",
    };

    private Map<Network, Integer> mNetworkStateMap = new HashMap<>();

    public NetMonitor(Context ctx) {
        mCtx = ctx;
        init();
    }

    public void init() {
        mNetStates = new int[NET_TYPE_MAX];

        ConnectivityManager cm = (ConnectivityManager) mCtx.getSystemService(Context.CONNECTIVITY_SERVICE);
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

    public void release() {
        ConnectivityManager cm = (ConnectivityManager) mCtx.getSystemService(Context.CONNECTIVITY_SERVICE);
        cm.unregisterNetworkCallback(networkMonitorCallback);
    }

    public void setCallback( Callback callback ) {
        mCallback = callback;
    }

    ConnectivityManager.NetworkCallback networkMonitorCallback = new ConnectivityManager.NetworkCallback() {
//        private Map<Network, Long> validated = new HashMap<>();

        //참고: https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/connectivity/NetworkMonitor.java

        @Override
        public void onAvailable(Network network) {
            //super.onAvailable(network);

            Log.d(TAG, "--> onAvailable() : Enter...");
            ConnectivityManager cm = (ConnectivityManager) mCtx.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getNetworkInfo(network);
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            Log.i(TAG, "    Available network " + network + " " + ni);
            Log.i(TAG, "    Capabilities=" + capabilities);

            int netType = NET_TYPE_UNKNOWN;
            if( capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ) {
                netType = NET_TYPE_MOBILE;
            } else if( capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ) {
                netType = NET_TYPE_WIFI;
            } else if( capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ) {
                netType = NET_TYPE_ETHERNET;
            }
            mNetStates[netType] = MY_NET_STATE_AVAILABLE;
//            mNetType = netType;

            //save the netType : to take out onLost() later
            synchronized (mNetworkStateMap) {
                if (mNetworkStateMap.containsKey(network)) {
                    Log.i(TAG, "--> Already mNetworkStateMap " + network ); //maybe error
                } else {
                    mNetworkStateMap.put(network, netType);
                    Log.i(TAG, "--> New mNetworkStateMap " + network );
                }
            }

//            //checkConnectivity(network, ni, capabilities);	//--> 아래에 구현되어 있음.
//            checkConnectivity2(network, ni, capabilities);	//--> 아래에 구현되어 있음.

//            //setLogText("Available network " + network + " " + ni + "\n Capabilities=" + capabilities);
//            String logStr = new String("\n--> onAvailable() : Available network " + network + " " + ni + "\n Capabilities=" + capabilities);
//            Message msg = new Message();
//            msg.what = MSG_UPDATE_ACTIVE_NETWORK_DETAIL;
//            msg.obj = logStr;
//            mHandler.sendMessage( msg );
//
//            //String logStr = new String("--> onAvailable() : Available network " + network + " " + ni + "\n Capabilities=" + capabilities);
//            Message msg2 = new Message();
//            msg2.what = MSG_UPDATE_LOG_TXT;
//            msg2.obj = logStr;
//            mHandler.sendMessage( msg2 );
        }

        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
            //super.onCapabilitiesChanged(network, capabilities);

            Log.d(TAG, "--> onCapabilitiesChanged() : Enter...");
            ConnectivityManager cm = (ConnectivityManager) mCtx.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getNetworkInfo(network);
            Log.i(TAG, "    New capabilities network " + network + " " + ni);
            Log.i(TAG, "    Capabilities=" + capabilities);

            int netType = NET_TYPE_UNKNOWN;
            if( capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ) {
                netType = NET_TYPE_MOBILE;
            } else if( capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ) {
                netType = NET_TYPE_WIFI;
            } else if( capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ) {
                netType = NET_TYPE_ETHERNET;
            }

            if (ni != null && capabilities != null &&
                    ni.getDetailedState() != NetworkInfo.DetailedState.SUSPENDED &&
                    ni.getDetailedState() != NetworkInfo.DetailedState.BLOCKED &&
                    ni.getDetailedState() != NetworkInfo.DetailedState.DISCONNECTED &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                mNetStates[netType] = MY_NET_STATE_CAPABILITY_VALIDATED;
                synchronized(lockObj) {
                    mNetType = netType;
                }

                if(mCallback != null ) {
                    mCallback.onValidatedNetwork( netType, network );
                }

//                Message msg = new Message();
//                msg.what = MSG_NOTICE_NEW_NETWORK;
//                mHandler.sendMessage( msg );
            }


//            //checkConnectivity(network, ni, capabilities);	//--> 아래에 구현되어 있음.
//            checkConnectivity2(network, ni, capabilities);	//--> 아래에 구현되어 있음.

//            String logStr = new String("\n--> onCapabilitiesChanged() : New capabilities network " + network + " " + ni + "\n Capabilities=" + capabilities);
//            Message msg = new Message();
//            msg.what = MSG_UPDATE_LOG_TXT;
//            msg.obj = logStr;
//            mHandler.sendMessage( msg );
//
//            Message msg2 = new Message();
//            msg2.what = MSG_UPDATE_LOG_TXT_WITH_NET_STATE;
//            mHandler.sendMessage( msg2 );
        }

        @Override
        public void onLosing(Network network, int maxMsToLive) {
            //super.onLosing(network, maxMsToLive);

            Log.d(TAG, "--> onLosing() : Enter...");
            ConnectivityManager cm = (ConnectivityManager) mCtx.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getNetworkInfo(network);
            Log.i(TAG, "    Losing network " + network + " within " + maxMsToLive + " ms " + ni);

//            String logStr = new String("\n--> onLosing() : Losing network " + network + " " + ni);
//            Message msg = new Message();
//            msg.what = MSG_UPDATE_LOG_TXT;
//            msg.obj = logStr;
//            mHandler.sendMessage( msg );
        }

        @Override
        public void onLost(Network network) {
            //super.onLost(network);

            Log.d(TAG, "--> onLost() : Enter...");
            ConnectivityManager cm = (ConnectivityManager) mCtx.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getNetworkInfo(network);
            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            Log.i(TAG, "    Lost network " + network + " " + ni);   //ni == null
            Log.i(TAG, "    Capabilities=" + capabilities);         //capabilities == null

            synchronized (mNetworkStateMap) {
                if (mNetworkStateMap.containsKey(network)) {
                    Log.i(TAG, "--> Found mNetworkStateMap " + network );
                    int netType = mNetworkStateMap.get(network);    //take out the saved netType
                    mNetStates[netType] = MY_NET_STATE_LOST;
                    mNetworkStateMap.remove(network);

                    if(mCallback != null ) {
                        mCallback.onLostNetwork( netType, network );
                    }
                }
            }


//            String logStr = new String("\n--> onLost() : Lost network " + network + " " + ni);
//            Message msg = new Message();
//            msg.what = MSG_UPDATE_LOG_TXT;
//            msg.obj = logStr;
//            mHandler.sendMessage( msg );
//
//            Message msg2 = new Message();
//            msg2.what = MSG_UPDATE_LOG_TXT_WITH_NET_STATE;
//            mHandler.sendMessage( msg2 );
        }

        @Override
        public void onUnavailable() {
            //super.onUnavailable();

            Log.d(TAG, "--> onUnavailable() : Enter...");
            Log.i(TAG, "    No networks available");

//            String logStr = new String("\n--> onUnavailable() : No networks available");
//            Message msg = new Message();
//            msg.what = MSG_UPDATE_LOG_TXT;
//            msg.obj = logStr;
//            mHandler.sendMessage( msg );
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
                    ConnectivityManager cm = (ConnectivityManager) mCtx.getSystemService(Context.CONNECTIVITY_SERVICE);
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

    //require <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    /*
     * 로그 메세지 예:
     * --> active network = 174
     * --> inetAddresses.length = 2
     * --> inet = www.google.com/2404:6800:4004:80b::2004
     * --> inet = www.google.com/216.58.197.132
     */
    public Network getActiveNetwork() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ConnectivityManager cm = (ConnectivityManager) mCtx.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network activeNet = (cm == null ? null : cm.getActiveNetwork());
            Log.d(TAG, "--> active network = " + activeNet);

            //tryNetwork2(activeNet);

            return activeNet;
        }
        return null;
    }

    public NetworkInfo getNetworkInfo( Network network ) {
        ConnectivityManager cm = (ConnectivityManager) mCtx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getNetworkInfo(network);
        return ni;
    }

    public boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) mCtx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = cm.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    public boolean isNetworkAvailable2() {
        ConnectivityManager cm = (ConnectivityManager) mCtx.getSystemService(Context.CONNECTIVITY_SERVICE);
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
    public void printAllInetAddress( Network network ) {
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

    /*
     * getWifiIPAddress() / getMobileIPAddress() :
     * https://stackoverflow.com/questions/40670295/how-to-get-ip-address-of-cellular-network-when-device-is-connected-to-wifi-in-an
     * https://stackoverflow.com/questions/6064510/how-to-get-ip-address-of-the-device-from-code/13007325#13007325
     */
    public String getWifiIPAddress() {
        WifiManager wifiMgr = (WifiManager) mCtx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if( wifiMgr == null ) return "";

        WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
        if( wifiInfo != null ) {
            int ip = wifiInfo.getIpAddress();
            Log.d(TAG, "getWifiIPAddress() : ip = " + ip);
            //return Formatter.formatIpAddress(ip); //deprecated ...
            return ipAddressToString(ip);
        }

        return "";
    }

    public static String getMobileIPAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    Log.d(TAG, "getMobileIPAddress() : InetAddr.getHostAddress() = " + addr.getHostAddress() );
                    if (!addr.isLoopbackAddress()) {
                        //return  addr.getHostAddress();
                        String sAddr = addr.getHostAddress();
                        boolean isIPv4 = sAddr.indexOf(':') < 0;    //IPv6) fe80::b896:f7ff:feb1:dd27%dummy0
                        if( isIPv4 ) {
                            return sAddr;
                        } else {
                            int delim = sAddr.indexOf('%'); // drop ip6 zone suffix
                            return delim<0 ? sAddr.toUpperCase() : sAddr.substring(0, delim).toUpperCase();
                        }

                    }
                }
            }
        } catch (Exception ex) { } // for now eat exceptions
        return "";
    }

    /*
     * refer to : https://www.codeproject.com/Questions/470238/How-to-get-ethernet-IP-address
     * NICname : "eth0", "ppp0", ...
     */
    public String getIPAddress( String NICname ) {
        NetworkInterface ni = null;
        try {
            ni = NetworkInterface.getByName(NICname);
            Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();

            int count = 0;
            while (inetAddresses.hasMoreElements()) {
                count++;
                InetAddress ia = inetAddresses.nextElement();
                Log.d(TAG, "0 - getIPAddress("+NICname+") : " + ia.getHostAddress());
                if (!ia.isLinkLocalAddress()) {
                    Log.d(TAG, "1 - getIPAddress("+NICname+") : " + ia.getHostAddress());
                    //return ia.getHostAddress();   //그냥 리턴하지 않고 아래처럼 ipv6 처리 필요
                    String sAddr = ia.getHostAddress();
                    boolean isIPv4 = sAddr.indexOf(':') < 0;    //IPv6) fe80::b896:f7ff:feb1:dd27%dummy0
                    if( isIPv4 ) {
                        return sAddr;
                    } else {
                        int delim = sAddr.indexOf('%'); // drop ip6 zone suffix
                        return delim<0 ? sAddr.toUpperCase() : sAddr.substring(0, delim).toUpperCase();
                    }
                }
            }
            //If no isLinkLocalAddress() found then return the first address
            if(count > 0) {
                /*Enumeration<InetAddress>*/ inetAddresses = ni.getInetAddresses();
                InetAddress ia = inetAddresses.nextElement();
                //return ia.getHostAddress();   //그냥 리턴하지 않고 아래처럼 ipv6 처리 필요
                String sAddr = ia.getHostAddress();
                boolean isIPv4 = sAddr.indexOf(':') < 0;    //IPv6) fe80::b896:f7ff:feb1:dd27%dummy0
                if( isIPv4 ) {
                    return sAddr;
                } else {
                    int delim = sAddr.indexOf('%'); // drop ip6 zone suffix
                    return delim<0 ? sAddr.toUpperCase() : sAddr.substring(0, delim).toUpperCase();
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static String ipAddressToString(int ipAddress) {

        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }

        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

        String ipAddressString = "";
        try {
            ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
        } catch (UnknownHostException ex) {
            Log.e(TAG, "ipAddressToString() : Unable to get host address.");
        }

        return ipAddressString;
    }
}
