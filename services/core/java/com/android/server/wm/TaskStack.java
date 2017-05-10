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

import static com.android.server.wm.WindowManagerService.DEBUG_TASK_MOVEMENT;
import static com.android.server.wm.WindowManagerService.TAG;

import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Debug;
import android.util.DisplayMetrics;
import android.util.EventLog;
import android.util.Slog;
import android.util.TypedValue;
import android.view.Surface;

import com.android.server.EventLogTags;

import java.io.PrintWriter;
import java.util.ArrayList;

/// M: Add import.
import static com.android.server.wm.WindowManagerService.DEBUG_STACK;

/// M: BMW. @{
import android.content.pm.ActivityInfo;
import android.view.DisplayInfo;
import com.mediatek.multiwindow.MultiWindowProxy;
/// @}

public class TaskStack {
    /** Amount of time in milliseconds to animate the dim surface from one value to another,
     * when no window animation is driving it. */
    private static final int DEFAULT_DIM_DURATION = 200;

    /** Unique identifier */
    final int mStackId;

    /** The service */
    private final WindowManagerService mService;

    /** The display this stack sits under. */
    private DisplayContent mDisplayContent;

    /** The Tasks that define this stack. Oldest Tasks are at the bottom. The ordering must match
     * mTaskHistory in the ActivityStack with the same mStackId */
    private final ArrayList<Task> mTasks = new ArrayList<Task>();

    /** For comparison with DisplayContent bounds. */
    private Rect mTmpRect = new Rect();
    /** For handling display rotations. */
    private Rect mTmpRect2 = new Rect();

    /** Content limits relative to the DisplayContent this sits in. */
    private Rect mBounds = new Rect();

    /** Whether mBounds is fullscreen */
    private boolean mFullscreen = true;

    /** Used to support {@link android.view.WindowManager.LayoutParams#FLAG_DIM_BEHIND} */
    private DimLayer mDimLayer;

    /** The particular window with FLAG_DIM_BEHIND set. If null, hide mDimLayer. */
    WindowStateAnimator mDimWinAnimator;

    /** Support for non-zero {@link android.view.animation.Animation#getBackgroundColor()} */
    DimLayer mAnimationBackgroundSurface;

    /** The particular window with an Animation with non-zero background color. */
    WindowStateAnimator mAnimationBackgroundAnimator;

    /** Set to false at the start of performLayoutAndPlaceSurfaces. If it is still false by the end
     * then stop any dimming. */
    boolean mDimmingTag;

    /** Application tokens that are exiting, but still on screen for animations. */
    final AppTokenList mExitingAppTokens = new AppTokenList();

    /** Detach this stack from its display when animation completes. */
    boolean mDeferDetach;

    // Contains configurations settings that are different from the global configuration due to
    // stack specific operations. E.g. {@link #setBounds}.
    Configuration mOverrideConfig;
    // True if the stack was forced to fullscreen disregarding the override configuration.
    private boolean mForceFullscreen;
    // The {@link #mBounds} before the stack was forced to fullscreen. Will be restored as the
    // stack bounds once the stack is no longer forced to fullscreen.
    final private Rect mPreForceFullscreenBounds;

    // Device rotation as of the last time {@link #mBounds} was set.
    int mRotation;

    TaskStack(WindowManagerService service, int stackId) {
        mService = service;
        mStackId = stackId;
        mOverrideConfig = Configuration.EMPTY;
        mForceFullscreen = false;
        mPreForceFullscreenBounds = new Rect();
        // TODO: remove bounds from log, they are always 0.
        EventLog.writeEvent(EventLogTags.WM_STACK_CREATED, stackId, mBounds.left, mBounds.top,
                mBounds.right, mBounds.bottom);
    }

    DisplayContent getDisplayContent() {
        return mDisplayContent;
    }

    ArrayList<Task> getTasks() {
        return mTasks;
    }

    void resizeWindows() {
        final ArrayList<WindowState> resizingWindows = mService.mResizingWindows;
        for (int taskNdx = mTasks.size() - 1; taskNdx >= 0; --taskNdx) {
            final ArrayList<AppWindowToken> activities = mTasks.get(taskNdx).mAppTokens;
            for (int activityNdx = activities.size() - 1; activityNdx >= 0; --activityNdx) {
                final ArrayList<WindowState> windows = activities.get(activityNdx).allAppWindows;
                for (int winNdx = windows.size() - 1; winNdx >= 0; --winNdx) {
                    final WindowState win = windows.get(winNdx);
                    if (!resizingWindows.contains(win)) {
                        if (WindowManagerService.DEBUG_RESIZE) Slog.d(TAG,
                                "setBounds: Resizing " + win);
                        resizingWindows.add(win);
                    }
                }
            }
        }
    }

    /** Set the stack bounds. Passing in null sets the bounds to fullscreen. */
    boolean setBounds(Rect bounds) {
        /// M: Add debug info.
        if (DEBUG_STACK) {
            Slog.d(TAG, "setBounds bound = " + bounds
                    + ", stackId = " + mStackId, new Throwable("setBounds"));
        }

        boolean oldFullscreen = mFullscreen;
        int rotation = Surface.ROTATION_0;
        if (mDisplayContent != null) {
            mDisplayContent.getLogicalDisplayRect(mTmpRect);
            rotation = mDisplayContent.getDisplayInfo().rotation;
            if (bounds == null) {
                bounds = mTmpRect;
                mFullscreen = true;
            } else {
                // ensure bounds are entirely within the display rect
                /// M. BMW. floatwindow can set bounds outside the containing display
                if (MultiWindowProxy.isSupported()
                        && MultiWindowProxy.getInstance().isFloatingStack(mStackId)) {
                    //floating window do nothing
                } else {
                    if (!bounds.intersect(mTmpRect)) {
                        // Can't set bounds outside the containing display.. Sorry!
                        return false;
                    }
                }
                mFullscreen = mTmpRect.equals(bounds);
            }
        }

        if (bounds == null) {
            // Can't set to fullscreen if we don't have a display to get bounds from...
            return false;
        }
        if (MultiWindowProxy.isSupported()
                && MultiWindowProxy.getInstance().isFloatingStack(mStackId)) {
            verifyStackBounds(bounds);
        }
        /// @}
        if (mBounds.equals(bounds) && oldFullscreen == mFullscreen && mRotation == rotation) {
            return false;
        }

        mDimLayer.setBounds(bounds);
        mAnimationBackgroundSurface.setBounds(bounds);

        /// M: BMW. Add BackgroundSurface for floating stack @{
        if (MultiWindowProxy.isSupported()) {
            if (mStackBackgroundSurface != null) {
                mStackBackgroundSurface.setBounds(bounds);
            }
            /// M: [ALPS01876704] Need add to mResizingWindows
            /// when float stack bounds change
            resizeWindows();
        }
        /// @}
        mBounds.set(bounds);
        mRotation = rotation;
        updateOverrideConfiguration();
        return true;
    }

    void getBounds(Rect out) {
        out.set(mBounds);
    }

