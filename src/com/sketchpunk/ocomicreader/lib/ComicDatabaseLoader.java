package com.sketchpunk.ocomicreader.lib;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

import sage.data.DatabaseHelper;
import sage.data.domain.Comic;
import android.content.Context;
import android.support.v4.content.AsyncTaskLoader;
import android.util.Log;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.GenericRawResults;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;

public class ComicDatabaseLoader extends AsyncTaskLoader<Collection<Comic>> {

	private Collection<Comic> data = null;
	private RuntimeExceptionDao<Comic, Integer> comicDao = null;

	private boolean isSeriesFiltered = false;
	private String mSeriesFilter = "";
	private int mSeriesFilterMode = 1;
	private int mReadFilterMode = 0;

	public ComicDatabaseLoader(Context context) {
		super(context);
	}

	@Override
	public Collection<Comic> loadInBackground() {
		if (comicDao == null) {
			getComicDao();
		}

		QueryBuilder<Comic, Integer> queryBuilder = comicDao.queryBuilder();
		Where<Comic, Integer> where = queryBuilder.where();
		PreparedQuery<Comic> preparedQuery = null;

		try {
			if (mSeriesFilterMode == 1 && mSeriesFilter.isEmpty()) {// Filter by series
				GenericRawResults<String[]> rawResults = comicDao.queryRaw(getSeriesSearchString());
				data = new ArrayList<Comic>();
				for (String[] resultArray : rawResults) {
					Comic seriesResult = parseRawComicSeriesResults(resultArray);
					data.add(seriesResult);
				}

				return data;
			} else { // Filter by reading progress.
				if (mSeriesFilterMode == 1) { // series selected
					where.raw("LOWER(`series`) = '" + mSeriesFilter.replace("'", "''").toLowerCase(Locale.getDefault()) + "'");
				}
				if (mSeriesFilterMode == 2) { // showing recent
					where.isNotNull("dateRead");
					queryBuilder.orderBy("dateRead", false);
				} else { // normally order by series then issue
					// TODO: add "order by" to drawer
					queryBuilder.orderByRaw("LOWER(`series`) ASC").orderBy("issue", true);
				}

				if (mReadFilterMode != 0 && mSeriesFilterMode != 0) {
					where.and();
				}

				switch (mReadFilterMode) {
				case 0:
					break; // All;
				case 1:
					where.eq("pageRead", 0);
					break;// Unread
				case 2:
					where.gt("pageRead", 0).and().lt("pageRead", "pageCount - 1");
					break;// Progress
				case 3:
					where.ge("pageRead", "pageCount - 1");
					break;// Read
				}// switch
			}// if

			if (mSeriesFilterMode == 0 && mReadFilterMode == 0) {
				data = comicDao.queryForAll();
			} else {
				preparedQuery = queryBuilder.prepare();
				data = comicDao.query(preparedQuery);
			}
		} catch (SQLException e) {
			Log.e("sql", e.getLocalizedMessage());
		}

		return data;
	}

	// @formatter:off
	private String getSeriesSearchString() {
		return "SELECT min(id)[id], " + "series[title], " + "sum(pageCount)[pageCount], " + "sum(pageRead)[pageRead], " + "count(id) [issue] " + "FROM comics "
				+ getReadFilterStringForSeries() + "GROUP BY LOWER(series) " + "ORDER BY LOWER(series)";
	}

	// @formatter:on

	private String getReadFilterStringForSeries() {
		switch (mReadFilterMode) {
		default:
			return "";
		case 1:
			return "WHERE pageRead = 0 ";
		case 2:
			return "WHERE pageRead > 0 AND pageRead < pageCount - 1 ";
		case 3:
			return "WHERE pageRead >= pageCount - 1 ";
		}// switch
	}

	private Comic parseRawComicSeriesResults(String[] resultArray) {
		Comic seriesResult = new Comic();
		seriesResult.setId(Integer.parseInt(resultArray[0]));
		seriesResult.setTitle(resultArray[1]);
		seriesResult.setSeries(resultArray[1]);
		seriesResult.setPageCount(Integer.parseInt(resultArray[2]));
		seriesResult.setPageRead(Integer.parseInt(resultArray[3]));
		seriesResult.setIssue(Integer.parseInt(resultArray[4]));
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

	public int getSeriesFilterMode() {
		return mSeriesFilterMode;
	}

	public void setSeriesFilterMode(int seriesFilterMode) {
		this.mSeriesFilterMode = seriesFilterMode;
	}

	public int getReadFilterMode() {
		return mReadFilterMode;
	}

	public void setReadFilterMode(int readFilterMode) {
		this.mReadFilterMode = readFilterMode;
	}

}
