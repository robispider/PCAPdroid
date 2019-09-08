/*
    This file is part of RemoteCapture.

    RemoteCapture is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    RemoteCapture is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with RemoteCapture.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2019 by Emanuele Faranda
*/

package com.emanuelef.remote_capture;

import android.annotation.TargetApi;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.net.InetSocketAddress;

public class CaptureService extends VpnService implements Runnable {
    private static final String TAG = "CaptureService";
    private static final String VpnSessionName = "Remote Capture VPN";
    private ParcelFileDescriptor mParcelFileDescriptor = null;
    private Thread mThread;
    private String vpn_ipv4;
    private String vpn_dns;
    private String public_dns;
    private String collector_address;
    private int collector_port;
    private int uid_filter;
    private static CaptureService INSTANCE;

    static {
        /* Load native library */
        System.loadLibrary("vpnproxy-jni");
    }

    @Override
    public void onCreate() {
        Log.d(CaptureService.TAG, "onCreate");
        INSTANCE = this;
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            Log.d(CaptureService.TAG, "NULL intent onStartCommand");
            return super.onStartCommand(intent, flags, startId);
        }

        Log.d(CaptureService.TAG, "onStartCommand");

        Bundle settings = intent.getBundleExtra("settings");

        // retrieve settings
        assert settings != null;
        public_dns = settings.getString("dns_server");
        vpn_dns = "10.215.173.2";
        vpn_ipv4 = "10.215.173.1";
        collector_address = settings.getString(MainActivity.PREF_COLLECTOR_IP_KEY);
        collector_port = settings.getInt(MainActivity.PREF_COLLECTOR_PORT_KEY);;
        uid_filter = settings.getInt(MainActivity.PREF_UID_FILTER);

        // VPN
        /* In order to see the DNS packets into the VPN we must set an internal address as the DNS
         * server. */
        Builder builder = new Builder()
                .addAddress(vpn_ipv4, 30) // using a random IP as an address is needed
                .addRoute("0.0.0.0", 1)
                .addDnsServer(vpn_dns);

        try {
            mParcelFileDescriptor = builder.setSession(CaptureService.VpnSessionName).establish();
        } catch (IllegalArgumentException | IllegalStateException e) {
            Toast.makeText(this, "VPN setup failed", Toast.LENGTH_SHORT).show();
            return super.onStartCommand(intent, flags, startId);
        }

        // Stop the previous session by interrupting the thread.
        if (mThread != null) {
            mThread.interrupt();
        }

        // Start a new session by creating a new thread.
        mThread = new Thread(this, "CaptureService Thread");
        mThread.start();
        return START_STICKY;
        //return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onRevoke() {
        Log.d(CaptureService.TAG, "onRevoke");
        stop();

        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        Log.d(CaptureService.TAG, "onDestroy");
        stop();
        INSTANCE = null;

        if(mThread != null) {
            mThread.interrupt();
        }
        super.onDestroy();
    }

    private void stop() {
        if(mParcelFileDescriptor != null) {
            try {
                mParcelFileDescriptor.close();
            } catch (IOException e) {
                Toast.makeText(this, "Stopping VPN failed", Toast.LENGTH_SHORT).show();
            }
            mParcelFileDescriptor = null;
        }
    }

    /* Check if the VPN service is running */
    public static boolean isRunning() {
        return((INSTANCE != null) &&
                (INSTANCE.mParcelFileDescriptor != null));
    }

    /* Stop a running VPN service */
    public static void stopService() {
        if (INSTANCE != null) {
            stopPacketLoop();
            INSTANCE.stop();
        }
    }

    @Override
    public void run() {
        runPacketLoop(mParcelFileDescriptor.detachFd(), this, Build.VERSION.SDK_INT);
    }

    /* The following methods are called from native code */

    public String getVpnIPv4() {
        return(vpn_ipv4);
    }

    public String getVpnDns() {
        return(vpn_dns);
    }

    public String getPublicDns() {
        return(public_dns);
    }

    public String getPcapCollectorAddress() {
        return(collector_address);
    }

    /* TODO use int */
    public int getPcapCollectorPort() {
        return(collector_port);
    }

    public int getPcapUidFilter() {
        return(uid_filter);
    }

    // from NetGuard
    @TargetApi(Build.VERSION_CODES.Q)
    public int getUidQ(int version, int protocol, String saddr, int sport, String daddr, int dport) {
        if (protocol != 6 /* TCP */ && protocol != 17 /* UDP */)
            return -1;

        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null)
            return -1;

        InetSocketAddress local = new InetSocketAddress(saddr, sport);
        InetSocketAddress remote = new InetSocketAddress(daddr, dport);

        Log.i(TAG, "Get uid local=" + local + " remote=" + remote);
        int uid = cm.getConnectionOwnerUid(protocol, local, remote);
        Log.i(TAG, "Get uid=" + uid);
        return uid;
    }

    public String getApplicationByUid(int uid) {
        return(getPackageManager().getNameForUid(uid));
    }

    public static native void runPacketLoop(int fd, CaptureService vpn, int sdk);
    public static native void stopPacketLoop();
}