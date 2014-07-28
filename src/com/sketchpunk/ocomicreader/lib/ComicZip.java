package com.sketchpunk.ocomicreader.lib;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ComicZip implements iComicArchive {
	ZipFile mArchive;

	// I need to load a stream twice to read an image, so
	// instead of finding the same item again, save ref.
	ZipEntry mLastItemReq = null;
	String mLastItemReqPath = "";

	/*--------------------------------------------------------
	 */
	public ComicZip() {
	}// func

	/*--------------------------------------------------------
	 */
	@Override
	public void clearCache() {
	}

	@Override
	public boolean isStreamResetable() {
		return false;
	}

	@Override
	public void close() {
		mLastItemReq = null;
		if (mArchive != null) {
			try {
				mArchive.close();
				mArchive = null;
			} catch (Exception e) {
			}// try
		}// if
	}// func

	@Override
	public boolean loadFile(String path) {
		try {
			mArchive = new ZipFile(path);
			return true;
		} catch (Exception e) {
		}// try;
		return false;
	}// func

	/*--------------------------------------------------------
	 */
	@Override
	public List<String> getPageList() {
		try {
			String itmName;
			ZipEntry itm;
			List<String> pageList = new ArrayList<String>();
			Enumeration entries = mArchive.entries();

			// ..................................
			while (entries.hasMoreElements()) {
				itm = (ZipEntry) entries.nextElement();
				if (itm.isDirectory())
					continue;

				itmName = itm.getName().toLowerCase(Locale.getDefault());
				if (itmName.endsWith(".jpg") || itmName.endsWith(".gif")
						|| itmName.endsWith(".png")) {
					pageList.add(itm.getName());
				}// if
			}// while

			// ..................................
			if (pageList.size() > 0) {
				Collections.sort(pageList, Strings.getNaturalComparator()); // Sort the page names
				return pageList;
			}// if
		} catch (Exception e) {
			System.err.println("LoadArchive " + e.getMessage());
		}// try

		return null;
	}// func

	@Override
	public InputStream getItemInputStream(String path) {
		try {
			if (mLastItemReqPath.equals(path) && mLastItemReq != null) {
				return mArchive.getInputStream(mLastItemReq);
			}// if

			// ....................................
			mLastItemReqPath = path;
			mLastItemReq = mArchive.getEntry(path);
			return mArchive.getInputStream(mLastItemReq);
		} catch (Exception e) {
		}

		return null;
	}// func

	@Override
	public boolean getLibraryData(String[] outVar) {
		outVar[0] = "0"; // Page Count
		outVar[1] = ""; // Path to Cover Entry
		outVar[2] = ""; // Path to Meta Data

		try {
			int pgCnt = 0;
			String itmName, compare, coverPath = "", metaPath = "";

			ZipEntry itm;
			Enumeration<? extends ZipEntry> entries = mArchive.entries();

			// ..................................
			while (entries.hasMoreElements()) {
				itm = entries.nextElement();
				if (itm.isDirectory())
					continue;

				itmName = itm.getName();
				compare = itmName.toLowerCase(Locale.getDefault());
				if (compare.endsWith(".jpg") || compare.endsWith(".gif")
						|| compare.endsWith(".png")) {
					if (pgCnt == 0 || itmName.compareTo(coverPath) < 0)
						coverPath = itmName;
					pgCnt++;
				} else if (compare.endsWith("comicinfo.xml"))
					metaPath = itmName;

			}// while

			if (pgCnt > 0) {
				outVar[0] = Integer.toString(pgCnt);
				outVar[1] = coverPath;
				outVar[2] = metaPath;
			}// if
		} catch (Exception e) {
			System.err.println("getLibraryData " + e.getMessage());
			return false;
		}// try

		return true;
	}// func

	@Override
	public String[] getMeta() {
		// ......................................................
		// Find Meta data in archive
		String metaPath = "";
		try {
			ZipEntry itm;
			String itmName, compare;
			Enumeration<? extends ZipEntry> entries = mArchive.entries();

			// ..................................
			while (entries.hasMoreElements()) {
				itm = entries.nextElement();
				if (itm.isDirectory())
					continue;

				itmName = itm.getName();
				compare = itmName.toLowerCase(Locale.getDefault());
				if (compare.endsWith("comicinfo.xml")) {
					metaPath = itmName;
					break;
				}
			}// while

			if (metaPath.isEmpty())
				return null;
		} catch (Exception e) {
			System.err.println("error getting meta data " + e.getMessage());
			return null;
		}// try

		// ......................................................
		// Parse the meta data.
		String[] data = null;
		try {
			InputStream iStream = getItemInputStream(metaPath);
			data = MetaParser.ComicRack(iStream);
			iStream.close();
		} catch (IOException e) {
			System.err.println("getting meta from zip " + e.getMessage());
		}// try

		// ......................................................
		return data;
	}// func
}// cls
