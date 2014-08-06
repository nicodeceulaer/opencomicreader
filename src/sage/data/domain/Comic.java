package sage.data.domain;

import java.io.Serializable;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "comics")
public class Comic implements Serializable {

	private static final long serialVersionUID = -3439184021177920049L;

	@DatabaseField(generatedId = true)
	private int id;

	@DatabaseField
	private String title;

	@DatabaseField
	private String path;

	@DatabaseField
	private boolean coverExists;

	@DatabaseField
	private int pageCount;

	@DatabaseField
	private int pageRead;

	@DatabaseField
	private int pageCurrent;

	@DatabaseField
	private String series;

	@DatabaseField
	private int issue;

	public Comic() {

	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public boolean isCoverExists() {
		return coverExists;
	}

	public void setCoverExists(boolean coverExists) {
		this.coverExists = coverExists;
	}

	public int getPageCount() {
		return pageCount;
	}

	public void setPageCount(int pageCount) {
		this.pageCount = pageCount;
	}

	public int getPageRead() {
		return pageRead;
	}

	public void setPageRead(int pageRead) {
		this.pageRead = pageRead;
	}

	public int getPageCurrent() {
		return pageCurrent;
	}

	public void setPageCurrent(int pageCurrent) {
		this.pageCurrent = pageCurrent;
	}

	public String getSeries() {
		return series;
	}

	public void setSeries(String series) {
		this.series = series;
	}

	public int getIssue() {
		return issue;
	}

	public void setIssue(int issue) {
		this.issue = issue;
	}

}
