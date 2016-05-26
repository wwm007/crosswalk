// Copyright (c) 2015 Intel Corporation. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.xwalk.core;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.util.List;

import org.xwalk.core.XWalkLibraryLoader.DownloadListener;

/**
 * <p><code>XWalkUpdater</code> is a follow-up solution for {@link XWalkInitializer} in case the
 * initialization failed. The users of {@link XWalkActivity} don't need to use this class.</p>
 *
 * <p><code>XWalkUpdater</code> helps to download Crosswalk Project runtime and displays dialogs
 * to interact with the user. By default, it will navigate to the page of Crosswalk Project on the
 * default application store of the device, subsequent process will be up to the user. If the
 * developer specified the download URL, it will launch the download manager to fetch the APK.
 * </p>
 *
 * <p>After proper Crosswalk Project runtime is downloaded and installed, the user will return to
 * current activity from the application store or the installer. The developer should check this
 * point and invoke {@link XWalkInitializer#initAsync()} again to repeat the initialization process.
 * Please note that from now on, the application will be running in shared mode.</p>
 *
 * <p>Besides shared mode, there is another way to update Crosswalk Project runtime that all of
 * download process are executed in background silently without interrupting the user.</p>
 *
 * <p>In download mode, we support two kinds of APKs of Crosswalk Project runtime, one is the
 * original XWalkRuntimeLib.apk, another one is XWalkRuntimeLibLzma.apk which contains compressed
 * libraries and resources. It has smaller size but will take a little more time at the first
 * launch.</p>
 *
 * <h3>Sample Code</h3>
 *
 * <p>Here is the sample code for embedded mode and shared mode:</p>
 *
 * <pre>
 * import android.app.Activity;
 * import android.os.Bundle;
 * import org.xwalk.core.XWalkInitializer;
 * import org.xwalk.core.XWalkUpdater;
 * import org.xwalk.core.XWalkView;
 *
 * public class MainActivity extends Activity implements
 *        XWalkInitializer.XWalkInitListener,
 *        XWalkUpdater.XWalkUpdateListener {
 *
 *     XWalkInitializer mXWalkInitializer;
 *     XWalkUpdater mXWalkUpdater;
 *     XWalkView mXWalkView;
 *
 *     &#64;Override
 *     protected void onCreate(Bundle savedInstanceState) {
 *         super.onCreate(savedInstanceState);
 *
 *         // Must call initAsync() before anything that involves the embedding
 *         // API, including invoking setContentView() with the layout which
 *         // holds the XWalkView object.
 *         mXWalkInitializer = new XWalkInitializer(this, this);
 *         mXWalkInitializer.initAsync();
 *
 *         // Until onXWalkReady() is invoked, you should do nothing with the
 *         // embedding API except the following:
 *         // 1. Instantiate the XWalkView object
 *         // 2. Call XWalkPreferences.setValue()
 *         // 3. Call mXWalkView.setXXClient(), e.g., setUIClient
 *         // 4. Call mXWalkView.setXXListener(), e.g., setDownloadListener
 *         // 5. Call mXWalkView.addJavascriptInterface()
 *         setContentView(R.layout.activity_main);
 *         mXWalkView = (XWalkView) findViewById(R.id.xwalkview);
 *     }
 *
 *     &#64;Override
 *     public void onXWalkInitCompleted() {
 *         // Do anyting with the embedding API
 *         mXWalkView.load("https://crosswalk-project.org/", null);
 *     }
 *
 *     &#64;Override
 *     public void onXWalkInitStarted() {
 *     }
 *
 *     &#64;Override
 *     public void onXWalkInitCancelled() {
 *         // Perform error handling here
 *     }
 *
 *     &#64;Override
 *     public void onXWalkInitFailed() {
 *         if (mXWalkUpdater == null) {
 *             mXWalkUpdater = new XWalkUpdater(this, this);
 *         }
 *
 *         // The updater won't be launched if previous update dialog is
 *         // showing.
 *         mXWalkUpdater.updateXWalkRuntime();
 *     }
 *
 *     &#64;Override
 *     protected void onResume() {
 *         super.onResume();
 *
 *         // Try to initialize again when the user completed updating and
 *         // returned to current activity. The initAsync() will do nothing if
 *         // the initialization has already been completed successfully.
 *         mXWalkInitializer.initAsync();
 *     }
 *
 *     &#64;Override
 *     public void onXWalkUpdateCancelled() {
 *         // Perform error handling here
 *     }
 * }
 * </pre>
 *
 * <p>For download mode, the code is almost the same except you should use
 * {@link XWalkBackgroundUpdateListener} instead of {link XWalkUpdateListener}. </p>
 *
 * <pre>
 * public class MyActivity extends Activity implements
 *         XWalkInitializer.XWalkInitListener,
 *         XWalkUpdater.XWalkBackgroundUpdateListener {
 *
 *     ......
 *
 *     &#64;Override
 *     public void onXWalkUpdateStarted() {
 *     }
 *
 *     &#64;Override
 *     public void onXWalkUpdateProgress(int percentage) {
 *     }
 *
 *     &#64;Override
 *     public void onXWalkUpdateCancelled() {
 *         // Perform error handling here
 *     }
 *
 *     &#64;Override
 *     public void onXWalkUpdateFailed() {
 *         // Perform error handling here
 *     }
 *
 *     &#64;Override
 *     public void onXWalkUpdateCompleted() {
 *         mXWalkInitializer.initAsync();
 *     }
 * }
 * </pre>
 *
 * <h3>App Manifest</h3>
 *
 * <p>For shared mode and download mode, you might need to edit the Android manifest to set some
 * properties. </p>
 *
 * <h4>Shared Mode</h4>
 *
 * <p>If you want the end-user to download Crosswalk Project runtime from specified URL instead of
 * switching to the application store, add following &lt;meta-data&gt; element inside the
 * &lt;application&gt; element:</p>
 *
 * <pre>
 * &lt;application&gt;
 *     &lt;meta-data android:name="xwalk_apk_url" android:value="http://host/XWalkRuntimeLib.apk" /&gt;
 * </pre>
 *
 * <p>Please note that when the HTTP request is sent to server, the URL will be appended with
 * "?arch=CPU_API" to indicate that on which CPU architecture it's currently running. The CPU_API
 * is the same as the value returned from "adb shell getprop ro.product.cpu_abi", e.g. x86 for
 * IA 32bit, x86_64 for IA 64bit, armeabi-v7a for ARM 32bit and arm64-v8a for ARM 64bit.
 *
 * <p>The specified APK will be downloaded to SD card, so you have to grant following permission: </p>
 *
 * <pre>
 * &lt;uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" /&gt;
 * </pre>
 *
 * <h4>Download Mode</h4>
 *
 * <p>Firstly, you need to add following &lt;meta-data&gt; element to enable download mode:</p>
 *
 * <pre>
 * &lt;application&gt;
 *     &lt;meta-data android:name="xwalk_download_mode" android:value="enable"/&gt;
 * </pre>
 *
 * <p>In download mode, the value of <code>xwalk_apk_url</code> is mandatory. However, the
 * downloaded Apk will be saved into application's private storage, so the permission of writing to
 * SD card is not needed anymore.</p>
 *
 * <p>By default, the application will verify the signature of downloaded Crosswalk Project runtime,
 * which is required to be the same as your application. But you can disable it by adding following
 * &lt;meta-data&gt; element:
 *
 * <pre>
 * &lt;application&gt;
 *     &lt;meta-data android:name="xwalk_verify" android:value="disable"/&gt;
 * </pre>
 *
 * <p> If your application has already downloaded Crosswalk Project runtime but the application got
 * an update after that, the build version of shared library you used to bundle with your
 * new application may be newer than the build version of downloaded Crosswalk Project runtime.
 * In this case, it will download new version of Crosswalk Project runtime from the server again.
 * If you want to continue using old version of Crosswalk Project runtime, you could add following
 * &lt;meta-data&gt; element:
 *
 * <pre>
 * &lt;application&gt;
 *     &lt;meta-data android:name="xwalk_download_mode_update" android:value="disable"/&gt;
 * </pre>
 *
 */

