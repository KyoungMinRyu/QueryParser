package sql.model;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class Table {
	private String tableName;
	private String alias;
	private String[] columns;
	private String[] primaryKey;
	private String selectSQL;
	private String insertSQL;
	private String updateSQL;
	private String deleteSQL;
	private String allSQL;
	private Map<String, String> columnJavaTypeMap = new LinkedHashMap<>();
	private Map<String, String> columnCommentMap = new LinkedHashMap<>();

	// Getters and setters

	public Map<String, String> getColumnCommentMap() {
		return columnCommentMap;
	}

	public void setColumnCommentMap(Map<String, String> columnCommentMap) {
		this.columnCommentMap = columnCommentMap;
	}

	public Map<String, String> getColumnJavaTypeMap() {
		return columnJavaTypeMap;
	}

	public void setColumnJavaTypeMap(Map<String, String> columnJavaTypeMap) {
		this.columnJavaTypeMap = columnJavaTypeMap;
	}

	public String getAllSQL() {
		return allSQL;
	}

	public void setAllSQL(String allSQL) {
		this.allSQL = allSQL;
	}

	public String getAlias() {
		return alias;
	}

	public void setAlias(String alias) {
		this.alias = alias;
	}

	public String getUpdateSQL() {
		return updateSQL;
	}

	public void setUpdateSQL(String updateSQL) {
		this.updateSQL = updateSQL;
	}

	public String getInsertSQL() {
		return insertSQL;
	}

	public void setInsertSQL(String insertSQL) {
		this.insertSQL = insertSQL;
	}

	public String getSelectSQL() {
		return selectSQL;
	}

	public void setSelectSQL(String selectSQL) {
		this.selectSQL = selectSQL;
	}

	public String getDeleteSQL() {
		return deleteSQL;
	}

	public void setDeleteSQL(String deleteSQL) {
		this.deleteSQL = deleteSQL;
	}

	public String getTableName() {
		return tableName;
	}

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	public String[] getColumns() {
		return columns;
	}

	public void setColumns(String[] columns) {
		this.columns = columns;
	}

	public String[] getPrimaryKey() {
		return primaryKey;
	}

	public void setPrimaryKey(String[] primaryKey) {
		this.primaryKey = primaryKey;
	}

	@Override
	public String toString() {
		return String.format("Table Schema[tableName=%s, alias=%s, columns=%s, primaryKey=%s]\n\n\n%s",
			tableName, alias, Arrays.toString(columns), Arrays.toString(primaryKey), allSQL);
	}
}
