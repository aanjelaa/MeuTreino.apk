package br.com.meutreino;

import android.Manifest;
import androidx.activity.ComponentActivity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends ComponentActivity {
    private WebView web;
    private Saude saude;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#1E4FA3"));
        Descanso.criarCanal(this);
        Lembretes.criarCanais(this);
        saude = new Saude(this, json -> { runOnUiThread(() -> { if (web != null) web.evaluateJavascript("window.recebeHC && window.recebeHC(" + json + ")", null); }); return kotlin.Unit.INSTANCE; });
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.addJavascriptInterface(new Ponte(), "Android");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }
        });
        setContentView(web);
        String abrir = getIntent() != null ? getIntent().getStringExtra("abrir") : null;
        web.loadUrl("file:///android_asset/index.html" + (abrir != null ? "#" + abrir : ""));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String abrir = intent.getStringExtra("abrir");
        if (abrir != null && web != null)
            web.evaluateJavascript("window.abrirTela && window.abrirTela('" + abrir.replaceAll("[^a-z0-9]", "") + "')", null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.aoVoltar && window.aoVoltar()", null);
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.voltar && window.voltar()", r -> {
            if (!"true".equals(r)) MainActivity.super.onBackPressed();
        });
    }

    /** Ponte usada pelo index.html para agendar o aviso de fim do descanso. */
    class Ponte {
        @JavascriptInterface
        public void agendarDescanso(int segundos, String texto) {
            Descanso.agendar(MainActivity.this, segundos, texto);
        }

        @JavascriptInterface
        public void importarCorridas(String desde, boolean silencioso) {
            runOnUiThread(() -> saude.importar(desde, silencioso));
        }

        @JavascriptInterface
        public void agendarLembretes(String json) {
            Lembretes.agendarLembretes(MainActivity.this, json);
        }

        @JavascriptInterface
        public void agendarIntervalos(String json) {
            Lembretes.agendarIntervalos(MainActivity.this, json);
        }

        @JavascriptInterface
        public void cancelarIntervalos() {
            Lembretes.cancelarIntervalos(MainActivity.this);
        }

        @JavascriptInterface
        public void telaLigada(boolean ligada) {
            runOnUiThread(() -> {
                if (ligada) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        @JavascriptInterface
        public void cancelarDescanso() {
            Descanso.cancelar(MainActivity.this);
        }
    }
}
