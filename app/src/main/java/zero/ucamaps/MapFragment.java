package zero.ucamaps;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TextView;
import android.widget.Toast;

import com.esri.android.map.Callout;
import com.esri.android.map.GraphicsLayer;
import com.esri.android.map.Layer;
import com.esri.android.map.LocationDisplayManager;
import com.esri.android.map.LocationDisplayManager.AutoPanMode;
import com.esri.android.map.MapOnTouchListener;
import com.esri.android.map.MapView;
import com.esri.android.map.ags.ArcGISFeatureLayer;
import com.esri.android.map.event.OnPinchListener;
import com.esri.android.map.event.OnSingleTapListener;
import com.esri.android.map.event.OnStatusChangedListener;

import zero.ucamaps.dialogs.ProgressDialogFragment;
import zero.ucamaps.location.DirectionsDialogFragment;
import zero.ucamaps.location.DirectionsDialogFragment.DirectionsDialogListener;
import zero.ucamaps.location.RoutingDialogFragment;
import zero.ucamaps.location.RoutingDialogFragment.RoutingDialogListener;
import zero.ucamaps.tools.Compass;
import zero.ucamaps.tts.TTSManager;
import zero.ucamaps.util.TaskExecutor;

import com.esri.core.geometry.Envelope;
import com.esri.core.geometry.GeometryEngine;
import com.esri.core.geometry.LinearUnit;
import com.esri.core.geometry.Point;
import com.esri.core.geometry.Polyline;
import com.esri.core.geometry.SpatialReference;
import com.esri.core.geometry.Unit;
import com.esri.core.map.Graphic;
import com.esri.core.portal.BaseMap;
import com.esri.core.portal.Portal;
import com.esri.core.portal.WebMap;
import com.esri.core.symbol.PictureMarkerSymbol;
import com.esri.core.symbol.SimpleLineSymbol;
import com.esri.core.symbol.SimpleLineSymbol.STYLE;
import com.esri.core.tasks.geocode.Locator;
import com.esri.core.tasks.geocode.LocatorFindParameters;
import com.esri.core.tasks.geocode.LocatorGeocodeResult;
import com.esri.core.tasks.geocode.LocatorReverseGeocodeResult;
import com.esri.core.tasks.na.NAFeaturesAsFeature;
import com.esri.core.tasks.na.Route;
import com.esri.core.tasks.na.RouteDirection;
import com.esri.core.tasks.na.RouteParameters;
import com.esri.core.tasks.na.RouteResult;
import com.esri.core.tasks.na.RouteTask;
import com.esri.core.tasks.na.StopGraphic;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;

/**
 * Implements the view that shows the map.
 */
public class MapFragment extends Fragment implements RoutingDialogListener, OnCancelListener {
	public static final String TAG = MapFragment.class.getSimpleName();

	private static final String KEY_BASEMAP_ITEM = "KEY_BASEMAP_ITEM";
    private static final String KEY_SOUND_ITEM = "KEY_SOUND_ITEM";
	private static final String KEY_IS_LOCATION_TRACKING = "IsLocationTracking";
	private static final int REQUEST_CODE_PROGRESS_DIALOG = 1;
	private static final String SEARCH_HINT = "Search";

	private static FrameLayout.LayoutParams mlayoutParams;

	// Margins parameters for search view
	private static int TOP_MARGIN_SEARCH = 55;
	private static int LEFT_MARGIN_SEARCH = 15;
	private static int RIGHT_MARGIN_SEARCH = 15;
	private static int BOTTOM_MARGIN_SEARCH = 0;

	// Margin parameters for compass
	private static int TOP_MARGIN_COMPASS = 15;
	private static int LEFT_MARGIN_COMPASS = 0;
	private static int BOTTOM_MARGIN_COMPASS = 0;
	private static int RIGHT_MARGIN_COMPASS = 0;

	// Height and Width for the compass image
	private static int HEIGHT = 140;
	private static int WIDTH = 140;

	// The circle area specified by search_radius and input lat/lon serves searching purpose.
	// It is also used to construct the extent which map zooms to after the first GPS fix is retrieved.
	private final static double SEARCH_RADIUS = 5;

    private String mBasemapPortalItemId;

    //Sound
	TTSManager ttsManager = null;
	private String mSoundActive;

	//FrameLayout for the MapView
	private FrameLayout mMapContainer;
	public static MapView mMapView;
	private String mMapViewState;

	// GPS location tracking
	private boolean mIsLocationTracking;
	private Point mLocation = null;

	// Graphics layer to show geocode and reverse geocode results
	private GraphicsLayer mLocationLayer;
	private Point mLocationLayerPoint;
	private String mLocationLayerPointString;

	// Graphics layer to show routes
	private GraphicsLayer mRouteLayer;
	private List<RouteDirection> mRoutingDirections;

	// Spatial references used for projecting points
	private final SpatialReference mWm = SpatialReference.create(102100);
	private final SpatialReference mEgs = SpatialReference.create(4326);

    //Compass
	Compass mCompass;
	LayoutParams compassFrameParams;
	private MotionEvent mLongPressEvent;

	@SuppressWarnings("rawtypes")
	// - using this only to cancel pending tasks in a generic way
	private AsyncTask mPendingTask;
	private View mSearchBox;
	private View mSearchResult;
	private LayoutInflater mInflater;
	private String mStartLocation, mEndLocation;

