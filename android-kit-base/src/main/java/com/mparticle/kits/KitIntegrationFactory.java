package com.mparticle.kits;

import com.mparticle.MParticle;
import com.mparticle.internal.Logger;

import org.json.JSONException;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class KitIntegrationFactory {

    Map<Integer, Class> supportedKits;

    public KitIntegrationFactory() {
        loadIntegrations();
    }

    /**
     * This is the canonical method mapping all known Kit/Module IDs to Kit class names.
     *
     * @return a mapping of module Ids to kit classes
     */
    protected Map<Integer, String> getKnownIntegrations() {
        Map<Integer, String> kits = new HashMap<Integer, String>();
        kits.put(MParticle.ServiceProviders.ADJUST,           "com.mparticle.kits.AdjustKit");
        kits.put(MParticle.ServiceProviders.APPBOY,           "com.mparticle.kits.AppboyKit");
        kits.put(MParticle.ServiceProviders.BRANCH_METRICS,   "com.mparticle.kits.BranchMetricsKit");
        kits.put(MParticle.ServiceProviders.COMSCORE,         "com.mparticle.kits.ComscoreKit");
        kits.put(MParticle.ServiceProviders.KAHUNA,           "com.mparticle.kits.KahunaKit");
        kits.put(MParticle.ServiceProviders.KOCHAVA,          "com.mparticle.kits.KochavaKit");
        kits.put(MParticle.ServiceProviders.FORESEE_ID,       "com.mparticle.kits.ForeseeKit");
        kits.put(MParticle.ServiceProviders.LOCALYTICS,       "com.mparticle.kits.LocalyticsKit");
        kits.put(MParticle.ServiceProviders.FLURRY,           "com.mparticle.kits.FlurryKit");
        kits.put(MParticle.ServiceProviders.WOOTRIC,          "com.mparticle.kits.WootricKit");
        kits.put(MParticle.ServiceProviders.CRITTERCISM,      "com.mparticle.kits.CrittercismKit");
        kits.put(MParticle.ServiceProviders.TUNE,             "com.mparticle.kits.TuneKit");
        kits.put(MParticle.ServiceProviders.APPSFLYER,        "com.mparticle.kits.AppsFlyerKit");
        kits.put(MParticle.ServiceProviders.APPTENTIVE,       "com.mparticle.kits.ApptentiveKit");
        kits.put(MParticle.ServiceProviders.BUTTON,           "com.mparticle.kits.ButtonKit");
        kits.put(MParticle.ServiceProviders.URBAN_AIRSHIP,    "com.mparticle.kits.UrbanAirshipKit");
        kits.put(MParticle.ServiceProviders.LEANPLUM,         "com.mparticle.kits.LeanplumKit");
        kits.put(MParticle.ServiceProviders.APPTIMIZE,        "com.mparticle.kits.ApptimizeKit");
        kits.put(MParticle.ServiceProviders.REVEAL_MOBILE,    "com.mparticle.kits.RevealMobileKit");
        kits.put(MParticle.ServiceProviders.RADAR,            "com.mparticle.kits.RadarKit");
        kits.put(MParticle.ServiceProviders.ITERABLE,         "com.mparticle.kits.IterableKit");
        kits.put(MParticle.ServiceProviders.SKYHOOK,          "com.mparticle.kits.SkyhookKit");
        kits.put(MParticle.ServiceProviders.SINGULAR,         "com.mparticle.kits.SingularKit");
        kits.put(MParticle.ServiceProviders.ADOBE,            "com.mparticle.kits.AdobeKit");
        kits.put(MParticle.ServiceProviders.INSTABOT,         "com.mparticle.kits.InstabotKit");
        kits.put(MParticle.ServiceProviders.APPSEE,           "com.mparticle.kits.AppseeKit");
        kits.put(MParticle.ServiceProviders.TAPLYTICS,        "com.mparticle.kits.TaplyticsKit");
        kits.put(MParticle.ServiceProviders.OPTIMIZELY,       "com.mparticle.kits.OptimizelyKit");
        return kits;
    }

    public KitIntegration createInstance(KitManagerImpl manager, KitConfiguration configuration) throws JSONException, ClassNotFoundException{
        KitIntegration kit = createInstance(manager, configuration.getKitId());
        if (kit != null) {
            kit.setConfiguration(configuration);
        }
        return kit;
    }

    public KitIntegration createInstance(KitManagerImpl manager, int moduleId) throws JSONException, ClassNotFoundException{
        if (supportedKits != null) {
            try {
                Constructor<KitIntegration> constructor = supportedKits.get(moduleId).getConstructor();
                constructor.setAccessible(true);
                return constructor.newInstance()
                        .setKitManager(manager);
            } catch (Exception e) {
                Logger.debug(e, "Failed to create Kit with ID: " + moduleId);
            }
        }
        return null;
    }

    private void loadIntegrations() {
        Map<Integer, String> knownIntegrations = getKnownIntegrations();
        for (Map.Entry<Integer, String> entry : knownIntegrations.entrySet()) {
            Class kitClass = loadKit(entry.getValue());
            if (kitClass != null) {
                if (supportedKits == null) {
                    supportedKits = new HashMap<Integer, Class>();
                }
                supportedKits.put(entry.getKey(), kitClass);
                Logger.debug(entry.getValue().substring(entry.getValue().lastIndexOf(".") + 1) + " detected.");
            }
        }
    }

    private Class loadKit(String className) {
        try {
            return Class.forName(className);
        } catch (Exception e) {
            Logger.verbose("Kit not bundled: ", className);
        }
        return null;
    }

    /**
     * Get the module IDs of the Kits that have been detected.
     *
     * @return
     */
    public Set<Integer> getSupportedKits() {
        if (supportedKits == null) {
            return null;
        }
        return supportedKits.keySet();
    }

    public boolean isSupported(int kitModuleId) {
        return supportedKits != null &&
                supportedKits.containsKey(kitModuleId);
    }
}
