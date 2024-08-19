package sql;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import sql.model.Table;

public static void main(String[] args) {
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		Transferable transferable = clipboard.getContents(null); // 클립보드에서 데이터 가져오기
		Table table = null;

		if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
			try {
				StringBuilder stringBuilder = new StringBuilder(
						(String) transferable.getTransferData(DataFlavor.stringFlavor));
				System.out.println("원본\n" + stringBuilder.toString() + "\n\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");

				table = handleQuery(detectQueryType(stringBuilder.toString()), stringBuilder);

				System.out.println("수정 후\n\n" + (table != null ? table.toString() : "Parsing Error"));
				clipboard.setContents(new StringSelection(table.getAllSQL()), null); // 클립보드에 다시 넣기
			} catch (StringIndexOutOfBoundsException e) {
				System.out.println("SQL 구문 오류");
			} catch (IllegalArgumentException e) {
				System.out.println("CREATE DDL 구문 오류");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private static String detectQueryType(String query) {
		for (String type : Arrays.asList("CREATE", "DELETE", "UPDATE", "INSERT", "SELECT")) {
			if (query.contains(type)) {
				return type;
			}
		}
		return null; // Default to null if no type is found
	}

	private static Table handleQuery(String queryType, StringBuilder stringBuilder) {
		switch (queryType) {
		case "CREATE":
			return tableToQuery(parseDDL(stringBuilder.toString()));
		case "DELETE":
			return delete(stringBuilder);
		case "UPDATE":
			return update(stringBuilder);
		case "INSERT":
			return insert(stringBuilder);
		case "SELECT":
			return select(stringBuilder);
		default:
			return null;
		}
	}

	private static void getAllSQL(Table table) {
		StringBuilder concatAllSQL = new StringBuilder();

		concatAllSQL.append("================================================================\n");
		table.setSelectSQL(getSelectSQL(table));
		concatAllSQL.append("SELECT\n\n" + table.getSelectSQL());
		concatAllSQL.append("\n\n================================================================");

		concatAllSQL.append("================================================================\n");
		table.setInsertSQL(getInsertSQL(table));
		concatAllSQL.append("INSERT\n\n" + table.getInsertSQL());
		concatAllSQL.append("\n\n================================================================");

		concatAllSQL.append("================================================================\n");
		table.setUpdateSQL(getUpdateSQL(table));
		concatAllSQL.append("UPDATE\n\n" + table.getUpdateSQL());
		concatAllSQL.append("\n\n================================================================");

		concatAllSQL.append("================================================================\n");
		table.setDeleteSQL(getDeleteSQL(table));
		concatAllSQL.append("DELETE\n\n" + table.getDeleteSQL());
		concatAllSQL.append("\n\n================================================================");

		table.setAllSQL(concatAllSQL.toString());
	}

	private static Table insert(StringBuilder parseQuery) {
		Table table = new Table();
		String tableName = subString(parseQuery, "INSERT INTO ", "(");
		table.setTableName(
				tableName.contains(".") ? subString(parseQuery, ".", "(") : subString(parseQuery, "INSERT INTO ", ";"));
		table.setAlias(getAlias(table.getTableName()));
		table.setColumns(subString(parseQuery, "(", ")").split(","));
		setColumnsToPrimaryKey(table);
		getAllSQL(table);
		return table;
	}

	private static String getInsertSQL(Table table) {
		String columns = Arrays.stream(table.getColumns()).map(String::trim).collect(Collectors.joining(",\n\t"));
		String values = Arrays.stream(table.getColumns()).map(column -> "#{" + snakeToCamel(column.trim()) + "}")
				.collect(Collectors.joining(",\n\t"));
		return String.format("INSERT INTO %s\n(\n\t%s\n)\nVALUES\n(\n\t%s\n)", table.getTableName(), columns, values);
	}

	private static void setColumnsToPrimaryKey(Table table) {
		table.setPrimaryKey(
				Arrays.stream(table.getColumns()).filter(column -> column.contains("id")).toArray(String[]::new));
	}

	private static Table delete(StringBuilder parseQuery) {
		Table table = new Table();
		table.setTableName(subString(parseQuery, "FROM ", "WHERE").contains(".") ? subString(parseQuery, ".", "WHERE")
				: subString(parseQuery, "FROM ", "WHERE"));
		table.setAlias(getAlias(table.getTableName()));
		table.setPrimaryKey(pairArrayToSingle(subString(parseQuery, "WHERE ", ";").split("AND")));
		table.setColumns(table.getPrimaryKey());
		getAllSQL(table);
		return table;
	}

	private static String getDeleteSQL(Table table) {
		return "DELETE FROM\n\t" + table.getTableName() + "\nWHERE\n\t" + whereClause(table);
	}

	private static Table update(StringBuilder parseQuery) {
		Table table = new Table();
		table.setTableName(subString(parseQuery, "UPDATE ", "SET").contains(".") ? subString(parseQuery, ".", "SET")
				: subString(parseQuery, "UPDATE ", "SET"));
		table.setAlias(getAlias(table.getTableName()));
		table.setPrimaryKey(pairArrayToSingle(subString(parseQuery, "WHERE ", ";").split("AND")));
		table.setColumns(
				Stream.concat(Arrays.stream(pairArrayToSingle(subString(parseQuery, "SET ", "WHERE").split(","))),
						Arrays.stream(table.getPrimaryKey())).toArray(String[]::new));
		getAllSQL(table);
		return table;
	}

	private static String getUpdateSQL(Table table) {
		String setClause = Arrays.stream(table.getColumns())
				.map(column -> column.trim() + " = #{" + snakeToCamel(column.trim()) + "}")
				.collect(Collectors.joining(",\n\t"));
		return String.format("UPDATE\n\t%s\nSET\n\t%s\nWHERE\n\t%s", table.getTableName(), setClause,
				whereClause(table));
	}

	private static String whereClause(Table table) {
		return Arrays.stream(table.getPrimaryKey())
				.map(column -> column.trim() + " = #{" + snakeToCamel(column.trim()) + "}")
				.collect(Collectors.joining("\n\tAND "));
	}

	private static String[] pairArrayToSingle(String[] arr) {
		return Arrays.stream(arr).map(String::trim).map(column -> column.split("=")[0].trim()).toArray(String[]::new);
	}

	private static Table select(StringBuilder parseQuery) {
		Table table = new Table();
		String columns = subString(parseQuery, "SELECT ", "FROM");
		table.setColumns(columns.split(","));
		table.setTableName(subString(parseQuery, "FROM ", ";").contains(".") ? subString(parseQuery, ".", ";")
				: subString(parseQuery, "FROM ", ";"));
		table.setAlias(getAlias(table.getTableName()));
		setColumnsToPrimaryKey(table);
		getAllSQL(table);
		return table;
	}

	private static String getSelectSQL(Table table) {
		String columnAliases = Arrays.stream(table.getColumns()).map(String::trim)
				.map(column -> table.getAlias() + "." + column + " AS " + snakeToCamel(column))
				.collect(Collectors.joining(",\n\t"));
		return String.format("SELECT\n\t%s\nFROM\n\t%s AS %s", columnAliases, table.getTableName(), table.getAlias());
	}

	private static String getAlias(String tableName) {
		return Arrays.stream(tableName.split("_")).map(word -> word.substring(0, 1)).collect(Collectors.joining());
	}

	public static Table parseDDL(String ddl) {
		Table table = new Table();

		// 테이블 이름 추출
		Matcher tableNameMatcher = Pattern.compile("CREATE TABLE `(\\w+)`").matcher(ddl);
		if (tableNameMatcher.find()) {
			table.setTableName(tableNameMatcher.group(1));
		}
		table.setAlias(getAlias(table.getTableName()));

		// 컬럼 이름 추출
		List<String> columns = new ArrayList<>();
		String[] definitions = ddl.substring(ddl.indexOf("(") + 1, ddl.lastIndexOf(")")).trim().split(",");
		Pattern columnPattern = Pattern.compile("`([^`]*)`\\s+[^,]+"); // 컬럼 정의 정규 표현식
		for (String definition : definitions) {
			definition = definition.trim();
			if (!definition.startsWith("PRIMARY KEY") && !definition.startsWith("FOREIGN KEY")
					&& !definition.startsWith("UNIQUE") && !definition.startsWith("INDEX")
					&& !definition.startsWith("CONSTRAINT") && !definition.startsWith("COMMENT")) {
				Matcher columnMatcher = columnPattern.matcher(definition);
				if (columnMatcher.find()) {
					columns.add(columnMatcher.group(1));
				}
			}
		}

		// PRIMARY KEY 추출
		Matcher pkMatcher = Pattern.compile("PRIMARY KEY \\(`([^`]*)`(?:,`[^`]*)*`\\)").matcher(ddl);
		List<String> primaryKeys = new ArrayList<>();
		while (pkMatcher.find()) {
			Collections.addAll(primaryKeys, pkMatcher.group(1).split("`,`"));
		}

		table.setColumns(columns.toArray(new String[0]));
		table.setPrimaryKey(primaryKeys.toArray(new String[0]));
		return table;
	}

	public static Table tableToQuery(Table table) {
		getAllSQL(table);
		return table;
	}

	public static String paramToCoalesce(String alias, String param) {
		return "COALESCE(" + alias + "." + param + ", '') AS " + param;
	}

	public static String paramSet(String alias, String param) {
		return alias + "." + param + " AS " + param;
	}

	private static String snakeToCamel(String param) {
		return IntStream.range(0, param.split("_").length).mapToObj(i -> {
			String word = param.split("_")[i];
			return i == 0 ? word : capitalize(word);
		}).collect(Collectors.joining());
	}

	private static String capitalize(String word) {
		if (word.isEmpty())
			return word;
		return Character.toUpperCase(word.charAt(0)) + word.substring(1);
	}

	private static String paramToNull(String param) {
		return "CASE WHEN TRIM(#{" + param + "}) = '' THEN NULL ELSE TRIM(#{" + param + "}) END";
	}

	private static String paramSet(String param) {
		return "#{" + param + "}";
	}

	private static boolean isIn(String str, String con) {
		return str.contains(con);
	}

	private static String subString(StringBuilder stringBuilder, String condition1, String condition2) {
		return stringBuilder
				.substring(stringBuilder.indexOf(condition1) + condition1.length(), stringBuilder.indexOf(condition2))
				.trim();
	}

	private static String subString(String str, String condition, boolean isFront) {
		return isFront ? str.substring(0, str.indexOf(condition)).trim() : str.substring(str.indexOf(condition)).trim();
	}

	private static void deleteStringBuilder(StringBuilder stringBuilder, String condition) {
		stringBuilder.delete(0, stringBuilder.indexOf(condition));
	}

	private static void deleteStringBuilder(StringBuilder stringBuilder, int condition) {
		stringBuilder.delete(0, condition);
	}
}
