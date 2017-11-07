package chaos.list;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.support.v4.widget.DrawerLayout;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.estimote.coresdk.common.config.EstimoteSDK;
import com.estimote.coresdk.common.requirements.SystemRequirementsChecker;
import com.estimote.coresdk.observation.region.beacon.BeaconRegion;
import com.estimote.coresdk.recognition.packets.Beacon;
import com.estimote.coresdk.service.BeaconManager;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class MainActivity extends ActionBarActivity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks {

    //Create Objects.
    private ListView myList;
    private TextView closestBeaconTV;
    private ListAdapter todoListAdapter;
    private TodoListSQLHelper todoListSQLHelper;

    // Estimote SDK Objects for Ranging
    private BeaconManager beaconManager;
    private BeaconRegion region;
    private Beacon highestBeacon;



    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */
    private NavigationDrawerFragment mNavigationDrawerFragment;

    /**
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */
    private CharSequence mTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        beaconManager = new BeaconManager(this);
        EstimoteSDK.enableDebugLogging(true);
        region = new BeaconRegion("ranged region", UUID.fromString("4e6ed5ab-b3ed-4e10-8247-c5f5524d4b21"), 12, null);

        closestBeaconTV = (TextView) findViewById(R.id.preview_beacon);
        beaconManager.setForegroundScanPeriod(200,0);
        beaconManager.setRangingListener(new BeaconManager.BeaconRangingListener() {
            @Override
            public void onBeaconsDiscovered(BeaconRegion region, List<Beacon> list) {
                if (!list.isEmpty()) {
                    highestBeacon = list.get(0);
                    closestBeaconTV.setText("Minor: " + String.valueOf(highestBeacon.getMinor()) + "  RSSI: "  + String.valueOf(highestBeacon.getRssi()));
                }
            }
        });

        beaconManager.startRanging(region);

        myList = (ListView) findViewById(R.id.list);
        ImageButton fabImageButton = (ImageButton) findViewById(R.id.fab_image_button);

        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
        mTitle = getTitle();

        // Set up the drawer.
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout));
        final ArrayList<String> list = new ArrayList<>();
        final MyCustomAdapter adapter = new MyCustomAdapter(this, list);
        SwipeDismissListViewTouchListener touchListener =
                new SwipeDismissListViewTouchListener(
                        (ListView) findViewById(R.id.list),
                        new SwipeDismissListViewTouchListener.DismissCallbacks() {
                            @Override
                            public boolean canDismiss(int position) {
                                return true;
                            }

                            @Override
                            public void onDismiss(ListView listView, int[] reverseSortedPositions) {
                                for (int position : reverseSortedPositions) {

                                    String deleteTodoItemSql = "DELETE FROM " + TodoListSQLHelper.TABLE_NAME +
                                            " WHERE " + TodoListSQLHelper._ID+ " = '" + todoListAdapter.getItemId(position) + "'";

                                    todoListSQLHelper = new TodoListSQLHelper(MainActivity.this);
                                    SQLiteDatabase sqlDB = todoListSQLHelper.getWritableDatabase();
                                    sqlDB.execSQL(deleteTodoItemSql);
                                    updateTodoList();

                                }
                            }

                        });
        findViewById(R.id.list).setOnTouchListener(touchListener);
        // Setting this scroll listener is required to ensure that during ListView scrolling,
        // we don't look for swipes.

        // findViewById(R.id.list).setOnScrollListener(touchListener.makeScrollListener());

        fabImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (highestBeacon != null) {
                    Intent intent = new Intent(MainActivity.this, beaconInfo.class);
                    int minor = highestBeacon.getMinor();
                    int major = highestBeacon.getMajor();
                    intent.putExtra("minorInt", minor);
                    intent.putExtra("majorInt", major);
                    startActivity(intent);
                }
            }
        });

        // Aquí checamos si hay información en el intent, que quiere decir que venimos de beacon info
        final int zone = getIntent().getIntExtra("zoneInt", -1);
        final int minor = getIntent().getIntExtra("minorInt", -1);
        // Si tenemos una zona, entonces la agregamos a la lista
        if (zone != -1) {
            list.add("New Item");
            adapter.notifyDataSetChanged();

            todoListSQLHelper = new TodoListSQLHelper(MainActivity.this);
            SQLiteDatabase sqLiteDatabase = todoListSQLHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.clear();

            //write the Todo task input into database table
            String testeroonee = "Zone: " + String.valueOf(zone) + " | Minor: " + String.valueOf(minor);
            values.put(TodoListSQLHelper.COL1_TASK, testeroonee);
            sqLiteDatabase.insertWithOnConflict(TodoListSQLHelper.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_IGNORE);

            updateTodoList();
        }

        //show the ListView on the screen
        // The adapter MyCustomAdapter is responsible for maintaining the data backing this list and for producing
        // a view to represent an item in that data set.

        updateTodoList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SystemRequirementsChecker.checkWithDefaultDialogs(this);
        beaconManager.connect(new BeaconManager.ServiceReadyCallback() {
            @Override
            public void onServiceReady() {
                beaconManager.startRanging(region);
            }
        });
    }

    // Template del SDK
    @Override
    protected void onPause() {
        beaconManager.stopRanging(region);
        super.onPause();
    }

    @Override
    public void onNavigationDrawerItemSelected(int position) {
        // update the main content by replacing fragments
        FragmentManager fragmentManager = getSupportFragmentManager();
        switch (position){
            case 0:
                fragmentManager.beginTransaction()
                        .replace(R.id.container, PlaceholderFragment.newInstance(position + 1))
                        .commit();
                break;
            case 1:
                Intent intent = new Intent(this, About.class);
                startActivity(intent);
                break;
        }
    }

    public void onSectionAttached(int number) {
        switch (number) {
            case 1:
                mTitle = getString(R.string.title_section1);
                break;
            case 2:
                mTitle = getString(R.string.title_section2);
                break;
        }
    }

    public void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            getMenuInflater().inflate(R.menu.main, menu);
            restoreActionBar();
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            return rootView;
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            ((MainActivity) activity).onSectionAttached(
                    getArguments().getInt(ARG_SECTION_NUMBER));
        }
    }
    private void updateTodoList() {
        todoListSQLHelper = new TodoListSQLHelper(MainActivity.this);
        SQLiteDatabase sqLiteDatabase = todoListSQLHelper.getReadableDatabase();

        //cursor to read todo task list from database
        Cursor cursor = sqLiteDatabase.query(TodoListSQLHelper.TABLE_NAME,
                new String[]{TodoListSQLHelper._ID, TodoListSQLHelper.COL1_TASK},
                null, null, null, null, null);

        //binds the todo task list with the UI
        todoListAdapter = new SimpleCursorAdapter(
                this,
                R.layout.due,
                cursor,
                new String[]{TodoListSQLHelper.COL1_TASK},
                new int[]{R.id.due_text_view},
                0
        );

        myList.setAdapter(todoListAdapter);
    }

    //closing the todo task item
    public void onDoneButtonClick(View view) {
        View v = (View) view.getParent();
        TextView todoTV = (TextView) v.findViewById(R.id.due_text_view);
        String todoTaskItem = todoTV.getText().toString();

        String deleteTodoItemSql = "DELETE FROM " + TodoListSQLHelper.TABLE_NAME +
                " WHERE " + TodoListSQLHelper.COL1_TASK + " = '" + todoTaskItem + "'";

        todoListSQLHelper = new TodoListSQLHelper(MainActivity.this);
        SQLiteDatabase sqlDB = todoListSQLHelper.getWritableDatabase();
        sqlDB.execSQL(deleteTodoItemSql);
        updateTodoList();
        sqlDB.close();
    }

}
