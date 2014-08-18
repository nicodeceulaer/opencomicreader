package com.sketchpunk.ocomicreader.ui;

import java.util.ArrayList;
import java.util.Collection;

import sage.adapter.ComicGridAdapter;
import sage.data.DatabaseHelper;
import sage.data.domain.Comic;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.util.AttributeSet;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;
import android.widget.Toast;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.sketchpunk.ocomicreader.R;
import com.sketchpunk.ocomicreader.ViewActivity;
import com.sketchpunk.ocomicreader.lib.ComicDatabaseLoader;
import com.sketchpunk.ocomicreader.lib.ComicLibrary;

public class CoverGridView extends GridView implements OnItemClickListener, LoaderManager.LoaderCallbacks<Collection<Comic>> {

	public interface iCallback {
		void onDataRefreshComplete();
	}

	private ComicGridAdapter mAdapter;
	private RuntimeExceptionDao<Comic, Integer> comicDao;
	private ComicDatabaseLoader comicDatabaseLoader;

	public int recordCount = 0;
	private int mFilterMode = 0;
	private String mSeriesFilter = "";

	private final int mTopPadding = 130; // TODO: get the proper bar height to
											// make this work.
	private int mThumbPadding = 0;
	private int mGridPadding = 0;
	private int mGridColNum = 2;

	public CoverGridView(Context context) {
		super(context);
	}// func

	public CoverGridView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public CoverGridView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	public void init() {
		// Get Preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getContext());
		try {
			this.mGridColNum = prefs.getInt("libColCnt", 2);
			this.mGridPadding = prefs.getInt("libPadding", 0);
			this.mThumbPadding = prefs.getInt("libCoverPad", 3);
		} catch (Exception e) {
			System.err.println("Error Loading Library Prefs " + e.getMessage());
		}// try

		// ....................................
		// set values

		mAdapter = new ComicGridAdapter(this.getContext(), R.layout.listitem_library, new ArrayList<Comic>());

		this.setNumColumns(mGridColNum);
		this.setPadding(mGridPadding, mGridPadding + mTopPadding, mGridPadding, mGridPadding);
		this.setHorizontalSpacing(mThumbPadding);
		this.setVerticalSpacing(mThumbPadding);
		this.setAdapter(mAdapter);
		this.setOnItemClickListener(this);

		// ....................................
		// Start DB and Data Loader
		getComicDao();
		LoaderManager loaderManager = getLoaderManager();
		loaderManager.initLoader(0, null, this);

	}// func

	private LoaderManager getLoaderManager() {
		return ((FragmentActivity) this.getContext()).getSupportLoaderManager();
	}// func

	private void getComicDao() {
		DatabaseHelper databaseHelper = OpenHelperManager.getHelper(this.getContext(), DatabaseHelper.class);
		comicDao = databaseHelper.getRuntimeExceptionDao(Comic.class);
	}

	public void dispose() {
		if (comicDao != null) {
			OpenHelperManager.releaseHelper();
			comicDao = null;
		}
	}// func

	/*
	 * ======================================================== Getter & Setters
	 */
	public int getFilterMode() {
		return mFilterMode;
	}

	public void setFilterMode(int i) {
		mFilterMode = i;
	}

	public String getSeriesFilter() {
		return mSeriesFilter;
	}

	public void setSeriesFilter(String str) {
		mSeriesFilter = (str == null) ? "" : str;
	}

	/*
	 * ======================================================== misc
	 */
	public boolean isSeriesFiltered() {
		return (mFilterMode == 1);
	}

	@Override
	// ComicCover.onClick
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		Comic comic = mAdapter.getItem(position);

