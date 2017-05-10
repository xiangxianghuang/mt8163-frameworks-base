/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;

import android.animation.LayoutTransition;
import android.animation.LayoutTransition.TransitionListener;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.ActivityManagerNative;
import android.app.StatusBarManager;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewRootImpl;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.systemui.R;
import com.android.systemui.recents.RecentsActivity;
import com.android.systemui.statusbar.policy.DeadZone;
import com.android.systemui.statusbar.policy.KeyButtonView;
import com.android.systemui.statusbar.BaseStatusBar;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;

import com.mediatek.common.MPlugin;
import com.mediatek.multiwindow.MultiWindowProxy;
import com.mediatek.systemui.ext.DefaultNavigationBarPlugin;
import com.mediatek.systemui.ext.INavigationBarPlugin;
import android.os.UserHandle;
import android.os.storage.IMountService;
import android.os.storage.StorageManager;
import android.os.IBinder;
import android.os.ServiceManager;

public class NavigationBarView extends LinearLayout {
    final static boolean DEBUG = false;
    final static String TAG = "PhoneStatusBar/NavigationBarView";
    /// M: add for multi window @{
    private final static String ACTION_FLOADWINDOW_SHOW = "com.android.systemui.FLOATWINDOW_SHOW";
    private final static int MSG_RESTORE_SHOW = 1025;
    private final static boolean NAVBAR_ALWAYS_AT_RIGHT = true;
    private boolean mShowFloatWindow = true;
    private View mRestoreButton;
    private boolean mShowRestoreButton;
    private BaseStatusBar mBar;
    private boolean mIsSplitModeEnable = MultiWindowProxy.isSplitModeEnabled();
    private boolean currentFlag;
    /// @}

    // slippery nav bar when everything is disabled, e.g. during setup
    final static boolean SLIPPERY_WHEN_DISABLED = true;

    final Display mDisplay;
    View mCurrentView = null;
    View[] mRotatedViews = new View[4];

    int mBarSize;
    boolean mVertical;
    boolean mScreenOn;

    boolean mShowMenu;
    int mDisabledFlags = 0;
    int mNavigationIconHints = 0;

    private Drawable mBackIcon, mBackLandIcon, mBackAltIcon, mBackAltLandIcon;
    private Drawable mRecentIcon;
    private Drawable mRecentLandIcon;

    private NavigationBarViewTaskSwitchHelper mTaskSwitchHelper;
    private DeadZone mDeadZone;
    private final NavigationBarTransitions mBarTransitions;

    // workaround for LayoutTransitions leaving the nav buttons in a weird state (bug 5549288)
    final static boolean WORKAROUND_INVALID_LAYOUT = true;
    final static int MSG_CHECK_INVALID_LAYOUT = 8686;

    // performs manual animation in sync with layout transitions
    private final NavTransitionListener mTransitionListener = new NavTransitionListener();

    private OnVerticalChangedListener mOnVerticalChangedListener;
    private boolean mIsLayoutRtl;
    private boolean mLayoutTransitionsEnabled;

    // MPlugin for Navigation Bar
    private INavigationBarPlugin mNavBarPlugin;

    private class NavTransitionListener implements TransitionListener {
        private boolean mBackTransitioning;
        private boolean mHomeAppearing;
        private long mStartDelay;
        private long mDuration;
        private TimeInterpolator mInterpolator;

        @Override
        public void startTransition(LayoutTransition transition, ViewGroup container,
                View view, int transitionType) {
            if (view.getId() == R.id.back) {
                mBackTransitioning = true;
            } else if (view.getId() == R.id.home && transitionType == LayoutTransition.APPEARING) {
                mHomeAppearing = true;
                mStartDelay = transition.getStartDelay(transitionType);
                mDuration = transition.getDuration(transitionType);
                mInterpolator = transition.getInterpolator(transitionType);
            }
        }

        @Override
        public void endTransition(LayoutTransition transition, ViewGroup container,
                View view, int transitionType) {
            if (view.getId() == R.id.back) {
                mBackTransitioning = false;
            } else if (view.getId() == R.id.home && transitionType == LayoutTransition.APPEARING) {
                mHomeAppearing = false;
            }
        }

