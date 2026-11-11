package kg.evgeny.moidengi;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Offline-first Android application for expense tracking and shopping.
 *
 * An embedded, standalone UI works without Google login; local data stays in the
 * app's protected WebView DOM storage. The separate Cloud button uses Chrome
 * for the Google account chooser and private Apps Script spreadsheet. Do NOT
 * run Google authorization inside WebView or copy browser cookies into the app.
 *
 * WARNING: local-only entries and Google Sheets transactions are deliberately
 * distinct until an authenticated sync mechanism is implemented.
 */
public final class MainActivity extends Activity {
    private static final String CLOUD_WEB_APP =
        "https://script.google.com/macros/s/"
        + "AKfycbxldHZKNbo0Wwoq8MJwzKUTHPEB86bniLdO4GAeSojWz9fmeMm7TIRA4glbTdUNz75NYA"
        + "/exec";
    private static final String APP_ORIGIN = "https://appassets.androidplatform.net/";
    private static final int DARK = Color.rgb(19, 43, 36);
    private static final int LIME = Color.rgb(219, 255, 145);
    private static final int WHITE = Color.rgb(255, 255, 255);
    private static final int MUTED = Color.rgb(95, 116, 104);
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(DARK);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246, 247, 242));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(15), dp(8), dp(15), dp(8));
        toolbar.setBackgroundColor(DARK);

        TextView name = new TextView(this);
        name.setText("М.  Мои деньги");
        name.setTextSize(19);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setTextColor(LIME);
        toolbar.addView(name, new LinearLayout.LayoutParams(0, dp(43), 1));

        TextView cloud = new TextView(this);
        cloud.setText("Облачные расходы");
        cloud.setGravity(Gravity.CENTER);
        cloud.setTextColor(DARK);
        cloud.setTextSize(13);
        cloud.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        cloud.setPadding(dp(12), 0, dp(12), 0);
        cloud.setBackground(rounded(LIME, 12));
        toolbar.addView(cloud, new LinearLayout.LayoutParams(-2, dp(42)));
        cloud.setOnClickListener(v -> openCloudWithAccountChooser());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(60)));

        TextView mode = new TextView(this);
        mode.setText("Локальный учёт • записи только на этом телефоне");
        mode.setTextSize(11);
        mode.setTextColor(MUTED);
        mode.setGravity(Gravity.CENTER);
        mode.setPadding(dp(6), dp(6), dp(6), dp(6));
        root.addView(mode, new LinearLayout.LayoutParams(-1, dp(29)));

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("appassets.androidplatform.net".equals(uri.getHost())) return false;
                if ("https".equals(uri.getScheme())) openExternal(uri);
                return true;
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        try {
            String html = readAsset("index.html");
            // HTML is bundled within the APK, and has a stable secure origin for
            // private localStorage. No Google sign-in is attempted in this WebView.
            webView.loadDataWithBaseURL(APP_ORIGIN, html, "text/html", "UTF-8", APP_ORIGIN);
        } catch (IOException ex) {
            TextView fallback = new TextView(this);
            fallback.setText("Не удалось загрузить интерфейс. Переустанови приложение.");
            fallback.setTextColor(DARK);
            fallback.setTextSize(18);
            fallback.setGravity(Gravity.CENTER);
            root.removeView(webView);
            root.addView(fallback, new LinearLayout.LayoutParams(-1, 0, 1));
        }
    }

    private String readAsset(String filename) throws IOException {
        try (InputStream in = getAssets().open(filename);
             ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[8192];
            int n;
            while ((n = in.read(bytes)) != -1) buf.write(bytes, 0, n);
            return buf.toString("UTF-8");
        }
    }

    private void openCloudWithAccountChooser() {
        // The server is deliberately restricted to its owner. Let Google ask
        // the user to select the account owning the spreadsheet, instead of
        // opening /exec with the wrong Chrome account and showing a Drive 404.
        String chooser = "https://accounts.google.com/AccountChooser?continue="
            + Uri.encode(CLOUD_WEB_APP);
        openExternal(Uri.parse(chooser));
    }

    private void openExternal(Uri uri) {
        Intent i = new Intent(Intent.ACTION_VIEW, uri);
        i.addCategory(Intent.CATEGORY_BROWSABLE);
        i.setPackage("com.android.chrome");
        try {
            startActivity(i);
        } catch (ActivityNotFoundException ex) {
            i.setPackage(null);
            try {
                startActivity(i);
            } catch (ActivityNotFoundException noBrowser) {
                Toast.makeText(this, "Для доступа к Google нужна установка Chrome", Toast.LENGTH_LONG).show();
            }
        }
    }

    private GradientDrawable rounded(int bg, float radiusDp) {
        GradientDrawable draw = new GradientDrawable();
        draw.setColor(bg);
        draw.setCornerRadius(dp(radiusDp));
        return draw;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
