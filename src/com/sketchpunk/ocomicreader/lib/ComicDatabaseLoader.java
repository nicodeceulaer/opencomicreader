package com.sketchpunk.ocomicreader.lib;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;

import sage.data.DatabaseHelper;
import sage.data.domain.Comic;
import android.content.Context;
import android.support.v4.content.AsyncTaskLoader;
import android.util.Log;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.GenericRawResults;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.stmt.QueryBuilder;

public class ComicDatabaseLoader extends AsyncTaskLoader<Collection<Comic>> {

	private Collection<Comic> data = null;
	private RuntimeExceptionDao<Comic, Integer> comicDao = null;

	private boolean isSeriesFiltered = false;
	private String mSeriesFilter = "";
	private int mFilterMode = 1;

	public ComicDatabaseLoader(Context context) {
		super(context);
	}

	@Override
	public Collection<Comic> loadInBackground() {
		if (comicDao == null) {
			getComicDao();
		}

		QueryBuilder<Comic, Integer> comicQuery = comicDao.queryBuilder();

		try {
			if (isSeriesFiltered) {// Filter by series
				if (mSeriesFilter.isEmpty()) {
					// sql =
					// "SELECT min(comicID) [_id],series [title],sum(pgCount) [pgCount],sum(pgRead) [pgRead],min(isCoverExists) [isCoverExists],count(comicID) [cntIssue] FROM ComicLibrary GROUP BY series ORDER BY series";
					GenericRawResults<String[]> rawResults = comicDao
							.queryRaw("SELECT min(id)[id], series[title], sum(pageCount)[pageCount], coverExists, sum(pageRead)[pageRead], count(id) [issue] FROM comics WHERE coverExists = 1 GROUP BY LOWER(series) ORDER BY LOWER(series)");
					data = new ArrayList<Comic>();
					for (String[] resultArray : rawResults) {
						Comic seriesResult = parseRawComicSeriesResults(resultArray);
						data.add(seriesResult);
					}
					GenericRawResults<String[]> rawResultsWithoutCovers = comicDao
							.queryRaw("SELECT min(id)[id], series[title], sum(pageCount)[pageCount], coverExists, sum(pageRead)[pageRead], count(id) [issue] FROM comics WHERE coverExists = 0 GROUP BY LOWER(series) ORDER BY LOWER(series)");

					for (String[] resultArray : rawResultsWithoutCovers) {
						Comic seriesResult = parseRawComicSeriesResults(resultArray);
						boolean alreadyFound = false;
						for (Comic comic : data) {
							if (comic.getSeries() != null && comic.getSeries().equals(seriesResult.getSeries())) {
								alreadyFound = true;
								break;
							}
						}
						if (alreadyFound == false) {
							data.add(seriesResult);
						}
					}
					return data;
				} else {
					comicQuery.orderBy("issue", true).where().raw("LOWER(`series`) = '" + mSeriesFilter.replace("'", "''").toLowerCase() + "'");
				}// if
			} else { // Filter by reading progress.
				switch (mFilterMode) {
				case 2:
					comicQuery.where().eq("pageRead", 0);
					comicQuery.orderBy("series", true).orderBy("issue", true);
					break; // Unread;
				case 3:
					comicQuery.where().gt("pageRead", 0).and().lt("pageRead", "pageCount - 1");
					comicQuery.orderBy("series", true).orderBy("issue", true);
					break;// Progress
				case 4:
					comicQuery.where().ge("pageRead", "pageCount - 1");
					comicQuery.orderBy("series", true).orderBy("issue", true);
					break;// Read
				case 5:
					comicQuery.orderBy("dateRead", false).where().isNotNull("dateRead");
					break; // Recent
				}// switch
			}// if

			data = comicQuery.query();
		} catch (SQLException e) {
			Log.e("sql", e.getLocalizedMessage());
		}

		return data;
	}

	private Comic parseRawComicSeriesResults(String[] resultArray) {
		Comic seriesResult = new Comic();
		seriesResult.setId(Integer.parseInt(resultArray[0]));
		seriesResult.setTitle(resultArray[1]);
		seriesResult.setSeries(resultArray[1]);
		seriesResult.setPageCount(Integer.parseInt(resultArray[2]));
		seriesResult.setCoverExists(resultArray[3].equals("1") ? true : false);
		seriesResult.setPageRead(Integer.parseInt(resultArray[4]));
		seriesResult.setIssue(Integer.parseInt(resultArray[5]));
		return seriesResult;
	}

	@Override
	protected void onStartLoading() {
		if (data != null) {
			deliverResult(data);
		}
		if (takeContentChanged() || data == null) {
			forceLoad();
		}
	}

	private void getComicDao() {
		DatabaseHelper databaseHelper = OpenHelperManager.getHelper(this.getContext(), DatabaseHelper.class);
		comicDao = databaseHelper.getRuntimeExceptionDao(Comic.class);
	}

	public boolean isSeriesFiltered() {
		return isSeriesFiltered;
	}

	public void setSeriesFiltered(boolean isSeriesFiltered) {
		this.isSeriesFiltered = isSeriesFiltered;
	}

	public String getmSeriesFilter() {
		return mSeriesFilter;
	}

	public void setmSeriesFilter(String mSeriesFilter) {
		this.mSeriesFilter = mSeriesFilter;
	}

	public int getmFilterMode() {
		return mFilterMode;
	}

	public void setmFilterMode(int mFilterMode) {
		this.mFilterMode = mFilterMode;
	}

}
