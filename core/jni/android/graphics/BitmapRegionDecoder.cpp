/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2010 The Android Open Source Project
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

#define LOG_TAG "BitmapRegionDecoder"

#include "SkBitmap.h"
#include "SkData.h"
#include "SkImageEncoder.h"
#include "GraphicsJNI.h"
#include "SkUtils.h"
#include "SkTemplates.h"
#include "SkPixelRef.h"
#include "SkStream.h"
#include "BitmapFactory.h"
#include "AutoDecodeCancel.h"
#include "CreateJavaOutputStreamAdaptor.h"
#include "Utils.h"
#include "JNIHelp.h"

#include "core_jni_helpers.h"
#include "android_util_Binder.h"
#include "android_nio_utils.h"
#include "CreateJavaOutputStreamAdaptor.h"

#include <binder/Parcel.h>
#include <jni.h>
#include <androidfw/Asset.h>
#include <sys/stat.h>

using namespace android;

class SkBitmapRegionDecoder {
public:
    SkBitmapRegionDecoder(SkImageDecoder* decoder, int width, int height) {
        fDecoder = decoder;
        fWidth = width;
        fHeight = height;
    }
    ~SkBitmapRegionDecoder() {
        SkDELETE(fDecoder);
    }

#if 0 //mtk skia multi thread jpeg region decode support
    bool decodeRegion(SkBitmap* bitmap, const SkIRect& rect,
                      SkColorType pref, int sampleSize) {
        fDecoder->setSampleSize(sampleSize);
        return fDecoder->decodeSubset(bitmap, rect, pref);
    }
#endif

    bool decodeRegion(SkBitmap* bitmap, const SkIRect& rect,
                      SkColorType pref, int sampleSize, void* dc) {
        fDecoder->setSampleSize(sampleSize);
#ifdef MTK_SKIA_MULTI_THREAD_JPEG_REGION
    #ifdef MTK_IMAGE_DC_SUPPORT
        if (fDecoder->getFormat() == SkImageDecoder::kJPEG_Format)
            return fDecoder->decodeSubset(bitmap, rect, pref, sampleSize, dc);
        else
            return fDecoder->decodeSubset(bitmap, rect, pref);
    #else
        if (fDecoder->getFormat() == SkImageDecoder::kJPEG_Format)
            return fDecoder->decodeSubset(bitmap, rect, pref, sampleSize, NULL);
        else
            return fDecoder->decodeSubset(bitmap, rect, pref);
    #endif
#else
        return fDecoder->decodeSubset(bitmap, rect, pref);
    #endif
    }


    SkImageDecoder* getDecoder() const { return fDecoder; }
    int getWidth() const { return fWidth; }
    int getHeight() const { return fHeight; }

private:
    SkImageDecoder* fDecoder;
    int fWidth;
    int fHeight;
};

// Takes ownership of the SkStreamRewindable. For consistency, deletes stream even
// when returning null.
static jobject createBitmapRegionDecoder(JNIEnv* env, SkStreamRewindable* stream) {
    SkImageDecoder* decoder = SkImageDecoder::Factory(stream);
    int width, height;
    if (NULL == decoder) {
        SkDELETE(stream);
        doThrowIOE(env, "Image format not supported");
        return nullObjectReturn("SkImageDecoder::Factory returned null");
    }

    JavaPixelAllocator *javaAllocator = new JavaPixelAllocator(env);
    decoder->setAllocator(javaAllocator);
    javaAllocator->unref();

    // This call passes ownership of stream to the decoder, or deletes on failure.
    if (!decoder->buildTileIndex(stream, &width, &height)) {
        char msg[100];
        snprintf(msg, sizeof(msg), "Image failed to decode using %s decoder",
                decoder->getFormatName());
        doThrowIOE(env, msg);
        SkDELETE(decoder);
        return nullObjectReturn("decoder->buildTileIndex returned false");
    }

    SkBitmapRegionDecoder *bm = new SkBitmapRegionDecoder(decoder, width, height);
    return GraphicsJNI::createBitmapRegionDecoder(env, bm);
}

