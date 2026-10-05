package cl.negociospyme.blockpets;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;

public class MainActivity extends Activity {
    private WebView web;
    private PetAds ads;
    private boolean pageReady;
    private int pendingPet = -1;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        pendingPet = getIntent().getIntExtra("petIndex", -1);
        PetNotifications.createChannel(this);
        PetNotifications.schedule(this);
        web = new WebView(this);
        setContentView(web);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(true);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setAllowFileAccessFromFileURLs(false);
        web.getSettings().setAllowUniversalAccessFromFileURLs(false);
        // Only the bundled, trusted HTML is loaded. External navigation is blocked.
        web.addJavascriptInterface(new PetBridge(), "PetNative");
        ads = new PetAds(this, status -> { if(web!=null && pageReady) web.evaluateJavascript("window.onPetAdEvent && window.onPetAdEvent(" + org.json.JSONObject.quote(status) + ")", null); });
        web.addJavascriptInterface(new AdsBridge(), "PetAdNative");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return true; }
            @Override public void onPageFinished(WebView view, String url) {
                pageReady = true;
                openPet();
                web.evaluateJavascript("if(typeof collectAdRewards==='function')collectAdRewards()", null);
                ads.start();
            }
        });
        web.loadUrl("file:///android_asset/index.html");
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
    private void notifyStatus() {
        if (web != null && pageReady) web.evaluateJavascript("window.onNativeNotificationStatus && window.onNativeNotificationStatus()", null);
    }
    private void openPet() {
        if (web == null || !pageReady || pendingPet < 0 || pendingPet > 3) return;
        int pet = pendingPet; pendingPet = -1;
        getIntent().removeExtra("petIndex");
        web.evaluateJavascript("if(typeof openNotifiedPet==='function')openNotifiedPet(" + pet + ")", null);
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        pendingPet = intent.getIntExtra("petIndex", -1); openPet();
    }
    public final class AdsBridge {
        @JavascriptInterface public String status() { return ads.status(); }
        @JavascriptInterface public void show() { runOnUiThread(() -> ads.show()); }
        @JavascriptInterface public String receipts() { return ads.receipts(); }
        @JavascriptInterface public void acknowledge(String id) { ads.acknowledge(id); }
        @JavascriptInterface public void privacy() { runOnUiThread(() -> ads.privacy()); }
    }
    public final class PetBridge {
        @JavascriptInterface public void sync(String snapshot) { PetNotifications.snapshot(MainActivity.this, snapshot); }
        @JavascriptInterface public String status() { return PetNotifications.status(MainActivity.this); }
        @JavascriptInterface public void setEnabled(boolean enabled) {
            runOnUiThread(() -> {
                PetNotifications.setEnabled(MainActivity.this, enabled);
                if (enabled && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1404);
                notifyStatus();
            });
        }
        @JavascriptInterface public void test() { runOnUiThread(() -> PetNotifications.test(MainActivity.this)); }
        @JavascriptInterface public void openSettings() {
            runOnUiThread(() -> {
                Intent intent;
                if (Build.VERSION.SDK_INT >= 26) intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                else intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            });
        }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 1404) notifyStatus();
    }
    @Override public void onBackPressed() {
        web.evaluateJavascript("window.back ? window.back() : false", result -> { if ("false".equals(result)) super.onBackPressed(); });
    }
    @Override protected void onPause() {
        if (web != null) {
            web.evaluateJavascript("if(typeof save==='function'){Rules.age(state);save();if(typeof syncPetNotifications==='function')syncPetNotifications(true)}", null);
            web.onPause();
        }
        PetNotifications.foreground = false;
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume(); PetNotifications.foreground = true;
        if (web != null) web.onResume();
        PetNotifications.schedule(this); notifyStatus();
        if(web!=null && pageReady) web.evaluateJavascript("if(typeof collectAdRewards==='function')collectAdRewards()",null);
    }
    @Override protected void onDestroy() {
        if(ads!=null)ads.dispose();
        if (web != null) { web.removeJavascriptInterface("PetAdNative"); web.removeJavascriptInterface("PetNative"); web.destroy(); web = null; }
        super.onDestroy();
    }
}
