package sage.data;

import java.sql.SQLException;

import sage.data.domain.Comic;
import sage.data.domain.ReadingHistory;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import com.sketchpunk.ocomicreader.R;

public class DatabaseHelper extends OrmLiteSqliteOpenHelper {

	private static final String DATABASE_NAME = "comicreader.db";

	private static final int DATABASE_VERSION = 1;

	private Dao<Comic, Integer> comicDao = null;
	private Dao<ReadingHistory, Integer> readingHistoryDao = null;

	private RuntimeExceptionDao<Comic, Integer> comicRuntimeDao = null;
	private RuntimeExceptionDao<ReadingHistory, Integer> readingHistoryRuntimeDao = null;

	public DatabaseHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION, R.raw.ormlite_config);
	}

	@Override
	public void onCreate(SQLiteDatabase arg0, ConnectionSource arg1) {
		try {
			Log.i(DatabaseHelper.class.getName(), "onCreate");
			TableUtils.createTable(connectionSource, Comic.class);
			TableUtils.createTable(connectionSource, ReadingHistory.class);
		} catch (SQLException e) {
			Log.e(DatabaseHelper.class.getName(), "Can't create database", e);
			throw new RuntimeException(e);
		}

	}

	@Override
	public void onUpgrade(SQLiteDatabase arg0, ConnectionSource arg1, int arg2, int arg3) {
		// TODO: nothing here yet, as we use new database
	}

	public Dao<Comic, Integer> getComicDao() throws SQLException {
		if (comicDao == null) {
			comicDao = getDao(Comic.class);
		}
		return comicDao;
	}

	public Dao<ReadingHistory, Integer> getReadingHistoryDao() throws SQLException {
		if (readingHistoryDao == null) {
			readingHistoryDao = getDao(ReadingHistory.class);
		}
		return readingHistoryDao;
	}

	public RuntimeExceptionDao<Comic, Integer> getComicRuntimeDao() throws SQLException {
		if (comicRuntimeDao == null) {
			comicRuntimeDao = getRuntimeExceptionDao(Comic.class);
		}
		return comicRuntimeDao;
	}

	public RuntimeExceptionDao<ReadingHistory, Integer> getReadingHistoryRuntimeDao() throws SQLException {
		if (readingHistoryRuntimeDao == null) {
			readingHistoryRuntimeDao = getRuntimeExceptionDao(ReadingHistory.class);
		}
		return readingHistoryRuntimeDao;
	}

	@Override
	public void close() {
		super.close();
		comicDao = null;
		readingHistoryDao = null;

		comicRuntimeDao = null;
		readingHistoryRuntimeDao = null;
	}
}