static jobject nativeNewInstanceFromByteArray(JNIEnv* env, jobject, jbyteArray byteArray,
                                     jint offset, jint length, jboolean isShareable) {
    /*  If isShareable we could decide to just wrap the java array and
        share it, but that means adding a globalref to the java array object
        For now we just always copy the array's data if isShareable.
     */
    AutoJavaByteArray ar(env, byteArray);
    SkMemoryStream* stream = new SkMemoryStream(ar.ptr() + offset, length, true);

    // the decoder owns the stream.
    jobject brd = createBitmapRegionDecoder(env, stream);
    return brd;
}

static jobject nativeNewInstanceFromFileDescriptor(JNIEnv* env, jobject clazz,
                                          jobject fileDescriptor, jboolean isShareable) {
    NPE_CHECK_RETURN_ZERO(env, fileDescriptor);

    jint descriptor = jniGetFDFromFileDescriptor(env, fileDescriptor);

    struct stat fdStat;
    if (fstat(descriptor, &fdStat) == -1) {
        doThrowIOE(env, "broken file descriptor");
        return nullObjectReturn("fstat return -1");
    }

    SkAutoTUnref<SkData> data(SkData::NewFromFD(descriptor));
    SkMemoryStream* stream = new SkMemoryStream(data);

    // the decoder owns the stream.
    jobject brd = createBitmapRegionDecoder(env, stream);
    return brd;
}

static jobject nativeNewInstanceFromStream(JNIEnv* env, jobject clazz,
                                  jobject is,       // InputStream
                                  jbyteArray storage, // byte[]
                                  jboolean isShareable) {
    jobject brd = NULL;
    // for now we don't allow shareable with java inputstreams
    SkStreamRewindable* stream = CopyJavaInputStream(env, is, storage);

    if (stream) {
        // the decoder owns the stream.
        brd = createBitmapRegionDecoder(env, stream);
    }
    return brd;
}

static jobject nativeNewInstanceFromAsset(JNIEnv* env, jobject clazz,
                                 jlong native_asset, // Asset
                                 jboolean isShareable) {
    Asset* asset = reinterpret_cast<Asset*>(native_asset);
    SkMemoryStream* stream = CopyAssetToStream(asset);
    if (NULL == stream) {
        return NULL;
    }

    // the decoder owns the stream.
    jobject brd = createBitmapRegionDecoder(env, stream);
    return brd;
}

/*
 * nine patch not supported
 *
 * purgeable not supported
 * reportSizeToVM not supported
 */

