package me.macron.podcastplayer;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.text.SpannableString;
import android.text.util.Linkify;
import android.widget.TextView;

public class AboutDialogBuilder {
   public static AlertDialog create( Context context ) throws NameNotFoundException {
      // Try to load the a package matching the name of our own package
      PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_META_DATA);
      String versionInfo = pInfo.versionName;

      String aboutTitle = String.format("%s %s", context.getString(R.string.app_name), versionInfo);
      String aboutText = context.getResources().getString(R.string.about_text);

      // Set up the TextView
      final TextView message = new TextView(context);
      // We'll use a spannablestring to be able to make links clickable
      final SpannableString s = new SpannableString(aboutText);

      // Set some padding
      message.setPadding(5, 5, 5, 5);
      // Set up the final string
      message.setText(s);
      // Now linkify the text
      Linkify.addLinks(message, Linkify.ALL);

      return new AlertDialog.Builder(context).setTitle(aboutTitle).setCancelable(true).setIcon(R.drawable.dialog_info).setPositiveButton(
          context.getString(android.R.string.ok), null).setView(message).create();
   }
}