    private void updateOverrideConfiguration() {
        final Configuration serviceConfig = mService.mCurConfiguration;
        if (mFullscreen) {
            mOverrideConfig = Configuration.EMPTY;
            return;
        }

        if (mOverrideConfig == Configuration.EMPTY) {
            mOverrideConfig  = new Configuration();
        }

        // TODO(multidisplay): Update Dp to that of display stack is on.
        final float density = serviceConfig.densityDpi * DisplayMetrics.DENSITY_DEFAULT_SCALE;
        mOverrideConfig.screenWidthDp =
                Math.min((int)(mBounds.width() / density), serviceConfig.screenWidthDp);
        mOverrideConfig.screenHeightDp =
                Math.min((int)(mBounds.height() / density), serviceConfig.screenHeightDp);
        mOverrideConfig.smallestScreenWidthDp =
                Math.min(mOverrideConfig.screenWidthDp, mOverrideConfig.screenHeightDp);
        mOverrideConfig.orientation =
                (mOverrideConfig.screenWidthDp <= mOverrideConfig.screenHeightDp)
                        ? Configuration.ORIENTATION_PORTRAIT : Configuration.ORIENTATION_LANDSCAPE;
    }

    void updateDisplayInfo() {
        /// M. BMW. Floating windwo no need update @{
        if (MultiWindowProxy.isSupported()
               && MultiWindowProxy.getInstance().isFloatingStack(mStackId))
            return;
        /// @}
        if (mFullscreen) {
            setBounds(null);
        } else if (mDisplayContent != null) {
            final int newRotation = mDisplayContent.getDisplayInfo().rotation;
            if (mRotation == newRotation) {
                return;
            }

            // Device rotation changed. We don't want the stack to move around on the screen when
            // this happens, so update the stack bounds so it stays in the same place.
            final int rotationDelta = DisplayContent.deltaRotation(mRotation, newRotation);
            mDisplayContent.getLogicalDisplayRect(mTmpRect);
            switch (rotationDelta) {
                case Surface.ROTATION_0:
                    mTmpRect2.set(mBounds);
                    break;
                case Surface.ROTATION_90:
                    mTmpRect2.top = mTmpRect.bottom - mBounds.right;
                    mTmpRect2.left = mBounds.top;
                    mTmpRect2.right = mTmpRect2.left + mBounds.height();
                    mTmpRect2.bottom = mTmpRect2.top + mBounds.width();
                    break;
                case Surface.ROTATION_180:
                    mTmpRect2.top = mTmpRect.bottom - mBounds.bottom;
                    mTmpRect2.left = mTmpRect.right - mBounds.right;
                    mTmpRect2.right = mTmpRect2.left + mBounds.width();
                    mTmpRect2.bottom = mTmpRect2.top + mBounds.height();
                    break;
                case Surface.ROTATION_270:
                    mTmpRect2.top = mBounds.left;
                    mTmpRect2.left = mTmpRect.right - mBounds.bottom;
                    mTmpRect2.right = mTmpRect2.left + mBounds.height();
                    mTmpRect2.bottom = mTmpRect2.top + mBounds.width();
                    break;
            }
            setBounds(mTmpRect2);
        }
    }

    boolean isFullscreen() {
        return mFullscreen;
    }

    /** Forces the stack to fullscreen if input is true, else un-forces the stack from fullscreen.
     * Returns true if something happened.
     */
    boolean forceFullscreen(boolean forceFullscreen) {
        if (mForceFullscreen == forceFullscreen) {
            return false;
        }
        mForceFullscreen = forceFullscreen;
        if (forceFullscreen) {
            if (mFullscreen) {
                return false;
            }
            mPreForceFullscreenBounds.set(mBounds);
            return setBounds(null);
        } else {
            if (!mFullscreen || mPreForceFullscreenBounds.isEmpty()) {
                return false;
            }
            return setBounds(mPreForceFullscreenBounds);
        }
    }