    public static MapFragment newSoundInstance(String basemapPortalItemId, String changeSound) {
        MapFragment mapFragment = new MapFragment();

        Bundle args = new Bundle();
        args.putString(KEY_BASEMAP_ITEM, basemapPortalItemId);
        args.putString(KEY_SOUND_ITEM, changeSound);

        mapFragment.setArguments(args);
        return mapFragment;
    }

	public MapFragment() {
		// make MapFragment ctor private - use newInstance() instead
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        Bundle args = savedInstanceState != null ? savedInstanceState
                : getArguments();
        if (args != null) {
            mIsLocationTracking = args.getBoolean(KEY_IS_LOCATION_TRACKING);
            mBasemapPortalItemId = args.getString(KEY_BASEMAP_ITEM);
            mSoundActive = args.getString(KEY_SOUND_ITEM);
        }

        // Calling setRetainInstance() causes the Fragment instance to be retained when its Activity is destroyed and
        // recreated. This allows map Layer objects to be retained so data will not need to be fetched from the network again.
        setRetainInstance(true);

        ttsManager = new TTSManager();
        ttsManager.init(getActivity());

	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState) {

        mMapContainer = (FrameLayout) inflater.inflate(R.layout.map_fragment_layout,container,false);

		if (mBasemapPortalItemId != null) {
			// show a map with the basemap represented by mBasemapPortalItemId
			loadWebMapIntoMapView(mBasemapPortalItemId, new Portal("http://www.arcgis.com", null));
		} else {
			// show the default map
			String defaultBaseMapURL = getString(R.string.default_basemap_url);
			MapView mapView = new MapView(getActivity(), defaultBaseMapURL,"", "");

			// Set the MapView
			setMapView(mapView);
			mapView.zoomin();
		}
		return mMapContainer;
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		// Inflate the menu items for use in the action bar
		inflater.inflate(R.menu.action, menu);
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {

		case R.id.location:
			// Toggle location tracking on or off
			if (mIsLocationTracking) {
				item.setIcon(R.drawable.ic_action_compass_mode);
				mMapView.getLocationDisplayManager().setAutoPanMode(AutoPanMode.COMPASS);
				mCompass.start();
				mCompass.setVisibility(View.VISIBLE);
				mIsLocationTracking = false;
			} else {
				startLocationTracking();
				item.setIcon(android.R.drawable.ic_menu_mylocation);
				if (mMapView.getRotationAngle() != 0) {
					mCompass.setVisibility(View.VISIBLE);
					mCompass.setRotationAngle(mMapView.getRotationAngle());
				} else {
					mCompass.setVisibility(View.GONE);
				}

			}
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onPause() {
		super.onPause();

		// Pause the MapView and stop the LocationDisplayManager to save battery
		if (mMapView != null) {
			if (mIsLocationTracking) {
				mMapView.getLocationDisplayManager().stop();
				mCompass.stop();
			}
			mMapViewState = mMapView.retainState();
			mMapView.pause();
		}
	}

	@Override
	public void onResume() {
		super.onResume();

		// Start the MapView and LocationDisplayManager running again
        if (mMapView != null) {
            mMapView.unpause();
			if (mMapViewState != null) {
				mMapView.restoreState(mMapViewState);
			}
			if (mIsLocationTracking) {
				mMapView.getLocationDisplayManager().start();
			}
		}
	}

	@Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_SOUND_ITEM, mSoundActive);
		outState.putString(KEY_BASEMAP_ITEM, mBasemapPortalItemId);
		outState.putBoolean(KEY_IS_LOCATION_TRACKING, mIsLocationTracking);

	}

