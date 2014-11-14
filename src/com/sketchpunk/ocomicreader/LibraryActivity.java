package com.sketchpunk.ocomicreader;

import java.sql.SQLException;

import sage.Util;
import sage.data.DatabaseHelper;
import sage.data.domain.Comic;
import android.app.ActionBar;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
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
import android.widget.TextView;
import android.widget.Toast;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.stmt.QueryBuilder;
import com.onyx.android.sdk.device.DeviceInfo;
import com.onyx.android.sdk.device.IDeviceFactory.IDeviceController;
import com.runeai.runereader.R;
import com.sketchpunk.ocomicreader.fragments.ComicGridFragment;
import com.sketchpunk.ocomicreader.fragments.DrawerFragment;
import com.sketchpunk.ocomicreader.fragments.WelcomeFragment;
import com.sketchpunk.ocomicreader.lib.ComicLibrary;
import com.sketchpunk.ocomicreader.ui.CoverGridView;

public class LibraryActivity extends FragmentActivity implements ComicLibrary.SyncCallback, CoverGridView.iCallback, DrawerFragment.OnSettingsSelectedListener {

	private ProgressDialog mProgress;
	private RuntimeExceptionDao<Comic, Integer> comicDao;

	private SharedPreferences prefs;
	private long backPressed;
	ComicGridFragment comicGridFragment = null;
	private ActionBarDrawerToggle mDrawerToggle;
	private DrawerLayout mDrawerLayout;
	private View mFiltersDrawer;
	private DrawerFragment drawerFragment;
	DatabaseHelper databaseHelper = null;

	/*
	 * ======================================================== Main
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		IDeviceController deviceController = DeviceInfo.currentDevice;
		deviceController.showSystemStatusBar(getApplicationContext());

		databaseHelper = getHelper();

		setContentView(R.layout.activity_library);
		overridePendingTransition(R.anim.fadein, R.anim.fadeout);

		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		determineScreenDimensions();

		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		mFiltersDrawer = findViewById(R.id.filters_drawer);

		createDrawerToggle();

		mDrawerLayout.setDrawerListener(mDrawerToggle);

		ActionBar actionBar = getActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			actionBar.setHomeButtonEnabled(true);
		}

		addDrawerFragment();
		changeToCorrectContentFragment();

		if(prefs.getBoolean("openLastComicAfterLaunch", false)) {
			goToLastComic();
		}
	}// func

	private DatabaseHelper getHelper() {
		if (databaseHelper == null) {
			databaseHelper = OpenHelperManager.getHelper(this, DatabaseHelper.class);
		}
		return databaseHelper;
	}

	@Override
	protected void onResume() {
		super.onResume();
		generateTitle();
	}

	private void addDrawerFragment() {
		FragmentManager fragmentManager = getFragmentManager();
		FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

		drawerFragment = new DrawerFragment();
		fragmentTransaction.add(R.id.filters_drawer, drawerFragment);

		fragmentTransaction.commit();
	}

	private void createDrawerToggle() {
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
	}

	private void determineScreenDimensions() {
		Editor edit = prefs.edit();
		Point realSize = Util.getRealSize(getWindowManager().getDefaultDisplay());
		int width = realSize.x < realSize.y ? realSize.x : realSize.y;
		int height = realSize.x > realSize.y ? realSize.x : realSize.y;
		edit.putInt("maxWidth", width);
		edit.putInt("maxHeight", height);
		edit.commit();
	}

	private void changeToCorrectContentFragment() {
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
		if (comicGridFragment == null) {
			comicGridFragment = new ComicGridFragment();
			fragmentTransaction.replace(R.id.library_content, comicGridFragment);
		}
	}

	private void setWelcomeFragmentAsContent(FragmentTransaction fragmentTransaction) {
		WelcomeFragment fragment = new WelcomeFragment();
		fragmentTransaction.replace(R.id.library_content, fragment);
		comicGridFragment = null;
	}

	private boolean noDataSynchronized() {
		comicDao = databaseHelper.getComicRuntimeDao();
		long comicsCount = comicDao.countOf();
		return comicsCount <= 0;
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
	protected void onDestroy() {
		super.onDestroy();
		OpenHelperManager.releaseHelper();
		databaseHelper = null;
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
		comicDao = databaseHelper.getComicRuntimeDao();

		QueryBuilder<Comic, Integer> lastComicQuery = comicDao.queryBuilder();
		Comic lastRead = null;
		try {
			lastRead = lastComicQuery.orderBy("dateRead", false).where().isNotNull("dateRead").queryForFirst();
		} catch (SQLException e) {
			Log.e("sql", "Couldn't find last read comic " + e.getMessage());
		}

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
			changeToCorrectContentFragment();
			if (comicGridFragment != null) {
				comicGridFragment.refreshData();
			}
			break;
		case ComicLibrary.STATUS_NOSETTINGS:
			Toast.makeText(this, getString(R.string.set_library_folders_first), Toast.LENGTH_LONG).show();
			break;
		}// switch
	}// func

	@Override
	public void onDataRefreshComplete(int recordCount) {
		generateTitle();

		updateNothingFoundMessage(recordCount);
	}

	private void updateNothingFoundMessage(int recordCount) {
		TextView tv = (TextView) findViewById(R.id.nothingFound);
		if (tv != null) {
			if (recordCount == 0) {
				tv.setVisibility(View.VISIBLE);
			} else {
				tv.setVisibility(View.GONE);
			}
		}
	}

	protected void generateTitle() {
		ActionBar actionBar = getActionBar();
		if (actionBar == null) {
			return;
		}

		if (comicGridFragment != null && drawerFragment != null) {
			int seriesFilterMode = comicGridFragment.getSeriesFilterMode();
			int readingFilterMode = comicGridFragment.getReadFilterMode();
			String seriesFilter = comicGridFragment.getSeriesFilter();

			actionBar.setSubtitle(null);
			if (seriesFilterMode <= 0) {
				actionBar.setTitle(drawerFragment.getReadFilters()[readingFilterMode]);
			} else {
				if (!seriesFilter.isEmpty()) {
					actionBar.setTitle(seriesFilter);
				} else {
					actionBar.setTitle(drawerFragment.getSeriesFilters()[seriesFilterMode]);
				}
				generateSubtitle(readingFilterMode);
			}
		} else {
			actionBar.setTitle(R.string.app_name);
		}
	}

	private void generateSubtitle(int readingFilterMode) {
		if (drawerFragment != null) {
			if (readingFilterMode > 0 && getActionBar() != null) {
				getActionBar().setSubtitle(drawerFragment.getReadFilters()[readingFilterMode]);
			}
		}
	}

	@Override
	public boolean onSettingSelected(int childPosition) {
		switch (childPosition) {
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

	@Override
	public void onReadFilterSelected(int position) {
		if (comicGridFragment != null) {
			comicGridFragment.updateReadFilter(position);
		}
	}

	@Override
	public void onSeriesFilterSelected(int position) {
		if (comicGridFragment != null) {
			comicGridFragment.updateSeriesFilter(position);
		}
	}
}
