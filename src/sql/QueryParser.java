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

import sql.model.Table;

public class QueryParsing 
{
   public static void main(String [] args)
   {
      Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable transferable = clipboard.getContents(null); // 클립보드에서 데이터 가져오기 
        StringBuilder query = new StringBuilder();
        if(transferable != null && transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) 
        {
            try 
            {
               StringBuilder stringBuilder = new StringBuilder((String) transferable.getTransferData(DataFlavor.stringFlavor));
                System.out.println(stringBuilder.toString());
                if(isIn(stringBuilder.toString(), "CREATE"))
                   tableToQuery(query, parseDDL(stringBuilder.toString()));
                else if(isIn(stringBuilder.toString(), "DELETE"))
                   delete(query, stringBuilder);
                else if(isIn(stringBuilder.toString(), "UPDATE"))
                   update(query, stringBuilder);
                else if(isIn(stringBuilder.toString(), "INSERT"))
                   insert(query, stringBuilder);
                else
                   select(query, stringBuilder);
            }  
            catch(Exception e) 
            {
                e.printStackTrace();
            }
            System.out.println(query.toString());
            clipboard.setContents(new StringSelection(query.toString()), null); // 클립보드에 다시 넣기
        }
   }
   
   private static void insert(StringBuilder query, StringBuilder parseQuery) 
   {
      query.append(subString(parseQuery, "(", true) + "\n(\n\t");
       deleteStringBuilder(parseQuery, "(");
       String[] params = subString(parseQuery, "(", ")").split(",");
       query.append(subString(parseQuery, "(", ")").replaceAll(", ", ",\n\t"));
       query.append("\n)\nVALUES\n(\n\t");
       String param = "";
       for(int i = 0; i < params.length; i++)
       {
          param = params[i].trim();
          if(i == params.length - 1)
             query.append(paramSet(snakeToCamel(param)) + "\n"); // query.append(paramToNull(snakeToCamel(param)) + "\n"); // CASE WHEN (IFNULL(#{prm_type}, '') = '') THEN NULL ELSE #{prm_type} END
         else
            query.append(paramSet(snakeToCamel(param)) +  ",\n\t"); // query.append(paramToNull(snakeToCamel(param)) +  ",\n\t");
       }
       query.append(")");
   }
   
   private static void delete(StringBuilder query, StringBuilder parseQuery)
   {
      query.append("DELETE FROM\n\t");
       query.append(subString(parseQuery, "DELETE FROM", "\n"));
       deleteStringBuilder(parseQuery, "\n");
       query.append("\nWHERE\n\t1 = 1");
       deleteStringBuilder(parseQuery, "WHERE ".length());
       query.append(whereClause(parseQuery.toString().split("AND")));
   }

   private static void update(StringBuilder query, StringBuilder parseQuery)
   {
      query.append("UPDATE\n\t");
       query.append(subString(parseQuery, "UPDATE", "\n"));
       deleteStringBuilder(parseQuery, "\n");
       query.append("\nSET\n\t");
       deleteStringBuilder(parseQuery, "SET ".length());
       String[] params = subString(parseQuery, "\n", true).split(",");
       deleteStringBuilder(parseQuery, "\n");
       String param = "";
       for(int i = 0; i < params.length; i++)
       {
          param = params[i].trim();
          param = param.substring(0, param.indexOf("=")).trim();
         query.append(param);
         if(i == params.length - 1)
             query.append(" = " + paramSet(snakeToCamel(param)) + "\n");
         else
             query.append(" = " + paramSet(snakeToCamel(param)) + ",\n\t");
       }
       deleteStringBuilder(parseQuery, "WHERE ".length());
       params = parseQuery.toString().split("AND");
       query.append("WHERE\n\t1 = 1");
       query.append(whereClause(params));
   }

