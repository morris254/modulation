package me.macron.podcastplayer;

//Todo: Detect media card insert/removal and rescan podcasts

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.util.Log;

public class MountReceiver extends BroadcastReceiver {
   private Intent mNoSDCardIntent; 
   private MountListener mListener;

   public IntentFilter getIntentFilter() {
      IntentFilter intentFilter = new IntentFilter();
      intentFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
      intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
      intentFilter.addDataScheme("file");
      return intentFilter;
  }

  public void setOnMountListener(MountListener listener) {
     mListener = listener;
  }
  
  @Override
  public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
         if (mListener != null) {
            mListener.onMounted();
         }
      } else if (Intent.ACTION_MEDIA_UNMOUNTED.equals(action)) {
         if (mListener != null) {
            mListener.onUnmounted();
         }
         
         //mNoSDCardIntent = new Intent(context, NoSDCard.class);
         //context.startActivity(mNoSDCardIntent);
          
         //Builder builder = new AlertDialog.Builder(context);
         //builder.setTitle("Error");
         //builder.setMessage("No SD Card");
         //builder.show();
      }
  }

}
