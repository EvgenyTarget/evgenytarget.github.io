package kg.evgeny.moidengi;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.content.Intent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.InputStream;

/** One-screen, private offline-first finance app with encrypted feed sync.
 * Google credentials are never requested or stored by this application.
 * Only local APK assets are privileged. The remote snapshot is encrypted to a
 * nonextractable P-256 ECDH device private key held in IndexedDB.
 */
public final class MainActivity extends Activity {
    private static final String ORIGIN = "https://appassets.androidplatform.net";
    private static final String HOME = ORIGIN + "/assets/index.html";
    private WebView browser;

    public final class ShareBridge {
        @JavascriptInterface
        public void sharePublicKey(final String message) {
            if (message == null || message.length() > 1600 ||
                    !message.startsWith("Подключи Мои деньги к нашему чату. Публичный ключ P-256: ")) return;
            runOnUiThread(() -> {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT, message);
                startActivity(Intent.createChooser(send, "Отправить публичный ключ"));
            });
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(244,245,242));
        getWindow().setNavigationBarColor(Color.rgb(23,39,32));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        browser = new WebView(this);
        browser.setBackgroundColor(Color.rgb(244,245,242));
        WebSettings s = browser.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setSafeBrowsingEnabled(true);

        browser.addJavascriptInterface(new ShareBridge(), "AndroidBridge");
        browser.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                android.net.Uri uri = req.getUrl();
                if ("https".equals(uri.getScheme()) &&
                    "appassets.androidplatform.net".equals(uri.getHost()) &&
                    "/assets/index.html".equals(uri.getPath())) {
                    try {
                        InputStream data = getAssets().open("index.html");
                        WebResourceResponse response = new WebResourceResponse("text/html","UTF-8",data);
                        response.setStatusCodeAndReasonPhrase(200,"OK");
                        java.util.Map<String,String> headers = new java.util.HashMap<>();
                        headers.put("Cache-Control","no-store");
                        response.setResponseHeaders(headers);
                        return response;
                    } catch (Exception ignored) {
                        return new WebResourceResponse("text/plain","UTF-8",
                            new java.io.ByteArrayInputStream("Ошибка загрузки приложения".getBytes()));
                    }
                }
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                android.net.Uri uri = req.getUrl();
                if (!req.isForMainFrame()) return false;
                if ("appassets.androidplatform.net".equals(uri.getHost()) &&
                    "https".equals(uri.getScheme())) return false;
                if ("https".equals(uri.getScheme())) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW,uri));
                    } catch (Exception ignored) {}
                }
                return true;
            }
        });
        setContentView(browser);
        if (state != null) browser.restoreState(state);
        else browser.loadUrl(HOME);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (browser != null) browser.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (browser != null && browser.canGoBack()) browser.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (browser != null) {
            browser.removeJavascriptInterface("AndroidBridge");
            browser.destroy();
            browser = null;
        }
        super.onDestroy();
    }
}