    boolean isAnimating() {
        for (int taskNdx = mTasks.size() - 1; taskNdx >= 0; --taskNdx) {
            final ArrayList<AppWindowToken> activities = mTasks.get(taskNdx).mAppTokens;
            for (int activityNdx = activities.size() - 1; activityNdx >= 0; --activityNdx) {
                final ArrayList<WindowState> windows = activities.get(activityNdx).allAppWindows;
                for (int winNdx = windows.size() - 1; winNdx >= 0; --winNdx) {
                    final WindowStateAnimator winAnimator = windows.get(winNdx).mWinAnimator;
                    if (winAnimator.isAnimating() || winAnimator.mWin.mExiting) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    void addTask(Task task, boolean toTop) {
        addTask(task, toTop, task.showForAllUsers());
    }

    /**
     * Put a Task in this stack. Used for adding and moving.
     * @param task The task to add.
     * @param toTop Whether to add it to the top or bottom.
     * @param showForAllUsers Whether to show the task regardless of the current user.
     */
    void addTask(Task task, boolean toTop, boolean showForAllUsers) {
        int stackNdx;
        if (!toTop) {
            stackNdx = 0;
        } else {
            stackNdx = mTasks.size();
            if (!showForAllUsers && !mService.isCurrentProfileLocked(task.mUserId)) {
                // Place the task below all current user tasks.
                while (--stackNdx >= 0) {
                    final Task tmpTask = mTasks.get(stackNdx);
                    if (!tmpTask.showForAllUsers()
                            || !mService.isCurrentProfileLocked(tmpTask.mUserId)) {
                        break;
                    }
                }
                // Put it above first non-current user task.
                ++stackNdx;
            }
        }
        if (DEBUG_TASK_MOVEMENT) Slog.d(TAG, "addTask: task=" + task + " toTop=" + toTop
                + " pos=" + stackNdx);
        mTasks.add(stackNdx, task);

        task.mStack = this;
        /// M: BMW. Find the 1st appwindow token and init the stack size @{
        /// [ALPS02058853]. Avoid apptoken size is zero since
        /// ams removeAppToken before removing task in removeActivityFromHistoryLocked
        if (MultiWindowProxy.isSupported()) {
            MultiWindowProxy.getInstance().onTaskAdded(task.mTaskId, mStackId);
            initFloatStackSize();
        }
        /// @}

        if (toTop) {
            mDisplayContent.moveStack(this, true);
        }
        EventLog.writeEvent(EventLogTags.WM_TASK_MOVED, task.mTaskId, toTop ? 1 : 0, stackNdx);
    }

    void moveTaskToTop(Task task) {
        if (DEBUG_TASK_MOVEMENT) Slog.d(TAG, "moveTaskToTop: task=" + task + " Callers="
                + Debug.getCallers(6));
        mTasks.remove(task);
        addTask(task, true);
        /// M: BMW. [ALPS02523483] Update stack size for split mode @{
        if (MultiWindowProxy.isSupported()) {
            updateStackSizeIfNeeded();
        }
        /// @}
    }

    void moveTaskToBottom(Task task) {
        if (DEBUG_TASK_MOVEMENT) Slog.d(TAG, "moveTaskToBottom: task=" + task);
        mTasks.remove(task);
        addTask(task, false);
    }

    /**
     * Delete a Task from this stack. If it is the last Task in the stack, move this stack to the
     * back.
     * @param task The Task to delete.
     */
    void removeTask(Task task) {
        if (DEBUG_TASK_MOVEMENT) Slog.d(TAG, "removeTask: task=" + task);
        mTasks.remove(task);
        /// M: BMW.@{
        if (MultiWindowProxy.isSupported()) {
            MultiWindowProxy.getInstance().onTaskRemoved(task.mTaskId, mStackId);
        }
        /// @}
        if (mDisplayContent != null) {
            if (mTasks.isEmpty()) {
                mDisplayContent.moveStack(this, false);
            }
            mDisplayContent.layoutNeeded = true;
        }
        for (int appNdx = mExitingAppTokens.size() - 1; appNdx >= 0; --appNdx) {
            final AppWindowToken wtoken = mExitingAppTokens.get(appNdx);
            if (wtoken.mTask == task) {
                wtoken.mIsExiting = false;
                mExitingAppTokens.remove(appNdx);
            }
        }
    }

    void attachDisplayContent(DisplayContent displayContent) {
        if (mDisplayContent != null) {
            throw new IllegalStateException("attachDisplayContent: Already attached");
        }

        mDisplayContent = displayContent;
        mDimLayer = new DimLayer(mService, this, displayContent);
        mAnimationBackgroundSurface = new DimLayer(mService, this, displayContent);

        /// M: BMW. Add black background for floating stack @{
        if (MultiWindowProxy.isSupported()
                // MultiWindowProxy.getInstance().isStackBackgroundEnabled()
                && MultiWindowProxy.getInstance().isFloatingStack(mStackId)) {
            mStackBackgroundEnabled = true;
            mStackBackgroundSurface = new StackBackgroundSurface(mService, this, displayContent);
            mStackBackgroundSurface.prepareSurface();
        }
        /// @}
        updateDisplayInfo();
    }

    void detachDisplay() {
        EventLog.writeEvent(EventLogTags.WM_STACK_REMOVED, mStackId);

        boolean doAnotherLayoutPass = false;
        for (int taskNdx = mTasks.size() - 1; taskNdx >= 0; --taskNdx) {
            final AppTokenList appWindowTokens = mTasks.get(taskNdx).mAppTokens;
            for (int appNdx = appWindowTokens.size() - 1; appNdx >= 0; --appNdx) {
                final WindowList appWindows = appWindowTokens.get(appNdx).allAppWindows;
                for (int winNdx = appWindows.size() - 1; winNdx >= 0; --winNdx) {
                    // We are in the middle of changing the state of displays/stacks/tasks. We need
                    // to finish that, before we let layout interfere with it.
                    mService.removeWindowInnerLocked(appWindows.get(winNdx),
                            false /* performLayout */);
                    doAnotherLayoutPass = true;
                }
            }
        }
        if (doAnotherLayoutPass) {
            mService.requestTraversalLocked();
        }

        close();
    }

    void resetAnimationBackgroundAnimator() {
        mAnimationBackgroundAnimator = null;
        if (mAnimationBackgroundSurface != null) {
            mAnimationBackgroundSurface.hide();
        }
    }

    private long getDimBehindFadeDuration(long duration) {
        TypedValue tv = new TypedValue();
        mService.mContext.getResources().getValue(
                com.android.internal.R.fraction.config_dimBehindFadeDuration, tv, true);
        if (tv.type == TypedValue.TYPE_FRACTION) {
            duration = (long)tv.getFraction(duration, duration);
        } else if (tv.type >= TypedValue.TYPE_FIRST_INT && tv.type <= TypedValue.TYPE_LAST_INT) {
            duration = tv.data;
        }
        return duration;
    }

    boolean animateDimLayers() {
        final int dimLayer;
        final float dimAmount;
        if (mDimWinAnimator == null) {
            dimLayer = mDimLayer.getLayer();
            dimAmount = 0;
        } else {
            dimLayer = mDimWinAnimator.mAnimLayer - WindowManagerService.LAYER_OFFSET_DIM;
            dimAmount = mDimWinAnimator.mWin.mAttrs.dimAmount;
        }
        final float targetAlpha = mDimLayer.getTargetAlpha();
        if (targetAlpha != dimAmount) {
            if (mDimWinAnimator == null) {
                mDimLayer.hide(DEFAULT_DIM_DURATION);
            } else {
                long duration = (mDimWinAnimator.mAnimating && mDimWinAnimator.mAnimation != null)
                        ? mDimWinAnimator.mAnimation.computeDurationHint()
                        : DEFAULT_DIM_DURATION;
                if (targetAlpha > dimAmount) {
                    duration = getDimBehindFadeDuration(duration);
                }
                /// M: BMW. [ALPS01891760]. Disable dimlayer if
                /// mDimWinAnimator is the bottom window @{
                if (MultiWindowProxy.isSupported()
                        && ((mDimWinAnimator != null && mDimWinAnimator.mWin == getBottomWindow())
                             || getBottomWindow() == null)) {
                    if (DEBUG_STACK) {
                        Slog.v(TAG, "[BMW]mDimWinAnimator is the bottom window, "
                                + "do not show dimlayer");
                    }
                } else {
                    mDimLayer.show(dimLayer, dimAmount, duration);
                }
                /// @}
            }
        } else if (mDimLayer.getLayer() != dimLayer) {
            mDimLayer.setLayer(dimLayer);
        }
        if (mDimLayer.isAnimating()) {
            if (!mService.okToDisplay()) {
                // Jump to the end of the animation.
                /// M: BMW. [ALPS01891760]. Disable dimlayer if
                /// mDimWinAnimator is the bottom window @{
                if (MultiWindowProxy.isSupported()
                        && ((mDimWinAnimator != null && mDimWinAnimator.mWin == getBottomWindow())
                             || getBottomWindow() == null)) {
                    if (DEBUG_STACK) {
                        Slog.v(TAG, "[BMW]mDimWinAnimator is the bottom window,"
                                + " do not show dimlayer");
                    }
                } else {
                    mDimLayer.show();
                }
                /// @}
            } else {
                return mDimLayer.stepAnimation();
            }
        }
        return false;
    }

    void resetDimmingTag() {
        mDimmingTag = false;
    }

    void setDimmingTag() {
        mDimmingTag = true;
    }

    boolean testDimmingTag() {
        return mDimmingTag;
    }

    boolean isDimming() {
        if (mDimLayer == null) {
            return false;
        }
        return mDimLayer.isDimming();
    }

    boolean isDimming(WindowStateAnimator winAnimator) {
        return mDimWinAnimator == winAnimator && isDimming();
    }

    void startDimmingIfNeeded(WindowStateAnimator newWinAnimator) {
        // Only set dim params on the highest dimmed layer.
        // Don't turn on for an unshown surface, or for any layer but the highest dimmed layer.
        if (newWinAnimator.mSurfaceShown && (mDimWinAnimator == null
                || !mDimWinAnimator.mSurfaceShown
                || mDimWinAnimator.mAnimLayer < newWinAnimator.mAnimLayer)) {
            mDimWinAnimator = newWinAnimator;
            if (mDimWinAnimator.mWin.mAppToken == null
                    && !mFullscreen && mDisplayContent != null) {
                // Dim should cover the entire screen for system windows.
                mDisplayContent.getLogicalDisplayRect(mTmpRect);
                mDimLayer.setBounds(mTmpRect);
            }
        }
    }

    void stopDimmingIfNeeded() {
        if (!mDimmingTag && isDimming()) {
            mDimWinAnimator = null;
            /// M. BMW. Adjust dimlayer bounds for floatstack offset @{
            if (MultiWindowProxy.isSupported()) {
                int offsets[] = new int[2];
                Rect bounds = new Rect();
                getStackOffsets(offsets);
                bounds.set(mBounds);
                bounds.left += offsets[0];
                bounds.right += offsets[0];
                bounds.top += offsets[1];
                bounds.bottom += offsets[1];
                mDimLayer.setBounds(bounds);
            } else {
                mDimLayer.setBounds(mBounds);
            }
            /// @}
        }
    }

    void setAnimationBackground(WindowStateAnimator winAnimator, int color) {
        int animLayer = winAnimator.mAnimLayer;
        if (mAnimationBackgroundAnimator == null
                || animLayer < mAnimationBackgroundAnimator.mAnimLayer) {
            mAnimationBackgroundAnimator = winAnimator;
            animLayer = mService.adjustAnimationBackground(winAnimator);
            mAnimationBackgroundSurface.show(animLayer - WindowManagerService.LAYER_OFFSET_DIM,
                    ((color >> 24) & 0xff) / 255f, 0);
        }
    }

    void switchUser() {
        int top = mTasks.size();
        for (int taskNdx = 0; taskNdx < top; ++taskNdx) {
            Task task = mTasks.get(taskNdx);
            if (mService.isCurrentProfileLocked(task.mUserId) || task.showForAllUsers()) {
                mTasks.remove(taskNdx);
                mTasks.add(task);
                --top;
            }
        }
    }

    void close() {
        if (mAnimationBackgroundSurface != null) {
            mAnimationBackgroundSurface.destroySurface();
            mAnimationBackgroundSurface = null;
        }
        if (mDimLayer != null) {
            mDimLayer.destroySurface();
            mDimLayer = null;
        }
        /// M: BMW. Disable background for floating stack @{
        if (MultiWindowProxy.isSupported()
                    && mStackBackgroundSurface != null) {
            mStackBackgroundSurface.destroySurface();
            mStackBackgroundSurface = null;
        }
        /// @}
        mDisplayContent = null;
    }

    public void dump(String prefix, PrintWriter pw) {
        pw.print(prefix); pw.print("mStackId="); pw.println(mStackId);
        pw.print(prefix); pw.print("mDeferDetach="); pw.println(mDeferDetach);
        for (int taskNdx = 0; taskNdx < mTasks.size(); ++taskNdx) {
            pw.print(prefix); pw.println(mTasks.get(taskNdx));
        }
        if (mAnimationBackgroundSurface.isDimming()) {
            pw.print(prefix); pw.println("mWindowAnimationBackgroundSurface:");
            mAnimationBackgroundSurface.printTo(prefix + "  ", pw);
        }
        if (mDimLayer.isDimming()) {
            pw.print(prefix); pw.println("mDimLayer:");
            mDimLayer.printTo(prefix + " ", pw);
            pw.print(prefix); pw.print("mDimWinAnimator="); pw.println(mDimWinAnimator);
        }
        if (!mExitingAppTokens.isEmpty()) {
            pw.println();
            pw.println("  Exiting application tokens:");
            for (int i=mExitingAppTokens.size()-1; i>=0; i--) {
                WindowToken token = mExitingAppTokens.get(i);
                pw.print("  Exiting App #"); pw.print(i);
                pw.print(' '); pw.print(token);
                pw.println(':');
                token.dump(pw, "    ");
            }
        }
        /// M: BMW. Dump more info about multi window@{
        if (MultiWindowProxy.isSupported()) {
            dumpOthers(prefix, pw);
        }
        /// @}
    }

    @Override
    public String toString() {
        return "{stackId=" + mStackId + " tasks=" + mTasks + "}";
    }

    /// M: BMW. Add for Multi-Window Begin @{
    /// M: BMW. Floating layout policy @{
    private boolean initFloatStackSize() {
        if (mInited) {
            Slog.e(TAG, "[BMW]Floating stack had been inited!");
            return false;
        }

        if (!MultiWindowProxy.getInstance().isFloatingStack(mStackId)) {
            Slog.e(TAG, "[BMW]Non floating stack did the function initFloatStackSize");
            return false;
        }

        Rect bounds = computeStackSize();
        if (bounds == null) {
            return false;
        }
        setBounds(bounds);
        if (DEBUG_STACK) {
            Slog.d(TAG, "[BMW]initFloatStackSize mBounds = " + mBounds);
        }
        mDisplayRotation = mDisplayContent.getDisplayInfo().rotation;
        mXOffset = 0;
        mYOffset = 0;
        mInited = true;
        return true;
    }

    private int deltaRotation(int rotation) {
        int delta = rotation - mDisplayRotation;
        if (delta < 0) delta += 4;
        return delta;
    }

    private void rotateBounds(int rotation, int displayWidth, int displayHeight) {
        int pivotX = mBounds.centerX();
        int pivotY = mBounds.centerY();
        int tmpX = 0, tmpY = 0;
        Rect tmpbounds = new Rect();

        switch (deltaRotation(rotation)) {
            case Surface.ROTATION_90:
                // Calculate the pivot point after rotating 90 degree
                tmpX = pivotY;
                tmpY = displayHeight - pivotX;

                // Calculate the top point after rotating 90 degree
                tmpX -= mBounds.width() / 2;
                tmpY -= mBounds.height() / 2;
                break;
            case Surface.ROTATION_180:
                // Calculate the pivot point after rotating 180 degree
                tmpX = displayWidth - pivotX;
                tmpY = displayHeight - pivotY;
                // Calculate the top point after rotating 180 degree
                tmpX -= mBounds.width() / 2;
                tmpY -= mBounds.height() / 2;
                break;
            case Surface.ROTATION_270:
                // Calculate the pivot point after rotating 270 degree
                tmpX = displayWidth - pivotY;
                tmpY = pivotX;
                // Calculate the top point after rotating 270 degree
                tmpX -= mBounds.width() / 2;
                tmpY -= mBounds.height() / 2;
                break;
            case Surface.ROTATION_0:
            default:
                Slog.e(TAG, "[BMW]rotateBounds exception, rotation = " + rotation
                                + ", mDisplayRotation = " + mDisplayRotation);
                break;

        }
        /// M: use tmpbounds replace mBounds.fix set mBounds too early
        ///then dimlayer can't be seted when rotate[ALPS01882560]
        tmpbounds.set(mBounds);
        tmpbounds.offsetTo(tmpX, tmpY);

        verifyStackBounds(tmpbounds);
        setBounds(tmpbounds);
        /// DimLayer control can be better in future.
        if (mDimLayer.isDimming()) {
            mDimLayer.hide();
            mDimLayer.show();
        }
        mDisplayRotation = rotation;

    }

    private void computeBoundaryLimit() {
        DisplayInfo displayInfo = mDisplayContent.getDisplayInfo();

        mLeftBoundLimit = STACK_BOUNDS_MARGIN_DP * displayInfo.logicalDensityDpi / 160;
        mRightBoundLimit = displayInfo.appWidth - mLeftBoundLimit;

        /// M: ALPS01669346 Compute top bound and bottom bound limit @{
        Rect tempContent = new Rect();
        mDisplayContent.mService.mPolicy.getContentRectLw(tempContent);
        mTopBoundLimit = tempContent.top;
        mBottomBoundLimit = tempContent.bottom
                - FLOAT_CONTROL_BAR_HEIGHT_DP * displayInfo.logicalDensityDpi / 160;

        mRightBoundLimitFirstLaunch = displayInfo.appWidth;
        mBottomBoundLimitFirstLaunch = tempContent.bottom;

        /// @}
        mStacksOffset = STACKS_OFFSET_MARGIN_DP * displayInfo.logicalDensityDpi / 160;
        if (DEBUG_STACK) {
            Slog.d(TAG, "[BMW]computeBoundaryLimit mTopBoundLimit = "
                                + mTopBoundLimit
                                + ", mBottomBoundLimit = " + mBottomBoundLimit
                                + ", mStacksOffset = " + mStacksOffset);
        }
    }

    Rect computeStackSize() {
        computeTopFloatStack();
        if (MultiWindowProxy.isSplitMode()) {
            return computeStackSizeForSplit();
        } else {
            return computeStackSizeForFloating();
        }
    }

    // The api is designed for the StackBox
    // if the founds is outside the display scope,
    // it should be back to the display content.
    // Based on the UX spec, the left and right margin
    // is 220 dp
    private void verifyStackBounds(Rect bounds) {
        /// M: Add for Split Mode @{
        if (MultiWindowProxy.isSplitMode()) {
            verifyStackBoundsForSplit(bounds);
            return;
        }
        /// @}
        DisplayInfo displayInfo = mDisplayContent.getDisplayInfo();
        computeBoundaryLimit();

        if (bounds.right < mLeftBoundLimit) {
            bounds.offsetTo(bounds.left + (mLeftBoundLimit - bounds.right), bounds.top);
        }

        if (bounds.left > mRightBoundLimit) {
            bounds.offsetTo(mRightBoundLimit, bounds.top);
        }

        if (bounds.top < mTopBoundLimit) {
            bounds.offsetTo(bounds.left, mTopBoundLimit);
        }

        if (bounds.top > mBottomBoundLimit) {
            bounds.offsetTo(bounds.left, mBottomBoundLimit);
        }

        if (bounds.width() > displayInfo.appWidth) {
            bounds.right -= bounds.width() - displayInfo.appWidth;
        }

        if (bounds.height() > displayInfo.appHeight) {
            bounds.bottom -= bounds.height() - displayInfo.appHeight;
        }
    }

    public Rect getStackBounds(int rotation, int displayWidth, int displayHeight) {
        if (mDisplayRotation == rotation) {
            return mBounds;
        }

        /// Need to rotate the mBounds
        /// Add for Split Mode
        if (MultiWindowProxy.isSplitMode()) {
            rotateBoundsForSpilt(rotation, displayWidth, displayHeight);
        } else {
            rotateBounds(rotation, displayWidth, displayHeight);
        }
        return mBounds;
    }

    public void adjustFloatingRect(int xOffset, int yOffset) {
        if (mXOffset != xOffset || mYOffset != yOffset) {
            Rect bounds = new Rect();
            bounds.set(mBounds);
            bounds.left += xOffset;
            bounds.right += xOffset;
            bounds.top += yOffset;
            bounds.bottom += yOffset;
            mDimLayer.setBounds(bounds);
        }
        mXOffset = xOffset;
        mYOffset = yOffset;
    }

    public void getStackOffsets(int[] offsets) {
        if (offsets == null || offsets.length < 2) {
            throw new IllegalArgumentException("offsets must be an array of two integers");
        }
        offsets[0] = mXOffset;
        offsets[1] = mYOffset;
    }

    public void dumpOthers(String prefix, PrintWriter pw) {
        pw.print(prefix); pw.print("FLOATING LAYOUT POLICY INFO:");
        pw.print(prefix); pw.print(prefix);
        pw.print("Bounds="); pw.print(mBounds);
        pw.print(", "); pw.print("mDisplayRotation="); pw.print(mDisplayRotation);
        pw.print(", "); pw.print("Launch Mode=");
        if (mOrientation ==
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                || mOrientation ==
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT) {
            pw.println("Portrait");
        } else {
            pw.println("Landscape");
        }

        pw.print(prefix); pw.print(prefix);
        pw.print("mXOffset="); pw.print(mXOffset); pw.print(", ");
        pw.print("mYOffset="); pw.println(mYOffset);
        pw.print(prefix); pw.print(prefix);
        pw.print("mFloatStackPortWidth="); pw.print(mFloatStackPortWidth); pw.print(", ");
        pw.print("mFloatStackPortHeight="); pw.println(mFloatStackPortHeight);
        pw.print(prefix); pw.print(prefix);
        pw.print("mFloatStackLandWidth="); pw.print(mFloatStackLandWidth); pw.print(", ");
        pw.print("mFloatStackLandHeight="); pw.println(mFloatStackLandHeight);
        pw.print(prefix); pw.print(prefix);
        pw.print("mTopBoundLimit="); pw.print(mTopBoundLimit); pw.print(", ");
        pw.print("mBottomBoundLimit="); pw.print(mBottomBoundLimit); pw.print(", ");
        pw.print("mRightBoundLimit="); pw.print(mRightBoundLimit); pw.print(", ");
        pw.print("mLeftBoundLimit="); pw.println(mLeftBoundLimit);
        pw.print(prefix); pw.print(prefix);
        pw.print("mStacksOffset="); pw.print(mStacksOffset); pw.print(", ");
        pw.print("mTopFloatStack="); pw.println(mTopFloatStack);
        pw.println();

    }
    /// M: BMW. Add for multi window floating layout policy @}

    /// M: BMW. Add black background for floating stack. @{
    void setStackBackground(boolean forceShow) {
        if (!needShowStackBackground()) {
            resetStackBackgroundAnimator();
            return;
        }
        WindowStateAnimator winAnimator = adjustStackBackgroundAnimator();
        if (DEBUG_STACK) Slog.v(TAG, "[BMW]setStackBackground winAnimator = " + winAnimator);
        if (winAnimator == null) {
            resetStackBackgroundAnimator();
            return;
        }
        boolean needUpdata = false;
        if (mStackBackgroundAnimator == null
                || winAnimator != mStackBackgroundAnimator) {
            mStackBackgroundAnimator = winAnimator;
            needUpdata = true;
        }
        int animLayer = adjustStackBackgroundLayer();
        if (animLayer < 0) {
            animLayer = mStackBackgroundAnimator.mAnimLayer;
        }
        mStackBackgroundSurface.setLayer(animLayer - 4);
        if (DEBUG_STACK) {
            Slog.v(TAG, "[BMW]setStackBackground mStackBackgroundAnimator = "
                    + mStackBackgroundAnimator + ", animLayer:" + animLayer);
        }
        if (forceShow)
            mStackBackgroundSurface.show();
    }

     /// M: BMW. Find a WindowStateAnimator for
     /// controlling the size, pos, matrix of mStackBackgroundSurface
     private WindowStateAnimator adjustStackBackgroundAnimator() {
        WindowList windows = mDisplayContent.getWindowList();
        for (int winNdx = windows.size() - 1; winNdx >= 0; winNdx--) {
            final WindowState win = windows.get(winNdx);
            if (win.mAppToken == null)
                continue;
            Task task = win.mAppToken.mTask;
            if (task == null || (task != null && task.mStack == null))
                continue;
            int stackId = task.mStack.mStackId;
            if (win.isFullFloatWindow() && stackId == mStackId
                    && win.isVisibleOrBehindKeyguardLw()) {
                if (DEBUG_STACK) Slog.v(TAG, "[BMW]adjustStackBackgroundAnimator WinAnimator:"
                            + win.mWinAnimator);
                return win.mWinAnimator;
            }
        }
        return null;
    }

    /// M: BMW. Compute the layer of mStackBackgroundSurface
    private int adjustStackBackgroundLayer() {
        WindowList windows = mDisplayContent.getWindowList();
        for (int winNdx = 0; winNdx < windows.size(); winNdx++) {
            final WindowState win = windows.get(winNdx);
            if (win.mAppToken == null)
                continue;
            Task task = win.mAppToken.mTask;
            if (task == null || (task != null && task.mStack == null))
                continue;
            int stackId = task.mStack.mStackId;
            if (win.isVisibleNow() && stackId == mStackId) {
                if (DEBUG_STACK) Slog.v(TAG, "[BMW]adjustStackBackgroundLayer AnimLayer:"
                            + win.mWinAnimator.mAnimLayer);
                return win.mWinAnimator.mAnimLayer;
            }
        }
        return -1;
    }

    /// M: BMW.
    void resetStackBackgroundAnimator() {
        if (mStackBackgroundSurface != null)
            mStackBackgroundSurface.hide();
    }

    /// M: BMW. Temp Solution. Check if the TaskStack need to show black background
    private boolean needShowStackBackground() {
        if (!mStackBackgroundEnabled
                || !MultiWindowProxy.getInstance().isFloatingStack(mStackId)) {
            return false;
        }
        for (int taskNdx = mTasks.size() - 1; taskNdx >= 0; --taskNdx) {
            final AppTokenList tokens = mTasks.get(taskNdx).mAppTokens;
            for (int tokenNdx = tokens.size() - 1; tokenNdx >= 0; --tokenNdx) {
                final WindowList windows = tokens.get(tokenNdx).allAppWindows;
                for (int winNdx = windows.size() - 1; winNdx >= 0; --winNdx) {
                    final WindowState win = windows.get(winNdx);
                    if (win.mAttrs.getTitle() != null
                            && win.mAttrs.getTitle().toString().contains("SurfaceView")
                            && win.isVisibleOrBehindKeyguardLw()) {
                         return true;
                    }
                }
            }
        }
        return false;
    }

    /// M: BMW. mStackBackgroundSurface will be updated synchronously
    /// when mStackBackgroundAnimator's position changed.
    void onWinPositionChanged(WindowStateAnimator winAnimator, float left, float top) {
        if (DEBUG_STACK) {
            Slog.v(TAG, "[BMW]onSurfacePositionChanged winAnimator:" + winAnimator
                        + ", left:" + left + ", top:" + top);
        }
        if (mStackBackgroundSurface == null || winAnimator == null)
            return;

        if (winAnimator == mStackBackgroundAnimator) {
            mStackBackgroundSurface.setPosition(left, top);
        }
    }

    /// M: BMW. mStackBackgroundSurface will be updated synchronously
    /// when mStackBackgroundAnimator's size changed.
    void onWinSizeChanged(WindowStateAnimator winAnimator, int w, int h) {
        if (DEBUG_STACK) {
            Slog.v(TAG, "[BMW]onSurfaceSizeChanged winAnimator:" + winAnimator
                        + ", w:" + w + ", h:" + h);
        }
        if (mStackBackgroundSurface == null || winAnimator == null)
            return;
        if (winAnimator == mStackBackgroundAnimator) {
            mStackBackgroundSurface.setSize(w, h);
        }
    }
    /// M: BMW. mStackBackgroundSurface will be updated synchronously
    /// when mStackBackgroundAnimator's matrix changed.
    void onWinMatrixChanged(WindowStateAnimator winAnimator,
                    float dsdx, float dtdx, float dsdy, float dtdy) {
        if (DEBUG_STACK) {
            Slog.v(TAG, "[BMW]onSurfaceMatrixChanged winAnimator:" + winAnimator
                        + ", dsdx:" + dsdx + ", dtdx:" + dtdx
                        + ", dsdy:" + dsdy + ", dtdy:" + dtdy);
        }
        if (mStackBackgroundSurface == null || winAnimator == null)
            return;
        if (winAnimator == mStackBackgroundAnimator) {
            mStackBackgroundSurface.setMatrix(dsdx,  dtdx,  dsdy,  dtdy);
        }
    }
    /// M: BMW. mStackBackgroundSurface will be updated synchronously
    /// when mStackBackgroundAnimator's windowCrop changed.
    void onWinCropChanged(WindowStateAnimator winAnimator, Rect crop) {
        if (DEBUG_STACK) {
            Slog.v(TAG, "[BMW]onSurfaceCropChanged winAnimator:" + winAnimator
                        + ", crop:" + crop);
        }
        if (mStackBackgroundSurface == null || winAnimator == null)
            return;
        if (winAnimator == mStackBackgroundAnimator) {
            mStackBackgroundSurface.setWindowCrop(crop);
        }
    }

    /// M: BMW. mStackBackgroundSurface will be show synchronously
    /// when mStackBackgroundAnimator has shown
    void onWinShown(WindowStateAnimator winAnimator) {
        if (DEBUG_STACK) Slog.v(TAG, "[BMW]onSurfaceShown winAnimator:" + winAnimator);
        setStackBackground(true);
        if (mStackBackgroundSurface == null || winAnimator == null)
            return;
        if (winAnimator == mStackBackgroundAnimator) {
            /// M: BMW. [ALPS02018327] Update Background position/size/matix before show @{
            mStackBackgroundSurface.setPosition(mStackBackgroundAnimator.mSurfaceX,
                    mStackBackgroundAnimator.mSurfaceY);
            mStackBackgroundSurface.setSize((int) mStackBackgroundAnimator.mSurfaceW,
                    (int) mStackBackgroundAnimator.mSurfaceH);
            mStackBackgroundSurface.setMatrix(
                            mStackBackgroundAnimator.mDsDx * mStackBackgroundAnimator.mWin.mHScale,
                            mStackBackgroundAnimator.mDtDx * mStackBackgroundAnimator.mWin.mVScale,
                            mStackBackgroundAnimator.mDsDy * mStackBackgroundAnimator.mWin.mHScale,
                            mStackBackgroundAnimator.mDtDy * mStackBackgroundAnimator.mWin.mVScale);
            /// @}
            mStackBackgroundSurface.show();
        }
    }

    /// M: BMW. mStackBackgroundSurface will be hide synchronously
    /// when mStackBackgroundAnimator has hiden
    void onWinHiden(WindowStateAnimator winAnimator) {
        if (DEBUG_STACK) Slog.v(TAG, "[BMW]onSurfaceHiden winAnimator:" + winAnimator);
        setStackBackground(false);
        if (mStackBackgroundSurface == null || winAnimator == null)
            return;
        if (winAnimator == mStackBackgroundAnimator) {
            mStackBackgroundSurface.hide();
        }
    }
    /// M: Add black background for floating stack. @}

    /// M: BMW. [ALPS01891760]. Add getBottomWindow for disable dimlayer.
    WindowState getBottomWindow() {
         WindowState win = null;
         if (mTasks.size() > 0) {
             Task task = mTasks.get(0);
             for (int tokenNdx = 0; tokenNdx < task.mAppTokens.size(); tokenNdx++) {
                AppWindowToken token = task.mAppTokens.get(tokenNdx);
                for (int winNdx = 0; winNdx < token.allAppWindows.size(); winNdx++) {
                    win = token.allAppWindows.get(winNdx);
                    if (win != null) {
                        if (DEBUG_STACK) Slog.v(TAG, "[BMW]getBottomWindow win:" + win);
                        return win;
                    }
                }
             }
         }
         return win;
    }

    /// M: BMW. Add for Split Mode
    private void rotateBoundsForSpilt(int rotation, int displayWidth, int displayHeight) {
        mTopFloatStack = null;
        Rect tmpbounds = computeStackSizeForSplit();

        setBounds(tmpbounds);
        /// DimLayer control can be better in future.
        if (mDimLayer.isDimming()) {
            mDimLayer.hide();
            mDimLayer.show();
        }
        mDisplayRotation = rotation;
    }

    /// M: BMW. Add for Split Mode
    private boolean updateReqOrientation() {
        int taskNdx = mTasks.size();
        if (taskNdx <= 0) {
            return false;
        }
        Task topTask = mTasks.get(taskNdx - 1);
        //Task topTask = mTasks.get(0);
        int appTokenNdx = topTask.mAppTokens.size();
        if (appTokenNdx <= 0) {
            return false;
        }
        mOrientation = topTask.mAppTokens.get(appTokenNdx - 1).requestedOrientation;
        //mOrientation = topTask.mAppTokens.get(0).requestedOrientation;
        if (mOrientation != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                || mOrientation != ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                || mOrientation != ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
            mOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        }
        return true;
    }

    /// M: BMW. Add for Split Mode
    void computeTopFloatStack() {
        WindowState win;
        mTopFloatStack = null;
        for (int i = mDisplayContent.getWindowList().size() - 1 ; i >= 0 ; i--) {
            win = mDisplayContent.getWindowList().get(i);
            TaskStack tmpStack = win.getStack();
            int stackId = tmpStack.mStackId;
            boolean isFloating =
                    MultiWindowProxy.getInstance().isFloatingStack(stackId);
            boolean isInMiniMax = win.mAppToken != null &&
                    MultiWindowProxy.getInstance().isInMiniMax(win.mAppToken.mTask.mTaskId);

            if (MultiWindowProxy.isSplitMode() && !tmpStack.mInSplitMode)
                continue;
            if (!MultiWindowProxy.isSplitMode() && tmpStack.mInSplitMode)
                continue;
            /// [ALPS02614112] Only compute higher z-order floating window. @{
            if (!win.isFloatingWindow())
                continue;
            if (stackId == mStackId)
                break;
            /// @}
            if (mTopFloatStack == null && win.isWinVisibleLw()
                    && isFloating && !isInMiniMax && stackId != mStackId) {
                mTopFloatStack = tmpStack;
                break;
            }
        }
    }

    /// M: BMW. Add for Split Mode
    void verifyStackBoundsForSplit(Rect bounds) {
        DisplayInfo displayInfo = mDisplayContent.getDisplayInfo();
        int orient = mDisplayContent.getDisplay().getOrientation();
        final int dw = displayInfo.logicalWidth;
        final int dh = displayInfo.logicalHeight;
        final int rotation = mService.getRotation();
        int availW = mService.mPolicy.getConfigDisplayWidth(dw, dh, rotation);
        int availH = mService.mPolicy.getConfigDisplayHeight(dw, dh, rotation);
        int margin = STACK_BOUNDS_MARGIN_DP * displayInfo.logicalDensityDpi / 160;
        int stackPosition = MultiWindowProxy.getInstance().computeStackPosition(mStackId);
        int minHeight = STACK_BOUNDS_MARGIN_DP * displayInfo.logicalDensityDpi / 160;
        int maxHeight = availH - minHeight;
        int minWidth = minHeight;
        int maxWidth = availW - minWidth;
        if (stackPosition == MultiWindowProxy.PLACE_RIGHT_OR_BOTTOM) {
            // port
            if (orient == Surface.ROTATION_0 || orient == Surface.ROTATION_180) {
                if (bounds.height() > maxHeight) {
                    bounds.top = bounds.bottom - maxHeight;
                } else if (bounds.height() < minHeight) {
                    bounds.top = bounds.bottom - minHeight;
                }
            } else {
                if (bounds.width() > maxWidth) {
                    bounds.left = bounds.right - maxWidth;
                } else if (bounds.width() < minWidth) {
                    bounds.left = bounds.right - minWidth;
                }
            }
        } else {
            // port
            if (orient == Surface.ROTATION_0 || orient == Surface.ROTATION_180) {
                if (bounds.height() > maxHeight) {
                    bounds.bottom = bounds.top + maxHeight;
                } else if (bounds.height() < minHeight) {
                    bounds.bottom = bounds.top + minHeight;
                }
            } else {
                if (bounds.width() > maxWidth) {
                    bounds.right = bounds.top + maxWidth;
                } else if (bounds.width() < minWidth) {
                    bounds.right = bounds.top + minWidth;
                }
            }
        }
    }

    /// M: BMW. Add for Split Mode
    Rect computeStackSizeForSplit() {
        Rect stackSize = new Rect();

        DisplayInfo displayInfo = mDisplayContent.getDisplayInfo();
        int orient = mDisplayContent.getDisplay().getOrientation();

        final int dw = displayInfo.logicalWidth;
        final int dh = displayInfo.logicalHeight;
        final int rotation = mService.getRotation();
        int availW = mService.mPolicy.getConfigDisplayWidth(dw, dh, rotation);
        int availH = mService.mPolicy.getConfigDisplayHeight(dw, dh, rotation);
        int nonDecorHeight = mService.mPolicy.getNonDecorDisplayHeight(dw, dh, rotation);
        int statusBarHeight = nonDecorHeight - availH;

        if (DEBUG_STACK) {
            Slog.e(TAG, "[BMW]computeStackSizeForSplit mTopFloatStack = " + mTopFloatStack
                + ", availW = " + availW + ", availH = " + availH
                + ", statusBarHeight = " + statusBarHeight);
        }

        int topOffset = statusBarHeight;
        int leftOffset = 0;
        int width = 0;
        int height = 0;

        if (mTopFloatStack == null) {
            /// [ALPS02550570] If stack pos was not changed, then return the last stack size.@{
            int lastStackPos = mStackPos;
            mStackPos = MultiWindowProxy.getInstance().computeStackPosition(mStackId);
            if (lastStackPos == mStackPos && mDisplayRotation == rotation) {
                getBounds(stackSize);
                if (stackSize != null)
                    return stackSize;
            }
            /// @}
            // land
            width = availW / 2;
            height = availH;
            // port
            if (orient == Surface.ROTATION_0 || orient == Surface.ROTATION_180) {
                width = availW;
                height = availH / 2;
            }
            if (mStackPos == MultiWindowProxy.PLACE_RIGHT_OR_BOTTOM) {
                if (orient == Surface.ROTATION_0 || orient == Surface.ROTATION_180) {
                    topOffset += height;
                } else {
                    leftOffset += width;
                }
            }
        } else {
            int topStackPosition =
                MultiWindowProxy.getInstance().computeStackPosition(mTopFloatStack.mStackId);
            mStackPos = MultiWindowProxy.getInstance().computeStackPosition(mStackId);
            Rect topStackBounds = new Rect();
            mTopFloatStack.getBounds(topStackBounds);
            // land
            width = availW - topStackBounds.width();
            height = availH;
            // port
            if (orient == Surface.ROTATION_0 || orient == Surface.ROTATION_180) {
                width = availW;
                height = availH - topStackBounds.height();
            }
            if (mStackPos == MultiWindowProxy.PLACE_RIGHT_OR_BOTTOM) {
                // port
                if (orient == Surface.ROTATION_0 || orient == Surface.ROTATION_180) {
                    topOffset = topStackBounds.bottom;
                } else { // land
                    leftOffset += topStackBounds.right;
                }
            }
        }

        // port
        if (orient == Surface.ROTATION_0 || orient == Surface.ROTATION_180) {
            int maHeight = availH - 220 * displayInfo.logicalDensityDpi / 160;
            if (height > maHeight) {
                height = maHeight;
            }
        } else {
            int maxWidth = availW - 220 * displayInfo.logicalDensityDpi / 160;
            if (width > maxWidth) {
                width = maxWidth;
            }
        }
        stackSize.set(0, 0, width, height);
        stackSize.offset(leftOffset, topOffset);

        if (DEBUG_STACK) {
            Slog.d(TAG, "[BMW]computeStackSizeForSplit stackSize = " + stackSize);
        }
        mInSplitMode = true;
        return stackSize;
    }

    /// M: BMW. Add for Split Mode
    // For floating window, we will compute stack's size and position
    // according to the app's request orientation and the top floating stack's position.
    // So, we should update request orientation first.
    Rect computeStackSizeForFloating() {
        if (!updateReqOrientation()) {
            Slog.e(TAG, "[BMW]computeStackSizeForFloating" +
                    ":update request orientation failed! This:" + this);
            return null;
        }
        if (DEBUG_STACK) {
            Slog.v(TAG, "[BMW]computeStackSizeForFloating this:" + this
                + ", mOrientation=" + mOrientation);
        }
        mStackPos = MultiWindowProxy.PLACE_NULL;
        Rect stackSize = new Rect();
        DisplayInfo displayInfo = mDisplayContent.getDisplayInfo();

        if (mOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                || mOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT) {
            if (mFloatStackPortWidth == 0 && mFloatStackPortHeight == 0) {
                if (mDisplayContent.mInitialDisplayWidth < mDisplayContent.mInitialDisplayHeight) {
                    mFloatStackPortHeight = mDisplayContent.mInitialDisplayHeight / 2;
                } else {
                    mFloatStackPortHeight = mDisplayContent.mInitialDisplayWidth / 2;
                }
                mFloatStackPortWidth = (mFloatStackPortHeight * 3) / 4;
            }
            stackSize.set(0, 0, mFloatStackPortWidth, mFloatStackPortHeight);
            stackSize.offsetTo((displayInfo.logicalWidth - mFloatStackPortWidth) / 2,
                    (displayInfo.logicalHeight - mFloatStackPortHeight) / 2);
        } else {
            if (mFloatStackLandWidth == 0 && mFloatStackLandHeight == 0) {
                if (mDisplayContent.mInitialDisplayWidth < mDisplayContent.mInitialDisplayHeight) {
                    mFloatStackLandWidth = mDisplayContent.mInitialDisplayHeight / 2;
                } else {
                    mFloatStackLandWidth = mDisplayContent.mInitialDisplayWidth / 2;
                }
                mFloatStackLandHeight = (mFloatStackLandWidth * 3) / 4;
            }
            stackSize.set(0, 0, mFloatStackLandWidth, mFloatStackLandHeight);
            stackSize.offsetTo((displayInfo.logicalWidth - mFloatStackLandWidth) / 2,
                    (displayInfo.logicalHeight - mFloatStackLandHeight) / 2);
        }

        /// decide the proper the top position.
        computeBoundaryLimit();

        if (mTopFloatStack != null) {
            Rect bound = new Rect();
            mTopFloatStack.getBounds(bound);

            int left, top;
            left = bound.left + mStacksOffset;
            top = bound.top + mStacksOffset;
            if (left > mRightBoundLimit ||
                (!mInited && (left + stackSize.width()) > mRightBoundLimitFirstLaunch)) {
                left = 0;
            }
            if (top > mBottomBoundLimit ||
                (!mInited && (top + stackSize.height()) > mBottomBoundLimitFirstLaunch)) {
                top = mTopBoundLimit;
            }
            stackSize.offsetTo(left, top);
        }
        if (DEBUG_STACK) {
            Slog.d(TAG, "[BMW]computeStackSizeForFloating boxSize = " + stackSize);
        }
        mInSplitMode = false;
        return stackSize;
    }

    /// M: BMW. Add for Split Mode.
    void updateStackSizeIfNeeded() {
        if (!MultiWindowProxy.getInstance().isFloatingStack(mStackId)) {
            return;
        }

        boolean needUpdated = false;
        if (MultiWindowProxy.isSplitMode()) {
            needUpdated = true;
        }

        if (MultiWindowProxy.isSplitMode() != mInSplitMode) {
            needUpdated = true;
        }

        if (needUpdated) {
            Rect bounds = computeStackSize();
            if (bounds == null) {
                return;
            }
            setBounds(bounds);
        }
    }

    /// M: Assume floating stack is at the portrait mode.
    private int mOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
    private int mDisplayRotation = Surface.ROTATION_0;
    private int mXOffset, mYOffset;

    private TaskStack mTopFloatStack = null;
    private int mFloatStackPortWidth = 0;
    private int mFloatStackPortHeight = 0;
    private int mFloatStackLandWidth = 0;
    private int mFloatStackLandHeight = 0;
    private int mTopBoundLimit = 0, mBottomBoundLimit = 0;
    private int mRightBoundLimit = 0, mLeftBoundLimit = 0;
    private int mRightBoundLimitFirstLaunch = 0, mBottomBoundLimitFirstLaunch = 0;
    private int mStacksOffset = 0;
    private boolean mInited = false;
    boolean mInSplitMode = false; /// M: Add for Split Mode
    private int mStackPos = MultiWindowProxy.PLACE_NULL;

    final private static int STACK_BOUNDS_MARGIN_DP = 220;
    final private static int STACKS_OFFSET_MARGIN_DP = 50;
    final private static int FLOAT_CONTROL_BAR_HEIGHT_DP = 44;

    /// M: Add black background for TaskStack(Floating) @{
    WindowStateAnimator mStackBackgroundAnimator;
    StackBackgroundSurface mStackBackgroundSurface;
    private boolean mStackBackgroundEnabled = false;
    /// @}

    /// M. BMW. Add for Multi-Window End @}
}
