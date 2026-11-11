package kg.evgeny.moidengi.portal;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;

/**
 * Opens the owner's EXISTING, authorized, private Google Apps Script finance
 * application through Chrome's signed-in session. No Android OAuth client,
 * password, token, or Google API credential is stored in this APK.
 *
 * This is a browser-based fallback for the existing non-updateable v2.0.
 * Chrome Custom Tabs are required: embedded WebView cannot borrow Google's
 * authenticated Chrome session and may violate Google's embedded-login rules.
 */
public final class MainActivity extends Activity {
    private static final Uri FINANCE_APP = Uri.parse(
        "https://script.google.com/macros/s/AKfycbxldHZKNboOWwoq8MJwzKUTHPEB86bniLdO4GAeSojWz9fmeMm7TIRA4glbTdUNz75NYA/exec"
    );

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(19,43,36));
        getWindow().setNavigationBarColor(Color.rgb(19,43,36));

        CustomTabColorSchemeParams appearance = new CustomTabColorSchemeParams.Builder()
            .setToolbarColor(Color.rgb(19,43,36))
            .setNavigationBarColor(Color.rgb(19,43,36))
            .build();
        try {
            CustomTabsIntent tabs = new CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setUrlBarHidingEnabled(true)
                .setDefaultColorSchemeParams(appearance)
                .build();
            // Chrome shares the already authenticated Chrome account session.
            tabs.intent.setPackage("com.android.chrome");
            tabs.launchUrl(this, FINANCE_APP);
        } catch (ActivityNotFoundException e) {
            // Phones without Chrome may use their default verified web browser.
            Intent fallback = new Intent(Intent.ACTION_VIEW, FINANCE_APP);
            fallback.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(fallback);
        } catch (Exception e) {
            // Never show a blank Activity on unexpected Custom Tabs failures.
            try {
                Intent fallback = new Intent(Intent.ACTION_VIEW, FINANCE_APP);
                fallback.addCategory(Intent.CATEGORY_BROWSABLE);
                startActivity(fallback);
            } catch (Exception ignored) { }
        } finally {
            finish();
        }
    }
}
