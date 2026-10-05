package cl.negociospyme.blockpets;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
public class MainActivity extends Activity {
 private WebView web;
 @Override public void onCreate(Bundle saved){super.onCreate(saved);web=new WebView(this);setContentView(web);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setAllowFileAccess(true);web.getSettings().setAllowContentAccess(false);web.getSettings().setAllowFileAccessFromFileURLs(false);web.getSettings().setAllowUniversalAccessFromFileURLs(false);web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView v,String url){return true;}});web.loadUrl("file:///android_asset/index.html");getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
 @Override public void onBackPressed(){web.evaluateJavascript("window.back ? window.back() : false",result->{if("false".equals(result))super.onBackPressed();});}
 @Override protected void onPause(){web.evaluateJavascript("if(typeof save==='function')save()",null);web.onPause();super.onPause();}
 @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
 @Override protected void onDestroy(){if(web!=null)web.destroy();super.onDestroy();}
}