public class XWalkUpdater {
    /**
     * Interface used to update the Crosswalk runtime
     */
    public interface XWalkUpdateListener {
        /**
         * Run on the UI thread to notify the update is cancelled. It could be the user refused to
         * update or the download (from the specified URL) is cancelled
         */
        public void onXWalkUpdateCancelled();
    }

    /**
     * Interface used to update the Crosswalk runtime silently
     */
    public interface XWalkBackgroundUpdateListener {
        /**
         * Run on the UI thread to notify the update is started.
         */
        public void onXWalkUpdateStarted();

        /**
         * Run on the UI thread to notify the update progress.
         * @param percentage Shows the update progress in percentage.
         */
        public void onXWalkUpdateProgress(int percentage);

        /**
         * Run on the UI thread to notify the update is cancelled.
         */
        public void onXWalkUpdateCancelled();

        /**
         * Run on the UI thread to notify the update failed.
         */
        public void onXWalkUpdateFailed();

        /**
         * Run on the UI thread to notify the update is completed.
         */
        public void onXWalkUpdateCompleted();
    }

    private static final String ANDROID_MARKET_DETAILS = "market://details?id=";
    private static final String GOOGLE_PLAY_PACKAGE = "com.android.vending";

    private static final String META_XWALK_APK_URL = "xwalk_apk_url";
    private static final String META_XWALK_VERIFY = "xwalk_verify";
    private static final String ARCH_QUERY_STRING = "?arch=";

