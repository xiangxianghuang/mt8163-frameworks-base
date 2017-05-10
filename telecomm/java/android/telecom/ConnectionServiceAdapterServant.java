/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 R* limitations under the License.
 */

package android.telecom;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;

import com.android.internal.os.SomeArgs;
import com.android.internal.telecom.IConnectionServiceAdapter;
import com.android.internal.telecom.IVideoProvider;
import com.android.internal.telecom.RemoteServiceCallback;

import java.util.List;

/**
 * A component that provides an RPC servant implementation of {@link IConnectionServiceAdapter},
 * posting incoming messages on the main thread on a client-supplied delegate object.
 *
 * TODO: Generate this and similar classes using a compiler starting from AIDL interfaces.
 *
 * @hide
 */
final class ConnectionServiceAdapterServant {
    private static final int MSG_HANDLE_CREATE_CONNECTION_COMPLETE = 1;
    private static final int MSG_SET_ACTIVE = 2;
    private static final int MSG_SET_RINGING = 3;
    private static final int MSG_SET_DIALING = 4;
    private static final int MSG_SET_DISCONNECTED = 5;
    private static final int MSG_SET_ON_HOLD = 6;
    private static final int MSG_SET_RINGBACK_REQUESTED = 7;
    private static final int MSG_SET_CONNECTION_CAPABILITIES = 8;
    private static final int MSG_SET_IS_CONFERENCED = 9;
    private static final int MSG_ADD_CONFERENCE_CALL = 10;
    private static final int MSG_REMOVE_CALL = 11;
    private static final int MSG_ON_POST_DIAL_WAIT = 12;
    private static final int MSG_QUERY_REMOTE_CALL_SERVICES = 13;
    private static final int MSG_SET_VIDEO_STATE = 14;
    private static final int MSG_SET_VIDEO_CALL_PROVIDER = 15;
    private static final int MSG_SET_IS_VOIP_AUDIO_MODE = 16;
    private static final int MSG_SET_STATUS_HINTS = 17;
    private static final int MSG_SET_ADDRESS = 18;
    private static final int MSG_SET_CALLER_DISPLAY_NAME = 19;
    private static final int MSG_SET_CONFERENCEABLE_CONNECTIONS = 20;
    private static final int MSG_ADD_EXISTING_CONNECTION = 21;
    private static final int MSG_ON_POST_DIAL_CHAR = 22;
    private static final int MSG_SET_CONFERENCE_MERGE_FAILED = 23;
    private static final int MSG_SET_EXTRAS = 24;

    /* M: CC part start */
    private static final int MTK_MSG_BASE = 1000;
    private static final int MSG_NOTIFY_CONNECTION_LOST = MTK_MSG_BASE;
    private static final int MSG_NOTIFY_ACTION_FAILED = MTK_MSG_BASE + 1;
    private static final int MSG_NOTIFY_SS_TOAST = MTK_MSG_BASE + 2;
    private static final int MSG_NOTIFY_NUMBER_UPDATE = MTK_MSG_BASE + 3;
    private static final int MSG_NOTIFY_INCOMING_INFO_UPDATE = MTK_MSG_BASE + 4;
    private static final int MSG_NOTIFY_CDMA_CALL_ACCEPTED = MTK_MSG_BASE + 5;
    /* M: CC part end */

    /// M: For 3G VT only @{
    private static final int MSG_NOTIFY_VT_STATUS_INFO = MTK_MSG_BASE + 6;
    /// @}

    // M: For Volte @{
    private static final int MSG_UPDATE_EXTRAS = 180;
    private static final int MSG_HANDLE_CREATE_CONFERENCE_COMPLETE = 182;
    /// @}

