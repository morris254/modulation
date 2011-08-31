package me.macron.podcastplayer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader.TileMode;
import android.util.Log;

public class BitmapReflector {
   
   public BitmapReflector() {
   }

   // Swap this version with reflect() below if out-of-memory problems become too severe
   public Bitmap reflectNull(Bitmap originalImage, int reflectionGap) {
      return originalImage;
   }
  
   // Based on code from Neil Davies' Blog
   //   http://www.inter-fuser.com/2009/12/android-reflections-with-bitmaps.html
   //
   // Optimized to use less memory by just creating the reflection portion
   // instead of original image + reflection
   //
   public Bitmap reflect(Bitmap originalImage, int reflectionGap) {
      int width = originalImage.getWidth();
      int height = originalImage.getHeight();
      
     
      // This will not scale but will flip on the Y axis
      Matrix matrix = new Matrix();
      matrix.preScale(1, -1);
      
      Bitmap reflectionImage = null;
      
      // Create a Bitmap with the flip matrix applied to it.
      // We only want the bottom half of the image
      try {
         reflectionImage = Bitmap.createBitmap(originalImage, 0, (height - height/8) - reflectionGap, width, (height/8) + reflectionGap, matrix, false);
      } catch (Exception e) {
         Log.e("PodcastPlayer", "Bitmap error: " + e.getMessage());
      }
       
      if (reflectionImage == null) {
         return null;
      }
      
      // Create a new Canvas with the bitmap that's big enough for
      // the gap plus reflection
      Canvas canvas = new Canvas(reflectionImage);
      // Draw in the gap
      Paint defaultPaint = new Paint();
      canvas.drawRect(0, 0, width, reflectionGap, defaultPaint);
      
      // Create a shader that is a linear gradient that covers the reflection
      Paint paint = new Paint(); 
      LinearGradient shader = new LinearGradient(0, 0, 0, 
        height / 8 + reflectionGap, 0x70ffffff, 0x00ffffff, 
        TileMode.CLAMP); 
      // Set the paint to use this shader (linear gradient)
      paint.setShader(shader); 
      // Set the Transfer mode to be porter duff and destination in
      paint.setXfermode(new PorterDuffXfermode(Mode.DST_IN)); 
      // Draw a rectangle using the paint with our linear gradient
      canvas.drawRect(0, 0, width, 
        reflectionGap + height/8, paint); 
     
      return reflectionImage;
   }
   
}

