package zero.ucamaps.basemaps;

import android.graphics.Bitmap;

import com.esri.core.portal.PortalItem;

public class BasemapItem {

  public PortalItem item;

  public Bitmap itemThumbnail;

  public BasemapItem(PortalItem item, Bitmap bt) {
    this.item = item;
    this.itemThumbnail = bt;
  }
}
