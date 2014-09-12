package com.mparticle;

import android.annotation.SuppressLint;
import android.app.IntentService;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code IntentService } used internally by the SDK to process incoming broadcast messages in the background. Required for push notification functionality.
 * <p/>
 * This {@code IntentService} must be specified within the {@code <application>} block of your application's {@code AndroidManifest.xml} file:
 * <p/>
 * <pre>
 * {@code
 * <service android:name="com.mparticle.MPService" />}
 * </pre>
 */
@SuppressLint("Registered")
public class MPService extends IntentService {

    private static final String TAG = Constants.LOG_TAG;
    static final String MPARTICLE_NOTIFICATION_OPENED = "com.mparticle.push.notification_opened";
    private static final Object LOCK = MPService.class;

    private static PowerManager.WakeLock sWakeLock;
    private static final String APP_STATE = "com.mparticle.push.appstate";
    //private static final String PUSH_ORIGINAL_PAYLOAD = "com.mparticle.push.originalpayload";
    //private static final String PUSH_REDACTED_PAYLOAD = "com.mparticle.push.redactedpayload";
    private static final String BROADCAST_PERMISSION = ".mparticle.permission.NOTIFICATIONS";

    public MPService() {
        super("com.mparticle.MPService");
    }

    static void runIntentInService(Context context, Intent intent) {
        synchronized (LOCK) {
            if (sWakeLock == null) {
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                sWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            }
        }
        sWakeLock.acquire();
        intent.setClass(context, MPService.class);
        context.startService(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("MPService", "onStartCommand");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public final void onHandleIntent(final Intent intent) {
        boolean release = true;
        try {

            MParticle.start(getApplicationContext());
            MParticle.getInstance().mEmbeddedKitManager.handleIntent(intent);

            String action = intent.getAction();
            Log.i("MPService", "Handling action: " + action);
            if (action.equals("com.google.android.c2dm.intent.REGISTRATION")) {
                handleRegistration(intent);
            } else if (action.equals("com.google.android.c2dm.intent.RECEIVE")) {
                release = false;
                (new AsyncTask<Intent, Void, Notification>() {
                    @Override
                    protected Notification doInBackground(Intent... params) {
                        return handleGcmMessage(params[0]);
                    }

                    @Override
                    protected void onPostExecute(Notification notification) {
                        super.onPostExecute(notification);
                        if (notification != null) {
                            NotificationManager mNotifyMgr =
                                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                            mNotifyMgr.notify(notification.hashCode(), notification);

                        }
                        synchronized (LOCK) {
                            if (sWakeLock != null && sWakeLock.isHeld()) {
                                sWakeLock.release();
                            }
                        }
                    }
                }).execute(intent);

            } else if (action.equals("com.google.android.c2dm.intent.UNREGISTER")) {
                intent.putExtra("unregistered", "true");
                handleRegistration(intent);
            } else if (action.equals(MPARTICLE_NOTIFICATION_OPENED)) {
                handleNotificationClick(intent);
            } else {
                handeReRegistration();
            }
        } finally {
            synchronized (LOCK) {
                if (release && sWakeLock != null && sWakeLock.isHeld()) {
                    sWakeLock.release();
                }
            }
        }
    }

    private void handeReRegistration() {
        MParticle.start(getApplicationContext());
        MParticle.getInstance();
    }

    private void handleNotificationClick(Intent intent) {

        broadcastNotificationClicked(intent.getBundleExtra(PUSH_ORIGINAL_PAYLOAD));

        try {
            MParticle.start(getApplicationContext());
            MParticle mMParticle = MParticle.getInstance();
            Bundle extras = intent.getExtras();
            String appState = extras.getString(APP_STATE);
            mMParticle.logNotification(intent.getExtras().getBundle(PUSH_REDACTED_PAYLOAD),
                                        appState);
        } catch (Throwable t) {

        }
        PackageManager packageManager = getPackageManager();
        Intent launchIntent = packageManager.getLaunchIntentForPackage(getPackageName());
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(launchIntent);
    }

    private void handleRegistration(Intent intent) {
        try {
            MParticle mMParticle = MParticle.getInstance();
            String registrationId = intent.getStringExtra("registration_id");
            String unregistered = intent.getStringExtra("unregistered");
            String error = intent.getStringExtra("error");

            if (registrationId != null) {
                // registration succeeded
                mMParticle.setPushRegistrationId(registrationId);
            } else if (unregistered != null) {
                // unregistration succeeded
                mMParticle.clearPushNotificationId();
            } else if (error != null) {
                // Unrecoverable error, log it
                Log.i(TAG, "GCM registration error: " + error);
            }
        } catch (Throwable t) {

        }
    }

    private Notification handleGcmMessage(Intent intent) {
        try {
            Bundle newExtras = new Bundle();
            newExtras.putBundle(PUSH_ORIGINAL_PAYLOAD, intent.getExtras());
            newExtras.putBundle(PUSH_REDACTED_PAYLOAD, intent.getExtras());

            if (!MParticle.appRunning) {
                newExtras.putString(APP_STATE, AppStateManager.APP_STATE_NOTRUNNING);
            }else {
                if (!newExtras.containsKey(Constants.MessageKey.APP_STATE)) {
                    if (MParticle.getInstance().mAppStateManager.isBackgrounded()) {
                        newExtras.putString(APP_STATE, AppStateManager.APP_STATE_BACKGROUND);
                    } else {
                        newExtras.putString(APP_STATE, AppStateManager.APP_STATE_FOREGROUND);
                    }
                }
            }

            Notification notification;
            if (MPCloudMessage.isValidMPMessage(intent.getExtras())){
                notification = MPCloudMessage.buildNotification(getApplicationContext(), intent.getExtras());
            }else{
                String message = findProviderMessage(intent.getExtras());
                newExtras.getBundle(PUSH_REDACTED_PAYLOAD).putString(message, "");
                newExtras.getBundle(PUSH_ORIGINAL_PAYLOAD).putString(MParticlePushUtility.PUSH_ALERT_EXTRA, message);
                broadcastNotificationReceived(newExtras.getBundle(PUSH_ORIGINAL_PAYLOAD));
                notification = showBasicPush(MPCloudMessage.getFallbackIcon(getApplicationContext()),
                        MPCloudMessage.getFallbackTitle(getApplicationContext()),
                        message,
                        newExtras);
            }
            broadcastNotificationReceived(newExtras.getBundle(PUSH_ORIGINAL_PAYLOAD));
            return notification;
        } catch (Throwable t) {

            return null;
        }
    }


    private void broadcastNotificationReceived(Bundle originalPayload) {
        Intent intent = new Intent(MParticlePushUtility.BROADCAST_NOTIFICATION_RECEIVED);
        intent.putExtras(originalPayload);
        String packageName = getPackageName();
        sendBroadcast(intent, packageName + BROADCAST_PERMISSION);
    }

    private void broadcastNotificationClicked(Bundle originalPayload) {
        Intent intent = new Intent(MParticlePushUtility.BROADCAST_NOTIFICATION_TAPPED);
        intent.putExtras(originalPayload);
        String packageName = getPackageName();
        sendBroadcast(intent, packageName + BROADCAST_PERMISSION);
    }

    private String findProviderMessage(Bundle extras){
        String[] possibleKeys = MParticle.getInstance().mConfigManager.getPushKeys();
        if (possibleKeys != null) {
            for (String key : possibleKeys) {
                String message = extras.getString(key);
                if (message != null && message.length() > 0) {
                    extras.remove(key);
                    return message;
                }
            }
        }
        Log.w(Constants.LOG_TAG, "Failed to extract 3rd party push message.");
        return "";
    }

    private Notification showBasicPush(int iconId, String title, String message, Bundle newExtras) {
        MParticle.start(getApplicationContext());
        MParticle mMParticle = MParticle.getInstance();
        Intent launchIntent = new Intent(getApplicationContext(), MPService.class);
        launchIntent.setAction(MPARTICLE_NOTIFICATION_OPENED);
        launchIntent.putExtras(newExtras);
        PendingIntent notifyIntent = PendingIntent.getService(getApplicationContext(), 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(this)
                .setContentIntent(notifyIntent)
                .setSmallIcon(iconId).setTicker(message).setContentTitle(title).setContentText(message).build();

        if (mMParticle.mConfigManager.isPushSoundEnabled()) {
            notification.defaults |= Notification.DEFAULT_SOUND;
        }
        if (mMParticle.mConfigManager.isPushVibrationEnabled()) {
            notification.defaults |= Notification.DEFAULT_VIBRATE;
        }
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        return notification;
    }

}