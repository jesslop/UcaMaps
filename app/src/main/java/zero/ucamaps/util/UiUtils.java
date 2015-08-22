package zero.ucamaps.util;

import android.content.res.Resources;

public class UiUtils {
  /**
   * Converts dp units to pixel units. Code taken from <a
   * href="http://developer.android.com/guide/practices/screens_support.html#dips-pels">
   * http://developer.android.com/guide/practices/screens_support.html#dips-pels</a>
   * 
   * @param dips The number in dp (dip) that you wish to convert to px (pixels)
   * @return The number of pixels that corresponds to the number in dips on this device
   */
  public static int dipsToPixels(int dips) {
    if (dips == 0) {
      return 0;
    }

    final float scale = Resources.getSystem().getDisplayMetrics().density;
    return (int) (dips * scale + 0.5f);
  }

}