#define MTK_BRD_MULTI_THREAD
static jobject nativeDecodeRegion(JNIEnv* env, jobject, jlong brdHandle,
                                jint start_x, jint start_y, jint width, jint height, jobject options) {
    SkBitmapRegionDecoder *brd = reinterpret_cast<SkBitmapRegionDecoder*>(brdHandle);
    jobject tileBitmap = NULL;
    SkImageDecoder *decoder = brd->getDecoder();
    int sampleSize = 1;
    int postproc = 0;
    int postprocflag = 0;
#ifdef MTK_IMAGE_DC_SUPPORT
    void* dc = NULL;
    bool dcflag = false;
    jint* pdynamicCon = NULL;
    jintArray dynamicCon;
    jsize size = 0;
#endif

    SkColorType prefColorType = kUnknown_SkColorType;
    bool doDither = true;
    bool preferQualityOverSpeed = false;
    bool requireUnpremultiplied = false;

#ifdef MTK_BRD_MULTI_THREAD
    jobject ret = NULL;
    decoder->regionDecodeLock();
#endif

    if (NULL != options) {
        sampleSize = env->GetIntField(options, gOptions_sampleSizeFieldID);
        // initialize these, in case we fail later on
        env->SetIntField(options, gOptions_widthFieldID, -1);
        env->SetIntField(options, gOptions_heightFieldID, -1);
        env->SetObjectField(options, gOptions_mimeFieldID, 0);

        jobject jconfig = env->GetObjectField(options, gOptions_configFieldID);
        prefColorType = GraphicsJNI::getNativeBitmapColorType(env, jconfig);
        doDither = env->GetBooleanField(options, gOptions_ditherFieldID);
        postproc = env->GetBooleanField(options, gOptions_postprocFieldID);
        postprocflag = env->GetIntField(options, gOptions_postprocflagFieldID);

#ifdef MTK_IMAGE_DC_SUPPORT
        dcflag = env->GetBooleanField(options, gOptions_dynamicConflagFieldID);
        dynamicCon = (jintArray)env->GetObjectField(options, gOptions_dynamicConFieldID);
        pdynamicCon = env->GetIntArrayElements(dynamicCon, NULL);
        size = env->GetArrayLength(dynamicCon);
#endif

        preferQualityOverSpeed = env->GetBooleanField(options,
                gOptions_preferQualityOverSpeedFieldID);
        // Get the bitmap for re-use if it exists.
        tileBitmap = env->GetObjectField(options, gOptions_bitmapFieldID);
        requireUnpremultiplied = !env->GetBooleanField(options, gOptions_premultipliedFieldID);
    }
#ifdef MTK_BRD_MULTI_THREAD
    if(tileBitmap != NULL && decoder->isAllowMultiThreadRegionDecode()){
      decoder->regionDecodeUnlock();
    }
    SkDebugf("nativeDecodeRegion: options %x, tileBitmap %x!!\n", options, tileBitmap);
#endif
    decoder->setPostProcFlag((postproc | (postprocflag << 4)));

#ifdef MTK_IMAGE_DC_SUPPORT
    if (dcflag == true) {
        dc = (void*)pdynamicCon;
        int len = (int)size;
        decoder->setDynamicCon(dc, len);
    } else {
        dc = NULL;
        decoder->setDynamicCon(dc, 0);
    }
    ALOGD("nativeDecodeRegion dcflag %d, dc %p", dcflag, dc);
#endif

    decoder->setDitherImage(doDither);
    decoder->setPreferQualityOverSpeed(preferQualityOverSpeed);
    decoder->setRequireUnpremultipliedColors(requireUnpremultiplied);
    AutoDecoderCancel adc(options, decoder);

    // To fix the race condition in case "requestCancelDecode"
    // happens earlier than AutoDecoderCancel object is added
    // to the gAutoDecoderCancelMutex linked list.
    if (NULL != options && env->GetBooleanField(options, gOptions_mCancelID)) {
      #ifdef MTK_BRD_MULTI_THREAD
        decoder->regionDecodeUnlock();
      #endif
      return nullObjectReturn("gOptions_mCancelID");;
    }

    SkIRect region;
    region.fLeft = start_x;
    region.fTop = start_y;
    region.fRight = start_x + width;
    region.fBottom = start_y + height;
    SkBitmap bitmap;

    if (tileBitmap != NULL) {
        // Re-use bitmap.
        GraphicsJNI::getSkBitmap(env, tileBitmap, &bitmap);
    }

    #ifdef MTK_IMAGE_DC_SUPPORT
    if (!brd->decodeRegion(&bitmap, region, prefColorType, sampleSize, dc))
    #else
    if (!brd->decodeRegion(&bitmap, region, prefColorType, sampleSize, NULL))
    #endif
    {
      #ifdef MTK_BRD_MULTI_THREAD
        decoder->regionDecodeUnlock();
      #endif
      return nullObjectReturn("decoder->decodeRegion returned false");
    }

#if 0 //mtk skia multi thread jpeg region decode support
    if (!brd->decodeRegion(&bitmap, region, prefColorType, sampleSize)) {
      return nullObjectReturn("decoder->decodeRegion returned false");
    }
#endif

    // update options (if any)
    if (NULL != options) {
        env->SetIntField(options, gOptions_widthFieldID, bitmap.width());
        env->SetIntField(options, gOptions_heightFieldID, bitmap.height());
        // TODO: set the mimeType field with the data from the codec.
        // but how to reuse a set of strings, rather than allocating new one
        // each time?
        env->SetObjectField(options, gOptions_mimeFieldID,
                            getMimeTypeString(env, decoder->getFormat()));
    }

    if (tileBitmap != NULL) {
      #ifdef MTK_BRD_MULTI_THREAD
        decoder->regionDecodeUnlock();
      #endif
      bitmap.notifyPixelsChanged();
      return tileBitmap;
    }

    JavaPixelAllocator* allocator = (JavaPixelAllocator*) decoder->getAllocator();

    int bitmapCreateFlags = 0;
    if (!requireUnpremultiplied) bitmapCreateFlags |= GraphicsJNI::kBitmapCreateFlag_Premultiplied;

#ifdef MTK_BRD_MULTI_THREAD
    ret =  GraphicsJNI::createBitmap(env, allocator->getStorageObjAndReset(),bitmapCreateFlags);
    decoder->regionDecodeUnlock();
    return ret ;
#else
    GraphicsJNI::createBitmap(env, allocator->getStorageObjAndReset(),bitmapCreateFlags);
#endif
}

