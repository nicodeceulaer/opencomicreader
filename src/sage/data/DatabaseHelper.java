package sage.data;

import java.sql.SQLException;

import sage.data.domain.Comic;
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

	private static final int DATABASE_VERSION = 2;

	private Dao<Comic, Integer> comicDao = null;

	private RuntimeExceptionDao<Comic, Integer> comicRuntimeDao = null;

	public DatabaseHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION, R.raw.ormlite_config);
	}

	@Override
	public void onCreate(SQLiteDatabase arg0, ConnectionSource arg1) {
		try {
			Log.i(DatabaseHelper.class.getName(), "onCreate");
			TableUtils.createTable(connectionSource, Comic.class);
		} catch (SQLException e) {
			Log.e(DatabaseHelper.class.getName(), "Can't create database", e);
			throw new RuntimeException(e);
		}

	}

	@Override
	public void onUpgrade(SQLiteDatabase arg0, ConnectionSource arg1, int oldVersion, int newVersion) {
		if (oldVersion < 2) { // Simplified database by changing reading history to a field
			RuntimeExceptionDao<Comic, Integer> dao;
			try {
				dao = this.getComicRuntimeDao();
				dao.executeRaw("ALTER TABLE `comics` ADD COLUMN dateRead DATE;");
				dao.executeRaw("DROP TABLE `reading_history`;");
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	public Dao<Comic, Integer> getComicDao() throws SQLException {
		if (comicDao == null) {
			comicDao = getDao(Comic.class);
		}
		return comicDao;
	}

	public RuntimeExceptionDao<Comic, Integer> getComicRuntimeDao() throws SQLException {
		if (comicRuntimeDao == null) {
			comicRuntimeDao = getRuntimeExceptionDao(Comic.class);
		}
		return comicRuntimeDao;
	}

	@Override
	public void close() {
		super.close();
		comicDao = null;

		comicRuntimeDao = null;
	}
}
