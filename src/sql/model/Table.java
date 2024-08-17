package sql.model;

import java.util.Arrays;

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

	// Getters and setters

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