    private final IConnectionServiceAdapter mDelegate;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            try {
                internalHandleMessage(msg);
            } catch (RemoteException e) {
            }
        }

        // Internal method defined to centralize handling of RemoteException
        private void internalHandleMessage(Message msg) throws RemoteException {
            switch (msg.what) {
                case MSG_HANDLE_CREATE_CONNECTION_COMPLETE: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        mDelegate.handleCreateConnectionComplete(
                                (String) args.arg1,
                                (ConnectionRequest) args.arg2,
                                (ParcelableConnection) args.arg3);
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                case MSG_SET_ACTIVE:
                    mDelegate.setActive((String) msg.obj);
                    break;
                case MSG_SET_RINGING:
                    mDelegate.setRinging((String) msg.obj);
                    break;
                case MSG_SET_DIALING:
                    mDelegate.setDialing((String) msg.obj);
                    break;
                case MSG_SET_DISCONNECTED: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        mDelegate.setDisconnected((String) args.arg1, (DisconnectCause) args.arg2);
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                case MSG_SET_ON_HOLD:
                    mDelegate.setOnHold((String) msg.obj);
                    break;
                case MSG_SET_RINGBACK_REQUESTED:
                    mDelegate.setRingbackRequested((String) msg.obj, msg.arg1 == 1);
                    break;
                case MSG_SET_CONNECTION_CAPABILITIES:
                    mDelegate.setConnectionCapabilities((String) msg.obj, msg.arg1);
                    break;
                case MSG_SET_IS_CONFERENCED: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        mDelegate.setIsConferenced((String) args.arg1, (String) args.arg2);
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                case MSG_ADD_CONFERENCE_CALL: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        mDelegate.addConferenceCall(
                                (String) args.arg1, (ParcelableConference) args.arg2);
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                case MSG_REMOVE_CALL:
                    mDelegate.removeCall((String) msg.obj);
                    break;
                case MSG_ON_POST_DIAL_WAIT: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        mDelegate.onPostDialWait((String) args.arg1, (String) args.arg2);
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                case MSG_ON_POST_DIAL_CHAR: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        mDelegate.onPostDialChar((String) args.arg1, (char) args.argi1);
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                case MSG_QUERY_REMOTE_CALL_SERVICES:
                    mDelegate.queryRemoteConnectionServices((RemoteServiceCallback) msg.obj);
                    break;
                case MSG_SET_VIDEO_STATE:
                    mDelegate.setVideoState((String) msg.obj, msg.arg1);
                    break;
                case MSG_SET_VIDEO_CALL_PROVIDER: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        mDelegate.setVideoProvider((String) args.arg1,
                                (IVideoProvider) args.arg2);
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                case MSG_SET_IS_VOIP_AUDIO_MODE:
                    mDelegate.setIsVoipAudioMode((String) msg.obj, msg.arg1 == 1);
                    break;
                case MSG_SET_STATUS_HINTS: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        mDelegate.setStatusHints((String) args.arg1, (StatusHints) args.arg2);
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                case MSG_SET_ADDRESS: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        mDelegate.setAddress((String) args.arg1, (Uri) args.arg2, args.argi1);
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                case MSG_SET_CALLER_DISPLAY_NAME: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        mDelegate.setCallerDisplayName(
                                (String) args.arg1, (String) args.arg2, args.argi1);
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                case MSG_SET_CONFERENCEABLE_CONNECTIONS: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        mDelegate.setConferenceableConnections(
                                (String) args.arg1, (List<String>) args.arg2);
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                case MSG_ADD_EXISTING_CONNECTION: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        mDelegate.addExistingConnection(
                                (String) args.arg1, (ParcelableConnection) args.arg2);
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                case MSG_SET_CONFERENCE_MERGE_FAILED: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        mDelegate.setConferenceMergeFailed((String) args.arg1);
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                case MSG_SET_EXTRAS: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        mDelegate.setExtras((String) args.arg1, (Bundle) args.arg2);
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                /* M: CC part start */
                case MSG_NOTIFY_CONNECTION_LOST: {
                    mDelegate.notifyConnectionLost((String) msg.obj);
                    break;
                }
                case MSG_NOTIFY_ACTION_FAILED: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    mDelegate.notifyActionFailed((String) args.arg1, (int) args.arg2);
                    break;
                }
                case MSG_NOTIFY_SS_TOAST: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    mDelegate.notifySSNotificationToast(
                            (String) args.arg1, (int) args.arg2, (int) args.arg3, (int) args.arg4, (String) args.arg5, (int) args.arg6);
                    break;
                }
                case MSG_NOTIFY_NUMBER_UPDATE: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    mDelegate.notifyNumberUpdate((String) args.arg1, (String) args.arg2);
                    break;
                }
                case MSG_NOTIFY_INCOMING_INFO_UPDATE: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    mDelegate.notifyIncomingInfoUpdate((String) args.arg1, (int) args.arg2, (String) args.arg3, (int) args.arg4);
                    break;
                }
                case MSG_NOTIFY_CDMA_CALL_ACCEPTED: {
                    mDelegate.notifyCdmaCallAccepted((String) msg.obj);
                    break;
                }
                /* M: CC part end */

                /// M: For 3G VT only @{
                case MSG_NOTIFY_VT_STATUS_INFO: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        mDelegate.notifyVtStatusInfo((String) args.arg1, (int) args.arg2);
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                /// @}

                /// M: For volte @{
                case MSG_UPDATE_EXTRAS: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    mDelegate.updateExtras((String) args.arg1, (Bundle) args.arg2);
                    break;
                }
                case MSG_HANDLE_CREATE_CONFERENCE_COMPLETE: {
                    SomeArgs args = (SomeArgs) msg.obj;
                    try {
                        mDelegate.handleCreateConferenceComplete(
                                (String) args.arg1,
                                (ConnectionRequest) args.arg2,
                                (ParcelableConference) args.arg3);
                    } finally {
                        args.recycle();
                    }
                    break;
                }
                /// @}
            }
        }
    };

    private final IConnectionServiceAdapter mStub = new IConnectionServiceAdapter.Stub() {
        @Override
        public void handleCreateConnectionComplete(
                String id,
                ConnectionRequest request,
                ParcelableConnection connection) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = id;
            args.arg2 = request;
            args.arg3 = connection;
            mHandler.obtainMessage(MSG_HANDLE_CREATE_CONNECTION_COMPLETE, args).sendToTarget();
        }

        @Override
        public void setActive(String connectionId) {
            mHandler.obtainMessage(MSG_SET_ACTIVE, connectionId).sendToTarget();
        }

        @Override
        public void setRinging(String connectionId) {
            mHandler.obtainMessage(MSG_SET_RINGING, connectionId).sendToTarget();
        }

        @Override
        public void setDialing(String connectionId) {
            mHandler.obtainMessage(MSG_SET_DIALING, connectionId).sendToTarget();
        }

        @Override
        public void setDisconnected(
                String connectionId, DisconnectCause disconnectCause) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = connectionId;
            args.arg2 = disconnectCause;
            mHandler.obtainMessage(MSG_SET_DISCONNECTED, args).sendToTarget();
        }

        @Override
        public void setOnHold(String connectionId) {
            mHandler.obtainMessage(MSG_SET_ON_HOLD, connectionId).sendToTarget();
        }

        @Override
        public void setRingbackRequested(String connectionId, boolean ringback) {
            mHandler.obtainMessage(MSG_SET_RINGBACK_REQUESTED, ringback ? 1 : 0, 0, connectionId)
                    .sendToTarget();
        }

        @Override
        public void setConnectionCapabilities(String connectionId, int connectionCapabilities) {
            mHandler.obtainMessage(
                    MSG_SET_CONNECTION_CAPABILITIES, connectionCapabilities, 0, connectionId)
                    .sendToTarget();
        }

        @Override
        public void setConferenceMergeFailed(String callId) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = callId;
            mHandler.obtainMessage(MSG_SET_CONFERENCE_MERGE_FAILED, args).sendToTarget();
        }

        @Override
        public void setIsConferenced(String callId, String conferenceCallId) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = callId;
            args.arg2 = conferenceCallId;
            mHandler.obtainMessage(MSG_SET_IS_CONFERENCED, args).sendToTarget();
        }

        @Override
        public void addConferenceCall(String callId, ParcelableConference parcelableConference) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = callId;
            args.arg2 = parcelableConference;
            mHandler.obtainMessage(MSG_ADD_CONFERENCE_CALL, args).sendToTarget();
        }

        @Override
        public void removeCall(String connectionId) {
            mHandler.obtainMessage(MSG_REMOVE_CALL, connectionId).sendToTarget();
        }

        @Override
        public void onPostDialWait(String connectionId, String remainingDigits) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = connectionId;
            args.arg2 = remainingDigits;
            mHandler.obtainMessage(MSG_ON_POST_DIAL_WAIT, args).sendToTarget();
        }

        @Override
        public void onPostDialChar(String connectionId, char nextChar) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = connectionId;
            args.argi1 = nextChar;
            mHandler.obtainMessage(MSG_ON_POST_DIAL_CHAR, args).sendToTarget();
        }

        @Override
        public void queryRemoteConnectionServices(RemoteServiceCallback callback) {
            mHandler.obtainMessage(MSG_QUERY_REMOTE_CALL_SERVICES, callback).sendToTarget();
        }

        @Override
        public void setVideoState(String connectionId, int videoState) {
            mHandler.obtainMessage(MSG_SET_VIDEO_STATE, videoState, 0, connectionId).sendToTarget();
        }

        @Override
        public void setVideoProvider(String connectionId, IVideoProvider videoProvider) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = connectionId;
            args.arg2 = videoProvider;
            mHandler.obtainMessage(MSG_SET_VIDEO_CALL_PROVIDER, args).sendToTarget();
        }

        @Override
        public final void setIsVoipAudioMode(String connectionId, boolean isVoip) {
            mHandler.obtainMessage(MSG_SET_IS_VOIP_AUDIO_MODE, isVoip ? 1 : 0, 0,
                    connectionId).sendToTarget();
        }

        @Override
        public final void setStatusHints(String connectionId, StatusHints statusHints) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = connectionId;
            args.arg2 = statusHints;
            mHandler.obtainMessage(MSG_SET_STATUS_HINTS, args).sendToTarget();
        }

        @Override
        public final void setAddress(String connectionId, Uri address, int presentation) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = connectionId;
            args.arg2 = address;
            args.argi1 = presentation;
            mHandler.obtainMessage(MSG_SET_ADDRESS, args).sendToTarget();
        }

        @Override
        public final void setCallerDisplayName(
                String connectionId, String callerDisplayName, int presentation) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = connectionId;
            args.arg2 = callerDisplayName;
            args.argi1 = presentation;
            mHandler.obtainMessage(MSG_SET_CALLER_DISPLAY_NAME, args).sendToTarget();
        }

        @Override
        public final void setConferenceableConnections(
                String connectionId, List<String> conferenceableConnectionIds) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = connectionId;
            args.arg2 = conferenceableConnectionIds;
            mHandler.obtainMessage(MSG_SET_CONFERENCEABLE_CONNECTIONS, args).sendToTarget();
        }

        @Override
        public final void addExistingConnection(
                String connectionId, ParcelableConnection connection) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = connectionId;
            args.arg2 = connection;
            mHandler.obtainMessage(MSG_ADD_EXISTING_CONNECTION, args).sendToTarget();
        }

        @Override
        public final void setExtras(String connectionId, Bundle extras) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = connectionId;
            args.arg2 = extras;
            mHandler.obtainMessage(MSG_SET_EXTRAS, args).sendToTarget();
        }

        /* M: CC part start */
        @Override
        public void notifyConnectionLost(String connectionId) {
            mHandler.obtainMessage(MSG_NOTIFY_CONNECTION_LOST, connectionId).sendToTarget();
        }

        @Override
        public void notifyActionFailed(String connectionId, int action) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = connectionId;
            args.arg2 = action;
            mHandler.obtainMessage(MSG_NOTIFY_ACTION_FAILED, args).sendToTarget();
        }

        @Override
        public void notifySSNotificationToast(String callId, int notiType, int type, int code, String number, int index) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = callId;
            args.arg2 = notiType;
            args.arg3 = type;
            args.arg4 = code;
            args.arg5 = number;
            args.arg6 = index;
            mHandler.obtainMessage(MSG_NOTIFY_SS_TOAST, args).sendToTarget();
        }

        @Override
        public void notifyNumberUpdate(String callId, String number) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = callId;
            args.arg2 = number;
            mHandler.obtainMessage(MSG_NOTIFY_NUMBER_UPDATE, args).sendToTarget();
        }

        @Override
        public void notifyIncomingInfoUpdate(String callId, int type, String alphaid, int cli_validity) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = callId;
            args.arg2 = type;
            args.arg3 = alphaid;
            args.arg4 = cli_validity;
            mHandler.obtainMessage(MSG_NOTIFY_INCOMING_INFO_UPDATE, args).sendToTarget();
        }

        @Override
        public void notifyCdmaCallAccepted(String connectionId) {
            mHandler.obtainMessage(MSG_NOTIFY_CDMA_CALL_ACCEPTED, connectionId).sendToTarget();
        }
        /* M: CC part end */

        /// M: For 3G VT only @{
        @Override
        public void notifyVtStatusInfo(String connectionId, int status) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = connectionId;
            args.arg2 = status;
            mHandler.obtainMessage(MSG_NOTIFY_VT_STATUS_INFO, args).sendToTarget();
        }
        /// @}

        /// M: For Volte @{
        @Override
        public void updateExtras(String callId, Bundle bundle) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = callId;
            args.arg2 = bundle;
            mHandler.obtainMessage(MSG_UPDATE_EXTRAS, args).sendToTarget();
        }

        @Override
        public void handleCreateConferenceComplete(
                String conferenceId,
                ConnectionRequest request,
                ParcelableConference conference) {
            SomeArgs args = SomeArgs.obtain();
            args.arg1 = conferenceId;
            args.arg2 = request;
            args.arg3 = conference;
            mHandler.obtainMessage(MSG_HANDLE_CREATE_CONFERENCE_COMPLETE, args).sendToTarget();
        }
        /// @}
    };

    public ConnectionServiceAdapterServant(IConnectionServiceAdapter delegate) {
        mDelegate = delegate;
    }

    public IConnectionServiceAdapter getStub() {
        return mStub;
    }
}
