package sage.data;

import com.j256.ormlite.android.apptools.OrmLiteConfigUtil;

public class DataBaseConfigUtil extends OrmLiteConfigUtil {
	public static void main(String[] args) throws Exception {
		writeConfigFile("ormlite_config.txt");
	}
}
