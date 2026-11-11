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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Thin Android shell for a personal, Google-authenticated Apps Script web app.
 * Uses Chrome Custom Tabs instead of WebView, which Google sign-in does not support.
 * The native app cannot read Google cookies or personal transaction data.
 */
public final class MainActivity extends Activity {
    private static final String APP_URL =
        "https://script.google.com/macros/s/"
        + "AKfycbxldHZKNbo0Wwoq8MJwzKUTHPEB86bniLdO4GAeSojWz9fmeMm7TIRA4glbTdUNz75NYA"
        + "/exec";
    private static final int DARK = Color.rgb(19, 43, 36);
    private static final int LIME = Color.rgb(217, 255, 146);
    private static final int MUTED = Color.rgb(102, 120, 108);
    private boolean launched = false;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(Color.rgb(245, 246, 239));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(245, 246, 239));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24), dp(55), dp(24), dp(32));
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -1));

        TextView mark = label("М.", 40, DARK, true);
        mark.setGravity(Gravity.CENTER);
        mark.setTextColor(LIME);
        mark.setBackground(round(DARK, 23));
        body.addView(mark, new LinearLayout.LayoutParams(dp(92), dp(92)));

        TextView small = label("ЛИЧНЫЕ ФИНАНСЫ", 12, MUTED, true);
        small.setLetterSpacing(0.22f);
        spacing(body, small, 23);
        TextView heading = label("Мои деньги.", 32, DARK, true);
        spacing(body, heading, 5);
        TextView subtitle = label("Твои расходы, покупки и валюты — в одном месте.", 16, MUTED, false);
        subtitle.setGravity(Gravity.CENTER);
        spacing(body, subtitle, 11);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(24), dp(20), dp(22));
        card.setBackground(round(Color.WHITE, 22));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.topMargin = dp(36);
        body.addView(card, cardLp);

        TextView cardTitle = label("Семейный бюджет", 22, DARK, true);
        card.addView(cardTitle);
        TextView cardDetail = label("Вход через Google Chrome. Данные остаются в твоей закрытой Google Таблице.", 15, MUTED, false);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-1, -2);
        detailLp.topMargin = dp(10);
        card.addView(cardDetail, detailLp);

        Button open = button("Открыть приложение", DARK, LIME);
        LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(-1, dp(58));
        openLp.topMargin = dp(25);
        card.addView(open, openLp);
        open.setOnClickListener(v -> launchInBrowser(true));

        Button browser = button("Открыть в Chrome", Color.rgb(239, 243, 235), DARK);
        LinearLayout.LayoutParams browserLp = new LinearLayout.LayoutParams(-1, dp(53));
        browserLp.topMargin = dp(9);
        card.addView(browser, browserLp);
        browser.setOnClickListener(v -> launchInBrowser(false));

        TextView security = label("Доступ только через твой Google-аккаунт. Никаких банковских разрешений приложение не запрашивает.", 13, MUTED, false);
        security.setGravity(Gravity.CENTER);
        spacing(body, security, 31);

        setContentView(scroll);

        // First launch from home-screen immediately opens the already deployed web app.
        if (savedState == null) {
            scroll.post(() -> {
                if (!launched) {
                    launched = true;
                    launchInBrowser(true);
                }
            });
        }
    }

    private void launchInBrowser(boolean customTab) {
        Uri url = Uri.parse(APP_URL);
        if (!"https".equals(url.getScheme()) || !"script.google.com".equals(url.getHost())) {
            Toast.makeText(this, "Неверный адрес приложения", Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, url);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        if (customTab) {
            // Custom Tabs share the real browser's Google login and cookies.
            Bundle extras = new Bundle();
            extras.putBinder("android.support.customtabs.extra.SESSION", null);
            intent.putExtras(extras);
            intent.putExtra("android.support.customtabs.extra.TOOLBAR_COLOR", DARK);
            intent.putExtra("android.support.customtabs.extra.SHARE_MENU_ITEM", false);
            intent.setPackage("com.android.chrome");
            try {
                startActivity(intent);
                return;
            } catch (ActivityNotFoundException missingChrome) {
                intent.setPackage(null);
            }
        }
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Установи Google Chrome и попробуй ещё раз", Toast.LENGTH_LONG).show();
        }
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(sp);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void spacing(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(top);
        parent.addView(child, lp);
    }

    private Button button(String text, int bg, int color) {
        Button view = new Button(this);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(15);
        view.setAllCaps(false);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setBackground(round(bg, 16));
        return view;
    }

    private GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
