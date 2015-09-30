package zero.ucamaps.basemaps;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import zero.ucamaps.R;

public class BasemapsAdapter extends BaseAdapter {
  /**
   * A callback interface that indicates when a basemap in the list has been clicked.
   */
  public interface BasemapsAdapterClickListener {
    /**
     * Callback for when a basemap list item is clicked.
     * @param listPosition Position within the list of an item that has been clicked.
     */
    public void onBasemapItemClicked(int listPosition);
  }

  BasemapsAdapterClickListener mOnClickListener;

  // need context to use it to construct view
  Context mContext;

  // hold onto a copy of all basemap items
  List<BasemapItem> items;

  //mandatory constructor
  public BasemapsAdapter(Context c) {
    mContext = c;
  }

  //custom constructor with the paramenters we need
  public BasemapsAdapter(Context c, ArrayList<BasemapItem> portalItems, BasemapsAdapterClickListener listener) {
    mContext = c;
    this.items = portalItems;
    mOnClickListener = listener;
  }

  @Override
  public void notifyDataSetChanged() {
    super.notifyDataSetChanged();
  }

  @Override
  public int getCount() {
    return items == null ? 0 : items.size();
  }

  @Override
  public Object getItem(int position) {
    return items.get(position);
  }

  @Override
  public long getItemId(int position) {
    return position;
  }

  @Override
  public View getView(final int position, View convertView, ViewGroup parent) {

    // Inflate view unless we have an old one to reuse
    View newView = convertView;
    if (convertView == null) {
      LayoutInflater inflator = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      newView = inflator.inflate(R.layout.basemap_image, null);
    }

    // Create view for the thumbnail
    ImageView image = (ImageView) newView.findViewById(R.id.basemap_grid_item_thumbnail_imageview);
    image.setImageBitmap(items.get((position)).itemThumbnail);

    // Register listener for clicks on the thumbnail
    image.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(final View view) {
        mOnClickListener.onBasemapItemClicked(position);
      }
    });

    // Set the title and return the view we've created
    TextView text = (TextView) newView.findViewById(R.id.basemap_grid_item_title_textview);
    text.setText(items.get((position)).item.getTitle());
    return newView;
  }

}
