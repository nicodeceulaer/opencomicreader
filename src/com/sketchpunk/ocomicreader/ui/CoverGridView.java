package com.sketchpunk.ocomicreader.ui;

import java.sql.SQLException;
import java.util.Collection;

import sage.data.DatabaseHelper;
import sage.data.domain.Comic;
import sage.loader.LoadImageView;
import sage.ui.ProgressCircle;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.stmt.QueryBuilder;
import com.sketchpunk.ocomicreader.R;
import com.sketchpunk.ocomicreader.ViewActivity;
import com.sketchpunk.ocomicreader.lib.ComicLibrary;

public class CoverGridView extends GridView implements OnItemClickListener, LoaderManager.LoaderCallbacks<Collection<Comic>>,
		LoadImageView.OnImageLoadedListener {

	public interface iCallback {
		void onDataRefreshComplete();
	}

	private final String TAG = "COVERGRIDVIEW";
	private ArrayAdapter<Comic> mAdapter;
	private RuntimeExceptionDao<Comic, Integer> comicDao;

	public int recordCount = 0;
	private int mFilterMode = 0;
	private String mSeriesFilter = "";

	private final int mTopPadding = 130; // TODO: get the proper bar height to
											// make this work.
	private int mThumbHeight = 600;
	private int mThumbPadding = 0;
	private int mGridPadding = 0;
	private int mGridColNum = 2;

	private boolean mIsFirstRun = true;
	private String mThumbPath;

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
			this.mThumbHeight = prefs.getInt("libCoverHeight", 800);
		} catch (Exception e) {
			System.err.println("Error Loading Library Prefs " + e.getMessage());
		}// try

		// ....................................
		// set values
		mThumbPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/OpenComicReader/thumbs/";

		mAdapter = new ArrayAdapter<Comic>(this.getContext(), R.layout.listitem_library);

		this.setNumColumns(mGridColNum);
		this.setPadding(mGridPadding, mGridPadding + mTopPadding, mGridPadding, mGridPadding);
		this.setHorizontalSpacing(mThumbPadding);
		this.setVerticalSpacing(mThumbPadding);
		this.setAdapter(mAdapter);
		this.setOnItemClickListener(this);

		// ....................................
		// Start DB and Data Loader
		getComicDao();

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
		AdapterItemRef itmRef = (AdapterItemRef) view.getTag();

		if (isSeriesFiltered() && mSeriesFilter.isEmpty()) { // if series if
																// selected but
																// not filtered
																// yet.
			mSeriesFilter = itmRef.series;
			refreshData();
		} else { // Open comic in viewer.
			Intent intent = new Intent(this.getContext(), ViewActivity.class);
			intent.putExtra("comicid", itmRef.id);
			((FragmentActivity) this.getContext()).startActivityForResult(intent, 0);
		}// if
	}// func

	/*
	 * ======================================================== Cursor Loader : LoaderManager.LoaderCallbacks<Cursor>
	 */
	public void refreshData() {
		if (!mIsFirstRun) {
			if (comicDao == null) {
				getComicDao();
			}

			// TODO: refresh data here
			// getLoaderManager().restartLoader(0, null, this);
		} else
			mIsFirstRun = false;
	}// func

	@Override
	public Loader<Collection<Comic>> onCreateLoader(int id, Bundle arg) {
		getComicDao();
		QueryBuilder<Comic, Integer> comicQuery = comicDao.queryBuilder();

		try {
			if (isSeriesFiltered()) {// Filter by series
				if (mSeriesFilter.isEmpty()) {
					// sql =
					// "SELECT min(comicID) [_id],series [title],sum(pgCount) [pgCount],sum(pgRead) [pgRead],min(isCoverExists) [isCoverExists],count(comicID) [cntIssue] FROM ComicLibrary GROUP BY series ORDER BY series";
					comicQuery.orderBy("series", true).distinct();
				} else {
					comicQuery.orderBy("title", true).where().eq("series", mSeriesFilter.replace("'", "''"));
				}// if
			} else { // Filter by reading progress.
				switch (mFilterMode) {
				case 2:
					comicQuery.where().eq("pageRead", 0);
					break; // Unread;
				case 3:
					comicQuery.where().gt("pageRead", 0).and().lt("pageRead", "pageCount - 1");
					break;// Progress
				case 4:
					comicQuery.where().ge("pageRead", "pageCount - 1");
					break;// Read
				}// switch
				comicQuery.orderBy("title", true);
			}// if
		} catch (SQLException e) {
			Log.e("sql", e.getLocalizedMessage());
		}
		// ............................................
		Loader<Collection<Comic>> loader = new Loader<Collection<Comic>>(getContext());
		// TODO: do something magic with loader?
		return loader;
	}// func

	/*
	 * ======================================================== Adapter Events
	 */
	public class AdapterItemRef {
		public String id = "";
		public TextView lblTitle;
		public ImageView imgCover;
		public ProgressCircle pcProgress;
		public Bitmap bitmap = null;
		public String series = "";
	}// cls

	// @Override
	// public View onCreateListItem(View v) {
	// try {
	// AdapterItemRef itmRef = new AdapterItemRef();
	// itmRef.lblTitle = (TextView) v.findViewById(R.id.lblTitle);
	// itmRef.pcProgress = (ProgressCircle) v.findViewById(R.id.pcProgress);
	// itmRef.imgCover = (ImageView) v.findViewById(R.id.imgCover);
	// itmRef.imgCover.setTag(itmRef);
	// itmRef.imgCover.getLayoutParams().height = mThumbHeight;
	// v.setTag(itmRef);
	// } catch (Exception e) {
	// System.out.println("onCreateListItem " + e.getMessage());
	// }// try
	// return v;
	// }// func
	//
	// @Override
	// public void onBindListItem(View v, Cursor c) {
	// try {
	// AdapterItemRef itmRef = (AdapterItemRef) v.getTag();
	//
	// // ..............................................
	// String id = c.getString(mAdapter.getColIndex("_id")), tmp = c.getString(mAdapter.getColIndex("title"));
	//
	// // Grid view binds more then one time, limit double loading to save
	// // on resources used to load images.
	// // Need to change ID to uniqueness, but also check title because
	// // there is a small bind issue going back/forth between series view
	// if (itmRef.id.equals(tmp) && itmRef.lblTitle.getText().equals(tmp)) {
	// System.out.println("Repeat");
	// return;
	// }
	// itmRef.id = id;
	//
	// if (isSeriesFiltered() && mSeriesFilter.isEmpty()) {
	// itmRef.series = tmp;
	// tmp += " (" + c.getString(mAdapter.getColIndex("cntIssue")) + ")";
	// } else
	// itmRef.series = "";
	// itmRef.lblTitle.setText(tmp);
	//
	// // ..............................................
	// // load Cover Image
	// if (c.getString(mAdapter.getColIndex("isCoverExists")).equals("1")) {
	// LoadImageView.loadImage(mThumbPath + itmRef.id + ".jpg", itmRef.imgCover, this);
	// } else {
	// // No image, clear out images TODO put a default image for
	// // missing covers.
	// itmRef.imgCover.setImageBitmap(null);
	// if (itmRef.bitmap != null) {
	// itmRef.bitmap.recycle();
	// itmRef.bitmap = null;
	// }// if
	// }// if
	//
	// // ..............................................
	// // display reading progress
	// float progress = 0f;
	// int pTotal = c.getInt(mAdapter.getColIndex("pgCount"));
	// if (pTotal > 0) {
	// float pRead = c.getFloat(mAdapter.getColIndex("pgRead"));
	// if (pRead > 0)
	// pRead += 1; // Page index start at 0, so if the user has
	// // already passed the first page, Add value to
	// // it to be able to get 100%, else leave it so
	// // it can get 0%
	// progress = (pRead / (pTotal));
	// }// if
	//
	// itmRef.pcProgress.setProgress(progress);
	// } catch (Exception e) {
	// System.out.println("onBindListItem " + e.getMessage());
	// }// try
	// }// func

	/*
	 * ======================================================== Image Loading
	 */
	@Override
	public void onImageLoaded(boolean isSuccess, Bitmap bmp, View view) {
		if (view == null)
			return;
		ImageView iv = (ImageView) view;

		if (!isSuccess)
			iv.setImageBitmap(null); // release reference, if cover didn't load
										// show that it didn't.

		AdapterItemRef itmRef = (AdapterItemRef) iv.getTag();
		if (itmRef.bitmap != null) {
			itmRef.bitmap.recycle();
			itmRef.bitmap = null;
		}// if

		itmRef.bitmap = bmp; // keeping reference to make sure to clear it out
								// when its not needed
	}// func

	/*
	 * ======================================================== Context Menu
	 */
	public void createContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		AdapterItemRef ref = (AdapterItemRef) info.targetView.getTag();

		menu.setHeaderTitle(ref.lblTitle.getText().toString());
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
		final AdapterItemRef ref = (AdapterItemRef) info.targetView.getTag();
		final Integer comicID = Integer.parseInt(ref.id);
		final String seriesName = ref.series;
		final Context context = this.getContext();
		AlertDialog.Builder abBuilder;

		switch (itmID) {
		// ...................................
		case 2:// DELETE
			abBuilder = new AlertDialog.Builder(this.getContext());
			abBuilder.setTitle("Delete Comic : " + ref.lblTitle.getText().toString());
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
			abBuilder.setTitle("Reset Progress : " + ref.lblTitle.getText().toString());
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
			abBuilder.setTitle("Mark as Read : " + ref.lblTitle.getText().toString());
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

			sage.ui.InputDialog inDialog = new sage.ui.InputDialog(this.getContext(), "Edit Series : " + ref.lblTitle.getText().toString(), null, sSeries) {
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

}// cls
