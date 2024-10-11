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

public class QueryParser {
	public static void main(String[] args) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable transferable = clipboard.getContents(null); // 클립보드에서 데이터 가져오기
        Table table = null;

        if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            try {
                StringBuilder stringBuilder = new StringBuilder(
                    (String) transferable.getTransferData(DataFlavor.stringFlavor));
                System.out.println("원본\n" + stringBuilder.toString() + "\n\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");

                table = handleQuery(detectQueryType(stringBuilder.toString()), stringBuilder, false, true, false);

                System.out.println("수정 후\n\n" + (table != null ? table.toString() : "Parsing Error"));
                clipboard.setContents(new StringSelection(table.getAllSQL()), null); // 클립보드에 다시 넣기
            } catch (NameNotFoundException e) {
                System.out.println("Is Not SQL");
            } catch (StringIndexOutOfBoundsException e) {
                System.out.println("SQL Error");
            } catch (IllegalArgumentException e) {
                System.out.println("CREATE DDL SQL Error");
            } catch (NullPointerException e) {
                System.out.println("Collectors Stream Error");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * <pre>
     * 메소드명 : detectQueryType
     * 작성자 : LAM
     * 작성일 : 2024.10.10
     * @param String query
     * @return String query
     * @throws NameNotFoundException
     * @apiNote 들어온 SQL 쿼리 문을 CREATE, DELETE, UPDATE, INSERT, SELECT를 선택한다.
     * </pre>
     */
    private static String detectQueryType(String query) throws NameNotFoundException {
        return Arrays.asList("CREATE", "DELETE", "UPDATE", "INSERT", "SELECT").stream().filter(query::contains)
            .findFirst()
            .orElseThrow(() -> new NameNotFoundException("Query type not found"));
    }

    private static Table handleQuery(
        String queryType, StringBuilder sql, boolean firstLineDrop, boolean isCommaFront, boolean newAlias) {
        switch (queryType) {
            case "CREATE":
                return tableToQuery(parseDDL(sql), firstLineDrop, isCommaFront, newAlias);
            case "DELETE":
                return tableToQuery(delete(sql), firstLineDrop, isCommaFront, newAlias);
            case "UPDATE":
                return tableToQuery(update(sql), firstLineDrop, isCommaFront, newAlias);
            case "INSERT":
                return tableToQuery(insert(sql), firstLineDrop, isCommaFront, newAlias);
            case "SELECT":
                return tableToQuery(select(sql), firstLineDrop, isCommaFront, newAlias);
            default:
                throw new IllegalArgumentException("Unsupported query type: " + queryType);
        }
    }

    private static String extractTableName(StringBuilder parseQuery, String startKeyword, String endKeyword) {
        String tableName = subString(parseQuery, startKeyword, endKeyword);
        return tableName.contains(".") ? subString(parseQuery, ".", endKeyword) : tableName;
    }


    private static Table insert(StringBuilder parseQuery) {
        Table table = new Table();
        table.setTableName(extractTableName(parseQuery, "INSERT INTO ", "("));
        table.setAlias(getAlias(table.getTableName()));
        table.setColumns(subString(parseQuery, "(", ")").split(","));
        setColumnsToPrimaryKey(table);
        return table;
    }

    private static String getInsertSQL(Table table, boolean firstLineDrop, boolean isCommaFront) {
        String columns = Arrays.stream(table.getColumns()).map(String::trim)
            .collect(Collectors.joining(isCommaFront ? "\n\t, " : ",\n\t"));
        String values = Arrays.stream(table.getColumns()).map(column -> "#{" + snakeToCamel(column.trim()) + "}")
            .collect(Collectors.joining(isCommaFront ? "\n\t, " : ",\n\t"));
        return String.format(firstLineDrop ? "INSERT INTO %s\n(\n\t%s\n)\nVALUES\n(\n\t%s\n)" :
            "INSERT INTO %s (\n\t%s\n\t)\nVALUES (\n\t%s\n\t)", table.getTableName(), columns, values);
    }

    private static void setColumnsToPrimaryKey(Table table) {
        table.setPrimaryKey(
            Arrays.stream(table.getColumns()).filter(column -> (column.contains("id") || column.contains("seq")))
                .toArray(String[]::new));
    }

    private static Table delete(StringBuilder parseQuery) {
        Table table = new Table();
        table.setTableName(extractTableName(parseQuery, "FROM ", "WHERE"));
        table.setAlias(getAlias(table.getTableName()));
        table.setPrimaryKey(pairArrayToSingle(subString(parseQuery, "WHERE ", ";").split("AND")));
        table.setColumns(table.getPrimaryKey());
        return table;
    }

    private static String getDeleteSQL(Table table, boolean firstLineDrop) {
        return (firstLineDrop ? "DELETE FROM\n\t" : "DELETE\nFROM ") + table.getTableName() + "\nWHERE\n\t"
               + whereClause(table);
    }

    private static Table update(StringBuilder parseQuery) {
        Table table = new Table();
        table.setTableName(extractTableName(parseQuery, "UPDATE ", "SET"));
        table.setAlias(getAlias(table.getTableName()));
        table.setPrimaryKey(pairArrayToSingle(subString(parseQuery, "WHERE ", ";").split("AND")));
        table.setColumns(
            Stream.concat(Arrays.stream(pairArrayToSingle(subString(parseQuery, "SET ", "WHERE").split(","))),
                Arrays.stream(table.getPrimaryKey())).toArray(String[]::new));
        return table;
    }

    private static String getUpdateSQL(Table table, boolean firstLineDrop, boolean isCommaFront) {
        String setClause = Arrays.stream(table.getColumns())
            .map(column -> column.trim() + " = #{" + snakeToCamel(column.trim()) + "}")
            .collect(Collectors.joining(isCommaFront ? "\n\t, " : ",\n\t"));
        return String.format(firstLineDrop ? "UPDATE\n\t%s\nSET\n\t%s\nWHERE\n\t%s" : "UPDATE %s\nSET %s\nWHERE\n\t%s",
            table.getTableName(), setClause,
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
        table.setTableName(extractTableName(parseQuery, "FROM ", ";"));
        table.setAlias(getAlias(table.getTableName()));
        setColumnsToPrimaryKey(table);
        return table;
    }

    private static String getSelectSQL(Table table, boolean firstLineDrop, boolean isCommaFront, boolean newAlias) {
        String columnAliases = Arrays.stream(table.getColumns()).map(String::trim)
            .map(column -> newAlias ? table.getAlias() + "." + column + " AS " + snakeToCamel(column) :
                table.getAlias() + "." + column)
            .collect(Collectors.joining(isCommaFront ? "\n\t, " : ",\n\t"));
        return String.format(firstLineDrop ? "SELECT\n\t%s\nFROM\n\t%s AS %s" : "SELECT %s\nFROM %s AS %s",
            columnAliases, table.getTableName(), table.getAlias());
    }

    private static String getAlias(String tableName) {
        return Arrays.stream(tableName.split("_")).map(word -> word.substring(0, 1)).collect(Collectors.joining());
    }

    public static Table parseDDL(StringBuilder parseQuery) {
        Table table = new Table();

        // 테이블 이름 추출
        Matcher tableNameMatcher = Pattern.compile("CREATE TABLE `(\\w+)`").matcher(parseQuery.toString());
        if (tableNameMatcher.find()) {
            table.setTableName(tableNameMatcher.group(1));
        }
        table.setAlias(getAlias(table.getTableName()));

        // 컬럼 이름 추출
        List<String> columns = new ArrayList<>();
        String ddl = parseQuery.toString();
        String[] definitions = ddl.substring(ddl.indexOf("(") + 1, ddl.lastIndexOf(")")).trim().split("\n");
        Pattern columnPattern = Pattern.compile("`([^`]*)`\\s+[^,]+"); // 컬럼 정의 정규 표현식
        for (String definition : definitions) {
            definition = definition.trim();
            if (!definition.startsWith("PRIMARY KEY") && !definition.startsWith("FOREIGN KEY")
                && !definition.startsWith("UNIQUE") && !definition.startsWith("INDEX")
                && !definition.startsWith("CONSTRAINT") && !definition.startsWith("COMMENT")
                && !definition.startsWith("KEY") && !definition.startsWith("REFERENCES")) {
                Matcher columnMatcher = columnPattern.matcher(definition);
                if (columnMatcher.find()) {
                    columns.add(columnMatcher.group(1));
                }
            }
        }

        // PRIMARY KEY 추출
        List<String> primaryKeys = new ArrayList<>();
        Matcher pkMatcher = Pattern.compile("PRIMARY KEY \\(([^)]+)\\)").matcher(ddl);
        while (pkMatcher.find()) {
            String[] pkCols = pkMatcher.group(1).split("`,`");
            Collections.addAll(primaryKeys, pkCols);
        }

        table.setColumns(columns.toArray(new String[0]));
        table.setPrimaryKey(primaryKeys.stream().map(column -> column.replaceAll("[`'\"\\s]+", ""))
            .collect(Collectors.toList()).toArray(new String[0]));
        return table;
    }

    public static Table tableToQuery(Table table, boolean firstLineDrop, boolean isCommaFront, boolean newAlias) {
        StringBuilder concatAllSQL = new StringBuilder();

        concatAllSQL.append("================================================================\n\n");
        table.setSelectSQL(getSelectSQL(table, firstLineDrop, isCommaFront, newAlias));
        concatAllSQL.append(table.getSelectSQL());
        concatAllSQL.append("\n\n================================================================");

        concatAllSQL.append("================================================================\n\n");
        table.setInsertSQL(getInsertSQL(table, firstLineDrop, isCommaFront));
        concatAllSQL.append(table.getInsertSQL());
        concatAllSQL.append("\n\n================================================================");

        concatAllSQL.append("================================================================\n\n");
        table.setUpdateSQL(getUpdateSQL(table, firstLineDrop, isCommaFront));
        concatAllSQL.append(table.getUpdateSQL());
        concatAllSQL.append("\n\n================================================================");

        concatAllSQL.append("================================================================\n\n");
        table.setDeleteSQL(getDeleteSQL(table, firstLineDrop));
        concatAllSQL.append(table.getDeleteSQL());
        concatAllSQL.append("\n\n================================================================");

        table.setAllSQL(concatAllSQL.toString());

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
        if (word.isEmpty()) {
            return word;
        }
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
