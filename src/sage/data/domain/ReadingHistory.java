package sage.data.domain;

import java.io.Serializable;
import java.util.Date;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "reading_history")
public class ReadingHistory implements Serializable {

	private static final long serialVersionUID = -7399198261584641345L;

	@DatabaseField(generatedId = true)
	private long id;

	@DatabaseField(foreign = true, columnName = "comic", canBeNull = false, foreignAutoCreate = true)
	private Comic comic;

	@DatabaseField
	private Date date;

	public ReadingHistory() {

	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public Comic getComic() {
		return comic;
	}

	public void setComic(Comic comic) {
		this.comic = comic;
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

}
