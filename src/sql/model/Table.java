package sql.model;

import java.util.Arrays;

public class Table 
{
    private String tableName;
    private String[] columns;
    private String[] primaryKey;

    // Getters and setters
    public String getTableName() 
    {
        return tableName;
    }

    public void setTableName(String tableName) 
    {
        this.tableName = tableName;
    }

    public String[] getColumns() 
    {
        return columns;
    }

    public void setColumns(String[] columns) 
    {
        this.columns = columns;
    }

    public String[] getPrimaryKey() 
    {
        return primaryKey;
    }

    public void setPrimaryKey(String[] primaryKey) 
    {
        this.primaryKey = primaryKey;
    }

	@Override
	public String toString() {
		return "Table [tableName=" + tableName + ", columns=" + Arrays.toString(columns) + ", primaryKey="
				+ Arrays.toString(primaryKey) + "]";
	}


}
