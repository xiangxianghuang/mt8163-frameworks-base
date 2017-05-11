/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2010. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

package com.mediatek.location;

import android.net.ConnectivityManager;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.IOException;

/**
 * Class that is used to send info to agpsd.
 */
class C2kAgpsInterface {

    private static final String SOCKET_ADDRESS = "c2kagpsd";
    private static final String TAG = "C2kAgpsInterface";
    private static final int EVENT_AGPS_SET_NETWORK_ID = 4;
    private static final int EVENT_AGPS_NETWORK_STATE  = 5000;
    private static final int NETWORK_AVAILABLE = 1;
    private static final int NETWORK_LOST      = 0;

    private LocalSocket mClient;
    private BufferedOutputStream mOut;
    private final NetworkRequest mNetworkRequest;
    private ConnectivityManager.NetworkCallback mNetworkCallback;
    private ConnectivityManager mConnectivityManager;

    C2kAgpsInterface(ConnectivityManager manager) {
        mConnectivityManager = manager;
        mNetworkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
                .build();
        mNetworkCallback = null;
    }

    void requestNetwork() {
        mNetworkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                super.onAvailable(network);
                Log.d(TAG, "[agps] WARNING: onAvailable: network=" + network);
                setNetworkId(network.netId);
                setNetworkState(NETWORK_AVAILABLE);
            }

            @Override
            public void onLost(Network network) {
                super.onLost(network);
                Log.d(TAG, "[agps] WARNING: onLost: network=" + network);
                setNetworkState(NETWORK_LOST);
                releaseNetwork();
            }
        };
        Log.d(TAG, "[agps] WARNING: requestNetwork");
        mConnectivityManager.requestNetwork(mNetworkRequest, mNetworkCallback);
    }

    void releaseNetwork() {
        Log.d(TAG, "[agps] WARNING: releaseNetwork");
        if (mNetworkCallback != null) {
            mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
            mNetworkCallback = null;
        }
    }

    private void setNetworkId(int netId) {
         try {
             connect();
             C2kAgpsInterface.putInt(mOut, EVENT_AGPS_SET_NETWORK_ID);
             C2kAgpsInterface.putInt(mOut, netId);
             mOut.flush();
         } catch (IOException e) {
             Log.e(TAG, "Exception " + e);
         } finally {
             close();
         }
    }

    private void setNetworkState(int state) {
         try {
             connect();
             C2kAgpsInterface.putInt(mOut, EVENT_AGPS_NETWORK_STATE);
             C2kAgpsInterface.putInt(mOut, state);
             mOut.flush();
         } catch (IOException e) {
             Log.e(TAG, "Exception " + e);
         } finally {
             close();
         }
    }

    private void connect() throws IOException {
        if (mClient != null) {
            mClient.close();
        }
        mClient = new LocalSocket();
        mClient.connect(
            new LocalSocketAddress(SOCKET_ADDRESS,
                                   LocalSocketAddress.Namespace.ABSTRACT));
        mClient.setSoTimeout(3000);
        mOut = new BufferedOutputStream(mClient.getOutputStream());
    }

    private void close() {
        try {
            if (mClient != null) {
                mClient.close();
                mClient = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void putByte(BufferedOutputStream out, byte data) throws IOException {
        out.write(data);
    }

    private static void putShort(BufferedOutputStream out, short data) throws IOException {
        putByte(out, (byte) (data & 0xff));
        putByte(out, (byte) ((data >> 8) & 0xff));
    }

    private static void putInt(BufferedOutputStream out, int data) throws IOException {
        putShort(out, (short) (data & 0xffff));
        putShort(out, (short) ((data >> 16) & 0xffff));
    }
}