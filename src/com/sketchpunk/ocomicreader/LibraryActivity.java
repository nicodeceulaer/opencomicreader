package com.sketchpunk.ocomicreader;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import sage.Util;
import sage.adapter.LibraryDrawerAdapter;
import sage.data.DatabaseHelper;
import sage.data.domain.Comic;
import android.app.ActionBar;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.Toast;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.stmt.QueryBuilder;
import com.runeai.runereader.R;
import com.sketchpunk.ocomicreader.fragments.ComicGridFragment;
import com.sketchpunk.ocomicreader.fragments.WelcomeFragment;
import com.sketchpunk.ocomicreader.lib.ComicLibrary;
import com.sketchpunk.ocomicreader.ui.CoverGridView;

public class LibraryActivity extends FragmentActivity implements ComicLibrary.SyncCallback, CoverGridView.iCallback {

	private static final int SERIES_GROUP = 0;
	private static final int READING_GROUP = 1;
	private ProgressDialog mProgress;
	private RuntimeExceptionDao<Comic, Integer> comicDao;
	private DrawerLayout mDrawerLayout;
	private View mFiltersDrawer;
	private ActionBarDrawerToggle mDrawerToggle;
	private String[] readFilters;
	private String[] seriesFilters;
	private SharedPreferences prefs;
	private long backPressed;
	DrawerChildClickListener drawerChildClickListener;
	ComicGridFragment comicGridFragment = null;

	/*
	 * ======================================================== Main
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_library);
		overridePendingTransition(R.anim.fadein, R.anim.fadeout);

		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		determineScreenDimensions();

		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		mFiltersDrawer = findViewById(R.id.filters_drawer);

		prepareDrawer();

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

		generateTitle();
		checkInitialDrawerOptions();

		getActionBar().setDisplayHomeAsUpEnabled(true);
		getActionBar().setHomeButtonEnabled(true);

		showNavigationDrawerOnFirstRun();

		changeToCorrectFragment();
	}// func

	private void determineScreenDimensions() {
		Editor edit = prefs.edit();
		Point realSize = Util.getRealSize(getWindowManager().getDefaultDisplay());
		int width = realSize.x < realSize.y ? realSize.x : realSize.y;
		int height = realSize.x > realSize.y ? realSize.x : realSize.y;
		edit.putInt("maxWidth", width);
		edit.putInt("maxHeight", height);
		edit.commit();
	}

	private void changeToCorrectFragment() {
		FragmentManager fragmentManager = getFragmentManager();
		FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

		if (noDataSynchronized()) {
			setWelcomeFragmentAsContent(fragmentTransaction);
		} else {
			setComicGridAsContent(fragmentTransaction);
		}

		fragmentTransaction.commit();
	}

	private void setComicGridAsContent(FragmentTransaction fragmentTransaction) {
		comicGridFragment = new ComicGridFragment();
		fragmentTransaction.add(R.id.library_content, comicGridFragment);
	}

	private void setWelcomeFragmentAsContent(FragmentTransaction fragmentTransaction) {
		WelcomeFragment fragment = new WelcomeFragment();
		fragmentTransaction.add(R.id.library_content, fragment);

		getActionBar().setTitle(R.string.app_name);
		getActionBar().setSubtitle(null);
	}

	private boolean noDataSynchronized() {
		getComicDao(getApplicationContext());
		long comicsCount = comicDao.countOf();
		OpenHelperManager.releaseHelper();
		return comicsCount <= 0;
	}

	ExpandableListView expListView;
	LibraryDrawerAdapter listAdapter;
	List<String> listDataHeader;
	HashMap<String, List<String>> listDataChild;

	private void prepareDrawer() {
		// get the listview
		expListView = (ExpandableListView) findViewById(R.id.drawer_content);

		// preparing list data
		prepareListData();

		listAdapter = new LibraryDrawerAdapter(this, listDataHeader, listDataChild);

		// setting list adapter
		expListView.setAdapter(listAdapter);
		expListView.setGroupIndicator(null);

		expandAllDrawerGroups();
		disableDrawerGroupsContracting();

		addDrawerChildClickListener();
	}

	private void checkInitialDrawerOptions() {
		if (comicGridFragment != null) {
			drawerChildClickListener.checkItem(SERIES_GROUP, comicGridFragment.getSeriesFilterMode());
			drawerChildClickListener.checkItem(READING_GROUP, comicGridFragment.getReadFilterMode());
		} else {
			drawerChildClickListener.checkItem(SERIES_GROUP, 0);
			drawerChildClickListener.checkItem(READING_GROUP, 0);
		}
	}

	private void addDrawerChildClickListener() {
		drawerChildClickListener = new DrawerChildClickListener();
		expListView.setOnChildClickListener(drawerChildClickListener);
	}

	private void disableDrawerGroupsContracting() {
		expListView.setOnGroupClickListener(new ExpandableListView.OnGroupClickListener() {

			@Override
			public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id) {
				return true;
			}
		});
	}

	private void expandAllDrawerGroups() {
		for (int i = 0; i < listDataHeader.size(); i++) {
			expListView.expandGroup(i);
		}
	}

	private void prepareListData() {
		listDataHeader = new ArrayList<String>();
		listDataChild = new HashMap<String, List<String>>();

		// Adding child data
		listDataHeader.add(getString(R.string.series_filter_title));
		listDataHeader.add(getString(R.string.read_filter_title));
		listDataHeader.add(getString(R.string.other));

		// Adding child data
		seriesFilters = this.getResources().getStringArray(R.array.seriesFilter);
		readFilters = this.getResources().getStringArray(R.array.readFilter);
		List<String> other = Arrays.asList(this.getResources().getStringArray(R.array.other));

		listDataChild.put(listDataHeader.get(0), Arrays.asList(seriesFilters)); // Header, Child data
		listDataChild.put(listDataHeader.get(1), Arrays.asList(readFilters));
		listDataChild.put(listDataHeader.get(2), other);
	}

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

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
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
			goToSettings();
			return true;
		case R.id.menu_about:
			showAbout();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private void showAbout() {
		sage.ui.Dialogs.About(this, this.getText(R.string.app_about));
	}

	private void goToSettings() {
		Intent intent = new Intent(this, PrefActivity.class);
		this.startActivityForResult(intent, 0);
	}

	/*
	 * ======================================================== UI Events
	 */
	@Override
	public void onBackPressed() {
		// Override back press to make it easy to back out of series filter.
		if (comicGridFragment != null && comicGridFragment.isSeriesView()) {
			comicGridFragment.closeSeriesView();
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
			if (comicGridFragment != null) {
				comicGridFragment.refreshData();
			}
			break;
		case ComicLibrary.STATUS_NOSETTINGS:
			Toast.makeText(this, getString(R.string.set_library_folders_first), Toast.LENGTH_LONG).show();
			break;
		}// switch
	}// func

