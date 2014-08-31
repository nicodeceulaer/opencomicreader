package com.sketchpunk.ocomicreader;

import java.sql.SQLException;

import sage.adapter.DrawerFilterAdapter;
import sage.data.DatabaseHelper;
import sage.data.domain.Comic;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.stmt.QueryBuilder;
import com.runeai.runereader.R;
import com.sketchpunk.ocomicreader.lib.ComicLibrary;
import com.sketchpunk.ocomicreader.ui.CoverGridView;

public class LibraryActivity extends FragmentActivity implements ComicLibrary.SyncCallback, CoverGridView.iCallback {

	private CoverGridView mGridView;
	private ProgressDialog mProgress;
	private RuntimeExceptionDao<Comic, Integer> comicDao;
	private DrawerLayout mDrawerLayout;
	private View mFiltersDrawer;
	private ActionBarDrawerToggle mDrawerToggle;
	private ListView readFilterList;
	private ListView seriesFilterList;
	private TextView readFilterTitle;
	private CharSequence mTitle;
	private String[] readFilters;
	private String[] seriesFilters;
	private SharedPreferences prefs;
	private long backPressed;

	/*
	 * ======================================================== Main
	 */
	@Override
	public void onDestroy() {
		mGridView.dispose();
		super.onDestroy();
	}// func

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_library);
		overridePendingTransition(R.anim.fadein, R.anim.fadeout);

		mTitle = getTitle();

		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		mFiltersDrawer = findViewById(R.id.filters_drawer);

		seriesFilterList = (ListView) findViewById(R.id.series_filter);
		seriesFilters = this.getResources().getStringArray(R.array.libraryFilter);
		seriesFilterList.setAdapter(new DrawerFilterAdapter(this, R.layout.list_item, seriesFilters));
		SeriesFilterClickListener seriesFilterClickListener = new SeriesFilterClickListener();
		seriesFilterList.setOnItemClickListener(seriesFilterClickListener);

		readFilterList = (ListView) findViewById(R.id.read_filter);
		readFilterTitle = (TextView) findViewById(R.id.filter_by_read);
		readFilters = this.getResources().getStringArray(R.array.readFilter);
		readFilterList.setAdapter(new DrawerFilterAdapter(this, R.layout.list_item, readFilters));
		ReadFilterClickListener readFilterClickListener = new ReadFilterClickListener();
		readFilterList.setOnItemClickListener(readFilterClickListener);

		mGridView = (CoverGridView) findViewById(R.id.lvMain);

		mDrawerToggle = new ActionBarDrawerToggle(this, /* host Activity */
		mDrawerLayout, /* DrawerLayout object */
		R.drawable.ic_drawer, /* nav drawer icon to replace 'Up' caret */
		R.string.drawer_open, /* "open drawer" description */
		R.string.drawer_close /* "close drawer" description */
		) {

			/** Called when a drawer has settled in a completely closed state. */
			@Override
			public void onDrawerClosed(View view) {
				super.onDrawerClosed(view);
				generateTitle();
			}

			/** Called when a drawer has settled in a completely open state. */
			@Override
			public void onDrawerOpened(View drawerView) {
				super.onDrawerOpened(drawerView);
			}
		};

		// Set the drawer toggle as the DrawerListener
		mDrawerLayout.setDrawerListener(mDrawerToggle);

		// ....................................
		// Load state of filter from Bundle
		if (savedInstanceState != null) {
			mGridView.setSeriesFilter(savedInstanceState.getString("mSeriesFilter"));
			mGridView.setSeriesFilterMode(savedInstanceState.getInt("mSeriesFilterMode"));
			mGridView.setReadFilterMode(savedInstanceState.getInt("mReadFilterMode"));
		} else {// if no state, load in default pref.
			mGridView.setSeriesFilterMode(prefs.getInt("seriesFilter", 0));
			mGridView.setReadFilterMode(prefs.getInt("readFilter", 0));
		}// if

		seriesFilterList.setItemChecked(mGridView.getSeriesFilterMode(), true);
		readFilterList.setItemChecked(mGridView.getReadFilterMode(), true);
		setReadFilterVisibility();
		generateTitle();

		// ....................................
		// int barHeight =
		// ((RelativeLayout)findViewById(R.id.topBar)).getHeight();
		mGridView.init();
		registerForContextMenu(mGridView); // Route event from Activity to View

		getActionBar().setDisplayHomeAsUpEnabled(true);
		getActionBar().setHomeButtonEnabled(true);

		showNavigationDrawerOnFirstRun();

	}// func

	private void showNavigationDrawerOnFirstRun() {
		boolean isFirstRun = prefs.getBoolean("isFirstRun", true);
		if (isFirstRun) {
			mDrawerLayout.openDrawer(mFiltersDrawer);
			noteFirstRunIsDone();
		}
	}

	private void noteFirstRunIsDone() {
		Editor e = prefs.edit();
		e.putBoolean("isFirstRun", false);
		e.commit();
	}

	protected void generateTitle() {
		int seriesFilterMode = mGridView.getSeriesFilterMode();
		int readingFilterMode = mGridView.getReadFilterMode();
		String seriesFilter = mGridView.getSeriesFilter();

		getActionBar().setSubtitle(null);
		if (seriesFilterMode <= 0) {
			if (readingFilterMode > 0) {
				getActionBar().setTitle(readFilters[readingFilterMode]);
			} else {
				getActionBar().setTitle(mTitle);
			}
		} else {
			if (seriesFilterMode == 1 && seriesFilter.isEmpty()) {
				getActionBar().setTitle(seriesFilters[seriesFilterMode]);
			} else {
				if (!seriesFilter.isEmpty()) {
					getActionBar().setTitle(seriesFilter);
				} else {
					getActionBar().setTitle(seriesFilters[seriesFilterMode]);
				}
				generateSubtitle(readingFilterMode);
			}
		}
	}

	private void generateSubtitle(int readingFilterMode) {
		if (readingFilterMode > 0) {
			getActionBar().setSubtitle(readFilters[readingFilterMode]);
		}
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		// Sync the toggle state after onRestoreInstanceState has occurred.
		mDrawerToggle.syncState();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		mDrawerToggle.onConfigurationChanged(newConfig);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.activity_main, menu);

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (mDrawerToggle.onOptionsItemSelected(item)) {
			return true;
		}

		switch (item.getItemId()) {
		case R.id.menu_last:
			goToLastComic();
			return true;
		case R.id.menu_import:
			showSyncDialog();
			return true;
		case R.id.menu_settings:
			Intent intent = new Intent(this, PrefActivity.class);
			this.startActivityForResult(intent, 0);
			return true;
		case R.id.menu_about:
			sage.ui.Dialogs.About(this, this.getText(R.string.app_about));
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle siState) {
		// Save the state of the filters so
		siState.putString("mSeriesFilter", mGridView.getSeriesFilter());
		siState.putInt("mSeriesFilterMode", mGridView.getSeriesFilterMode());
		siState.putInt("mReadFilterMode", mGridView.getReadFilterMode());
		super.onSaveInstanceState(siState);
	}// func

	// @Override
	// public boolean onCreateOptionsMenu(Menu menu) { //Todo:remove
	// getMenuInflater().inflate(R.menu.activity_main, menu);
	// return true;
	// }//func

	/*
	 * ======================================================== State
	 */
	@Override
	public void onPause() {
		mGridView.onPause();
		super.onPause();
	}// func

	@Override
	public void onResume() {
		super.onResume();
		// seriesFilterList.performItemClick(seriesFilterList.getAdapter().getView(0, null, null), 0, seriesFilterList.getAdapter().getItemId(0));
		mGridView.refreshData();
	}// func

	/*
	 * ======================================================== Context menu
	 */
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		switch (v.getId()) {
		case R.id.lvMain:
			mGridView.createContextMenu(menu, v, menuInfo);
			break;
		}// switch
	}// func

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		mGridView.contextItemSelected(item);
		return true;
	}// func

	/*
	 * ======================================================== UI Events
	 */
	@Override
	public void onBackPressed() {
		// Override back press to make it easy to back out of series filter.
		if (mGridView.isSeriesFiltered() && mGridView.getSeriesFilter() != "") {
			mGridView.setSeriesFilter("");
			mGridView.refreshData();
			generateTitle();
		} else if (mDrawerLayout.isDrawerOpen(mFiltersDrawer)) {
			mDrawerLayout.closeDrawer(mFiltersDrawer);
		} else {
			if (backPressed + 2000 > System.currentTimeMillis()) {
				super.onBackPressed();
			} else {
				Toast.makeText(getBaseContext(), R.string.press_again_to_exit, Toast.LENGTH_SHORT).show();
				backPressed = System.currentTimeMillis();
			}
		}
	}// func

	private void getComicDao(Context context) {
		if (comicDao == null) {
			DatabaseHelper databaseHelper = OpenHelperManager.getHelper(context, DatabaseHelper.class);
			comicDao = databaseHelper.getRuntimeExceptionDao(Comic.class);
		}
	}

	private void showSyncDialog() {
		sage.ui.Dialogs.ConfirmBox(this, getString(R.string.sync_library_popup_title), getString(R.string.sync_library_popup_message),
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						startSync();
					}
				});
	}

	private void goToLastComic() {
		getComicDao(getApplicationContext());

		QueryBuilder<Comic, Integer> lastComicQuery = comicDao.queryBuilder();
		Comic lastRead = null;
		try {
			lastRead = lastComicQuery.orderBy("dateRead", false).where().isNotNull("dateRead").queryForFirst();
		} catch (SQLException e) {
			Log.e("sql", "Couldn't find last read comic " + e.getMessage());
		}

		OpenHelperManager.releaseHelper();

		if (lastRead != null) {
			Intent intent = new Intent(getApplicationContext(), ViewActivity.class);
			intent.putExtra("comicid", lastRead.getId());
			this.startActivityForResult(intent, 0);
		} else {
			Toast.makeText(this, getString(R.string.read_something_first), Toast.LENGTH_SHORT).show();
		}
	}

	/*
	 * ======================================================== Sync Library
	 */
	private void startSync() {
		if (ComicLibrary.startSync(this)) {
			if (mProgress != null) {
				if (!mProgress.isShowing()) {
					mProgress.show(this, getString(R.string.library_syncing), "", true);
					return;
				}// if
			}// if

			mProgress = ProgressDialog.show(this, getString(R.string.library_syncing), "", true);
		} else {
			Toast.makeText(this, getString(R.string.library_syncing_failed_to_start), Toast.LENGTH_SHORT).show();
		}// if
	}// func

	// @Override
	@Override
	public void onSyncProgress(String txt) {
		if (mProgress != null) {
			if (mProgress.isShowing())
				mProgress.setMessage(txt);
		}// if
	}// func

	// @Override
	@Override
	public void onSyncComplete(int status) {
		// ............................................
		try {
			if (mProgress != null) {
				mProgress.dismiss();
				mProgress = null;
			}// if
		} catch (Exception e) {
			Toast.makeText(this, getString(R.string.error_closing_progress_dialog), Toast.LENGTH_LONG).show();
		}// try

		// ............................................
		switch (status) {
		case ComicLibrary.STATUS_COMPLETE:
			mGridView.refreshData();
			break;
		case ComicLibrary.STATUS_NOSETTINGS:
			Toast.makeText(this, getString(R.string.set_library_folders_first), Toast.LENGTH_LONG).show();
			break;
		}// switch
	}// func

	/* ======================================================== */
	// @Override
	@Override
	public void onDataRefreshComplete() {
		if (mGridView.isSeriesFiltered()) {// Filter by series
			if (!mGridView.getSeriesFilter().isEmpty()) {
				generateTitle();
				setReadFilterVisibility();
			}// if
		}// if
	}// func

	private void setReadFilterVisibility() {
		if (mGridView.getSeriesFilterMode() == 1 && (mGridView.getSeriesFilter() == null || mGridView.getSeriesFilter().isEmpty())) {
			// readFilterList.setEnabled(false);
			// readFilterTitle.setEnabled(false);
		} else {
			// readFilterList.setEnabled(true);
			// readFilterTitle.setEnabled(true);
		}
	}

	private class SeriesFilterClickListener implements OnItemClickListener {
		private final View previousView = null;

		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			if (previousView != null) {
				previousView.setPressed(false);
			}
			view.setPressed(true);
			prefs.edit().putInt("seriesFilter", position).commit();
			selectItem(position);
		}

		private void selectItem(int position) {
			if (mGridView.getSeriesFilterMode() != position) {// initially, refreshdata gets called twice,its a waste.
				mGridView.setSeriesFilterMode(position);
				mGridView.refreshData();
			}

			setReadFilterVisibility();

			mDrawerLayout.closeDrawer(mFiltersDrawer);
		}
	}

	private class ReadFilterClickListener implements OnItemClickListener {
		private View previousView = null;

		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			if (previousView != null) {
				previousView.setPressed(false);
			}
			view.setPressed(true);
			previousView = view;
			prefs.edit().putInt("readFilter", position).commit();
			selectItem(position);
		}

		private void selectItem(int position) {
			if (mGridView.getReadFilterMode() != position) {// initially, refreshdata gets called twice,its a waste.
				mGridView.setReadFilterMode(position);
				mGridView.refreshData();
			}

			mDrawerLayout.closeDrawer(mFiltersDrawer);
		}
	}
}// cls