   private static void select(StringBuilder query, StringBuilder parseQuery)
   {
      deleteStringBuilder(parseQuery, " ");
       query.append("SELECT\n\t");
       String[] params = subString(parseQuery, "\n", true).split(",");
       deleteStringBuilder(parseQuery, "FROM");
       String[] table = subString(parseQuery, " ", ";").indexOf(".") > -1 ? subString(parseQuery, ".", ";").split("_") : subString(parseQuery, " ", ";").split("_");
       String param = "", alias = "";
       for(int i = 0; i < table.length; i++)
       {
          alias += table[i].toString().substring(0, 1);
       }
       for(int i = 0; i < params.length; i++)
       {
          param = params[i].trim();
          if(i == params.length - 1)
             query.append(paramSet(alias, param) + "\n"); // query.append(paramToCoalesce(alias, param) + "\n");
         else
            query.append(paramSet(alias, param) + ",\n\t"); // query.append(paramToCoalesce(alias, param) + ",\n\t");
       }
       query.append("FROM\n\t");
       query.append(subString(parseQuery, " ", ";"));
       query.append(" " + alias);
   }

   /**
    * <pre>
    * 메소드명 : parseDDL
    * @param String ddl
    * @return Table
    * 설명 : DDL분석을 통해 TABLE 스키마 정보를 얻는다
    **/
   public static Table parseDDL(String ddl) {
      Table table = new Table();

        // 테이블 이름 추출
      Matcher tableNameMatcher = Pattern.compile("CREATE TABLE `(\\w+)`").matcher(ddl);
        if (tableNameMatcher.find()) {
            table.setTableName(tableNameMatcher.group(1));
        }

        // 컬럼 정의 추출
        Matcher columnMatcher = Pattern.compile("`(\\w+)`\\s+(\\w+\\(\\d+\\)(?:\\(\\d+\\))?)([^,]*)").matcher(ddl);
        List<String> columns = new ArrayList<>();
        while (columnMatcher.find()) {
            columns.add(columnMatcher.group(1));
        }
        System.out.println(columns);
        // 기본 키 추출
        Matcher pkMatcher = Pattern.compile("PRIMARY KEY \\(`(\\w+)`\\)").matcher(ddl);
        List<String> primaryKeys = new ArrayList<>();
        if (pkMatcher.find()) {
            primaryKeys.add(pkMatcher.group(1));
        }
        System.out.println(primaryKeys);

        table.setColumns(columns.toArray(new String[0]));
        table.setPrimaryKey(primaryKeys.toArray(new String[0]));

        return table;
    }

   /**
    * <pre>
    * 메소드명 : tableToQuery
    * @param StringBuilder query
    * @param Table table
    * @return void
    * 설명 : Table 객체를 통해 INSERT, DELETE, UPDATE, SELECT 쿼리 생성
    **/
   public static void tableToQuery(StringBuilder query, Table table)
   {
      System.out.println(table.toString());
   }

   /**
    * <pre>
    * 메소드명 : paramToCoalesce
    * @param String param
    * @return String
    * 설명 : SELECT절 NULL일 경우 ""공백으로 변경하는 쿼리
    **/
   public static String paramToCoalesce(String alias, String param)
   {
      return "COALESCE(" + alias + "." + param + ", '') AS " + param;
   }

   /**
    * <pre>
    * 메소드명 : paramSet
    * @param String param
    * @return String
    * 설명 : 그냥 SELECT절 세팅
    **/
   public static String paramSet(String alias,String param)
   {
      return "" + alias + "." + param + " AS " + param;
   }
   
   /**
    * <pre>
    * 메소드명 : snakeToCamel
    * @param String param
    * @return String
    * 설명 : snake Case로 작성된 문자를 Camel Case로 변경한다.
    **/
   private static String snakeToCamel(String param) 
   {
      return IntStream.range(0, param.split("_").length)
            .mapToObj(i -> 
            {
               String word = param.split("_")[i];
               return i == 0 ? word : capitalize(word);
            }).collect(Collectors.joining());
   }

   /**
    * <pre>
    * 메소드명 : capitalize
    * @param String word
    * @return String
    * 설명 : 해당 단어의 첫 글자를 대문자로 변경
    **/
   private static String capitalize(String word) 
   {
      if (word.isEmpty())
         return word;
      return Character.toUpperCase(word.charAt(0)) + word.substring(1);
   }
   