    private static final String TAG = "XWalkLib";

    private XWalkUpdateListener mUpdateListener;
    private XWalkBackgroundUpdateListener mBackgroundUpdateListener;
    private Activity mActivity;
    private XWalkDialogManager mDialogManager;
    private Runnable mDownloadCommand;
    private Runnable mCancelCommand;
    private String mXWalkApkUrl;
    private boolean mIsDownloading;

    /**
     * Create XWalkUpdater for single activity.
     *
     * @param listener The {@link XWalkUpdateListener} to use
     * @param activity The activity which initiate the update
     */
    public XWalkUpdater(XWalkUpdateListener listener, Activity activity) {
        mUpdateListener = listener;
        mActivity = activity;
        mDialogManager = new XWalkDialogManager(activity);
    }

    /**
     * Create XWalkUpdater for single activity.
     *
     * @param listener The {@link XWalkUpdateListener} to use
     * @param activity The activity which initiate the update
     * @param dialogManager The {@link XWalkDialogManager} to use
     */
    public XWalkUpdater(XWalkUpdateListener listener, Activity activity,
            XWalkDialogManager dialogManager) {
        mUpdateListener = listener;
        mActivity = activity;
        mDialogManager = dialogManager;
    }

    /**
     * Create XWalkUpdater for single activity. This updater will download silently.
     *
     * @param listener The {@link XWalkBackgroundUpdateListener} to use
     * @param activity The activity which initiate the update
     */
    public XWalkUpdater(XWalkBackgroundUpdateListener listener, Activity activity) {
        mBackgroundUpdateListener = listener;
        mActivity = activity;
    }

