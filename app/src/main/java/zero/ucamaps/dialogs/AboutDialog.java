package zero.ucamaps.dialogs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Html;
import android.text.util.Linkify;
import android.graphics.Color;
import android.widget.TextView;

import zero.ucamaps.R;

public class AboutDialog extends Dialog{
    private static Context mContext = null;
    public AboutDialog(Context context) {
        super(context);
        mContext = context;
    }

/**
 * Creates the template of the Dialog
 */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.about);
        TextView tv = (TextView)findViewById(R.id.legal_text);
        tv.setText(readRawTextFile(R.raw.legal));
        tv = (TextView)findViewById(R.id.info_text);
        tv.setText(Html.fromHtml(readRawTextFile(R.raw.info)));
        tv.setLinkTextColor(Color.DKGRAY);
        Linkify.addLinks(tv, Linkify.ALL);
    }

    /**
     * Read the text from archives
     */

    public static String readRawTextFile(int id) {
        InputStream inputStream = mContext.getResources().openRawResource(id);

        InputStreamReader in = new InputStreamReader(inputStream);
        BufferedReader buf = new BufferedReader(in);
        String line;

        StringBuilder text = new StringBuilder();
        try {
            while (( line = buf.readLine()) != null) text.append(line);
        } catch (IOException e) {
            return null;
        }

        return text.toString();
    }
}