package me.macron.podcastplayer;

public class GenreData {
   private String mName;
   private int mShow;
   
   public GenreData(String name, int show) {
      mName = name;
      mShow = show;
   }
   
   public String getName() {
      return mName;
   }
   
   public boolean isVisible() {
      if (mShow == 1) {
         return true;
      }
      
      return false;
   }
}