		if (isSeriesFiltered() && mSeriesFilter.isEmpty()) { // if series if
																// selected but
																// not filtered
																// yet.
			mSeriesFilter = comic.getSeries();
			refreshData();
		} else { // Open comic in viewer.
			Intent intent = new Intent(this.getContext(), ViewActivity.class);
			intent.putExtra("comicid", comic.getId());
			((FragmentActivity) this.getContext()).startActivityForResult(intent, 0);
		}// if
	}// func

	public void refreshData() {
		comicDatabaseLoader.setmFilterMode(mFilterMode);
		comicDatabaseLoader.setmSeriesFilter(mSeriesFilter);
		comicDatabaseLoader.setSeriesFiltered(isSeriesFiltered());
		getLoaderManager().restartLoader(0, null, this);
	}

	/*
	 * ======================================================== Context Menu
	 */
	public void createContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		ComicGridAdapter.ViewHolder ref = (ComicGridAdapter.ViewHolder) info.targetView.getTag();

		menu.setHeaderTitle(ref.libraryTitle.getText().toString());
		menu.add(0, 2, 0, "Delete");
		menu.add(0, 1, 1, "Reset Progress");
		menu.add(0, 3, 2, "Mark as Read");
		menu.add(0, 4, 3, "Edit Series Name");
	}// func

	public boolean contextItemSelected(MenuItem item) {
		int itmID = item.getItemId();

		if (isSeriesFiltered() && mSeriesFilter.isEmpty() && itmID == 2) {
			Toast.makeText(this.getContext(), "Can not perform operation on series.", Toast.LENGTH_SHORT).show();
			return false;
		}// if

		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
		final ComicGridAdapter.ViewHolder ref = (ComicGridAdapter.ViewHolder) info.targetView.getTag();
		final Integer comicID = ref.id;
		final String seriesName = ref.seriesName;
		final Context context = this.getContext();
		AlertDialog.Builder abBuilder;

		switch (itmID) {
		// ...................................
		case 2:// DELETE
			abBuilder = new AlertDialog.Builder(this.getContext());
			abBuilder.setTitle("Delete Comic : " + ref.libraryTitle.getText().toString());
			abBuilder.setMessage("You are able to remove the selected comic from the library or from the device competely.");
			abBuilder.setCancelable(true);
			abBuilder.setNegativeButton("Cancel", null);
			abBuilder.setPositiveButton("Remove from library", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					ComicLibrary.removeComic(context, comicID, false);
					refreshData();
				}
			});
			abBuilder.setNeutralButton("Remove from device", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					ComicLibrary.removeComic(context, comicID, true);
					refreshData();
				}
			});
			abBuilder.show();
			break;
		// ...................................
		case 1:// Reset Progress
			abBuilder = new AlertDialog.Builder(this.getContext());
			abBuilder.setTitle("Reset Progress : " + ref.libraryTitle.getText().toString());
			abBuilder.setMessage("Are you sure you want to reset the reading progress?");
			abBuilder.setCancelable(true);
			abBuilder.setNegativeButton("Cancel", null);
			abBuilder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					ComicLibrary.setSeriesProgress(context, comicID, 0);
					refreshData();
				}
			});
			abBuilder.show();
			break;
		// ...................................
		case 3:// Mark as Read
			abBuilder = new AlertDialog.Builder(this.getContext());
			abBuilder.setTitle("Mark as Read : " + ref.libraryTitle.getText().toString());
			abBuilder.setMessage("Are you sure you want to change the reading progress?");
			abBuilder.setCancelable(true);
			abBuilder.setNegativeButton("Cancel", null);
			abBuilder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					ComicLibrary.setComicProgress(context, comicID, 1);
					refreshData();
				}
			});
			abBuilder.show();
			break;

		// ...................................
		case 4:// Edit Serial
			String sSeries = "";
			if (seriesName == null || seriesName.isEmpty()) {
				getComicDao();
				Comic comic = comicDao.queryForId(comicID);
				sSeries = comic.getSeries();
				OpenHelperManager.releaseHelper();
			} else
				sSeries = seriesName;

			sage.ui.InputDialog inDialog = new sage.ui.InputDialog(this.getContext(), "Edit Series : " + ref.libraryTitle.getText().toString(), null, sSeries) {
				@Override
				public boolean onOk(String txt) {
					if (seriesName == txt)
						return true;

					if (seriesName != null && !seriesName.isEmpty())
						ComicLibrary.renameSeries(getContext(), seriesName, txt);
					else
						ComicLibrary.setSeriesName(getContext(), comicID, txt);

					refreshData();
					return true;
				}// func
			};

			inDialog.show();
			break;
		}// switch

		return true;
	}// func

	public void onPause() {
		mAdapter.clear();
	}

	@Override
	public void onLoadFinished(Loader<Collection<Comic>> arg0, Collection<Comic> arg1) {
		this.recordCount = arg1.size();
		mAdapter.clear();
		mAdapter.addAll(arg1);

		if (this.getContext() instanceof iCallback)
			((iCallback) this.getContext()).onDataRefreshComplete();
	}

	@Override
	public void onLoaderReset(Loader<Collection<Comic>> arg0) {
		mAdapter.clear();
	}

	@Override
	public Loader<Collection<Comic>> onCreateLoader(int id, Bundle arg) {
		comicDatabaseLoader = new ComicDatabaseLoader(getContext());

		comicDatabaseLoader.setmFilterMode(mFilterMode);
		comicDatabaseLoader.setmSeriesFilter(mSeriesFilter);
		comicDatabaseLoader.setSeriesFiltered(isSeriesFiltered());

		return comicDatabaseLoader;
	}// func
}// cls