   /**
    * <pre>
    * 메소드명 : whereClause
    * @param String[] params
    * @return String
    * 설명 : WHERE절 세팅
    **/
   private static String whereClause(String[] params)
   {
      StringBuilder query = new StringBuilder();
      String param = "";
      for(int i = 0; i < params.length; i++)
       {
           query.append("\n\tAND ");
          param = params[i].trim();
          param = param.substring(0, param.indexOf("=")).trim();
          System.out.println(param);
         query.append(param);
         query.append(" = #{" + param + "}");
       }
      return query.toString();
   }
   
   /**
    * <pre>
    * 메소드명 : paramToNull
    * @param String param
    * @return String
    * 설명 : 파라미터가 공백이면 NULL로 설정하는 문으로 바꿈
    **/
   private static String paramToNull(String param)
   {
      return "CASE WHEN TRIM(#{" + param + "}) = '' THEN NULL ELSE TRIM(#{" + param + "}) END";
   }
   
   /**
    * <pre>
    * 메소드명 : paramSet
    * @param String param
    * @return String
    * 설명 : 단순 MyBatis매핑
    **/
   private static String paramSet(String param)
   {
      return "#{" + param + "}";
   }
   
   
   /**
    * <pre>
    * 메소드명 : isIn
    * @param String str
    * @param String con 
    * @return boolean
    * 설명 : 문자열(str)안에 해당 문자열(con)을 포함하면 true
    **/
   private static boolean isIn(String str, String con) 
   {
      if(str.indexOf(con) > -1)
         return true;
      else 
         return false;
   }
   
   /**
    * <pre>
    * 메소드명 : subString
    * @param StringBuilder stringBuilder
    * @param String condition1
    * @param String condition2 
    * @return String
    * 설명 : stringBuilder의 문자열을 condition1(indexOf) + condition1.length, condition2(indexOf)으로 자른 뒤 반환
    **/
   private static String subString(StringBuilder stringBuilder, String condition1, String condition2) throws IndexOutOfBoundsException 
   {
      return stringBuilder.substring(stringBuilder.indexOf(condition1) + condition1.length(), stringBuilder.indexOf(condition2)).trim();
   }
   
   /**
    * <pre>
    * 메소드명 : subString
    * @param String str
    * @param String condition
    * @return String
    * 설명 : String의 문자열을 0, indexOf(condition) 잘라서 반환
    **/
   private static String subString(StringBuilder stringBuilder, String condition, boolean isFront) throws IndexOutOfBoundsException 
   {
      return subString(stringBuilder.toString(), condition, isFront);
   }
   
   /**
    * <pre>
    * 메소드명 : subString
    * @param String str
    * @param String condition
    * @return String
    * 설명 : String의 문자열을 0, indexOf(condition) 잘라서 반환
    **/
   private static String subString(String str, String condition, boolean isFront) throws IndexOutOfBoundsException 
   {
      if(isFront)
         return str.substring(0, str.indexOf(condition)).trim();
      else
         return str.substring(str.indexOf(condition)).trim();
   }
   
   /**
    * <pre>
    * 메소드명 : deleteStringBuilder
    * @param StringBuilder stringBuilder
    * @param String condition
    * @return void
    * 설명 : stringBuilder를 condition(indexOf), stringBuilder.length()으로 삭제
    **/
   private static void deleteStringBuilder(StringBuilder stringBuilder, String condition) throws IndexOutOfBoundsException 
   {
      stringBuilder.delete(0, stringBuilder.indexOf(condition));
   }
   
   /**
    * <pre>
    * 메소드명 : deleteStringBuilder
    * @param StringBuilder stringBuilder
    * @param int condition
    * @return void
    * 설명 : stringBuilder를 condition(indexOf), stringBuilder.length()으로 삭제
    **/
   private static void deleteStringBuilder(StringBuilder stringBuilder, int condition) throws IndexOutOfBoundsException 
   {
      stringBuilder.delete(0, condition);
   }
}