        public void onBackAltCleared() {
            // When dismissing ime during unlock, force the back button to run the same appearance
            // animation as home (if we catch this condition early enough).
            if (!mBackTransitioning && getBackButton().getVisibility() == VISIBLE
                    && mHomeAppearing && getHomeButton().getAlpha() == 0) {
                getBackButton().setAlpha(0);
                ValueAnimator a = ObjectAnimator.ofFloat(getBackButton(), "alpha", 0, 1);
                a.setStartDelay(mStartDelay);
                a.setDuration(mDuration);
                a.setInterpolator(mInterpolator);
                a.start();
            }
        }
    }

    private final OnClickListener mImeSwitcherClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            ((InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE))
                    .showInputMethodPicker(true /* showAuxiliarySubtypes */);
        }
    };

    private class H extends Handler {
        public void handleMessage(Message m) {
            switch (m.what) {
                 /// M: add for multi window @{
                case MSG_RESTORE_SHOW:
                    Bundle b = m.getData();
                    boolean flag = b.getBoolean("flag");
                    currentFlag = flag;
                    if (mRestoreButton != null) {
                        /// M: add for Multi-User support
                        mShowRestoreButton = flag && mShowFloatWindow
                          && (!mBar.isFloatPanelOpened());
                        mRestoreButton.setVisibility(mShowRestoreButton ?
                                                     View.VISIBLE : View.INVISIBLE);
                        /// @}
                    }
                    break;
                /// @}
                case MSG_CHECK_INVALID_LAYOUT:
                    final String how = "" + m.obj;
                    final int w = getWidth();
                    final int h = getHeight();
                    final int vw = mCurrentView.getWidth();
                    final int vh = mCurrentView.getHeight();

                    if (h != vh || w != vw) {
                        Log.w(TAG, String.format(
                            "*** Invalid layout in navigation bar (%s this=%dx%d cur=%dx%d)",
                            how, w, h, vw, vh));
                        if (WORKAROUND_INVALID_LAYOUT) {
                            requestLayout();
                        }
                    }
                    break;
            }
        }
    }

    public NavigationBarView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mDisplay = ((WindowManager)context.getSystemService(
                Context.WINDOW_SERVICE)).getDefaultDisplay();

        final Resources res = getContext().getResources();
        mBarSize = res.getDimensionPixelSize(R.dimen.navigation_bar_size);
        mVertical = false;
        mShowMenu = false;
        /// M: add for multi window @{
        if (MultiWindowProxy.isSupported()) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_FLOADWINDOW_SHOW);
            filter.addAction(Intent.ACTION_USER_SWITCHED);
            context.registerReceiver(mFloatWindowBroadcastReceiver, filter);
        }
        /// @}
        mTaskSwitchHelper = new NavigationBarViewTaskSwitchHelper(context);

        // MPlugin Navigation Bar creation and initialization
        try {
            mNavBarPlugin = (INavigationBarPlugin) MPlugin.createInstance(
            INavigationBarPlugin.class.getName(), context);
        } catch (Exception e) {
            Log.e(TAG, "Catch INavigationBarPlugin exception: ", e);
        }
        if (mNavBarPlugin == null) {
            Log.d(TAG, "DefaultNavigationBarPlugin");
            mNavBarPlugin = new DefaultNavigationBarPlugin(context);
        }

        getIcons(res);

        mBarTransitions = new NavigationBarTransitions(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ViewRootImpl root = getViewRootImpl();
        if (root != null) {
            root.setDrawDuringWindowsAnimating(true);
        }
    }

    public BarTransitions getBarTransitions() {
        return mBarTransitions;
    }

    public void setBar(PhoneStatusBar phoneStatusBar) {
        mTaskSwitchHelper.setBar(phoneStatusBar);
        /// M: add for multi window @{
        if(MultiWindowProxy.isSupported()){
            mBar = phoneStatusBar;
        }
        /// @}
    }
    public void setOnVerticalChangedListener(OnVerticalChangedListener onVerticalChangedListener) {
        mOnVerticalChangedListener = onVerticalChangedListener;
        notifyVerticalChangedListener(mVertical);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mTaskSwitchHelper.onTouchEvent(event)) {
            return true;
        }
        if (mDeadZone != null && event.getAction() == MotionEvent.ACTION_OUTSIDE) {
            mDeadZone.poke(event);
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return mTaskSwitchHelper.onInterceptTouchEvent(event);
    }

    public void abortCurrentGesture() {
        getHomeButton().abortCurrentGesture();
    }

    private H mHandler = new H();

    public View getCurrentView() {
        return mCurrentView;
    }

    public View getRecentsButton() {
        ImageView view = (ImageView) mCurrentView.findViewById(R.id.recent_apps);
        view.setImageDrawable(mNavBarPlugin.getRecentImage(view.getDrawable()));
        return view;
    }

    public View getMenuButton() {
        return mCurrentView.findViewById(R.id.menu);
    }

    public View getBackButton() {
        ImageView view = (ImageView) mCurrentView.findViewById(R.id.back);
        view.setImageDrawable(mNavBarPlugin.getBackImage(view.getDrawable()));
        return view;
    }

    public KeyButtonView getHomeButton() {
        KeyButtonView view = (KeyButtonView) mCurrentView.findViewById(R.id.home);
        view.setImageDrawable(mNavBarPlugin.getHomeImage(view.getDrawable()));
        return (KeyButtonView) view;
    }

    public View getImeSwitchButton() {
        return mCurrentView.findViewById(R.id.ime_switcher);
    }

    private void getIcons(Resources res) {
        mBackIcon = mNavBarPlugin.getBackImage(res.getDrawable(R.drawable.ic_sysbar_back));

        mBackLandIcon =
        mNavBarPlugin.getBackLandImage(res.getDrawable(R.drawable.ic_sysbar_back_land));

        mBackAltIcon =
        mNavBarPlugin.getBackImeImage(res.getDrawable(R.drawable.ic_sysbar_back_ime));

        mBackAltLandIcon =
        mNavBarPlugin.getBackImeImage(res.getDrawable(R.drawable.ic_sysbar_back_ime_land));

        mRecentIcon =
        mNavBarPlugin.getRecentImage(res.getDrawable(R.drawable.ic_sysbar_recent));

        mRecentLandIcon =
        mNavBarPlugin.getRecentLandImage(res.getDrawable(R.drawable.ic_sysbar_recent_land));
    }

    @Override
    public void setLayoutDirection(int layoutDirection) {
        getIcons(getContext().getResources());

        super.setLayoutDirection(layoutDirection);
    }

    public void notifyScreenOn(boolean screenOn) {
        mScreenOn = screenOn;
        setDisabledFlags(mDisabledFlags, true);
    }

    public void setNavigationIconHints(int hints) {
        setNavigationIconHints(hints, false);
    }

    public void setNavigationIconHints(int hints, boolean force) {
        if (!force && hints == mNavigationIconHints) return;
        final boolean backAlt = (hints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0;
        if ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0 && !backAlt) {
            mTransitionListener.onBackAltCleared();
        }
        if (DEBUG) {
            android.widget.Toast.makeText(getContext(),
                "Navigation icon hints = " + hints,
                500).show();
        }

        mNavigationIconHints = hints;

        ((ImageView)getBackButton()).setImageDrawable(backAlt
                ? (mVertical ? mBackAltLandIcon : mBackAltIcon)
                : (mVertical ? mBackLandIcon : mBackIcon));

        ((ImageView)getRecentsButton()).setImageDrawable(mVertical ? mRecentLandIcon : mRecentIcon);

        final boolean showImeButton = ((hints & StatusBarManager.NAVIGATION_HINT_IME_SHOWN) != 0);
        getImeSwitchButton().setVisibility(showImeButton ? View.VISIBLE : View.INVISIBLE);
        // Update menu button in case the IME state has changed.
        setMenuVisibility(mShowMenu, true);


        setDisabledFlags(mDisabledFlags, true);
    }

    public void setDisabledFlags(int disabledFlags) {
        setDisabledFlags(disabledFlags, false);
    }

    public void setDisabledFlags(int disabledFlags, boolean force) {
        if (!force && mDisabledFlags == disabledFlags) return;

        mDisabledFlags = disabledFlags;

        /// M: add for multi window @{
        boolean home = ((disabledFlags & View.STATUS_BAR_DISABLE_HOME) != 0);
        boolean recent = ((disabledFlags & View.STATUS_BAR_DISABLE_RECENT) != 0);
        boolean back = ((disabledFlags & View.STATUS_BAR_DISABLE_BACK) != 0)
                && ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) == 0);
        boolean search = ((disabledFlags & View.STATUS_BAR_DISABLE_SEARCH) != 0);
        if(MultiWindowProxy.isSupported()){
            home = home || mBar.isFloatPanelOpened();
            recent = recent || mBar.isFloatPanelOpened();
            back = back || mBar.isFloatPanelOpened();
            search = search || mBar.isFloatPanelOpened();
        }
        final boolean disableHome = home;
        boolean disableRecent = recent;
        final boolean disableBack = back;
        final boolean disableSearch = search;
        /// @}

        if (SLIPPERY_WHEN_DISABLED) {
            setSlippery(disableHome && disableRecent && disableBack && disableSearch);
        }

        ViewGroup navButtons = (ViewGroup) mCurrentView.findViewById(R.id.nav_buttons);
        if (navButtons != null) {
            LayoutTransition lt = navButtons.getLayoutTransition();
            if (lt != null) {
                if (!lt.getTransitionListeners().contains(mTransitionListener)) {
                    lt.addTransitionListener(mTransitionListener);
                }
            }
        }
        if (inLockTask() && disableRecent && !disableHome) {
            // Don't hide recents when in lock task, it is used for exiting.
            // Unless home is hidden, then in DPM locked mode and no exit available.
            disableRecent = false;
        }

        getBackButton()   .setVisibility(disableBack       ? View.INVISIBLE : View.VISIBLE);
        getHomeButton()   .setVisibility(disableHome       ? View.INVISIBLE : View.VISIBLE);
        getRecentsButton().setVisibility(disableRecent     ? View.INVISIBLE : View.VISIBLE);
        /// M: add for multi window @{
        if(MultiWindowProxy.isSupported()){
            setDisabledFlagsforMultiW(disableRecent);
        }
        /// @}
    }

    private boolean inLockTask() {
        try {
            return ActivityManagerNative.getDefault().isInLockTaskMode();
        } catch (RemoteException e) {
            return false;
        }
    }

    private void setVisibleOrGone(View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? VISIBLE : GONE);
        }
    }

    public void setWakeAndUnlocking(boolean wakeAndUnlocking) {
        setUseFadingAnimations(wakeAndUnlocking);
        setLayoutTransitionsEnabled(!wakeAndUnlocking);
    }

    private void setLayoutTransitionsEnabled(boolean enabled) {
        mLayoutTransitionsEnabled = enabled;
        ViewGroup navButtons = (ViewGroup) mCurrentView.findViewById(R.id.nav_buttons);
        LayoutTransition lt = navButtons.getLayoutTransition();
        if (lt != null) {
            if (enabled) {
                lt.enableTransitionType(LayoutTransition.APPEARING);
                lt.enableTransitionType(LayoutTransition.DISAPPEARING);
                lt.enableTransitionType(LayoutTransition.CHANGE_APPEARING);
                lt.enableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
            } else {
                lt.disableTransitionType(LayoutTransition.APPEARING);
                lt.disableTransitionType(LayoutTransition.DISAPPEARING);
                lt.disableTransitionType(LayoutTransition.CHANGE_APPEARING);
                lt.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
            }
        }
    }

    private void setUseFadingAnimations(boolean useFadingAnimations) {
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) getLayoutParams();
        if (lp != null) {
            boolean old = lp.windowAnimations != 0;
            if (!old && useFadingAnimations) {
                lp.windowAnimations = R.style.Animation_NavigationBarFadeIn;
            } else if (old && !useFadingAnimations) {
                lp.windowAnimations = 0;
            } else {
                return;
            }
            WindowManager wm = (WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE);
            wm.updateViewLayout(this, lp);
        }
    }

    public void setSlippery(boolean newSlippery) {
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) getLayoutParams();
        if (lp != null) {
            boolean oldSlippery = (lp.flags & WindowManager.LayoutParams.FLAG_SLIPPERY) != 0;
            if (!oldSlippery && newSlippery) {
                lp.flags |= WindowManager.LayoutParams.FLAG_SLIPPERY;
            } else if (oldSlippery && !newSlippery) {
                lp.flags &= ~WindowManager.LayoutParams.FLAG_SLIPPERY;
            } else {
                return;
            }
            WindowManager wm = (WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE);
            wm.updateViewLayout(this, lp);
        }
    }

    public void setMenuVisibility(final boolean show) {
        setMenuVisibility(show, false);
    }

    public void setMenuVisibility(final boolean show, final boolean force) {
        if (!force && mShowMenu == show) return;

        mShowMenu = show;

        // Only show Menu if IME switcher not shown.
        final boolean shouldShow = mShowMenu &&
                ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_IME_SHOWN) == 0);
        getMenuButton().setVisibility(shouldShow ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    public void onFinishInflate() {
        mRotatedViews[Surface.ROTATION_0] =
        mRotatedViews[Surface.ROTATION_180] = findViewById(R.id.rot0);

        mRotatedViews[Surface.ROTATION_90] = findViewById(R.id.rot90);

        mRotatedViews[Surface.ROTATION_270] = mRotatedViews[Surface.ROTATION_90];

        mCurrentView = mRotatedViews[Surface.ROTATION_0];

        getImeSwitchButton().setOnClickListener(mImeSwitcherClickListener);

        updateRTLOrder();
    }

    public boolean isVertical() {
        return mVertical;
    }

    public void reorient() {
        final int rot = mDisplay.getRotation();
        for (int i=0; i<4; i++) {
            mRotatedViews[i].setVisibility(View.GONE);
        }
        mCurrentView = mRotatedViews[rot];
        mCurrentView.setVisibility(View.VISIBLE);
        setLayoutTransitionsEnabled(mLayoutTransitionsEnabled);

        getImeSwitchButton().setOnClickListener(mImeSwitcherClickListener);

        mDeadZone = (DeadZone) mCurrentView.findViewById(R.id.deadzone);

        // force the low profile & disabled states into compliance
        mBarTransitions.init();
        setDisabledFlags(mDisabledFlags, true /* force */);
        setMenuVisibility(mShowMenu, true /* force */);

        if (DEBUG) {
            Log.d(TAG, "reorient(): rot=" + mDisplay.getRotation());
        }

        updateTaskSwitchHelper();

        setNavigationIconHints(mNavigationIconHints, true);
    }

    private void updateTaskSwitchHelper() {
        boolean isRtl = (getLayoutDirection() == View.LAYOUT_DIRECTION_RTL);
        mTaskSwitchHelper.setBarState(mVertical, isRtl);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (DEBUG) Log.d(TAG, String.format(
                    "onSizeChanged: (%dx%d) old: (%dx%d)", w, h, oldw, oldh));

        final boolean newVertical = w > 0 && h > w;
        if (newVertical != mVertical) {
            mVertical = newVertical;
            //Log.v(TAG, String.format("onSizeChanged: h=%d, w=%d, vert=%s", h, w, mVertical?"y":"n"));
            reorient();
            notifyVerticalChangedListener(newVertical);
        }

        postCheckForInvalidLayout("sizeChanged");
        super.onSizeChanged(w, h, oldw, oldh);
    }

    private void notifyVerticalChangedListener(boolean newVertical) {
        if (mOnVerticalChangedListener != null) {
            mOnVerticalChangedListener.onVerticalChanged(newVertical);
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateRTLOrder();
        updateTaskSwitchHelper();

        /// M: [ALPS01868023] Set ContentDescription when language changed. @{
        getBackButton().setContentDescription(
            getResources().getString(R.string.accessibility_back));
        getHomeButton().setContentDescription(
            getResources().getString(R.string.accessibility_home));
        getRecentsButton().setContentDescription(
            getResources().getString(R.string.accessibility_recent));
        getMenuButton().setContentDescription(
            getResources().getString(R.string.accessibility_menu));
        getImeSwitchButton().setContentDescription(
            getResources().getString(R.string.accessibility_ime_switch_button));
        /// M: [ALPS01868023] Set ContentDescription when language changed. @}
    }

    /**
     * In landscape, the LinearLayout is not auto mirrored since it is vertical. Therefore we
     * have to do it manually
     */
    private void updateRTLOrder() {
        boolean isLayoutRtl = getResources().getConfiguration()
                .getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        if (mIsLayoutRtl != isLayoutRtl) {

            // We swap all children of the 90 and 270 degree layouts, since they are vertical
            View rotation90 = mRotatedViews[Surface.ROTATION_90];
            swapChildrenOrderIfVertical(rotation90.findViewById(R.id.nav_buttons));
            adjustExtraKeyGravity(rotation90, isLayoutRtl);

            View rotation270 = mRotatedViews[Surface.ROTATION_270];
            if (rotation90 != rotation270) {
                swapChildrenOrderIfVertical(rotation270.findViewById(R.id.nav_buttons));
                adjustExtraKeyGravity(rotation270, isLayoutRtl);
            }
            mIsLayoutRtl = isLayoutRtl;
        }
    }

    private void adjustExtraKeyGravity(View navBar, boolean isLayoutRtl) {
        View menu = navBar.findViewById(R.id.menu);
        View imeSwitcher = navBar.findViewById(R.id.ime_switcher);
        if (menu != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) menu.getLayoutParams();
            lp.gravity = isLayoutRtl ? Gravity.BOTTOM : Gravity.TOP;
            menu.setLayoutParams(lp);
        }
        if (imeSwitcher != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) imeSwitcher.getLayoutParams();
            lp.gravity = isLayoutRtl ? Gravity.BOTTOM : Gravity.TOP;
            imeSwitcher.setLayoutParams(lp);
        }
    }

    /**
     * Swaps the children order of a LinearLayout if it's orientation is Vertical
     *
     * @param group The LinearLayout to swap the children from.
     */
    private void swapChildrenOrderIfVertical(View group) {
        if (group instanceof LinearLayout) {
            LinearLayout linearLayout = (LinearLayout) group;
            if (linearLayout.getOrientation() == VERTICAL) {
                int childCount = linearLayout.getChildCount();
                ArrayList<View> childList = new ArrayList<>(childCount);
                for (int i = 0; i < childCount; i++) {
                    childList.add(linearLayout.getChildAt(i));
                }
                linearLayout.removeAllViews();
                for (int i = childCount - 1; i >= 0; i--) {
                    linearLayout.addView(childList.get(i));
                }
            }
        }
    }

    /*
    @Override
    protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        if (DEBUG) Log.d(TAG, String.format(
                    "onLayout: %s (%d,%d,%d,%d)",
                    changed?"changed":"notchanged", left, top, right, bottom));
        super.onLayout(changed, left, top, right, bottom);
    }

    // uncomment this for extra defensiveness in WORKAROUND_INVALID_LAYOUT situations: if all else
    // fails, any touch on the display will fix the layout.
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (DEBUG) Log.d(TAG, "onInterceptTouchEvent: " + ev.toString());
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            postCheckForInvalidLayout("touch");
        }
        return super.onInterceptTouchEvent(ev);
    }
    */


    private String getResourceName(int resId) {
        if (resId != 0) {
            final android.content.res.Resources res = getContext().getResources();
            try {
                return res.getResourceName(resId);
            } catch (android.content.res.Resources.NotFoundException ex) {
                return "(unknown)";
            }
        } else {
            return "(null)";
        }
    }

    private void postCheckForInvalidLayout(final String how) {
        mHandler.obtainMessage(MSG_CHECK_INVALID_LAYOUT, 0, 0, how).sendToTarget();
    }

    private static String visibilityToString(int vis) {
        switch (vis) {
            case View.INVISIBLE:
                return "INVISIBLE";
            case View.GONE:
                return "GONE";
            default:
                return "VISIBLE";
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("NavigationBarView {");
        final Rect r = new Rect();
        final Point size = new Point();
        mDisplay.getRealSize(size);

        pw.println(String.format("      this: " + PhoneStatusBar.viewInfo(this)
                        + " " + visibilityToString(getVisibility())));

        getWindowVisibleDisplayFrame(r);
        final boolean offscreen = r.right > size.x || r.bottom > size.y;
        pw.println("      window: "
                + r.toShortString()
                + " " + visibilityToString(getWindowVisibility())
                + (offscreen ? " OFFSCREEN!" : ""));

        pw.println(String.format("      mCurrentView: id=%s (%dx%d) %s",
                        getResourceName(mCurrentView.getId()),
                        mCurrentView.getWidth(), mCurrentView.getHeight(),
                        visibilityToString(mCurrentView.getVisibility())));

        pw.println(String.format("      disabled=0x%08x vertical=%s menu=%s",
                        mDisabledFlags,
                        mVertical ? "true" : "false",
                        mShowMenu ? "true" : "false"));

        dumpButton(pw, "back", getBackButton());
        dumpButton(pw, "home", getHomeButton());
        dumpButton(pw, "rcnt", getRecentsButton());
        dumpButton(pw, "menu", getMenuButton());

        pw.println("    }");
    }

    private static void dumpButton(PrintWriter pw, String caption, View button) {
        pw.print("      " + caption + ": ");
        if (button == null) {
            pw.print("null");
        } else {
            pw.print(PhoneStatusBar.viewInfo(button)
                    + " " + visibilityToString(button.getVisibility())
                    + " alpha=" + button.getAlpha()
                    );
        }
        pw.println();
    }

    public interface OnVerticalChangedListener {
        void onVerticalChanged(boolean isVertical);
    }

  /// M: add for multi window @{
    private IMountService getMountService() {
        final IBinder service = ServiceManager.getService("mount");
        if (service != null) {
            return IMountService.Stub.asInterface(service);
        }
        return null;
    }

    private BroadcastReceiver mFloatWindowBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "action = " + action);
        boolean showFloatWindow = intent.getBooleanExtra("ShowFloatWindow", false);

        int passwordType = StorageManager.CRYPT_TYPE_DEFAULT;

        try {
            final IMountService service = getMountService();
            passwordType = service.getPasswordType();
        } catch (Exception e) {
            Log.e(TAG, "Error calling mount service " + e);
        }
        /// M: when switch to normal user,Not to show float button @{
        if (action.equals(Intent.ACTION_USER_SWITCHED)) {
            int newUserId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, -1);
            if(newUserId != UserHandle.USER_OWNER){
                showFloatWindow = false;
            }else{
                if(StorageManager.CRYPT_TYPE_DEFAULT != passwordType ){
                    Log.d(TAG, "Have passwordType,Not to show FloatButton" );
                    getFloatButton().setVisibility(View.INVISIBLE);
                    return ;
                }else{
                    Log.d(TAG, "No passwordType" );
                    showFloatWindow = true;
                }
            }
        }
        /// @}
            mShowFloatWindow = showFloatWindow;
            Log.d(TAG, "mFloatWindowBroadcastReceiver showFloatWindow is " + showFloatWindow);
            getFloatButton().setVisibility(showFloatWindow ? View.VISIBLE : View.INVISIBLE);
        }
    };
    public void refreshRestoreButton(){
        showRestoreButton(currentFlag);
    }

    public void showRestoreButton(boolean flag) {
        Message msg = mHandler.obtainMessage(MSG_RESTORE_SHOW);
        Bundle b = new Bundle();
        b.putBoolean("flag",flag);
        msg.setData(b);
        msg.sendToTarget();
    }

    public ImageView getFloatButton() {
        return (ImageView) mCurrentView.findViewById(R.id.multi_float);
    }

    public View getRestoreButton() {
        return mCurrentView.findViewById(R.id.restore);
    }

    public Button getExtensionButton() {
        return (Button)mCurrentView.findViewById(R.id.more);
    }

    public ImageView getFloatModeButton() {
        return (ImageView) mCurrentView.findViewById(R.id.float_mode);
    }

    public ImageView getSplitModeButton() {
        return (ImageView) mCurrentView.findViewById(R.id.split_mode);
    }

    public View getLineView() {
        return (View) mCurrentView.findViewById(R.id.button_line);
    }

    public void setDisabledFlagsforMultiW(boolean flag) {
        final boolean disableFloat = flag
             && !mBar.isFloatPanelOpened();
        getExtensionButton().setVisibility(
             mBar.isFloatPanelOpened() ? View.VISIBLE : View.INVISIBLE);
        getFloatButton().setVisibility(
             (!disableFloat && mShowFloatWindow) ? View.VISIBLE
                     : View.INVISIBLE);
        if(mIsSplitModeEnable){
            getFloatModeButton().setVisibility(
                  (mBar.isFloatPanelOpened()) ? View.VISIBLE : View.INVISIBLE);
            getSplitModeButton().setVisibility(
                  (mBar.isFloatPanelOpened()) ? View.VISIBLE : View.INVISIBLE);
            getLineView().setVisibility(
                  (mBar.isFloatPanelOpened()) ? View.VISIBLE : View.INVISIBLE);
        }

        // Add for restore button
        if (mCurrentView != null) {
            mRestoreButton = mCurrentView.findViewById(R.id.restore);
            mRestoreButton.setVisibility(
                mShowRestoreButton? View.VISIBLE : View.INVISIBLE);
            mRestoreButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                   /// M: for bug ALPS02521755 @{
                  // mShowRestoreButton = false;
                  // mRestoreButton.setVisibility(View.INVISIBLE);
                   /// @}
                   MultiWindowProxy.getInstance().restoreWindow(null, false);
                   Log.d(TAG, "added for restore button in navi onCLick!");
               }
           });
        }
        Log.d(TAG, "setDisabledFlags showFloatWindow is " + !disableFloat);
    }
    /// @}
}
