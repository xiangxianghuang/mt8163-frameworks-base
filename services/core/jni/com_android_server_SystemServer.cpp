/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2007 The Android Open Source Project
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

#include <jni.h>
#include <JNIHelp.h>

#include <sensorservice/SensorService.h>
#ifdef MTK_SENSOR_HUB_SUPPORT
#include <sensorhubservice/SensorHubService.h>
#endif

#include <cutils/properties.h>
#include <utils/Log.h>
#include <utils/misc.h>

namespace android {

static void android_server_SystemServer_startSensorService(JNIEnv* /* env */, jobject /* clazz */) {
    char propBuf[PROPERTY_VALUE_MAX];
    property_get("system_init.startsensorservice", propBuf, "1");
    if (strcmp(propBuf, "1") == 0) {
        // Start the sensor service
        SensorService::instantiate();
    }

#ifdef MTK_SENSOR_HUB_SUPPORT
    property_get("ro.mtk_sensorhub_support", propBuf, "1");
    if (strcmp(propBuf, "1") == 0) {
        SensorHubService::instantiate();
    }
#endif
}

/*
 * JNI registration.
 */
static JNINativeMethod gMethods[] = {
    /* name, signature, funcPtr */
    { "startSensorService", "()V", (void*) android_server_SystemServer_startSensorService },
};

int register_android_server_SystemServer(JNIEnv* env)
{
    return jniRegisterNativeMethods(env, "com/android/server/SystemServer",
            gMethods, NELEM(gMethods));
}

}; // namespace android
