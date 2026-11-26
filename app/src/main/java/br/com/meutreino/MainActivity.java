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
    private static final int PEGAR_BACKUP = 77;
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

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != PEGAR_BACKUP || res != RESULT_OK || data == null || data.getData() == null) return;
        try (java.io.InputStream in = getContentResolver().openInputStream(data.getData())) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            String txt = out.toString("UTF-8");
            String js = org.json.JSONObject.quote(txt);
            web.evaluateJavascript("window.restaurarTexto && window.restaurarTexto(" + js + ")", null);
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "Não consegui ler o arquivo", android.widget.Toast.LENGTH_LONG).show();
        }
    }

    /** Salva o backup na pasta Download/MeuTreino (continua lá mesmo se desinstalar o app). */
    private String salvarArquivo(String nome, String json) throws Exception {
        byte[] bytes = json.getBytes("UTF-8");
        if (Build.VERSION.SDK_INT >= 29) {
            android.content.ContentValues v = new android.content.ContentValues();
            v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, nome);
            v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json");
            v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/MeuTreino");
            Uri u = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            try (java.io.OutputStream o = getContentResolver().openOutputStream(u)) { o.write(bytes); }
            return "Download/MeuTreino/" + nome;
        }
        java.io.File dir = new java.io.File(getExternalFilesDir(null), "backup");
        dir.mkdirs();
        java.io.File f = new java.io.File(dir, nome);
        try (java.io.FileOutputStream o = new java.io.FileOutputStream(f)) { o.write(bytes); }
        return f.getAbsolutePath();
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
        public String salvarBackup(String nome, String json) {
            try { return salvarArquivo(nome, json); } catch (Exception e) { return ""; }
        }

        @JavascriptInterface
        public void compartilharBackup(String json) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_SUBJECT, "Backup Meu Treino");
                i.putExtra(Intent.EXTRA_TEXT, json);
                startActivity(Intent.createChooser(i, "Enviar backup para…"));
            });
        }

        @JavascriptInterface
        public void escolherBackup() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(i, PEGAR_BACKUP);
            });
        }

        @JavascriptInterface
        public void cancelarDescanso() {
            Descanso.cancelar(MainActivity.this);
        }
    }
}
