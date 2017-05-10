package com.mediatek.perfservice;

//import android.os.Bundle;

public interface IPerfServiceWrapper {

    public static final int SCN_NONE       = 0;
    public static final int SCN_APP_SWITCH = 1; /* apply for both launch/exit */
    public static final int SCN_APP_ROTATE = 2;
    public static final int SCN_APP_TOUCH       = 3;
    public static final int SCN_DONT_USE1       = 4;
    public static final int SCN_SW_FRAME_UPDATE = 5;
    public static final int SCN_APP_LAUNCH      = 6;
    public static final int SCN_GAMING          = 7;
    public static final int SCN_MAX             = 8; /* should be (last scenario + 1) */

    public static final int STATE_PAUSED    = 0;
    public static final int STATE_RESUMED   = 1;
    public static final int STATE_DESTROYED = 2;
    public static final int STATE_DEAD      = 3;
    public static final int STATE_STOPPED   = 4;

    public static final int DISPLAY_TYPE_GAME   = 0;
    public static final int DISPLAY_TYPE_OTHERS = 1;

    public static final int NOTIFY_USER_TYPE_PID = 0;
    public static final int NOTIFY_USER_TYPE_FRAME_UPDATE = 1;
    public static final int NOTIFY_USER_TYPE_DISPLAY_TYPE = 2;
    public static final int NOTIFY_USER_TYPE_SCENARIO_ON  = 3;
    public static final int NOTIFY_USER_TYPE_SCENARIO_OFF = 4;

    public static final int CMD_GET_CPU_FREQ_LEVEL_COUNT        = 0;
    public static final int CMD_GET_CPU_FREQ_LITTLE_LEVEL_COUNT = 1;
    public static final int CMD_GET_CPU_FREQ_BIG_LEVEL_COUNT    = 2;
    public static final int CMD_GET_GPU_FREQ_LEVEL_COUNT        = 3;
    public static final int CMD_GET_MEM_FREQ_LEVEL_COUNT        = 4;
    public static final int CMD_GET_PERF_INDEX_MIN              = 5;
    public static final int CMD_GET_PERF_INDEX_MAX              = 6;
    public static final int CMD_GET_PERF_NORMALIZED_INDEX_MAX   = 7;

    public static final int CMD_SET_CPU_CORE_MIN            = 0;
    public static final int CMD_SET_CPU_CORE_MAX            = 1;
    public static final int CMD_SET_CPU_CORE_BIG_LITTLE_MIN = 2;
    public static final int CMD_SET_CPU_CORE_BIG_LITTLE_MAX = 3;
    public static final int CMD_SET_CPU_FREQ_MIN            = 4;
    public static final int CMD_SET_CPU_FREQ_MAX            = 5;
    public static final int CMD_SET_CPU_FREQ_BIG_LITTLE_MIN = 6;
    public static final int CMD_SET_CPU_FREQ_BIG_LITTLE_MAX = 7;
    public static final int CMD_SET_GPU_FREQ_MIN            = 8;
    public static final int CMD_SET_GPU_FREQ_MAX            = 9;
    public static final int CMD_SET_VCORE                   = 10;
    public static final int CMD_SET_SCREEN_OFF_STATE        = 11;
    public static final int CMD_SET_CPUFREQ_HISPEED_FREQ    = 12;
    public static final int CMD_SET_CPUFREQ_MIN_SAMPLE_TIME = 13;
    public static final int CMD_SET_CPUFREQ_ABOVE_HISPEED_DELAY = 14;
    public static final int CMD_SET_CLUSTER_CPU_CORE_MIN    = 15;
    public static final int CMD_SET_CLUSTER_CPU_CORE_MAX    = 16;
    public static final int CMD_SET_CLUSTER_CPU_FREQ_MIN    = 17;
    public static final int CMD_SET_CLUSTER_CPU_FREQ_MAX    = 18;
    public static final int CMD_SET_ROOT_CLUSTER            = 19;
    public static final int CMD_SET_CPU_UP_THRESHOLD        = 20;
    public static final int CMD_SET_CPU_DOWN_THRESHOLD      = 21;
    public static final int CMD_SET_PERF_INDEX              = 22;
    public static final int CMD_SET_NORMALIZED_PERF_INDEX   = 23;

    public void boostEnable(int scenario);
    public void boostDisable(int scenario);
    public void boostEnableTimeout(int scenario, int timeout);
    public void boostEnableTimeoutMs(int scenario, int timeout_ms);
    public void notifyAppState(String packName, String className, int state, int pid);

    public int  userReg(int scn_core, int scn_freq);
    public int  userRegBigLittle(int scn_core_big, int scn_freq_big, int scn_core_little, int scn_freq_little);
    public void userUnreg(int handle);

    public int  userGetCapability(int cmd);

    public int  userRegScn();
    public void userRegScnConfig(int handle, int cmd, int param_1, int param_2, int param_3, int param_4);
    public void userUnregScn(int handle);

    public void userEnable(int handle);
    public void userEnableTimeout(int handle, int timeout);
    public void userEnableTimeoutMs(int handle, int timeout_ms);
    public void userEnableAsync(int handle);
    public void userEnableTimeoutAsync(int handle, int timeout);
    public void userEnableTimeoutMsAsync(int handle, int timeout_ms);
    public void userDisable(int handle);

    public void userResetAll();
    public void userDisableAll();
    public void userRestoreAll();

    public void dumpAll();

    public void setFavorPid(int pid);
    public void restorePolicy(int pid);
    public void notifyFrameUpdate(int level);
    public void notifyDisplayType(int type);
    public int getLastBoostPid();
    public void notifyUserStatus(int type, int status);
}
