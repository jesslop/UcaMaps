package zero.ucamaps;

import android.view.View;
import android.widget.LinearLayout;

/**
 * Represents an item in the navigation drawer list.
 */
public class DrawerItem {
  public interface OnClickListener {
    void onClick();
  }

  private final OnClickListener mListener;

  private final LinearLayout mView;

  public DrawerItem(LinearLayout view, OnClickListener listener) {
    mView = view;
    mListener = listener;
  }

  /**
   * Invokes the OnClickListener registered with this DrawerItem.
   */
  public void onClicked() {
    if (mListener != null) {
      mListener.onClick();
    }
  }

  public View getView() {
    return mView;
  }
}
