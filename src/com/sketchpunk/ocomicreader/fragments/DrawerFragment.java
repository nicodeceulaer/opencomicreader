package com.sketchpunk.ocomicreader.fragments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import sage.adapter.LibraryDrawerAdapter;
import android.app.Activity;
import android.app.Fragment;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.widget.DrawerLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;

import com.runeai.runereader.R;

public class DrawerFragment extends Fragment {

	public interface OnSettingsSelectedListener {
		public boolean onSettingSelected(int childPosition);

		public void onReadFilterSelected(int position);

		public void onSeriesFilterSelected(int position);
	}

	private static final int SERIES_GROUP = 0;
	private static final int READING_GROUP = 1;

	OnSettingsSelectedListener mListener;
	private String[] readFilters;
	private String[] seriesFilters;
	ExpandableListView expListView;
	LibraryDrawerAdapter listAdapter;
	List<String> listDataHeader;
	HashMap<String, List<String>> listDataChild;
	DrawerChildClickListener drawerChildClickListener;
	private DrawerLayout mDrawerLayout;
	private View mFiltersDrawer;
	private SharedPreferences prefs;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_drawer, container, false);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

		mDrawerLayout = (DrawerLayout) getActivity().findViewById(R.id.drawer_layout);
		mFiltersDrawer = getActivity().findViewById(R.id.filters_drawer);

		prepareDrawer();
		checkInitialDrawerOptions();

		showNavigationDrawerOnFirstRun();
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		try {
			mListener = (OnSettingsSelectedListener) activity;
		} catch (ClassCastException e) {
			throw new ClassCastException(activity.toString() + " must implement OnSettingsSelectedListener");
		}
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

	private void prepareDrawer() {
		// get the listview
		expListView = (ExpandableListView) getView().findViewById(R.id.drawer_content);

		// preparing list data
		prepareListData();

		listAdapter = new LibraryDrawerAdapter(getView().getContext(), listDataHeader, listDataChild);

		// setting list adapter
		expListView.setAdapter(listAdapter);
		expListView.setGroupIndicator(null);

		expandAllDrawerGroups();
		disableDrawerGroupsContracting();

		addDrawerChildClickListener();
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

	private void expandAllDrawerGroups() {
		for (int i = 0; i < listDataHeader.size(); i++) {
			expListView.expandGroup(i);
		}
	}

	private void disableDrawerGroupsContracting() {
		expListView.setOnGroupClickListener(new ExpandableListView.OnGroupClickListener() {

			@Override
			public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id) {
				return true;
			}
		});
	}

	private void addDrawerChildClickListener() {
		drawerChildClickListener = new DrawerChildClickListener();
		expListView.setOnChildClickListener(drawerChildClickListener);
	}

	public void checkInitialDrawerOptions() {
		drawerChildClickListener.checkItem(SERIES_GROUP, prefs.getInt("seriesFilter", 0));
		drawerChildClickListener.checkItem(READING_GROUP, prefs.getInt("readFilter", 0));
	}

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
				return mListener.onSettingSelected(childPosition);
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

		private boolean onReadSelected(View view, int position) {
			unpress(previousReadView);

			view.setPressed(true);
			prefs.edit().putInt("readFilter", position).commit();

			mListener.onReadFilterSelected(position);

			mDrawerLayout.closeDrawer(mFiltersDrawer);
			return true;
		}

		private boolean onSeriesSelected(View view, int position) {
			unpress(previousSeriesView);

			view.setPressed(true);
			prefs.edit().putInt("seriesFilter", position).commit();

			mListener.onSeriesFilterSelected(position);

			mDrawerLayout.closeDrawer(mFiltersDrawer);
			return true;
		}

		private void unpress(View previous) {
			if (previous != null) {
				previous.setPressed(false);
			}
		}
	}

	public String[] getReadFilters() {
		return readFilters;
	}

	public String[] getSeriesFilters() {
		return seriesFilters;
	}
}