package me.macron.podcastplayer;

import android.content.Context;
import android.util.DisplayMetrics;

public final class DeviceSpec {
   public static final int DEFAULT_BITMAP_WIDTH = 320;
   private static DeviceSpec mInstance = null;
   private static int mScreenWidth;
   private static int mScreenHeight;
   
   public DeviceSpec() {
      // never called
   }
   
   public static DeviceSpec getInstance(Context context) {
      if (mInstance == null) {
         mInstance = new DeviceSpec();
         
         DisplayMetrics metrics = context.getResources().getDisplayMetrics();
         float scale = metrics.density;
         mScreenWidth = (int)(scale * metrics.widthPixels);
         mScreenHeight = (int)(scale * metrics.heightPixels);
      }
      
      return mInstance;
   }
   
   public int getScreenHeight() {
      return mScreenHeight;
   }
   
   public int getScreenWidth() {
      return mScreenWidth;
   }
}