	private class DrawerChildClickListener implements OnChildClickListener {
		private final View previousSeriesView = null;
		private final View previousReadView = null;

		@Override
		public boolean onChildClick(ExpandableListView parent, View view, int groupPosition, int childPosition, long id) {
			if (groupPosition < 2) {
				checkItem(groupPosition, childPosition);
			}
			switch (groupPosition) {
			case SERIES_GROUP:
				return onSeriesSelected(view, childPosition);
			case READING_GROUP:
				return onReadSelected(view, childPosition);
			case 2:
				return onSettingSelected(childPosition);
			default:
				return false;
			}
		}

		public void checkItem(int groupPosition, int childPosition) {
			int startingId = getGroupStartingId(groupPosition);
			uncheckAllGroupOptions(groupPosition, startingId);
			expListView.setItemChecked(startingId + childPosition, true);
		}

		private void uncheckAllGroupOptions(int groupPosition, int startingId) {
			for (int i = startingId; i < startingId + listAdapter.getChildrenCount(groupPosition); i++) {
				expListView.setItemChecked(i, false);
			}
		}

		private int getGroupStartingId(int groupPosition) {
			int position = 0;
			for (int i = 0; i < groupPosition; i++) {
				position += listAdapter.getChildrenCount(i) + 0;
			}
			position += groupPosition + 1; // group titles
			return position;
		}

		private boolean onSettingSelected(int position) {
			switch (position) {
			case 0: // Sync
				showSyncDialog();
				return true;
			case 1: // Settings
				goToSettings();
				break;
			case 2: // About
				showAbout();
				return true;
			default:
				return false;
			}

			mDrawerLayout.closeDrawer(mFiltersDrawer);
			return true;
		}

		private boolean onReadSelected(View view, int position) {
			unpress(previousReadView);

			view.setPressed(true);
			prefs.edit().putInt("readFilter", position).commit();

			if (comicGridFragment != null) {
				comicGridFragment.updateReadFilter(position);
			}

			mDrawerLayout.closeDrawer(mFiltersDrawer);
			return true;
		}

		private boolean onSeriesSelected(View view, int position) {
			unpress(previousSeriesView);

			view.setPressed(true);
			prefs.edit().putInt("seriesFilter", position).commit();

			if (comicGridFragment != null) {
				comicGridFragment.updateSeriesFilter(position);
			}

			mDrawerLayout.closeDrawer(mFiltersDrawer);
			return true;
		}

		private void unpress(View previous) {
			if (previous != null) {
				previous.setPressed(false);
			}
		}
	}

	@Override
	public void onDataRefreshComplete() {
		generateTitle();
	}

	protected void generateTitle() {
		ActionBar actionBar = getActionBar();

		if (comicGridFragment != null) {
			int seriesFilterMode = comicGridFragment.getSeriesFilterMode();
			int readingFilterMode = comicGridFragment.getReadFilterMode();
			String seriesFilter = comicGridFragment.getSeriesFilter();

			actionBar.setSubtitle(null);
			if (seriesFilterMode <= 0) {
				actionBar.setTitle(readFilters[readingFilterMode]);
			} else {
				if (!seriesFilter.isEmpty()) {
					actionBar.setTitle(seriesFilter);
				} else {
					actionBar.setTitle(seriesFilters[seriesFilterMode]);
				}
				generateSubtitle(readingFilterMode);
			}
		} else {
			actionBar.setTitle(R.string.app_name);
		}
	}

	private void generateSubtitle(int readingFilterMode) {
		if (readingFilterMode > 0) {
			getActionBar().setSubtitle(readFilters[readingFilterMode]);
		}
	}
}// cls
