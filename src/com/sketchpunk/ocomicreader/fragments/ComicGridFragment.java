package com.sketchpunk.ocomicreader.fragments;

import android.app.Fragment;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.runeai.runereader.R;
import com.sketchpunk.ocomicreader.ui.CoverGridView;

public class ComicGridFragment extends Fragment {

	private CoverGridView mGridView;
	private SharedPreferences prefs;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_comic_grid, container, false);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		mGridView = (CoverGridView) getView().findViewById(R.id.lvMain);

		readSavedInstanceState(savedInstanceState);

		mGridView.init();

		getActivity().registerForContextMenu(mGridView); // Route event from Activity to View
	}

	private void readSavedInstanceState(Bundle savedInstanceState) {
		prefs = PreferenceManager.getDefaultSharedPreferences(getView().getContext());
		if (savedInstanceState != null) {
			mGridView.setSeriesFilter(savedInstanceState.getString("mSeriesFilter"));
			mGridView.setSeriesFilterMode(savedInstanceState.getInt("mSeriesFilterMode"));
			mGridView.setReadFilterMode(savedInstanceState.getInt("mReadFilterMode"));
		} else {// if no state, load in default pref.
			mGridView.setSeriesFilterMode(prefs.getInt("seriesFilter", 0));
			mGridView.setReadFilterMode(prefs.getInt("readFilter", 0));
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {

		outState.putString("mSeriesFilter", mGridView.getSeriesFilter());
		outState.putInt("mSeriesFilterMode", mGridView.getSeriesFilterMode());
		outState.putInt("mReadFilterMode", mGridView.getReadFilterMode());

		super.onSaveInstanceState(outState);
	}

	@Override
	public void onDestroy() {
		mGridView.dispose();
		super.onDestroy();
	}

	@Override
	public void onStop() {
		mGridView.nullAdapter();
		super.onStop();
	}

	@Override
	public void onResume() {
		mGridView.recoverAdapter();
		mGridView.invalidate();
		mGridView.scrollToLastPosition();
		super.onResume();
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		switch (v.getId()) {
		case R.id.lvMain:
			mGridView.createContextMenu(menu, v, menuInfo);
			break;
		}
		super.onCreateContextMenu(menu, v, menuInfo);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		mGridView.contextItemSelected(item);
		return true;
	}

	public void refreshData() {
		mGridView.refreshData();
	}

	public void updateSeriesFilter(int position) {
		if (mGridView.getSeriesFilterMode() != position) {// initially, refreshdata gets called twice,its a waste.
			mGridView.setSeriesFilterMode(position);
			mGridView.refreshData();
		}
	}

	public void updateReadFilter(int position) {
		if (mGridView.getReadFilterMode() != position) {// initially, refreshdata gets called twice,its a waste.
			mGridView.setReadFilterMode(position);
			mGridView.refreshData();
		}
	}

	public boolean isSeriesView() {
		return mGridView.isSeriesFiltered() && mGridView.getSeriesFilter() != "";
	}

	public void closeSeriesView() {
		mGridView.setSeriesFilter("");
		mGridView.refreshData();
	}

	public int getSeriesFilterMode() {
		return mGridView.getSeriesFilterMode();
	}

	public int getReadFilterMode() {
		return mGridView.getReadFilterMode();
	}

	public String getSeriesFilter() {
		return mGridView.getSeriesFilter();
	}
}
