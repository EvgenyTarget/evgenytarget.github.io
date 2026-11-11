package kg.evgeny.moidengi;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Private finance app. UI comes from the bundled APK, NOT GitHub.
 * The only finance backend is Google Sheets API, authorized by Google Play services.
 */
public final class MainActivity extends Activity {
    private static final String ORIGIN = "https://appassets.androidplatform.net";
    private static final String HOME = ORIGIN + "/assets/index.html";
    private WebView webView;
    private FrameLayout root;
    private SheetsGateway sheets;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(244,245,242));
        getWindow().setNavigationBarColor(Color.rgb(23,39,32));
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(244,245,242));

        // Android 15 / target 35 draws behind status and navigation bars.
        // Apply physical insets to the NATIVE root, not to the bundled HTML.
        // Older releases retain Android's normal window-fitting behavior.
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars() |
                    WindowInsets.Type.displayCutout());
                if (view.getPaddingLeft() != safe.left ||
                    view.getPaddingTop() != safe.top ||
                    view.getPaddingRight() != safe.right ||
                    view.getPaddingBottom() != safe.bottom) {
                    view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                }
                return insets;
            });
        }

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(244,245,242));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings settings=webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setSafeBrowsingEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        sheets=new SheetsGateway(this, webView);
        webView.addJavascriptInterface(sheets, "SheetsBridge");
        webView.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                android.net.Uri uri=request.getUrl();
                // Block untrusted network subresources. The UI and privileged
                // SheetsBridge are only ever hosted by the bundled asset origin.
                // The public FX endpoint does not require the Google token.
                boolean internal = "https".equals(uri.getScheme())
                    && "appassets.androidplatform.net".equals(uri.getHost())
                    && "/assets/index.html".equals(uri.getPath());
                if (!internal && !("https".equals(uri.getScheme())
                    && "open.er-api.com".equals(uri.getHost()))) {
                    return new WebResourceResponse("text/plain", "UTF-8",
                        new ByteArrayInputStream(new byte[0]));
                }
                if (internal) {
                    try {
                        InputStream input=getAssets().open("index.html");
                        WebResourceResponse response=new WebResourceResponse("text/html","UTF-8",input);
                        response.setStatusCodeAndReasonPhrase(200,"OK");
                        Map<String,String> headers=new HashMap<>();
                        headers.put("Cache-Control","no-store");
                        headers.put("X-Content-Type-Options","nosniff");
                        headers.put("Referrer-Policy","no-referrer");
                        // This page has privileged Android SheetsBridge access.
                        // Never allow remote frames or externally hosted scripts.
                        headers.put("Content-Security-Policy",
                            "default-src 'none'; script-src 'unsafe-inline'; " +
                            "style-src 'unsafe-inline'; img-src 'self' data:; " +
                            "connect-src 'self' https://open.er-api.com; " +
                            "frame-src 'none'; object-src 'none'; " +
                            "base-uri 'none'; form-action 'none'");
                        response.setResponseHeaders(headers);
                        return response;
                    } catch(Exception e) {
                        return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream("Ошибка интерфейса".getBytes(StandardCharsets.UTF_8)));
                    }
                }
                return null;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                android.net.Uri u=request.getUrl();
                if(!request.isForMainFrame())return false;
                if("https".equals(u.getScheme()) && "appassets.androidplatform.net".equals(u.getHost()))return false;
                if("https".equals(u.getScheme())){
                    try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}
                }
                return true;
            }
        });
        root.addView(webView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        // The document is bundled and fast to load. Never restore only the
        // WebView history without reloading the document: that caused blank
        // screens after Activity recreation.
        webView.loadUrl(HOME);
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Keep the SAME WebView + Google authorization gateway across rotation.
        // Reflow and redraw without rebuilding the page or losing scroll state.
        if (root != null && Build.VERSION.SDK_INT >= 35) root.requestApplyInsets();
        if (webView != null) {
            webView.requestLayout();
            webView.invalidate();
        }
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        // Configuration changes are handled in-place; process restarts reload
        // the local UI and resynchronize with the private Google Sheet.
        super.onSaveInstanceState(outState);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(sheets!=null)sheets.onAuthResult(requestCode,resultCode,data);
    }

    @Override public void onBackPressed() {
        if(webView!=null&&webView.canGoBack())webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if(sheets!=null){sheets.close();sheets=null;}
        if(webView!=null){webView.removeJavascriptInterface("SheetsBridge");webView.destroy();webView=null;}
        super.onDestroy();
    }
}
