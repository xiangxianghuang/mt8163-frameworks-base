/*
 * Copyright (C) 2013 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.server.wm;

import static com.android.server.wm.WindowManagerService.TAG;
import static com.android.server.wm.WindowManagerService.DEBUG_STACK;

import android.util.EventLog;
import android.util.Slog;
import com.android.server.EventLogTags;

/// M. BMW @{
import static com.android.server.wm.WindowManagerService.DEBUG_TASK_MOVEMENT;
import com.mediatek.multiwindow.MultiWindowProxy;
/// @}

class Task {
    TaskStack mStack;
    final AppTokenList mAppTokens = new AppTokenList();
    final int mTaskId;
    final int mUserId;
    boolean mDeferRemoval = false;
    final WindowManagerService mService;

    Task(int taskId, TaskStack stack, int userId, WindowManagerService service) {
        mTaskId = taskId;
        mStack = stack;
        mUserId = userId;
        mService = service;
    }

    DisplayContent getDisplayContent() {
        return mStack.getDisplayContent();
    }

    void addAppToken(int addPos, AppWindowToken wtoken) {
        final int lastPos = mAppTokens.size();
        if (addPos >= lastPos) {
            addPos = lastPos;
        } else {
            for (int pos = 0; pos < lastPos && pos < addPos; ++pos) {
                if (mAppTokens.get(pos).removed) {
                    // addPos assumes removed tokens are actually gone.
                    ++addPos;
                }
            }
        }
        mAppTokens.add(addPos, wtoken);
        wtoken.mTask = this;
        mDeferRemoval = false;

        /// M: BMW @{
        if (MultiWindowProxy.isSupported()) {
            MultiWindowProxy.getInstance().onAppTokenAdded(wtoken.appToken.asBinder(), mTaskId);
        }
        /// @}
    }

    void removeLocked() {
        /// M: BMW. [ALPS01900878][ALPS01926801]. Task Should be removed
        /// from stack immediately if it is inMimiMax state.
        if (!mAppTokens.isEmpty() && mStack.isAnimating()
                && !isInMiniMax(mTaskId)) {
            if (DEBUG_STACK) Slog.i(TAG, "removeTask: deferring removing taskId=" + mTaskId);
            mDeferRemoval = true;
            return;
        }
        if (DEBUG_STACK) Slog.i(TAG, "removeTask: removing taskId=" + mTaskId);
        EventLog.writeEvent(EventLogTags.WM_TASK_REMOVED, mTaskId, "removeTask");
        mDeferRemoval = false;
        mStack.removeTask(this);
        /// M: BMW. Don't delete from mTaskIdToTask when the task is inMimiMax state.
        if (!MultiWindowProxy.getInstance().isInMiniMax(mTaskId)) {
            mService.mTaskIdToTask.delete(mTaskId);
            /// M: BMW. Add more log information
            if (DEBUG_TASK_MOVEMENT) Slog.d(TAG, "[BMW] removeTask:delete task from mTaskIdToTask");
        }
    }

    void moveTaskToStack(TaskStack stack, boolean toTop) {
        if (stack == mStack) {
            return;
        }
        if (DEBUG_STACK) Slog.i(TAG, "moveTaskToStack: removing taskId=" + mTaskId
                + " from stack=" + mStack);
        EventLog.writeEvent(EventLogTags.WM_TASK_REMOVED, mTaskId, "moveTask");
        if (mStack != null) {
            mStack.removeTask(this);
        }
        stack.addTask(this, toTop);
    }

    boolean removeAppToken(AppWindowToken wtoken) {
        boolean removed = mAppTokens.remove(wtoken);
        /// M: BMW @{
        if (MultiWindowProxy.isSupported()) {
            MultiWindowProxy.getInstance().onAppTokenRemoved(wtoken.appToken.asBinder(), mTaskId);
        }
        /// @}
        if (mAppTokens.size() == 0) {
            EventLog.writeEvent(EventLogTags.WM_TASK_REMOVED, mTaskId,
                    "removeAppToken: last token");
            if (mDeferRemoval) {
                removeLocked();
            }
        }
        wtoken.mTask = null;
        /* Leave mTaskId for now, it might be useful for debug
        wtoken.mTaskId = -1;
         */
        return removed;
    }

    void setSendingToBottom(boolean toBottom) {
        for (int appTokenNdx = 0; appTokenNdx < mAppTokens.size(); appTokenNdx++) {
            mAppTokens.get(appTokenNdx).sendingToBottom = toBottom;
        }
    }

    boolean showForAllUsers() {
        final int tokensCount = mAppTokens.size();
        return (tokensCount != 0) && mAppTokens.get(tokensCount - 1).showForAllUsers;
    }

    @Override
    public String toString() {
        return "{taskId=" + mTaskId + " appTokens=" + mAppTokens + " mdr=" + mDeferRemoval + "}";
    }

    /// M: BMW. Add for Multi-Window Begin @{
    private boolean isInMiniMax(int taskId) {
        if (MultiWindowProxy.isSupported()) {
            return MultiWindowProxy.getInstance().isInMiniMax(taskId);
        }
        return false;
    }
    /// M. BMW. Add for Multi-Window End @}
}
