/**
 * Copyright 2010 Malcolm Locke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package nz.co.wholemeal.christchurchmetro;

import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.net.URL;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

public class PreferencesActivity extends PreferenceActivity {

  public static String TAG = "PreferencesActivity";
  public static String PLATFORMS_URL = "http://rtt.metroinfo.org.nz/RTT/Public/Utility/File.aspx?ContentType=SQLXML&Name=JPPlatform.xml";
  public static String ROUTE_PATTERN_URL = "http://rtt.metroinfo.org.nz/RTT/Public/Utility/File.aspx?ContentType=SQLXML&Name=JPRoutePattern.xml";
  //public static String PLATFORMS_URL = "http://10.0.2.2/~malc/JPPlatform.xml";
  //public static String ROUTE_PATTERN_URL = "http://10.0.2.2/~malc/JPRoutePattern.xml";

  @Override
  protected void onCreate(Bundle savedInstanceState) {

    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.preferences);


    /* Update the data from the API */
    Preference updateRoutes = (Preference)findPreference("updateRoutes");
    updateRoutes.setOnPreferenceClickListener(new OnPreferenceClickListener() {

      public boolean onPreferenceClick(Preference preference) {
        new AsyncLoadPlatforms().execute();
        return true;
      }

    });
  }

  /* Loads the platform data in a background thread. */
  public class AsyncLoadPlatforms extends AsyncTask<Void, Integer, String> {

    ProgressDialog progressDialog;

    @Override
    protected void onPreExecute() {
      progressDialog = new ProgressDialog(PreferencesActivity.this);
      progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
      progressDialog.setCancelable(false);

      // FIXME This is the total number of patterns at time of writing,
      // but is likely to change over time, although probably not by much.
      progressDialog.setMax(2600);
      progressDialog.setMessage(getString(R.string.loading_platforms));
      progressDialog.show();
    }

    @Override
    protected void onPostExecute(String message) {
      progressDialog.dismiss();
      Toast.makeText(getBaseContext(), message,
          Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onProgressUpdate(Integer... progress) {
      super.onProgressUpdate(progress);

      // This is a special value, and means the import mode has
      // progressed from platforms to patterns
      if (progress[0] == -1) {
        progressDialog.setMax(124);
        progressDialog.setMessage(getString(R.string.loading_routes));
      } else {
        progressDialog.setProgress(progress[0]);
      }
    }

    @Override
    protected String doInBackground(Void... voids) {
      Log.d(TAG, "Loading platforms");

      PlatformHandler platformHandler = null;
      PatternHandler patternHandler = null;
      DatabaseHelper databaseHelper = new DatabaseHelper(getApplicationContext());
      SQLiteDatabase database = databaseHelper.getWritableDatabase();


      try {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        SAXParser sp = spf.newSAXParser();
        XMLReader xr = sp.getXMLReader();
        URL source = new URL(PLATFORMS_URL);
        platformHandler = new PlatformHandler();
        platformHandler.database = database;
        xr.setContentHandler(platformHandler);
        database.beginTransaction();
        database.delete("platforms", null, null);
        xr.parse(new InputSource(source.openStream()));
        database.setTransactionSuccessful();
      } catch (SQLiteException e) {
        Log.e(TAG, "SQLiteException", e);
      } catch (Exception e) {
        Log.e(TAG, "Exception", e);
      } finally {
        database.endTransaction();
      }

      // Tell the progress bar that we're switching from platforms to patterns
      publishProgress(-1);

      try {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        SAXParser sp = spf.newSAXParser();
        XMLReader xr = sp.getXMLReader();
        URL source = new URL(ROUTE_PATTERN_URL);
        patternHandler = new PatternHandler();
        patternHandler.database = database;
        xr.setContentHandler(patternHandler);
        database.beginTransaction();
        database.delete("patterns", null, null);
        database.delete("patterns_platforms", null, null);
        xr.parse(new InputSource(source.openStream()));
        database.setTransactionSuccessful();
      } catch (SQLiteException e) {
        Log.e(TAG, "SQLiteException", e);
      } catch (Exception e) {
        Log.e(TAG, "Exception", e);
      } finally {
        database.endTransaction();
      }

      if (platformHandler != null && patternHandler != null) {

        SharedPreferences preferences =
          getSharedPreferences(PlatformActivity.PREFERENCES_FILE, 0);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putLong("lastDataLoad", System.currentTimeMillis());
        editor.commit();

        return getString(R.string.loaded_route_information);

      } else {
        return getString(R.string.error_loading_bus_routes);
      }
    }

    private class PlatformHandler extends DefaultHandler {

      public Integer platformCount = 0;
      public SQLiteDatabase database = null;
      private ContentValues values = null;

      public void startElement(String uri, String localName, String qName,
          Attributes attributes) throws SAXException {
        if (localName.equals("Platform")) {
          if( values == null) {
            values = new ContentValues();
          }
          values.put("platform_tag", attributes.getValue("PlatformTag"));
          values.put("platform_number", attributes.getValue("PlatformNo"));
          values.put("name", attributes.getValue("Name"));
          values.put("road_name", attributes.getValue("RoadName"));
        } else if (localName.equals("Position")) {
          values.put("latitude", Double.valueOf(attributes.getValue("Lat")));
          values.put("longitude", Double.valueOf(attributes.getValue("Long")));
        }
      }

      public void endElement(String uri, String localName, String qName)
        throws SAXException {
        if (localName.equals("Platform")) {
          if (database != null) {
            database.insert("platforms", null, values);
            platformCount++;

            values.putNull("platform_tag");
            values.putNull("platform_number");
            values.putNull("name");
            values.putNull("road_name");
            values.putNull("latitude");
            values.putNull("longitude");

            if ((platformCount % 10) == 0) {
              publishProgress(platformCount);
            }
          }
        }
      }
    }

    private class PatternHandler extends DefaultHandler {

      public Integer patternCount = 0;
      public SQLiteDatabase database = null;
      private ContentValues patternValues = null;
      private ContentValues patternPlatformsValues = null;

      public void startElement(String uri, String localName, String qName,
          Attributes attributes) throws SAXException {
        if (localName.equals("Route")) {
          if( patternValues == null) {
            patternValues = new ContentValues();
          }
          patternValues.put("route_number", attributes.getValue("RouteNo"));
          patternValues.put("route_name", attributes.getValue("Name"));
        } else if (localName.equals("Destination")) {
          patternValues.put("destination", attributes.getValue("Name"));
        } else if (localName.equals("Pattern")) {
          if(patternPlatformsValues == null) {
            patternPlatformsValues = new ContentValues();
          }
          patternValues.put("route_tag", attributes.getValue("RouteTag"));
          patternPlatformsValues.put("route_tag",
              attributes.getValue("RouteTag"));
          patternValues.put("pattern_name", attributes.getValue("Name"));
          patternValues.put("direction", attributes.getValue("Direction"));
          patternValues.put("length",
              Integer.parseInt(attributes.getValue("Length")));
          patternValues.put("active",
              attributes.getValue("Schedule").equals("Active") ? true : false);
        } else if (localName.equals("Platform")) {
          patternPlatformsValues.put("platform_tag",
              attributes.getValue("PlatformTag"));
          if (attributes.getValue("ScheduleAdheranceTimepoint") != null) {
            patternPlatformsValues.put("schedule_adherance_timepoint",
                attributes.getValue("ScheduleAdheranceTimepoint").equals("true") ? true : false);
          } else {
            patternPlatformsValues.put("schedule_adherance_timepoint", false);
          }
        }
      }

      public void endElement(String uri, String localName, String qName)
        throws SAXException {
        if (localName.equals("Platform")) {
          if (database != null) {
            database.insert("patterns_platforms", null, patternPlatformsValues);

            patternPlatformsValues.putNull("platform_tag");
            patternPlatformsValues.putNull("schedule_adherance_timepoint");
          }
        } else if (localName.equals("Pattern")) {
          if (database != null) {
            database.insert("patterns", null, patternValues);
            patternCount++;

            patternValues.putNull("route_tag");
            patternPlatformsValues.putNull("route_tag");
            patternValues.putNull("pattern_name");
            patternValues.putNull("direction");
            patternValues.putNull("length");
            patternValues.putNull("active");

            publishProgress(patternCount);
          }
        } else if (localName.equals("Route")) {
          if (database != null) {
            patternValues.putNull("route_number");
            patternValues.putNull("route_name");
          }
        }
      }
    }
  }
}