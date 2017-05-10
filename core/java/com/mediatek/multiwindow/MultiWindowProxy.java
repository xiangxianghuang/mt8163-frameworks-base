/*
 * Copyright (C) 2014 MediaTek Inc. Modification based on code covered by the mentioned
 * copyright and/or permission notice(s).
 */
/*
 * Copyright (C) 2014 The Android Open Source Project Licensed under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed
 * to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under the
 * License.
 */

package com.mediatek.multiwindow;

import com.mediatek.common.multiwindow.IMultiWindowManager;
import com.mediatek.common.multiwindow.IMWAmsCallback;
import com.mediatek.common.multiwindow.IMWWmsCallback;
import com.mediatek.common.multiwindow.IMWSystemUiCallback;
import com.mediatek.common.multiwindow.IMWBlackList;

import android.os.IBinder;
import android.os.SystemProperties;
import android.os.ServiceManager;
import android.os.RemoteException;
import android.util.Log;
import android.content.res.Configuration;
import android.content.pm.ActivityInfo;
import java.util.List;
import android.content.Intent;
import android.view.WindowManager;

public class MultiWindowProxy {
    private static final String TAG = "MultiWindowProxy";
    private static final boolean FEATURE_SUPPORTED = SystemProperties.get(
            "ro.mtk_multiwindow").equals("1");

    private static IMultiWindowManager sDefaultService = null;
    private static IMWBlackList sBlackListManager = null;

    private static MultiWindowProxy sInstance = null;

    // Window type
    private int mWindowType = 0;
    public static final int NOT_FLOATING_WINDOW = 0;
    public static final int FLOATING_WINDOW_FULL = 1;
    public static final int FLOATING_WINDOW_DIALOG = 2;

    // Add for Split-Screen Mode
    public static final int MODE_FLOATING = 0;
    public static final int MODE_SPLIT_SCREEN_TWO = 1;

    public static final int PLACE_LEFT_OR_TOP = 0;
    public static final int PLACE_RIGHT_OR_BOTTOM = 1;
    public static final int PLACE_NULL = -1;

    public static final int STACK_NULL = -1;

    private MultiWindowProxy() {
    }

    /**
     * @return
     */
    public static MultiWindowProxy getInstance() {
        synchronized (MultiWindowProxy.class) {
            if (sInstance == null) {
                sInstance = new MultiWindowProxy();
            }
            return sInstance;
        }
    }

    /**
     * multiwindow service runs on system server and will never die, and getService() may
     * be executed frequently, so cache the binder instance here for performance.
     */
    private static IMultiWindowManager getServiceInstance() {
        if (sDefaultService == null) {
            IBinder binder = ServiceManager.getService("multiwindow");
            sDefaultService = (IMultiWindowManager) IMultiWindowManager.Stub
                    .asInterface(binder);
        }
        return sDefaultService;
    }

    /**
     * multiwindow service runs on system server and will never die, and getService() may
     * be executed frequently, so cache the binder instance here for performance.
     */
    private static IMWBlackList getBlackListManager() {
        if (sBlackListManager == null) {
            IBinder binder = ServiceManager.getService("mw_blacklist");
            sBlackListManager = (IMWBlackList) IMWBlackList.Stub
                    .asInterface(binder);
        }
        return sBlackListManager;
    }

    /**
     * To check whether Multi-Window feature is supported or not.
     *
     * @return true if supported.
     */
    public static boolean isSupported() {
        // / We add a system property for turning off/on Multi-Window dynamically.
        boolean disableMultiWindow = SystemProperties.getInt(
                "persist.sys.mtk.disable.mw", 0) == 1;
        return FEATURE_SUPPORTED;
    }

    /**
     * Return true if the intent contains FLAG_ACTIVITY_FLOATING.
     *
     * @hide
     */
    public static boolean isFloatingIntent(Intent intent) {
        if (intent == null) {
            return false;
        }
        if ((intent.getFlags() & Intent.FLAG_ACTIVITY_FLOATING) != 0) {
            return true;
        }
        return false;
    }

