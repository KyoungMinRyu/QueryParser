package SQL;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;

public class QueryParser
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
                if(isIn(stringBuilder.toString(), "DELETE"))
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
    			query.append(paramToNVL(param) + "\n"); // CASE WHEN (IFNULL(#{prm_type}, '') = '') THEN NULL ELSE #{prm_type} END
			else
    			query.append(paramToNVL(param) +  ",\n\t");
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
    			query.append(" = " + paramToNVL(param) + "\n");
			else
    			query.append(" = " + paramToNVL(param) + ",\n\t");
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
    			query.append("COALESCE(" + alias + "." + param + ", '') AS " + param + "\n");
			else
    			query.append("COALESCE(" + alias + "." + param + ", '') AS " + param + ",\n\t");
    	}
    	query.append("FROM\n\t");
    	query.append(subString(parseQuery, " ", ";"));
    	query.append(" " + alias);
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
	 * 메소드명 : paramToNVL
	 * @param String param
	 * @return String
	 * 설명 : 파라미터가 공백이면 NULL로 설정하는 문으로 바꿈
	 **/
	private static String paramToNVL(String param)
	{
		return "CASE WHEN (COALESCE(TRIM(#{" + param + "}), '') = '') THEN NULL ELSE TRIM(#{" + param + "}) END";
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