static jint nativeGetHeight(JNIEnv* env, jobject, jlong brdHandle) {
    SkBitmapRegionDecoder *brd = reinterpret_cast<SkBitmapRegionDecoder*>(brdHandle);
    return static_cast<jint>(brd->getHeight());
}

static jint nativeGetWidth(JNIEnv* env, jobject, jlong brdHandle) {
    SkBitmapRegionDecoder *brd = reinterpret_cast<SkBitmapRegionDecoder*>(brdHandle);
    return static_cast<jint>(brd->getWidth());
}

static void nativeClean(JNIEnv* env, jobject, jlong brdHandle) {
    SkBitmapRegionDecoder *brd = reinterpret_cast<SkBitmapRegionDecoder*>(brdHandle);
    delete brd;
}

///////////////////////////////////////////////////////////////////////////////

static JNINativeMethod gBitmapRegionDecoderMethods[] = {
    {   "nativeDecodeRegion",
        "(JIIIILandroid/graphics/BitmapFactory$Options;)Landroid/graphics/Bitmap;",
        (void*)nativeDecodeRegion},

    {   "nativeGetHeight", "(J)I", (void*)nativeGetHeight},

    {   "nativeGetWidth", "(J)I", (void*)nativeGetWidth},

    {   "nativeClean", "(J)V", (void*)nativeClean},

    {   "nativeNewInstance",
        "([BIIZ)Landroid/graphics/BitmapRegionDecoder;",
        (void*)nativeNewInstanceFromByteArray
    },

    {   "nativeNewInstance",
        "(Ljava/io/InputStream;[BZ)Landroid/graphics/BitmapRegionDecoder;",
        (void*)nativeNewInstanceFromStream
    },

    {   "nativeNewInstance",
        "(Ljava/io/FileDescriptor;Z)Landroid/graphics/BitmapRegionDecoder;",
        (void*)nativeNewInstanceFromFileDescriptor
    },

    {   "nativeNewInstance",
        "(JZ)Landroid/graphics/BitmapRegionDecoder;",
        (void*)nativeNewInstanceFromAsset
    },
};

int register_android_graphics_BitmapRegionDecoder(JNIEnv* env)
{
    return android::RegisterMethodsOrDie(env, "android/graphics/BitmapRegionDecoder",
            gBitmapRegionDecoderMethods, NELEM(gBitmapRegionDecoderMethods));
}
