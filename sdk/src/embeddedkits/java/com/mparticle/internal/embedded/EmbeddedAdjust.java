package com.mparticle.internal.embedded;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Location;

import com.mparticle.internal.embedded.adjust.sdk.Adjust;
import com.mparticle.MPEvent;
import com.mparticle.MPProduct;
import com.mparticle.MParticle;
import com.mparticle.internal.MPActivityCallbacks;
import com.mparticle.internal.embedded.adjust.sdk.OnFinishedListener;
import com.mparticle.internal.embedded.adjust.sdk.ResponseData;

import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <p/>
 * Embedded implementation of the Adjust SDK 3.6.2
 * <p/>
 */
class EmbeddedAdjust extends EmbeddedProvider implements MPActivityCallbacks, OnFinishedListener {

    private static final String APP_TOKEN = "appToken";
    private static final String HOST = "app.adjust.io";
    boolean isRunning = false;
    boolean initialized = false;
    private AtomicBoolean hasResumed = new AtomicBoolean(false);
    //check once per run to make sure we've set the referrer.
    private boolean referrerSet = false;

    EmbeddedAdjust(EmbeddedKitManager ekManager) {
        super(ekManager);
    }

    private void initAdjust(){
        if (!initialized) {
            boolean production = MParticle.Environment.Production.equals(MParticle.getInstance().getEnvironment());
            Adjust.appDidLaunch(context,
                    properties.get(APP_TOKEN),
                    production ? "production" : "sandbox",
                    production ? "info" : "verbose",
                    false);
            Adjust.setOnFinishedListener(this);
            if (!mEkManager.getAppStateManager().isBackgrounded()) {
                if (!hasResumed.get()) {
                    Adjust.onResume(context);
                    hasResumed.set(true);
                }
            }
            initialized = true;
        }
    }

    @Override
    protected EmbeddedProvider update() {
        initAdjust();

        if (!referrerSet) {
            String installReferrer = MParticle.getInstance().getInstallReferrer();
            if (installReferrer != null) {
                referrerSet = true;
                MParticle.getInstance().setInstallReferrer(installReferrer);
            }
        }
        return this;
    }

    @Override
    public String getName() {
        return "Adjust";
    }

    @Override
    public boolean isOriginator(String uri) {
        return uri != null && uri.toLowerCase().contains(HOST);
    }

    @Override
    public void onActivityCreated(Activity activity, int activityCount) {

    }

    @Override
    public void onActivityResumed(Activity activity, int currentCount) {
        if (!hasResumed.get()) {
            Adjust.onResume(activity);
            hasResumed.set(true);
        }
    }

    @Override
    public void onActivityPaused(Activity activity, int activityCount) {
        Adjust.onPause();
        hasResumed.set(false);
    }

    @Override
    public void onActivityStopped(Activity activity, int activityCount) {

    }



    @Override
    public void onActivityStarted(Activity activity, int activityCount) {

    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public void onFinishedTracking(ResponseData responseData) {
        if (!this.isRunning() && responseData != null && responseData.wasSuccess()){
            isRunning = true;
            Adjust.setOnFinishedListener(null);
        }
    }
}
