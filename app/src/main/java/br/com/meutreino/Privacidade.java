package br.com.meutreino;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Tela exigida pelo Health Connect explicando o uso dos dados. */
public class Privacidade extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        TextView t = new TextView(this);
        t.setPadding(48, 64, 48, 48);
        t.setTextSize(17);
        t.setText("Meu Treino lê do Health Connect apenas os exercícios de corrida e a distância, "
                + "para registrar suas corridas no próprio app. Os dados ficam só no seu celular "
                + "e não são enviados para nenhum lugar.");
        setContentView(t);
    }
}