    /**
     * Return the modified Intent from the input Intent
     *
     * @hide
     */
    public static void adjustFloatingIntent(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_FLOATING);
    }
    /**
     * @param cb
     */
    public void setAMSCallback(IMWAmsCallback cb) {
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return;
            }
            service.setAMSCallback(cb);
        } catch (RemoteException e) {
            Log.e(TAG, "setAMSCallback failed!", e);
        }
    }

    /**
     * @param cb
     */
    public void setWMSCallback(IMWWmsCallback cb) {
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return;
            }
            service.setWMSCallback(cb);
        } catch (RemoteException e) {
            Log.e(TAG, "setWMSCallback failed!", e);
        }
    }

    /**
     * @param cb
     */
    public void setSystemUiCallback(IMWSystemUiCallback cb) {
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return;
            }
            service.setSystemUiCallback(cb);
        } catch (RemoteException e) {
            Log.e(TAG, "setSystemUiCallback failed!", e);
        }
    }

    /**
     * @param stackId
     * @return
     */
    public boolean isFloatingStack(int stackId) {
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return false;
            }
            return service.isFloatingStack(stackId);
        } catch (RemoteException e) {
            Log.e(TAG, "isFloatingStack failed!", e);
        }
        return false;
    }

    public boolean isFloatingByWinToken(IBinder winToken) {
        if (winToken == null)
            return false;
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return false;
            }
            return service.isFloatingByWinToken(winToken);
        } catch (RemoteException e) {
            Log.e(TAG, "isFloatingByWinToken failed!", e);
        }
        return false;
    }

    public boolean isFloatingByAppToken(IBinder appToken) {
        if (appToken == null)
            return false;
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return false;
            }
            return service.isFloatingByAppToken(appToken);
        } catch (RemoteException e) {
            Log.e(TAG, "isFloatingByAppToken failed!", e);
        }
        return false;
    }

    public boolean isFloating(IBinder token, int type) {
        if (token == null) {
            return false;
        }
        if (type >= WindowManager.LayoutParams.FIRST_SUB_WINDOW &&
            type <= WindowManager.LayoutParams.LAST_SUB_WINDOW) {
            return isFloatingByWinToken(token);
        } else {
            return isFloatingByAppToken(token);
        }
    }

    /**
     * @param stackId
     */
    public void createFloatingStack(int stackId) {
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return;
            }
            service.addStack(stackId, true/*isFloating*/);
        } catch (RemoteException e) {
            Log.e(TAG, "createFloatingStack failed!", e);
        }
    }

    /**
     * @param stackId
     */
    public void removeFloatingStack(int stackId) {
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return;
            }
            service.removeStack(stackId, true/*isFloating*/);
        } catch (RemoteException e) {
            Log.e(TAG, "onStackDetached failed!", e);
        }
    }

    /**
     * Called by ActivityManagerService when task has been added. MultiWindowService need
     * to maintain the all running task infos, so, we should synchronize task infos for
     * it.
     *
     * @param taskId Unique ID of the added task.
     * @hide
     */
    public void onTaskAdded(int taskId, int stackId) {
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return;
            }
            service.onTaskAdded(taskId, stackId);
        } catch (RemoteException e) {
            Log.e(TAG, "onTaskAdded failed!", e);
        }
    }

    /*
     * Called by ActivityManagerService when task has been removed. MultiWindowService
     * need to maintain the all running task infos, so, we should synchronize task infos
     * for it.
     * @param taskId Unique ID of the removed task.
     * @hide
     */
    public void onTaskRemoved(int taskId, int stackId) {
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return;
            }
            service.onTaskRemoved(taskId, stackId);
        } catch (RemoteException e) {
            Log.e(TAG, "onTaskRemoved failed!", e);
        }
    }

    /**
     * @param token
     */
    public void closeWindow(IBinder appToken) {
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return;
            }
            service.closeWindow(appToken);
        } catch (RemoteException e) {
            Log.e(TAG, "closeWindow failed!", e);
        }
    }

    /**
     * @param token
     * @param toMax
     */
    public void restoreWindow(IBinder appToken, boolean toMax) {
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return;
            }
            service.restoreWindow(appToken, toMax);
        } catch (RemoteException e) {
            Log.e(TAG, "restoreWindow failed!", e);
        }
    }

    /**
     * @param token
     * @param isSticky
     */
    public void stickWindow(IBinder appToken, boolean isSticky) {
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return;
            }
            service.stickWindow(appToken, isSticky);
        } catch (RemoteException e) {
            Log.e(TAG, "stickWindow failed!", e);
        }
    }

    /**
     * Check if the activity associated with the token is sticky.
     *
     * @hide
     */
    public boolean isSticky(IBinder appToken) {
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return false;
            }
            return service.isSticky(appToken);
        } catch (RemoteException e) {
            Log.e(TAG, "isSticky failed!", e);
        }
        return false;
    }

    /**
     * Move the floating window to the front Called by Activity.dispatchTouchEvent()
     *
     * @param token The Binder token referencing the Activity we want to move.
     * @hide
     */
    public void moveActivityTaskToFront(IBinder appToken) {
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return;
            }
            service.moveActivityTaskToFront(appToken);
        } catch (RemoteException e) {
            Log.e(TAG, "moveActivityTaskToFront failed!", e);
        }
    }

    /**
     * Check if the given stack is sticky.
     *
     * @hide
     */
    public boolean isStickStack(int stackId) {
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return false;
            }
            return service.isStickStack(stackId);
        } catch (RemoteException e) {
            Log.e(TAG, "isStickStack failed!", e);
        }
        return false;
    }

    /**
     * Check if the given task is in mini/max status.
     *
     * @hide
     */
    public boolean isInMiniMax(int taskId) {
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return false;
            }
            return service.isInMiniMax(taskId);
        } catch (RemoteException e) {
            Log.e(TAG, "isInMiniMax failed!", e);
        }
        return false;
    }

    /**
     * Tell MWS that this task need to be mini/max status.
     *
     * @hide
     */
    public void miniMaxTask(int taskId) {
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return;
            }
            service.miniMaxTask(taskId);
        } catch (RemoteException e) {
            Log.e(TAG, "miniMaxTask failed!", e);
        }
    }

    /**
     * Called by Window Manager Policy to move Floating Window
     *
     * @hide
     */
    public void moveFloatingWindow(int disX, int disY) {
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return;
            }
            service.moveFloatingWindow(disX, disY);
        } catch (RemoteException e) {
            Log.e(TAG, "moveFloatingWindow failed!", e);
        }
    }

    /**
     * Called by Window Manager Policy to resize Floating Window
     *
     * @hide
     */
    public void resizeFloatingWindow(int direction, int deltaX, int deltaY) {
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return;
            }
            service.resizeFloatingWindow(direction, deltaX, deltaY);
        } catch (RemoteException e) {
            Log.e(TAG, "resizeFloatingWindow failed!", e);
        }
    }

    /**
     * Called by Window Manager Policy to enable Focus Frame
     *
     * @hide
     */
    public void enableFocusedFrame(boolean enable) {
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return;
            }
            service.enableFocusedFrame(enable);
        } catch (RemoteException e) {
            Log.e(TAG, "enableFocusedFrame failed!", e);
        }
    }

    /**
     * Called by WindowManagerService to contorl restore button on systemUI module.
     *
     * @hide
     */
    public void showRestoreButton(boolean flag) {
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return;
            }
            service.showRestoreButton(flag);
        } catch (RemoteException e) {
            Log.e(TAG, "showRestoreButton failed!", e);
        }
    }

    /**
     * Called by ActivityThread to Tell the MultiWindowService we have created.
     * MultiWindowService need to do somethings for the activity association with the
     * token at this point.
     *
     * @param token The Binder token referencing the Activity that has created
     * @hide
     */
    public void activityCreated(IBinder appToken) {
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return;
            }
            service.activityCreated(appToken);
        } catch (RemoteException e) {
            Log.e(TAG, "activityCreated failed!", e);
        }
    }

    public void onAppTokenAdded(IBinder appToken, int taskId) {
         try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return;
            }
            service.onAppTokenAdded(appToken, taskId);
        } catch (RemoteException e) {
            Log.e(TAG, "onAppTokenAdded failed!", e);
        }
    }

    public void onAppTokenRemoved(IBinder appToken, int taskId) {
         try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return;
            }
            service.onAppTokenRemoved(appToken, taskId);
        } catch (RemoteException e) {
            Log.e(TAG, "onAppTokenRemoved failed!", e);
        }
    }

    public void addToken(IBinder winToken, IBinder appToken) {
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return;
            }
            service.addToken(winToken, appToken);
        } catch (RemoteException e) {
            Log.e(TAG, "addToken failed!", e);
        }
    }

    public void removeToken(IBinder winToken) {
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return;
            }
            service.removeToken(winToken);
        } catch (RemoteException e) {
            Log.e(TAG, "addToken failed!", e);
        }
    }

    // / black list@{
    /**
     * @param packageName
     * @return
     */
    public boolean shouldChangeConfig(String packageName) {
        try {
            IMWBlackList blManager = getBlackListManager();
            if (blManager == null) {
                Log.e(TAG, "getBlackListManager failed!");
                return true;
            }
            return blManager.shouldChangeConfig(packageName);
        } catch (RemoteException e) {
            Log.e(TAG, "shouldChangeConfig failed!", e);
        }
        return true;
    }

    /**
     * @param packageName
     * @return
     */
    public boolean shouldRestartWhenMiniMax(String packageName) {
        try {
            IMWBlackList blManager = getBlackListManager();
            if (blManager == null) {
                Log.e(TAG, "getBlackListManager failed!");
                return false;
            }
            return blManager.shouldRestartWhenMiniMax(packageName);
        } catch (RemoteException e) {
            Log.e(TAG, "shouldRestartWhenMiniMax failed!", e);
        }
        return false;
    }

    /**
     * @return
     */
    public List<String> getWhiteList() {
        try {
            IMWBlackList blManager = getBlackListManager();
            if (blManager == null) {
                Log.e(TAG, "getBlackListManager failed!");
                return null;
            }
            return blManager.getWhiteList();
        } catch (RemoteException e) {
            Log.e(TAG, "getWhiteList failed!", e);
        }
        return null;
    }

    /**
     * @param packageName
     * @return
     */
    public boolean inWhiteList(String packageName) {
        try {
            IMWBlackList blManager = getBlackListManager();
            if (blManager == null) {
                Log.e(TAG, "getBlackListManager failed!");
                return false;
            }
            return blManager.inWhiteList(packageName);
        } catch (RemoteException e) {
            Log.e(TAG, "inWhiteList failed!", e);
        }
        return false;
    }

    /**
     * @param packageName
     */
    public void addIntoWhiteList(String packageName) {
        try {
            IMWBlackList blManager = getBlackListManager();
            if (blManager == null) {
                Log.e(TAG, "getBlackListManager failed!");
                return;
            }
            blManager.addIntoWhiteList(packageName);
        } catch (RemoteException e) {
            Log.e(TAG, "addIntoWhiteList failed!", e);
        }
    }

    /**
     * @param packageList
     */
    public void addMoreIntoWhiteList(List<String> packageList) {
        try {
            IMWBlackList blManager = getBlackListManager();
            if (blManager == null) {
                Log.e(TAG, "getBlackListManager failed!");
                return;
            }
            blManager.addMoreIntoWhiteList(packageList);
        } catch (RemoteException e) {
            Log.e(TAG, "addMoreIntoWhiteList(List failed!", e);
        }
    }

    /**
     * @param packageName
     */
    public void removeFromWhiteList(String packageName) {
        try {
            IMWBlackList blManager = getBlackListManager();
            if (blManager == null) {
                Log.e(TAG, "getBlackListManager failed!");
                return;
            }
            blManager.removeFromWhiteList(packageName);
        } catch (RemoteException e) {
            Log.e(TAG, "removeFromWhiteList failed!", e);
        }
    }

    // / black list @}

    /**
     * Return the modified config from the input Config. Called by Activity.attach().
     * <p>
     * App should load different resources to fit the floating window's size. But this
     * maybe results in JE when restore/max window, or start a floating window from
     * extrance button.
     *
     * @param config The orignal configuration.
     * @param info Activity to get search information from.
     * @param packageName PackageName used to decide that if it can change config.
     * @return Return new config for app to load different resources.
     * @hide
     */
    public Configuration adjustActivityConfig(Configuration config,
            ActivityInfo info, String packageName) {
        int widthDp, heightDp;
        widthDp = config.screenWidthDp;
        heightDp = config.screenHeightDp;

        config.screenLayout = Configuration.SCREENLAYOUT_SIZE_NORMAL;
        config.smallestScreenWidthDp = config.smallestScreenWidthDp / 2;
        if (info.screenOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                || info.screenOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
            config.orientation = Configuration.ORIENTATION_LANDSCAPE;
            if (widthDp < heightDp) {
                config.screenWidthDp = heightDp / 2;
                config.screenHeightDp = widthDp / 2;
            } else {
                config.screenWidthDp = widthDp / 2;
                config.screenHeightDp = heightDp / 2;
            }
        } else {
            config.orientation = Configuration.ORIENTATION_PORTRAIT;
            if (widthDp < heightDp) {
                config.screenWidthDp = widthDp / 2;
                config.screenHeightDp = heightDp / 2;
            } else {
                config.screenWidthDp = heightDp / 2;
                config.screenHeightDp = widthDp / 2;
            }
        }
        Log.v(TAG, "adjustActivityConfig, apply override config=" + config);

        return config;
    }

    // Add for Split-Screen Mode
    public static boolean isSplitModeEnabled() {
        boolean enableSplitScreen = SystemProperties.getInt(
                "persist.sys.mtk.disable.Split", 0) == 0;
        return FEATURE_SUPPORTED && enableSplitScreen;
    }

    // Add for Split-Screen Mode
    public static boolean isSplitMode() {
        if (!isSplitModeEnabled()) {
            return false;
        }
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return false;
            }
            return service.isSplitMode();
        } catch (RemoteException e) {
            Log.e(TAG, "isSplitMode failed!", e);
        }

        return false;
    }

    // Add for Split-Screen Mode
    public static int getMaxFrontFloatingSize() {
        if (isSplitMode()) {
            return 2;
        } else {
            return 4;
        }
    }

    // Add for Split-Screen Mode
    public void switchMode(boolean toSplit) {
        if (!isSplitModeEnabled()) {
            return;
        }
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return;
            }
            service.switchMode(toSplit);
        } catch (RemoteException e) {
            Log.e(TAG, "switchMode failed!", e);
        }
    }

    // Add for Split-Screen Mode
    public void switchToSplitMode() {
        switchMode(true/*toSplit*/);
    }

    // Add for Split-Screen Mode
    public void switchToFloatingMode() {
        switchMode(false/*tofloating*/);
    }

    // Add for Split-Screen Mode
    public void resizeAndMoveStack(int stackId) {
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return;
            }
            service.resizeAndMoveStack(stackId);
        } catch (RemoteException e) {
            Log.e(TAG, "resizeAndMoveStack failed!", e);
        }
    }

    // Add for Split-Screen Mode
    public int getStackPosition(int stackId) {
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return PLACE_NULL;
            }
            return service.getStackPosition(stackId);
        } catch (RemoteException e) {
            Log.e(TAG, "getStackPosition failed!", e);
        }

        return PLACE_NULL;
    }



    public int computeStackPosition(int stackId) {
        if (!isSplitMode())
            return PLACE_NULL;
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return PLACE_NULL;
            }
            return service.computeStackPosition(stackId);
        } catch (RemoteException e) {
            Log.e(TAG, "computeStackPosition failed!", e);
        }
        return PLACE_NULL;
    }

    public void resetStackPosition(int stackId) {
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return;
            }
            service.resetStackPosition(stackId);
        } catch (RemoteException e) {
            Log.e(TAG, "resetStackPosition failed!", e);
        }
    }

    public int getNextFloatStack(int curStackId) {
        if (!isSplitMode())
            return STACK_NULL;
        try {
            IMultiWindowManager service = getServiceInstance();
            if (service == null) {
                Log.e(TAG, "getServiceInstance failed!");
                return STACK_NULL;
            }
            return service.getNextFloatStack(curStackId);
        } catch (RemoteException e) {
            Log.e(TAG, "getNextFloatStack failed!", e);
        }
        return STACK_NULL;
    }
}