    /**
     * Loads a WebMap and creates a MapView from it which is set into the
     * fragment's layout.
     * @param basemapPortalItemId
     *            The portal item id that represents the basemap.
     * @throws Exception
     *             if WebMap loading failed.
     */
    private void loadWebMapIntoMapView(final String basemapPortalItemId, final Portal portal) {

        TaskExecutor.getInstance().getThreadPool().submit(new Callable<Void>() {

            @Override
            public Void call() throws Exception {

                // load a WebMap instance from the portal item
                final WebMap webmap = WebMap.newInstance(basemapPortalItemId, portal);

                // load the WebMap that represents the basemap if one was specified
                WebMap basemapWebMap = null;
                if (basemapPortalItemId != null && !basemapPortalItemId.isEmpty()) {
                    basemapWebMap = WebMap.newInstance(basemapPortalItemId, portal);
                }
                final BaseMap basemap = basemapWebMap != null ? basemapWebMap.getBaseMap() : null;

                if (webmap != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            MapView mapView = new MapView(getActivity(), webmap, basemap, null, null);

                            setMapView(mapView);
                            mapView.zoomin();

                        }
                    });
                } else {
                    throw new Exception("Failed to load web map.");
                }
                return null;
            }
        });
    }


	/**
	 * Takes a MapView that has already been instantiated to show a WebMap, completes its setup by setting
     * various listeners and attributes, and sets it as the activity's content view.
	 * @param mapView
	 */
	private void setMapView(final MapView mapView) {

		mMapView = mapView;
		mMapView.setEsriLogoVisible(false);
		mMapView.enableWrapAround(true);
		mapView.setAllowRotationByPinch(true);

        //Initializing sound
        initializeSound();

		// Creating an inflater
		mInflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		// Setting up the layout params for the searchview and searchresult layout
		mlayoutParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP);
		mlayoutParams.setMargins(LEFT_MARGIN_SEARCH, TOP_MARGIN_SEARCH, RIGHT_MARGIN_SEARCH, BOTTOM_MARGIN_SEARCH);

		// set MapView into the activity layout
		mMapContainer.addView(mMapView);

		// Displaying the searchbox layout
		showSearchBoxLayout();

		mMapView.setOnPinchListener(new OnPinchListener() {
			/**
			 * Default value
			 */
			private static final long serialVersionUID = 1L;

			@Override
			public void postPointersDown(float x1, float y1, float x2, float y2, double factor) {
			}

			@Override
			public void postPointersMove(float x1, float y1, float x2, float y2, double factor) {
			}

			@Override
			public void postPointersUp(float x1, float y1, float x2, float y2, double factor) {
			}

			@Override
			public void prePointersDown(float x1, float y1, float x2, float y2, double factor) {
			}

			@Override
			public void prePointersMove(float x1, float y1, float x2, float y2, double factor) {
				if (mMapView.getRotationAngle() > 5 || mMapView.getRotationAngle() < -5) {
					mCompass.setVisibility(View.VISIBLE);
					mCompass.sensorManager.unregisterListener(mCompass.sensorEventListener);
					mCompass.setRotationAngle(mMapView.getRotationAngle());
				}
			}

			@Override
			public void prePointersUp(float x1, float y1, float x2, float y2,double factor) {
			}

		});

		// Setup listener for map initialized
		mMapView.setOnStatusChangedListener(new OnStatusChangedListener() {

			private static final long serialVersionUID = 1L;

			@Override
			public void onStatusChanged(Object source, STATUS status) {

				if (source == mMapView && status == STATUS.INITIALIZED) {
					if (mMapViewState == null) {
						// Starting location tracking will cause zoom to My Location
						startLocationTracking();
					} else {
						mMapView.restoreState(mMapViewState);
					}
					// add search and routing layers
					addGraphicLayers();
				}
			}

		});

		// Setup use of magnifier on a long press on the map
		mMapView.setShowMagnifierOnLongPress(true);
		mLongPressEvent = null;

		// Setup OnTouchListener to detect and act on long-press
		mMapView.setOnTouchListener(new MapOnTouchListener(getActivity(),
				mMapView) {

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
					// Start of a new gesture. Make sure mLongPressEvent is cleared.
					mLongPressEvent = null;
				}
				return super.onTouch(v, event);
			}

			@Override
			public void onLongPress(MotionEvent point) {
				// Set mLongPressEvent to indicate we are processing a long-press
				mLongPressEvent = point;
				super.onLongPress(point);

			}

			@Override
			public boolean onDragPointerUp(MotionEvent from, final MotionEvent to) {
				if (mLongPressEvent != null) {
					// This is the end of a long-press that will have displayed the magnifier.
					// Perform reverse-geocoding of the point that was pressed
					Point mapPoint = mMapView.toMapPoint(to.getX(), to.getY());
					ReverseGeocodingAsyncTask reverseGeocodeTask = new ReverseGeocodingAsyncTask();
					reverseGeocodeTask.execute(mapPoint);
					mPendingTask = reverseGeocodeTask;
					mLongPressEvent = null;
					// Remove any previous graphics
					resetGraphicsLayers();
				}
				return super.onDragPointerUp(from, to);
			}

		});

	}

	/**
	 * Adds the compass as per the height of the layout
	 * @param height
	 */
	private void addCompass(int height) {

		mMapContainer.removeView(mCompass);

		// Create the Compass custom view, and add it onto the MapView.
		mCompass = new Compass(mMapView.getContext());
		mCompass.setAlpha(1f);
		mCompass.setRotationAngle(45);
		compassFrameParams = new FrameLayout.LayoutParams(HEIGHT, WIDTH,Gravity.RIGHT);

		TOP_MARGIN_COMPASS = TOP_MARGIN_SEARCH + height + 15;

		((MarginLayoutParams) compassFrameParams).setMargins(LEFT_MARGIN_COMPASS, TOP_MARGIN_COMPASS, RIGHT_MARGIN_COMPASS, BOTTOM_MARGIN_COMPASS);

		mCompass.setLayoutParams(compassFrameParams);

		mCompass.setVisibility(View.GONE);

		mCompass.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				mCompass.setVisibility(View.GONE);
				mMapView.setRotationAngle(0);
			}
		});

		// Add the compass on the map
		mMapContainer.addView(mCompass);
	}

    /**
     * Initialize the sound depending on the variable in the MainActivity
     */
    private void initializeSound(){

        MainActivity main = (MainActivity) getActivity();
        mSoundActive = main.getChangeSound();

        if (mSoundActive.equals("Sound On")) {
            Toast.makeText(getActivity(),mSoundActive, Toast.LENGTH_LONG).show();
        }
        else{
            ttsManager.shutDown();
            Toast.makeText(getActivity(),mSoundActive, Toast.LENGTH_LONG).show();
        }

    }

    /**
	 * Displays the Dialog Fragment which allows users to route
	 */
	private void showRoutingDialogFragment() {
		// Show RoutingDialogFragment to get routing start and end points.
		// This calls back to onGetRoute() to do the routing.
		RoutingDialogFragment routingFrag = new RoutingDialogFragment();
		routingFrag.setRoutingDialogListener(this);
		Bundle arguments = new Bundle();
		if (mLocationLayerPoint != null) {
			arguments.putString(RoutingDialogFragment.ARG_END_POINT_DEFAULT, mLocationLayerPointString);
		}
		routingFrag.setArguments(arguments);
		routingFrag.show(getFragmentManager(), null);

	}

	/**
	 * Displays the Directions Dialog Fragment
	 */
	private void showDirectionsDialogFragment() {
		// Launch a DirectionsListFragment to display list of directions
		final DirectionsDialogFragment frag = new DirectionsDialogFragment();

		frag.setRoutingDirections(mRoutingDirections,
                new DirectionsDialogListener() {

                    @Override
                    public void onDirectionSelected(int position) {
                        // User has selected a particular direction dismiss the dialog and zoom to the selected direction
                        frag.dismiss();
                        RouteDirection direction = mRoutingDirections.get(position);
                        String text = mRoutingDirections.get(position).getText(); //getting the direction
                        mMapView.setExtent(direction.getGeometry());
                        //Reads the direction with sound
                        if (mSoundActive.equals("Sound On")) {
                            Toast.makeText(getActivity(),text, Toast.LENGTH_LONG).show();
                            ttsManager.initQueue(text);
                        }
                    }

                });
		getFragmentManager().beginTransaction().add(frag, null).commit();
	}

    /**
     * Releases the resources used by the TextToSpeech engine.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        ttsManager.shutDown();
    }

	/**
	 * Displays the search view layout
	 */
	private void showSearchBoxLayout() {

		// Inflating the layout from the xml file
		mSearchBox = mInflater.inflate(R.layout.searchview, null);
		// Setting the layout parameters to the layout
		mSearchBox.setLayoutParams(mlayoutParams);
		// Initializing the searchview and the image view
		final SearchView mSearchview = (SearchView) mSearchBox.findViewById(R.id.searchView1);

		ImageView iv_route = (ImageView) mSearchBox.findViewById(R.id.imageView1);
		mSearchview.setIconifiedByDefault(false);
		mSearchview.setQueryHint(SEARCH_HINT);

		// Adding the layout to the map conatiner
		mMapContainer.addView(mSearchBox);

		// Setup the listener for the route onclick
		iv_route.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				showRoutingDialogFragment();
			}
		});

		// Setup the listener when the search button is pressed on the keyboard
		mSearchview.setOnQueryTextListener(new OnQueryTextListener() {

			@Override
			public boolean onQueryTextSubmit(String query) {
				onSearchButtonClicked(query);
				mSearchview.clearFocus();
				return true;
			}

			@Override
			public boolean onQueryTextChange(String newText) {
				return false;
			}
		});

		// Add the compass after getting the height of the layout
		mSearchBox.getViewTreeObserver().addOnGlobalLayoutListener(
				new OnGlobalLayoutListener() {
					@Override
					public void onGlobalLayout() {
						addCompass(mSearchBox.getHeight());
						mSearchBox.getViewTreeObserver().removeGlobalOnLayoutListener(this);
					}

				});

	}

	/**
	 * Clears all graphics out of the location layer and the route layer.
	 */
	void resetGraphicsLayers() {
		mLocationLayer.removeAll();
		mRouteLayer.removeAll();
		mLocationLayerPoint = null;
		mLocationLayerPointString = null;
		mRoutingDirections = null;
	}

	/**
	 * Adds location layer and the route layer to the MapView.
	 */
	void addGraphicLayers() {
		// Add location layer
		if (mLocationLayer == null) {
			mLocationLayer = new GraphicsLayer();
		}
		mMapView.addLayer(mLocationLayer);

		// Add the route graphic layer
		if (mRouteLayer == null) {
			mRouteLayer = new GraphicsLayer();
		}
		mMapView.addLayer(mRouteLayer);
	}

	/**
	 * Starts tracking GPS location.
	 */
	void startLocationTracking() {
		LocationDisplayManager locDispMgr = mMapView.getLocationDisplayManager();
		mCompass.start();
		locDispMgr.setAutoPanMode(AutoPanMode.LOCATION);
		locDispMgr.setAllowNetworkLocation(true);

		locDispMgr.setLocationListener(new LocationListener() {

			boolean locationChanged = false;

			// Zooms to the current location when first GPS fix arrives
			@Override
			public void onLocationChanged(Location loc) {
				double locy = loc.getLatitude();
				double locx = loc.getLongitude();
				Point wgspoint = new Point(locx, locy);
				mLocation = (Point) GeometryEngine.project(wgspoint,SpatialReference.create(4326),mMapView.getSpatialReference());
				if (!locationChanged) {
					locationChanged = true;
					Unit mapUnit = mMapView.getSpatialReference().getUnit();
					double zoomWidth = Unit.convertUnits(SEARCH_RADIUS,Unit.create(LinearUnit.Code.METER), mapUnit);
					Envelope zoomExtent = new Envelope(mLocation,zoomWidth / 10, zoomWidth / 10);
					mMapView.setExtent(zoomExtent);
				}
			}

			@Override
			public void onProviderDisabled(String arg0) {
                Toast.makeText(getActivity(), "GPS turned off", Toast.LENGTH_SHORT).show();
			}

			@Override
			public void onProviderEnabled(String arg0) {
                Toast.makeText(getActivity(), "GPS turned on", Toast.LENGTH_SHORT).show();
			}

			@Override
			public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
			}
		});
		locDispMgr.start();
		mIsLocationTracking = true;
	}

	/**
	 * Called from search_layout.xml when user presses Search button.
     */
	public void onSearchButtonClicked(String address) {

		// Hide virtual keyboard
		InputMethodManager inputManager = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
		inputManager.hideSoftInputFromWindow(getActivity().getCurrentFocus().getWindowToken(), 0);

		// Remove any previous graphics and routes
		resetGraphicsLayers();
		executeLocatorTask(address);
	}

	/**
	 * Set up the search parameters and execute the Locator task.
	 * @param address
	 */
	private void executeLocatorTask(String address) {
		// Create Locator parameters from single line address string
		LocatorFindParameters findParams = new LocatorFindParameters(address);

		// Use the centre of the current map extent as the find location point
		findParams.setLocation(mMapView.getCenter(),mMapView.getSpatialReference());

		// Calculate distance for find operation
		Envelope mapExtent = new Envelope();
		mMapView.getExtent().queryEnvelope(mapExtent);
		// assume map is in metres, other units wont work, double current envelope
		double distance = (mapExtent != null && mapExtent.getWidth() > 0) ? mapExtent.getWidth() * 2 : 10000;
		findParams.setDistance(distance);
		findParams.setMaxLocations(2);

		// Set address spatial reference to match map
		findParams.setOutSR(mMapView.getSpatialReference());

		// Execute async task to find the address
		LocatorAsyncTask locatorTask = new LocatorAsyncTask();
		locatorTask.execute(findParams);
		mPendingTask = locatorTask;

		mLocationLayerPointString = address;
	}

	/**
	 * Called by RoutingDialogFragment when user presses Get Route button.
	 * @param startPoint
	 *            String entered by user to define start point.
	 * @param endPoint
	 *            String entered by user to define end point.
	 * @return true if routing task executed, false if parameters rejected. If
	 *         this method rejects the parameters it must display an explanatory
	 *         Toast to the user before returning.
	 */
	@Override
	public boolean onGetRoute(String startPoint, String endPoint) {
		// Check if we need a location fix
		if (startPoint.equals(getString(R.string.my_location)) && mLocation == null) {
			Toast.makeText(getActivity(),getString(R.string.need_location_fix), Toast.LENGTH_LONG).show();
			return false;
		}
		// Remove any previous graphics and routes
		resetGraphicsLayers();
		// Do the routing
		executeRoutingTask(startPoint, endPoint);
		return true;
	}

	/**
	 * Set up Route Parameters to execute RouteTask
	 * @param start
	 * @param end
	 */
	@SuppressWarnings("unchecked")
	private void executeRoutingTask(String start, String end) {
		// Create a list of start end point params
		LocatorFindParameters routeStartParams = new LocatorFindParameters(start);
		LocatorFindParameters routeEndParams = new LocatorFindParameters(end);
		List<LocatorFindParameters> routeParams = new ArrayList<LocatorFindParameters>();

		// Add params to list
		routeParams.add(routeStartParams);
		routeParams.add(routeEndParams);

		// Execute async task to do the routing
		RouteAsyncTask routeTask = new RouteAsyncTask();
		routeTask.execute(routeParams);
		mPendingTask = routeTask;
	}

	@Override
	public void onCancel(DialogInterface dialog) {
		// a pending task needs to be canceled
		if (mPendingTask != null) {
			mPendingTask.cancel(true);
		}
	}

	/**
	 * Shows the search result in the layout after successful geocoding and reverse geocoding
	 */
	private void showSearchResultLayout(String address) {
		// Remove the layouts
		mMapContainer.removeView(mSearchBox);
		mMapContainer.removeView(mSearchResult);

		// Inflate the new layout from the xml file
		mSearchResult = mInflater.inflate(R.layout.search_result, null);

		// Set layout parameters
		mSearchResult.setLayoutParams(mlayoutParams);

		// Initialize the textview and set its text
		TextView tv = (TextView) mSearchResult.findViewById(R.id.textView1);
		tv.setTypeface(null, Typeface.BOLD);
		tv.setText(address);
        ttsManager.initQueue(address);

		// Adding the search result layout to the map container
		mMapContainer.addView(mSearchResult);

		// Setup the listener for the "cancel" icon
		ImageView iv_cancel = (ImageView) mSearchResult .findViewById(R.id.imageView3);
		iv_cancel.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// Remove the search result view
				mMapContainer.removeView(mSearchResult);
				// Add the search box view
				showSearchBoxLayout();
				// Remove all graphics from the map
				resetGraphicsLayers();

			}
		});

		// Set up the listener for the "Get Directions" icon
		ImageView iv_route = (ImageView) mSearchResult.findViewById(R.id.imageView2);
		iv_route.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				onGetRoute(getString(R.string.my_location), mLocationLayerPointString);
			}
		});

		// Add the compass after getting the height of the layout
		mSearchResult.getViewTreeObserver().addOnGlobalLayoutListener(
				new OnGlobalLayoutListener() {
					@Override
					public void onGlobalLayout() {
						addCompass(mSearchResult.getHeight());
						mSearchResult.getViewTreeObserver().removeGlobalOnLayoutListener(this);
					}

				});

	}

	/**
	 * Shows the Routing result layout after successful routing
	 * @param distance in meters
	 */
	private void showRoutingResultLayout(double distance) {
        // Remove the layours
		mMapContainer.removeView(mSearchResult);
		mMapContainer.removeView(mSearchBox);

		// Inflate the new layout from the xml file
		mSearchResult = mInflater.inflate(R.layout.routing_result, null);
		mSearchResult.setLayoutParams(mlayoutParams);

		// Shorten the start and end location by finding the first comma if
		// present
		int index_from = mStartLocation.indexOf(",");
		int index_to = mEndLocation.indexOf(",");
		if (index_from != -1)
			mStartLocation = mStartLocation.substring(0, index_from);
		if (index_to != -1)
			mEndLocation = mEndLocation.substring(0, index_to);

		// Initialize the textview and display the text
		TextView tv_from = (TextView) mSearchResult.findViewById(R.id.tv_from);
		tv_from.setTypeface(null, Typeface.BOLD);
		tv_from.setText(" " + mStartLocation);

		TextView tv_to = (TextView) mSearchResult.findViewById(R.id.tv_to);
		tv_to.setTypeface(null, Typeface.BOLD);
		tv_to.setText(" " + mEndLocation);

		// Rounding off the values
		distance = Math.round((distance * 1609.344));

		TextView tv_dist = (TextView) mSearchResult.findViewById(R.id.tv_dist);
		tv_dist.setTypeface(null, Typeface.BOLD);
		tv_dist.setText(" (" + distance + " mts )");

		// Adding the layout
		mMapContainer.addView(mSearchResult);

		// Setup the listener for the "Cancel" icon
		ImageView iv_cancel = (ImageView) mSearchResult.findViewById(R.id.imageView3);
		iv_cancel.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// Remove the search result view
				mMapContainer.removeView(mSearchResult);
				// Add the default search box view
				showSearchBoxLayout();
				// Remove all graphics from the map
				resetGraphicsLayers();
			}
		});

		// Set up the listener for the "Show Directions" icon
		ImageView iv_directions = (ImageView) mSearchResult.findViewById(R.id.imageView2);
		iv_directions.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				showDirectionsDialogFragment();
			}
		});

		// Add the compass after getting the height of the layout
		mSearchResult.getViewTreeObserver().addOnGlobalLayoutListener(
				new OnGlobalLayoutListener() {
					@Override
					public void onGlobalLayout() {
						addCompass(mSearchResult.getHeight());
						mSearchResult.getViewTreeObserver().removeGlobalOnLayoutListener(this);
					}

				});

	}

	/*
	 * This class provides an AsyncTask that performs a geolocation request on a
	 * background thread and displays the first result on the map on the UI thread.
	 */
	private class LocatorAsyncTask extends AsyncTask<LocatorFindParameters, Void, List<LocatorGeocodeResult>> {
		private static final String TAG_LOCATOR_PROGRESS_DIALOG = "TAG_LOCATOR_PROGRESS_DIALOG";
		private Exception mException;
		private ProgressDialogFragment mProgressDialog;
		public LocatorAsyncTask() {
		}

		@Override
		protected void onPreExecute() {
			mProgressDialog = ProgressDialogFragment.newInstance(getActivity().getString(R.string.address_search));
			// set the target fragment to receive cancel notification
			mProgressDialog.setTargetFragment(MapFragment.this,REQUEST_CODE_PROGRESS_DIALOG);
			mProgressDialog.show(getActivity().getFragmentManager(),TAG_LOCATOR_PROGRESS_DIALOG);
		}

		@Override
		protected List<LocatorGeocodeResult> doInBackground(LocatorFindParameters... params) {
			// Perform routing request on background thread
			mException = null;
			List<LocatorGeocodeResult> results = null;

			// Create locator using default online geocoding service and tell it to find the given address
			Locator locator = Locator.createOnlineLocator(getString(R.string.geocodeservice_url));
			try {
				results = locator.find(params[0]);
			} catch (Exception e) {
				mException = e;
			}
			return results;
		}

		@Override
		protected void onPostExecute(List<LocatorGeocodeResult> result) {
			// Display results on UI thread
			mProgressDialog.dismiss();
			if (mException != null) {
				Log.w(TAG, "LocatorSyncTask failed with:");
				mException.printStackTrace();
				Toast.makeText(getActivity(),getString(R.string.addressSearchFailed),Toast.LENGTH_LONG).show();
				return;
			}

			if (result.size() == 0) {
				Toast.makeText(getActivity(),getString(R.string.noResultsFound), Toast.LENGTH_LONG).show();
			} else {
				// Use first result in the list
				LocatorGeocodeResult geocodeResult = result.get(0);
				// get return geometry from geocode result
				Point resultPoint = geocodeResult.getLocation();
				// create marker symbol to represent location
				Drawable drawable = getActivity().getResources().getDrawable(R.drawable.pin_circle_red);
				PictureMarkerSymbol resultSymbol = new PictureMarkerSymbol(getActivity(), drawable);
				// create graphic object for resulting location
				Graphic resultLocGraphic = new Graphic(resultPoint,resultSymbol);
				// add graphic to location layer
				mLocationLayer.addGraphic(resultLocGraphic);
				// Get the address
				String address = geocodeResult.getAddress();
				mLocationLayerPoint = resultPoint;
				// Zoom map to geocode result location
				mMapView.zoomToResolution(geocodeResult.getLocation(), 2);
				showSearchResultLayout(address);
			}
		}

	}

	/**
	 * This class provides an AsyncTask that performs a routing request on a
	 * background thread and displays the resultant route on the map on the UI
	 * thread.
	 */
	private class RouteAsyncTask extends AsyncTask<List<LocatorFindParameters>, Void, RouteResult> {
		private static final String TAG_ROUTE_SEARCH_PROGRESS_DIALOG = "TAG_ROUTE_SEARCH_PROGRESS_DIALOG";
		private Exception mException;
		private ProgressDialogFragment mProgressDialog;
		public RouteAsyncTask() {
		}

		@Override
		protected void onPreExecute() {
			mProgressDialog = ProgressDialogFragment.newInstance(getActivity().getString(R.string.route_search));
			// set the target fragment to receive cancel notification
			mProgressDialog.setTargetFragment(MapFragment.this,REQUEST_CODE_PROGRESS_DIALOG);
			mProgressDialog.show(getActivity().getFragmentManager(),TAG_ROUTE_SEARCH_PROGRESS_DIALOG);
		}

		@Override
		protected RouteResult doInBackground(List<LocatorFindParameters>... params) {
			// Perform routing request on background thread
			mException = null;

			// Define route objects
			List<LocatorGeocodeResult> geocodeStartResult = null;
			List<LocatorGeocodeResult> geocodeEndResult = null;
			Point startPoint = null;
			Point endPoint = null;

			// Create a new locator to geocode start/end points; by default uses ArcGIS online world geocoding service
			Locator locator = Locator.createOnlineLocator(getString(R.string.geocodeservice_url));

			try {
				// Geocode start position, or use My Location (from GPS)
				LocatorFindParameters startParam = params[0].get(0);
				if (startParam.getText().equals(getString(R.string.my_location))) {
					mStartLocation = getString(R.string.my_location);
					startPoint = (Point) GeometryEngine.project(mLocation, mWm,mEgs);
				} else {
					geocodeStartResult = locator.find(startParam);
					startPoint = geocodeStartResult.get(0).getLocation();
					mStartLocation = geocodeStartResult.get(0).getAddress();

					if (isCancelled()) {
						return null;
					}
				}

				// Geocode the destination
				LocatorFindParameters endParam = params[0].get(1);
				if (endParam.getText().equals(getString(R.string.my_location))) {
					mEndLocation = getString(R.string.my_location);
					endPoint = (Point) GeometryEngine.project(mLocation, mWm,mEgs);
				} else {
					geocodeEndResult = locator.find(endParam);
					endPoint = geocodeEndResult.get(0).getLocation();
					mEndLocation = geocodeEndResult.get(0).getAddress();
				}

			} catch (Exception e) {
				mException = e;
				return null;
			}
			if (isCancelled()) {
				return null;
			}

			// Create a new routing task pointing to an ArcGIS Network Analysis Service
			RouteTask routeTask;
			RouteParameters routeParams = null;
			try {
				routeTask = RouteTask.createOnlineRouteTask(getString(R.string.routingservice_url), null);
				// Retrieve default routing parameters
				routeParams = routeTask.retrieveDefaultRouteTaskParameters();
			} catch (Exception e) {
				mException = e;
				return null;
			}
			if (isCancelled()) {
				return null;
			}

			// Customize the route parameters
			NAFeaturesAsFeature routeFAF = new NAFeaturesAsFeature();
			StopGraphic sgStart = new StopGraphic(startPoint);
			StopGraphic sgEnd = new StopGraphic(endPoint);
			routeFAF.setFeatures(new Graphic[] { sgStart, sgEnd });
			routeFAF.setCompressedRequest(true);
			routeParams.setStops(routeFAF);
			//noinspection ResourceType
			routeParams.setOutSpatialReference(mMapView.getSpatialReference());

			// Solve the route
			RouteResult routeResult;
			try {
				routeResult = routeTask.solve(routeParams);
			} catch (Exception e) {
				mException = e;
				return null;
			}
			if (isCancelled()) {
				return null;
			}
			return routeResult;
		}

		@Override
		protected void onPostExecute(RouteResult result) {
			// Display results on UI thread
			mProgressDialog.dismiss();
			if (mException != null) {
				Log.w(TAG, "RouteSyncTask failed with:");
				mException.printStackTrace();
				Toast.makeText(getActivity(),getString(R.string.routingFailed), Toast.LENGTH_LONG).show();
				return;
			}

			// Get first item in list of routes provided by server
			Route route = result.getRoutes().get(0);

			// Create polyline graphic of the full route
			SimpleLineSymbol lineSymbol = new SimpleLineSymbol(Color.BLACK, 2,STYLE.SOLID);
			Graphic routeGraphic = new Graphic(route.getRouteGraphic().getGeometry(), lineSymbol);

			// Create point graphic to mark start of route
			Point startPoint = ((Polyline) routeGraphic.getGeometry()).getPoint(0);
			Graphic startGraphic = createMarkerGraphic(startPoint, false);

			// Create point graphic to mark end of route
			int endPointIndex = ((Polyline) routeGraphic.getGeometry()).getPointCount() - 1;
			Point endPoint = ((Polyline) routeGraphic.getGeometry()).getPoint(endPointIndex);
			Graphic endGraphic = createMarkerGraphic(endPoint, true);

			// Add these graphics to route layer
			mRouteLayer.addGraphics(new Graphic[] { routeGraphic, startGraphic,endGraphic });

			// Zoom to the extent of the entire route with a padding
			mMapView.setExtent(route.getEnvelope(),100);

			// Save routing directions so user can display them later
			mRoutingDirections = route.getRoutingDirections();

			// Show Routing Result Layout
			showRoutingResultLayout(route.getTotalMiles());

		}

		Graphic createMarkerGraphic(Point point, boolean endPoint) {
			Drawable marker = getResources().getDrawable(
					endPoint ? R.drawable.pin_circle_blue
							: R.drawable.pin_circle_red);
			PictureMarkerSymbol destinationSymbol = new PictureMarkerSymbol(
					mMapView.getContext(), marker);
			// NOTE: marker's bounds not set till marker is used to create destinationSymbol
			float offsetY = convertPixelsToDp(getActivity(), marker.getBounds().bottom);
			destinationSymbol.setOffsetY(offsetY);
			return new Graphic(point, destinationSymbol);
		}
	}

	/**
	 * This class provides an AsyncTask that performs a reverse geocoding
	 * request on a background thread and displays the resultant point on the
	 * map on the UI thread.
	 */
	public class ReverseGeocodingAsyncTask extends AsyncTask<Point, Void, LocatorReverseGeocodeResult> {
		private static final String TAG_REVERSE_GEOCODING_PROGRESS_DIALOG = "TAG_REVERSE_GEOCODING_PROGRESS_DIALOG";
		private Exception mException;
		private ProgressDialogFragment mProgressDialog;
		private Point mPoint;

		@Override
		protected void onPreExecute() {
			mProgressDialog = ProgressDialogFragment.newInstance(getActivity().getString(R.string.reverse_geocoding));
			// set the target fragment to receive cancel notification
			mProgressDialog.setTargetFragment(MapFragment.this,REQUEST_CODE_PROGRESS_DIALOG);
			mProgressDialog.show(getActivity().getFragmentManager(),TAG_REVERSE_GEOCODING_PROGRESS_DIALOG);
		}

		@Override
		protected LocatorReverseGeocodeResult doInBackground(Point... params) {
			// Perform reverse geocoding request on background thread
			mException = null;
			LocatorReverseGeocodeResult result = null;
			mPoint = params[0];

			// Create locator using default online geocoding service and tell it to find the given point
			Locator locator = Locator.createOnlineLocator(getString(R.string.geocodeservice_url));
			try {
				// Our input and output spatial reference will be the same as the map
				@SuppressWarnings("ResourceType") SpatialReference mapRef = mMapView.getSpatialReference();
				result = locator.reverseGeocode(mPoint, 100.0, mapRef, mapRef);
				mLocationLayerPoint = mPoint;
			} catch (Exception e) {
				mException = e;
			}
			// return the resulting point(s)
			return result;
		}

		@Override
		protected void onPostExecute(LocatorReverseGeocodeResult result) {
			// Display results on UI thread
			mProgressDialog.dismiss();
			if (mException != null) {
				Log.w(TAG, "LocatorSyncTask failed with:");
				mException.printStackTrace();
				Toast.makeText(getActivity(),getString(R.string.addressSearchFailed),Toast.LENGTH_LONG).show();
				return;
			}

			// Construct a nicely formatted address from the results
			StringBuilder address = new StringBuilder();
			if (result != null && result.getAddressFields() != null) {
				Map<String, String> addressFields = result.getAddressFields();
				address.append(String.format("%s\n", addressFields.get("SingleKey")));

				// Draw marker on map.
				// create marker symbol to represent location
				Drawable drawable = getActivity().getResources().getDrawable(R.drawable.pin_circle_red);
				PictureMarkerSymbol symbol = new PictureMarkerSymbol(getActivity(), drawable);
				mLocationLayer.addGraphic(new Graphic(mPoint, symbol));

				// Address string is saved for use in routing
				mLocationLayerPointString = address.toString();
				// center the map to result location
				mMapView.centerAt(mPoint, true);

				// Show the result on the search result layout
				showSearchResultLayout(address.toString());
			}
		}
	}

	/**
	 * Converts device specific pixels to density independent pixels.
	 * 
	 * @param context
	 * @param px
	 *            number of device specific pixels
	 * @return number of density independent pixels
	 */
	private float convertPixelsToDp(Context context, float px) {
		DisplayMetrics metrics = context.getResources().getDisplayMetrics();
		float dp = px / (metrics.densityDpi / 160f);
		return dp;
	}

    /**
     * Changes the String Values for the Menu.
     */

    public String getChangeSoundFlag() {
        return mSoundActive;
    }

    public void setChangeSoundFlag(String mSoundActive) {
        this.mSoundActive = mSoundActive;
    }


}
