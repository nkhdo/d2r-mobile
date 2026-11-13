package com.xiaoboluo.d2r;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

// The Build tab's "read the full guide": the guide page in a WebView over the Deck, on the bottom screen, so one device is enough.
//   A bar on top: back to the Deck, the page title, reload. The page stays on maxroll.gg (links elsewhere do nothing). Without a
//   connection: a short note and Retry instead of the browser's error page.
//   It lives in the Deck's Presentation, whose window cannot take key focus (SecondScreen), so the gamepad stays with the game: the
//   page is read by touch only.
//   Made on open and destroyed on close (and when the Deck hides), so it costs nothing while closed.
final class GuideWeb {
    private static final String HOST = "maxroll.gg";
    private static final int BG = 0xFF0E0B08, BAR = 0xFF1C1712, TEXT = 0xFFE8D9B0, DIM = 0xFF8A7F68;

    private final FrameLayout parent;
    private final java.util.function.Function<String, String> text;   // ThorDeck.nativeText
    private LinearLayout root;
    private WebView web;
    private TextView title, offline;
    private Button retry;

    GuideWeb(FrameLayout parent, java.util.function.Function<String, String> text) { this.parent = parent; this.text = text; }

    boolean isOpen() { return root != null; }

    static boolean allowed(String url) {
        Uri u = Uri.parse(url);
        String h = u.getHost();
        return "https".equals(u.getScheme()) && h != null && (h.equals(HOST) || h.endsWith("." + HOST));
    }

    void open(String name, String url) {
        if (!allowed(url)) return;
        close();
        Context c = parent.getContext();
        float d = c.getResources().getDisplayMetrics().density;
        root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setClickable(true);   // touches never fall through to the Deck under it

        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(BAR);
        int pad = Math.round(8 * d);
        bar.setPadding(pad, pad / 2, pad, pad / 2);
        Button back = button(c, text.apply("dmBuildBack"));
        back.setOnClickListener(v -> close());
        title = new TextView(c);
        title.setText(name);
        title.setTextColor(TEXT);
        title.setTextSize(16);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(pad * 2, 0, pad * 2, 0);
        Button reload = button(c, text.apply("dmBuildReload"));
        reload.setOnClickListener(v -> { if (web != null) { showOffline(false); web.reload(); } });
        bar.addView(back);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(reload);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(52 * d)));

        FrameLayout page = new FrameLayout(c);
        web = new WebView(c);
        web.setBackgroundColor(BG);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);   // the guide's tabs and item pop-ups
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportMultipleWindows(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return !allowed(r.getUrl().toString()); }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) { if (r.isForMainFrame()) showOffline(true); }
            @Override public void onPageFinished(WebView v, String u) { if (title != null && v.getTitle() != null && !v.getTitle().isEmpty()) title.setText(v.getTitle()); }
        });
        page.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout off = new LinearLayout(c);
        off.setOrientation(LinearLayout.VERTICAL);
        off.setGravity(Gravity.CENTER);
        off.setBackgroundColor(BG);
        offline = new TextView(c);
        offline.setText(text.apply("dmBuildOffline"));
        offline.setTextColor(DIM);
        offline.setTextSize(18);
        offline.setGravity(Gravity.CENTER);
        retry = button(c, text.apply("dmBuildRetry"));
        retry.setOnClickListener(v -> { if (web != null) { showOffline(false); web.reload(); } });
        off.addView(offline);
        off.addView(retry);
        off.setVisibility(View.GONE);
        off.setTag("offline");
        page.addView(off, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(page, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        parent.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        web.loadUrl(url);
    }

    private void showOffline(boolean on) {
        if (root == null) return;
        View off = root.findViewWithTag("offline");
        if (off != null) off.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    private static Button button(Context c, String label) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackgroundColor(Color.TRANSPARENT);
        return b;
    }

    void close() {
        if (root == null) return;
        parent.removeView(root);
        if (web != null) { web.stopLoading(); web.setWebViewClient(new WebViewClient()); web.destroy(); }
        root = null; web = null; title = null; offline = null; retry = null;
    }
}