    /**
     * Update the Crosswalk runtime. There are 2 ways to download the Crosswalk runtime: from the
     * play store or the specified URL. It will download from the play store if the download URL is
     * not specified. To specify the download URL, insert a meta-data element with the name
     * "xwalk_apk_url" inside the application tag in the Android manifest.
     *
     * <p>Please try to initialize by {@link XWalkInitializer} first and only invoke this method
     * when the initialization failed. This method must be invoked on the UI thread.
     *
     * @return true if the updater is launched, false if is in updating, or the Crosswalk runtime
     *         doesn't need to be updated, or the Crosswalk runtime has not been initialized yet.
     */
    public boolean updateXWalkRuntime() {
        if (mIsDownloading || (mDialogManager != null && mDialogManager.isShowingDialog())) {
            return false;
        }

        int status = XWalkLibraryLoader.getLibraryStatus();
        if (status == XWalkLibraryInterface.STATUS_PENDING ||
                status == XWalkLibraryInterface.STATUS_MATCH) return false;

        if (mUpdateListener != null) {
            mDownloadCommand = new Runnable() {
                @Override
                public void run() {
                    downloadXWalkApk();
                }
            };
            mCancelCommand = new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "XWalkUpdater cancelled");
                    mUpdateListener.onXWalkUpdateCancelled();
                }
            };

            mDialogManager.showInitializationError(status, mCancelCommand, mDownloadCommand);
        } else if (mBackgroundUpdateListener != null) {
            downloadXWalkApkInBackground();
        } else {
            throw new IllegalArgumentException("Update listener is null");
        }

        return true;
    }

    /**
     * Set the download URL of the Crosswalk runtime. By default, the updater will get the URL from
     * the Android manifest.
     *
     * @param url The download URL.
     */
    public void setXWalkApkUrl(String url) {
        mXWalkApkUrl = url;
    }

    /**
     * Cancel the background download
     *
     * @return false if it is not a background updater or is not downloading, true otherwise.
     */
    public boolean cancelBackgroundDownload() {
        if (mBackgroundUpdateListener == null || !mIsDownloading) return false;
        return XWalkLibraryLoader.cancelHttpDownload();
    }

    private boolean dismissDialog() {
        if (mDialogManager == null || !mDialogManager.isShowingDialog()) return false;
        mDialogManager.dismissDialog();
        return true;
    }

    private void downloadXWalkApk() {
        // The download url is defined by the meta-data element with the name "xwalk_apk_url"
        // inside the application tag in the Android manifest.
        if (mXWalkApkUrl == null) {
            mXWalkApkUrl = getXWalkApkUrl();
            Log.d(TAG, "Crosswalk APK download URL: " + mXWalkApkUrl);
        }

        if (!mXWalkApkUrl.isEmpty()) {
            XWalkLibraryLoader.startDownloadManager(new ForegroundListener(), mActivity,
                    mXWalkApkUrl);
            return;
        }

        try {
            String packageName = XWalkLibraryInterface.XWALK_CORE_PACKAGE;
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(ANDROID_MARKET_DETAILS + packageName));
            List<ResolveInfo> infos = mActivity.getPackageManager().queryIntentActivities(
                    intent, PackageManager.MATCH_ALL);
            boolean primaryStoreIsGooglePlay =
                    infos.get(0).activityInfo.packageName.equals(GOOGLE_PLAY_PACKAGE);

            String primaryCpuAbi = XWalkCoreWrapper.getPrimaryCpuAbi();
            boolean isArmDevice = primaryCpuAbi.equalsIgnoreCase("armeabi-v7a")
                    || primaryCpuAbi.equalsIgnoreCase("arm64-v8a");

            String appAbi = XWalkCoreWrapper.getApplicationAbi();
            boolean is32BitApp = appAbi.equalsIgnoreCase("x86")
                    || appAbi.equalsIgnoreCase("armeabi-v7a");

            if (is32BitApp) {
                if (primaryStoreIsGooglePlay || isArmDevice) {
                    packageName = XWalkLibraryInterface.XWALK_CORE_PACKAGE;
                } else {
                    packageName = XWalkLibraryInterface.XWALK_CORE_IA_PACKAGE;
                }
            } else {
                if (primaryStoreIsGooglePlay || isArmDevice) {
                    packageName = XWalkLibraryInterface.XWALK_CORE64_PACKAGE;
                } else {
                    packageName = XWalkLibraryInterface.XWALK_CORE64_IA_PACKAGE;
                }
            }

            intent.setData(Uri.parse(ANDROID_MARKET_DETAILS + packageName));
            mActivity.startActivity(intent);

            Log.d(TAG, "Market opened");
            mDialogManager.dismissDialog();
        } catch (ActivityNotFoundException e) {
            throw new RuntimeException("Market open failed");
        }
    }

    private void downloadXWalkApkInBackground() {
        if (mXWalkApkUrl == null) {
            mXWalkApkUrl = getXWalkApkUrl();
            Log.d(TAG, "Crosswalk APK download URL: " + mXWalkApkUrl);
        }
        XWalkLibraryLoader.startHttpDownload(new BackgroundListener(), mActivity, mXWalkApkUrl);
    }

    private String getXWalkApkUrl() {
        String url = getAppMetaData(META_XWALK_APK_URL);
        return url == null ? "" : url + ARCH_QUERY_STRING + Build.CPU_ABI;
    }

    private class ForegroundListener implements DownloadListener {
        @Override
        public void onDownloadStarted() {
            mDialogManager.showDownloadProgress(new Runnable() {
                @Override
                public void run() {
                    XWalkLibraryLoader.cancelDownloadManager();
                }
            });
        }

        @Override
        public void onDownloadUpdated(int percentage) {
            mDialogManager.setProgress(percentage, 100);
        }

        @Override
        public void onDownloadCancelled() {
            mUpdateListener.onXWalkUpdateCancelled();
        }

        @Override
        public void onDownloadFailed(int status, int error) {
            mDialogManager.dismissDialog();
            mDialogManager.showDownloadError(mCancelCommand, mDownloadCommand);
        }

        @Override
        public void onDownloadCompleted(Uri uri) {
            mDialogManager.dismissDialog();

            Log.d(TAG, "Install the Crosswalk runtime: " + uri.toString());
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(uri, "application/vnd.android.package-archive");
            mActivity.startActivity(install);
        }
    }

    private class BackgroundListener implements DownloadListener {
        @Override
        public void onDownloadStarted() {
            mIsDownloading = true;
            mBackgroundUpdateListener.onXWalkUpdateStarted();
        }

        @Override
        public void onDownloadUpdated(int percentage) {
            mBackgroundUpdateListener.onXWalkUpdateProgress(percentage);
        }

        @Override
        public void onDownloadCancelled() {
            mIsDownloading = false;
            mBackgroundUpdateListener.onXWalkUpdateCancelled();
        }

        @Override
        public void onDownloadFailed(int status, int error) {
            mIsDownloading = false;
            mBackgroundUpdateListener.onXWalkUpdateFailed();
        }

        @Override
        public void onDownloadCompleted(Uri uri) {
            mIsDownloading = false;
            final String libFile = uri.getPath();
            final String destDir = mActivity.getDir(XWalkLibraryInterface.XWALK_CORE_EXTRACTED_DIR,
                    Context.MODE_PRIVATE).getAbsolutePath();
            Log.d(TAG, "Download mode extract dir: " + destDir);

            new AsyncTask<Void, Void, Boolean>() {
                @Override
                protected Boolean doInBackground(Void... params) {
                    String xwalkVerify = getAppMetaData(META_XWALK_VERIFY);
                    if (xwalkVerify == null || (!xwalkVerify.equalsIgnoreCase("disable")
                            && !xwalkVerify.equalsIgnoreCase("false"))) {
                        if (!verifyDownloadedXWalkRuntime(libFile)) {
                            return false;
                        }
                    }

                    if (XWalkDecompressor.isResourceCompressed(libFile)) {
                        if (!XWalkDecompressor.decompressResource(libFile, destDir)) {
                            return false;
                        }
                    } else {
                        if (!XWalkDecompressor.extractResource(libFile, destDir)) {
                            return false;
                        }
                    }
                    return true;
                }

                @Override
                protected void onPostExecute(Boolean result) {
                    new File(libFile).delete();

                    if (!result) {
                        mBackgroundUpdateListener.onXWalkUpdateFailed();
                    } else {
                        mBackgroundUpdateListener.onXWalkUpdateCompleted();
                    }
                }
            }.execute();
        }
    }

    private boolean verifyDownloadedXWalkRuntime(String libFile) {
        // getPackageArchiveInfo also check the integrity of the downloaded runtime APK
        // besides returning the PackageInfo with signatures.
        PackageInfo runtimePkgInfo = mActivity.getPackageManager().getPackageArchiveInfo(
                libFile, PackageManager.GET_SIGNATURES);
        if (runtimePkgInfo == null) {
            Log.e(TAG, "The downloaded XWalkRuntimeLib.apk is invalid!");
            return false;
        }

        PackageInfo appPkgInfo = null;
        try {
            appPkgInfo = mActivity.getPackageManager().getPackageInfo(
                    mActivity.getPackageName(), PackageManager.GET_SIGNATURES);
        } catch (NameNotFoundException e) {
            return false;
        }

        if (runtimePkgInfo.signatures == null || appPkgInfo.signatures == null) {
            Log.e(TAG, "No signature in package info");
            return false;
        }

        if (runtimePkgInfo.signatures.length != appPkgInfo.signatures.length) {
            Log.e(TAG, "signatures length not equal");
            return false;
        }

        for (int i = 0; i < runtimePkgInfo.signatures.length; ++i) {
            Log.d(TAG, "Checking signature " + i);
            if (!appPkgInfo.signatures[i].equals(runtimePkgInfo.signatures[i])) {
                Log.e(TAG, "signatures do not match");
                return false;
            }
        }
        Log.d(TAG, "Signature check passed");
        return true;
    }

    private String getAppMetaData(String name) {
        try {
            PackageManager packageManager = mActivity.getPackageManager();
            ApplicationInfo appInfo = packageManager.getApplicationInfo(
                    mActivity.getPackageName(), PackageManager.GET_META_DATA);
            return appInfo.metaData.getString(name);
        } catch (NameNotFoundException | NullPointerException e) {
        }
        return null;
    }
}
