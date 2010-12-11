package nz.co.wholemeal.christchurchmetro;

import java.util.ArrayList;
import java.util.Iterator;

import android.app.ListActivity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import android.text.Editable;
import android.text.Html;
import android.text.Spanned;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.util.Log;

import nz.co.wholemeal.christchurchmetro.R;
import nz.co.wholemeal.christchurchmetro.Stop;

import org.json.JSONArray;

public class ChristchurchMetroActivity extends ListActivity
{
  private EditText entry;

  private Stop current_stop;
  private ArrayList arrivals = new ArrayList<Arrival>();
  private ArrivalAdapter arrival_adapter;
  private View stopHeader;
  private Button addToFavourites;

  static final int CHOOSE_FAVOURITE = 0;
  static final String TAG = "ChristchurchMetroActivity";

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.stop);

    stopHeader = getLayoutInflater().inflate(R.layout.stop_header, null);
    getListView().addHeaderView(stopHeader);

    addToFavourites = (Button)getLayoutInflater().inflate(R.layout.add_to_favourites, null);
    getListView().addFooterView((View)addToFavourites);
    addToFavourites.setEnabled(false);
    addToFavourites.setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        if (current_stop != null) {
          addToFavourites(current_stop);
        }
      }
    });

    arrival_adapter = new ArrivalAdapter(this, R.layout.list_item, arrivals);
    setListAdapter(arrival_adapter);

    current_stop = null;

    final Button go_button = (Button)findViewById(R.id.go);
    go_button.setEnabled(false);
    go_button.setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        String stop_number = entry.getText().toString();
        if (stop_number.length() == 5) {
          Stop stop = new Stop(stop_number);
          loadStop(stop);
        } else {
          Log.d(TAG, "go_button.onClick(): entry text incorrect length");
        }
      }
    });

    entry = (EditText)findViewById(R.id.entry);
    entry.addTextChangedListener(new TextWatcher() {
      /* The go button should only be enabled when there are 5 characters
       * in the stop number text entry */
      public void afterTextChanged(Editable s) {
        go_button.setEnabled(s.length() == 5);
      }

      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      public void onTextChanged(CharSequence s, int start, int before, int count) {
      }
    });


    final Button faves_button = (Button)findViewById(R.id.faves);
    faves_button.setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        Intent intent = new Intent(ChristchurchMetroActivity.this,
          FavouritesActivity.class);
        ChristchurchMetroActivity.this.startActivityForResult(intent, CHOOSE_FAVOURITE);
      }
    });
  }

  @Override
  protected void onStop() {
    super.onStop();

    ArrayList stops = FavouritesActivity.stops;

    if (!stops.isEmpty()) {
      SharedPreferences favourites = getSharedPreferences(FavouritesActivity.FAVOURITES_FILE, 0);
      SharedPreferences.Editor editor = favourites.edit();
      JSONArray stopArray = new JSONArray();
      Iterator iterator = stops.iterator();
      while (iterator.hasNext()) {
        Stop stop = (Stop)iterator.next();
        stopArray.put(stop.toJSONObject());
      }
      editor.putString("favouriteStops", stopArray.toString());
      editor.commit();
    }
  }

  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    Log.d(TAG, "Activity returned resultCode = " + resultCode);
    switch (requestCode) {
      case CHOOSE_FAVOURITE:
        if (resultCode != RESULT_CANCELED) {
          Bundle extras = data.getExtras();
          if (extras != null) {
            Log.d(TAG, "stop " + extras.getString("platformNumber") + " selected");
            Stop stop = new Stop(extras.getString("platformNumber"));
            entry.setText(extras.getString("platformNumber"));
            loadStop(stop);
          }
        }

      default:
        break;
    }
  }

  public void loadStop(Stop stop) {
    Log.d(TAG, "loadStop(): " + stop.getPlatformNumber());
    current_stop = stop;
    setStopHeader(stop);
    ArrayList stopArrivals = stop.getArrivals();
    if (stopArrivals.size() > 0) {
      Log.d(TAG, "arrivals.size() = " + arrivals.size());
      arrivals.clear();
      arrivals.addAll(stopArrivals);
      arrival_adapter.notifyDataSetChanged();
    }

    /* Set the add to favourites button state based on whether the stop is a
     * favourite or not */
    addToFavourites.setEnabled(!FavouritesActivity.isFavourite(stop));
  }

  public void loadStop(String platformNumber) {
    loadStop(new Stop(platformNumber));
  }

  public void setStopHeader(Stop stop) {
    TextView platformNumber = (TextView)stopHeader.findViewById(R.id.platform_number);
    TextView platformName = (TextView)stopHeader.findViewById(R.id.platform_name);
    TextView platformRoutes = (TextView)stopHeader.findViewById(R.id.platform_routes);
    platformNumber.setText(stop.getPlatformNumber());
    platformName.setText(stop.getName());
    platformRoutes.setText("Routes: " + stop.getRoutes());
  }

  public void addToFavourites(Stop stop) {
    Log.d(TAG, "addToFavourites(): " + stop.getPlatformNumber());

    if (! FavouritesActivity.isFavourite(stop)) {
      FavouritesActivity.stops.add(stop);
      Toast.makeText(getApplicationContext(), "Added '" + stop.getName() +
          "' to favourites", Toast.LENGTH_SHORT).show();
      addToFavourites.setEnabled(false);
    }
  }

  private class ArrivalAdapter extends ArrayAdapter<Arrival> {

    private ArrayList<Arrival> arrivalList;

    public ArrivalAdapter(Context context, int textViewResourceId, ArrayList<Arrival> items) {
      super(context, textViewResourceId, items);
      arrivalList = items;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      View v = convertView;
      if (v == null) {
        LayoutInflater vi = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        v = vi.inflate(R.layout.arrival, null);
      }
      Arrival arrival = arrivalList.get(position);
      if (arrival != null) {
        TextView routeNumber = (TextView) v.findViewById(R.id.route_number);
        TextView routeName = (TextView) v.findViewById(R.id.route_name);
        TextView eta = (TextView) v.findViewById(R.id.eta);
        if (routeNumber != null) {
          routeNumber.setText(arrival.getRouteNumber());
        }
        if (routeName != null) {
          routeName.setText(arrival.getRouteName());
        }
        if (eta != null) {
          eta.setText(arrival.getEta() + " minutes");
        }
      }
      return v;
    }
  }
}
