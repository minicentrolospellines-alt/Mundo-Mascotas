package cl.negociospyme.blockpets;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/** Voluntary rewarded ads only. All rewards originate from the SDK earn callback. */
public final class PetAds {
    public interface Events { void send(String status); }
    private static final Object RECEIPT_LOCK = new Object();
    private static final int MAX_DAILY = 3, COINS = 30;
    private static final long COOLDOWN = 10L * 60 * 1000;
    private final Activity activity;
    private final Events events;
    private final SharedPreferences prefs;
    private final ConsentInformation consent;
    private boolean initialized, initializing, consentBusy, readyToRequest, busy, disposed;
    private RewardedAd loaded;
    private long loadedAt;
    private int loadToken;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timeout;
    public PetAds(Activity activity, Events events) {
        this.activity = activity; this.events = events;
        prefs = activity.getSharedPreferences("pet-rewarded-ads", Activity.MODE_PRIVATE);
        consent = UserMessagingPlatform.getConsentInformation(activity);
    }
    private boolean demo() { return activity.getResources().getBoolean(R.bool.ads_test_mode); }
    private String unit() { return activity.getString(R.string.admob_rewarded_id); }
    private boolean validDemo() {
        return unit().equals("ca-app-pub-3940256099942544/5224354917")
            && activity.getString(R.string.admob_app_id).equals("ca-app-pub-3940256099942544~3347511713");
    }
    private String day() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }
    private int count() { return day().equals(prefs.getString("day", "")) ? prefs.getInt("count", 0) : 0; }
    private String eligibility() {
        if (count() >= MAX_DAILY) return "limit";
        if (System.currentTimeMillis() - prefs.getLong("lastReward", 0) < COOLDOWN) return "cooldown";
        return "ok";
    }
    public String status() {
        return "{\"test\":" + demo() + ",\"remaining\":" + Math.max(0, MAX_DAILY-count())
            + ",\"privacyRequired\":" + (consent.getPrivacyOptionsRequirementStatus() == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) + "}";
    }
    public void start() {
        if (disposed || consentBusy || initialized || initializing) return;
        // Conservative child/family setting until the owner confirms the audience.
        MobileAds.setRequestConfiguration(new RequestConfiguration.Builder()
            .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE)
            .setTagForUnderAgeOfConsent(RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE)
            .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G).build());
        if (demo()) {
            // This bypass is permitted only for Google's sample application AND sample unit.
            if (!validDemo()) { events.send("configuration"); return; }
            readyToRequest = true; initialize(); return;
        }
        consentBusy = true;
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(true).build();
        consent.requestConsentInfoUpdate(activity, params, () -> {
            if (disposed) return;
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, error -> {
                consentBusy = false; readyToRequest = consent.canRequestAds();
                if (readyToRequest) initialize(); else events.send("privacy");
            });
        }, error -> {
            consentBusy = false; readyToRequest = consent.canRequestAds();
            if (readyToRequest) initialize(); else events.send("unavailable");
        });
    }
    private void initialize() {
        if (initialized || initializing || disposed || !readyToRequest) return;
        initializing = true;
        new Thread(() -> MobileAds.initialize(activity, status -> activity.runOnUiThread(() -> {
            initializing = false; if (disposed) return; initialized = true; events.send("ready");
        }))).start();
    }
    public void show() {
        if (disposed || activity.isFinishing() || busy) return;
        String gate = eligibility();
        if (!gate.equals("ok")) { events.send(gate); return; }
        if (!initialized || !readyToRequest || (!demo() && !consent.canRequestAds())) {
            events.send("preparing"); start(); return;
        }
        busy = true; events.send("loading");
        if (loaded != null && System.currentTimeMillis()-loadedAt < 50L*60*1000) { display(); return; }
        final int token = ++loadToken;
        timeout = () -> { if(token==loadToken && busy) { loadToken++; busy=false; events.send("unavailable"); } };
        handler.postDelayed(timeout, 45000);
        RewardedAd.load(activity, unit(), new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
            @Override public void onAdLoaded(RewardedAd ad) {
                if (disposed || token!=loadToken) return;
                handler.removeCallbacks(timeout);
                loaded = ad; loadedAt = System.currentTimeMillis();
                // If the user left the app during loading, do not surprise them on return.
                if (!PetNotifications.foreground || activity.isFinishing()) { busy=false; events.send("retry"); return; }
                display();
            }
            @Override public void onAdFailedToLoad(LoadAdError error) {
                if(disposed || token!=loadToken)return;
                handler.removeCallbacks(timeout);
                loaded=null; busy=false; events.send("unavailable");
            }
        });
    }
    private void display() {
        RewardedAd ad=loaded; loaded=null;
        if (ad==null || disposed) { busy=false; return; }
        final boolean[] earned = {false};
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() {
                busy=false; if (!disposed) events.send(earned[0]?"earned":"closed");
            }
            @Override public void onAdFailedToShowFullScreenContent(AdError error) {
                busy=false; if (!disposed) events.send("unavailable");
            }
        });
        ad.show(activity, reward -> {
            if (earned[0]) return;
            earned[0]=true;
            synchronized (RECEIPT_LOCK) {
                try {
                    JSONArray receipts = new JSONArray(prefs.getString("receipts", "[]"));
                    JSONObject receipt = new JSONObject();
                    receipt.put("id", UUID.randomUUID().toString()); receipt.put("coins",COINS);
                    receipts.put(receipt);
                    prefs.edit().putString("receipts",receipts.toString()).putString("day",day())
                        .putInt("count",count()+1).putLong("lastReward",System.currentTimeMillis()).commit();
                } catch (Exception error) { events.send("saveerror"); }
            }
            if (!disposed) events.send("reward");
        });
    }
    public String receipts() { synchronized(RECEIPT_LOCK) { return prefs.getString("receipts", "[]"); } }
    public void acknowledge(String id) {
        synchronized(RECEIPT_LOCK) {
            try {
                JSONArray all=new JSONArray(receipts()), keep=new JSONArray();
                for(int i=0;i<all.length();i++) if(!id.equals(all.getJSONObject(i).optString("id"))) keep.put(all.get(i));
                prefs.edit().putString("receipts",keep.toString()).commit();
            } catch(Exception ignored) {}
        }
    }
    public void privacy() {
        if (demo() || consent.getPrivacyOptionsRequirementStatus()!=ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) { events.send("privacyinfo"); return; }
        loaded=null;
        UserMessagingPlatform.showPrivacyOptionsForm(activity, error -> {
            readyToRequest=consent.canRequestAds(); events.send("privacyupdated");
        });
    }
    public void dispose() { disposed=true; loaded=null; loadToken++; handler.removeCallbacksAndMessages(null); }
}
