package kg.evgeny.moidengi;

import android.app.Activity;
import android.content.Intent;
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
    private SheetsGateway sheets;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(244,245,242));
        getWindow().setNavigationBarColor(Color.rgb(23,39,32));
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(244,245,242));
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
                if ("https".equals(uri.getScheme()) && "appassets.androidplatform.net".equals(uri.getHost())
                    && "/assets/index.html".equals(uri.getPath())) {
                    try {
                        InputStream input=getAssets().open("index.html");
                        WebResourceResponse response=new WebResourceResponse("text/html","UTF-8",input);
                        response.setStatusCodeAndReasonPhrase(200,"OK");
                        Map<String,String> headers=new HashMap<>();
                        headers.put("Cache-Control","no-store");headers.put("X-Content-Type-Options","nosniff");
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
        setContentView(webView);
        if(savedInstanceState != null)webView.restoreState(savedInstanceState);
        else webView.loadUrl(HOME);
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
