package me.macron.podcastplayer;

import java.util.Calendar;
import java.util.Locale;
import java.text.DateFormat;

public final class DateFormatter {
   private static DateFormatter instance = null;
   
   /**
   * private constructor to stop people instantiating it.
   */
   protected DateFormatter() {
   ///this is never run
   }
   
   private static Calendar now;
   
   public static DateFormatter getInstance() {
      if (instance == null) {
         instance = new DateFormatter();
         now = Calendar.getInstance();
         now.setTimeInMillis(System.currentTimeMillis());
      }
      
      return instance;
   }

   private String getMonthName(Calendar calendar, Locale locale) {
      String monthName = "";
      int month = calendar.get(Calendar.MONTH);
      String language = locale.getLanguage();
      
      // Want to use getDisplayName() but it wasn't available until Android 2.3
      
      if (language.equals("en")) {
         String[] monthNames = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
         
         if ((month >= 0) && (month <= 11)) {
            monthName = monthNames[month];
         }
      } else if (language.equals("ja")) {
         monthName = Integer.toString(month + 1);
      }
      
      return monthName;
   }
   
   // Returns date in human readable form.
   // Has specific formatting for US and Japanese locales.  
   // For other locales just use the Android default format.
   //
   // Todo: Move this to a "Localization" class
   //
   public String formatDate(Calendar calendar) {
      String stringDate;
      Locale locale = Locale.getDefault();
      String language = locale.getLanguage();
      String country = locale.getCountry();
      
      if (language.equals("en") && country.equals("US")) {
         if (calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
            // Format date without year
            stringDate = getMonthName(calendar, locale)
               + " "
               + calendar.get(Calendar.DAY_OF_MONTH);
         } else {
            // Format date with year
            stringDate = getMonthName(calendar, locale)
            + " "
            + calendar.get(Calendar.DAY_OF_MONTH)
            + ", "
            + calendar.get(Calendar.YEAR);
         }
      } else if (language.equals("ja")) {
         if (calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
            // Format date without year
            stringDate = getMonthName(calendar, locale)
               + "ŒŽ"
               + calendar.get(Calendar.DAY_OF_MONTH)
               + "“ú";
         } else {
            // Format date with year
            stringDate = calendar.get(Calendar.YEAR)
               + "”N"
               + getMonthName(calendar, locale)
               + "ŒŽ"
               + calendar.get(Calendar.DAY_OF_MONTH)
               + "“ú";
         }
      } else {
         DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT, locale);
         stringDate = df.format(calendar.getTime());
      }
      
      return stringDate;
   }
   
   public String formatTime(int msec) {
      int time = msec;
      
      int hours = time / 3600000;
      time %= 3600000;
      int minutes = time / 60000;
      time %= 60000;
      int seconds = time / 1000; 
      
      if (hours > 0) {
         return String.format("%d:%02d:%02d", hours, minutes, seconds);   
      } else {
         return String.format("%d:%02d", minutes, seconds);   
      }
   }   
}
