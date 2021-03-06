// Copyright 2016 the original author or authors. All rights reserved.
// site: http://www.ganshane.com
/**
 * Copyright (c) 2015 Jun Tsai <jcai@ganshane.com>
 */
package stark.migration

import java.sql.{DatabaseMetaData, ResultSet}
import java.text.SimpleDateFormat
import java.util.Date

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, ListBuffer}

/**
 * support dump schema
 * @author <a href="mailto:jcai@ganshane.com">Jun Tsai</a>
 * @since 2015-07-29
 */
trait SchemaDumperSupport {
  this:Migrator =>
  //private final val TABLE_TYPES =Array[String] ( "TABLE", "VIEW", "SYNONYM" )
  private final val TABLE_TYPE =Array[String] ( "TABLE")
  protected val COLUMN_NAME = 4
  protected val DATA_TYPE = 5
  protected val TYPE_NAME = 6
  protected val COLUMN_SIZE = 7
  protected val DECIMAL_DIGITS = 9
  protected val COLUMN_DEF = 13
  protected val IS_NULLABLE = 18
  //列定义
  private case class Column(name:String,sqlType:SqlType,options:Array[ColumnOption]) extends Ordered[Column]{
    override def compare(that: Column): Int = name.compareTo(that.name)
  }
  private case class Index(name:String,columns:ArrayBuffer[String],isUnique:Boolean)

  /**
   * 得到所有的表名
   * @return 表的集合
   */
  def tables():Seq[String]={
    withLoggingConnection(AutoCommit) { connection =>
      val schemaPattern = adapter.schemaNameOpt match {
        case Some(n) => adapter.unquotedNameConverter(n)
        case None => null
      }
      val metadata = connection.getMetaData
      With.autoClosingResultSet(metadata.getTables(null,
        schemaPattern,
        null,
        TABLE_TYPE)) { rs =>
        val buffer  = new ListBuffer[String]()
        while (rs.next()) {
          buffer += rs.getString(3)
        }
        buffer.sorted.toSeq
      }
    }
  }
  def sequences():Seq[String]={
    adapter.findSequencesSql() match {
      case Some(sql) =>
        withLoggingConnection(AutoCommit) { connection =>
          val stmt = connection.createStatement()
          With.autoClosingStatement(stmt) { s =>
            val resultSet = s.executeQuery(sql)
            val buffer = new ListBuffer[String]()
            With.autoClosingResultSet(resultSet){rs=>
              while(rs.next())
                buffer += rs.getString(1)
            }

            buffer.toSeq
          }
        }
      case None =>
        Seq()
    }

  }
  // NOTE: metaData.getIndexInfo row mappings :
  protected val INDEX_INFO_TABLE_NAME = 3
  protected val INDEX_INFO_NON_UNIQUE = 4
  protected val INDEX_INFO_NAME = 6
  protected val INDEX_INFO_COLUMN_NAME = 9

  private def indexes(table:String): Seq[Index] ={
    withLoggingConnection(AutoCommit){connection=>
      val metaData = connection.getMetaData
      val schemaPattern = adapter.schemaNameOpt match {
        case Some(n) => adapter.unquotedNameConverter(n)
        case None => null
      }

      val primaryKeys = findPrimaryKeys(metaData,schemaPattern,table)

      @tailrec
      def fetchIndexInfo(rs:ResultSet,
                         currentIndexName:String,
                         indexColumns:ArrayBuffer[String],
                         buf:ListBuffer[Index]): Unit ={
        var columns = indexColumns
        if(rs.next()){
          val indexName = rs.getString(INDEX_INFO_NAME)
          val columnName = rs.getString(INDEX_INFO_COLUMN_NAME)
          if(indexName != null){
            if(!primaryKeys.contains(columnName)){//非主键字段
              if(indexName != currentIndexName){//新的
                columns = new ArrayBuffer[String]()
                buf += Index(indexName,columns,!rs.getBoolean(INDEX_INFO_NON_UNIQUE))
              }
              columns += columnName
            }
          }
          fetchIndexInfo(rs,indexName,columns,buf)
        }
      }
      val indexInfoSet = metaData.getIndexInfo(null, schemaPattern, table, false, true);
      With.autoClosingResultSet(indexInfoSet){rs=>
        val buf = new ListBuffer[Index]()
        fetchIndexInfo(rs,null,new ArrayBuffer[String](),buf)
        buf.toSeq
      }
    }
  }
  private def dumpIndex(tableName:String,index:Index)(implicit sb:mutable.StringBuilder): Unit ={
    sb.append(s"""    addIndex(\"${tableName}\",""")
    sb.append("Array[String](")
    sb.append(index.columns.map("\""+_+"\"").mkString(","))
    sb.append(")")
    if(index.isUnique)
      sb.append(",Unique")
    sb.append(s""",Name(\"${index.name}\"))\n""")
  }
  def dumpSequence(sequence:String)(implicit sb:mutable.StringBuilder): Unit ={
    sb.append(s"""    sequence(\"${sequence}\")\n""")
  }
  def dumpDropSequence(sequence:String)(implicit sb:mutable.StringBuilder): Unit ={
    sb.append(s"""    dropSequence(\"${sequence}\")\n""")
  }
  private val timestampReg = "TIMESTAMP([\\(\\d+\\)]*)".r
  protected def typeFromResultSet(resultSet:ResultSet):SqlType ={
    resultSet.getString(TYPE_NAME) match{
      case "BIGINT"=>
        BigintType
      case "BLOB"|"LONGBLOB"|"BYTEA" =>
        BlobType
      case "BOOLEAN" =>
        BooleanType
      case "CHAR" =>
        CharType
      case "DECIMAL" =>
        DecimalType
      case "INTEGER" =>
        IntegerType
      case "SMALLINT" =>
        SmallintType
      case timestampReg(n) =>
        TimestampType
      case "DATE" => //TODO oracle
        TimestampType
      case "VARBINARY"|"VARCHAR FOR BIT DATA"|"RAW" =>
        VarbinaryType
      case "VARCHAR"|"VARCHAR2" =>
        VarcharType
      case "NUMBER" => //for oracle number
        val precision = intFromResultSet(resultSet,COLUMN_SIZE)
        if(precision <=5 )
          SmallintType
        else if(precision <=10)
          IntegerType
        else
          BigintType
      case "LONG" => // oracle
        //throw new UnsupportedColumnTypeException("LONG")
        VarcharType
      case "CLOB" =>
        ClobType

      case other=>
        throw new UnsupportedColumnTypeException(other)
    }
  }
  protected def intFromResultSet(resultSet:ResultSet, column:Int):Int = {
    val precision = resultSet.getInt(column)
    if( precision == 0 && resultSet.wasNull() )  -1 else precision
  }
  private def columns(table:String): Seq[Column] ={
    withLoggingConnection(AutoCommit) { connection =>
      val schemaPattern = adapter.schemaNameOpt match {
        case Some(n) => adapter.unquotedNameConverter(n)
        case None => null
      }
      val metaData = connection.getMetaData
      val primaryKeys = findPrimaryKeys(metaData, schemaPattern,table)
      val columns = metaData.getColumns(null, schemaPattern, table, null)
      With.autoClosingResultSet(columns) { rs =>
        var buffer = new ListBuffer[Column]()
        while (rs.next()) {
          val columnOptions = new ArrayBuffer[ColumnOption]()
          val name = rs.getString(COLUMN_NAME)
          val defaultValue = rs.getString(COLUMN_DEF)
          if (defaultValue != null)
            columnOptions += Default(defaultValue)

          val sqlType = typeFromResultSet(rs)
          if (sqlType == null)
            throw new RuntimeException("tableName:" + table + " columnName:" + name)

          val precision = intFromResultSet(rs, COLUMN_SIZE);
          val scale = intFromResultSet(rs, DECIMAL_DIGITS);
          if (sqlType == DecimalType) {
            if (precision > 0)
              columnOptions += Precision(precision)
            if (scale > 0)
              columnOptions += Scale(scale)
          } else {
            if (precision > 0)
              columnOptions += Limit(precision)
          }

          //是否为空
          val nullable = rs.getString(IS_NULLABLE).trim() != "NO"
          if (nullable) columnOptions += Nullable else columnOptions += NotNull

          //列注释
          val colCommentSql = adapter.fetchColumnCommentSql(table, name)
          val commentOpt = fetchSingleResult(colCommentSql)
          commentOpt.foreach(x => columnOptions += Comment(x.replaceAll("\n","\\n")))

          //是否为主键
          if (primaryKeys.contains(name)) {
            columnOptions += PrimaryKey
          }

          buffer += Column(name, sqlType, columnOptions.toArray)
        }
        buffer.toSeq
      }
    }
  }
  def dumpTable(table:String)(implicit sb:mutable.StringBuilder):Unit = {
    try {
      val tableCommentSql = adapter.fetchTableCommentSql(table)
      val commentOpt = fetchSingleResult(tableCommentSql)

      sb.append( s"""    createTable(\"${table}\"""")
      commentOpt.foreach(x => sb.append(",").append(Comment(x).toTypeString))
      sb.append("){ t=> \n")
      columns(table).foreach { c =>
        sb.append( s"""      t.column(\"${c.name}\",${c.sqlType}""")
        c.options.foreach { o =>
          sb.append(",").append(o.toTypeString)
        }
        sb.append(")\n")
      }
      sb.append("    }\n")

      indexes(table).foreach(x => dumpIndex(table, x))
    }catch{
      case e:Throwable =>
        System.err.println("fail to dump table:"+table+" msg:"+e)
    }
  }
  def dumpDropTable(table:String)(implicit sb:mutable.StringBuilder): Unit ={
    sb.append(s"""    dropTable("${table}")""").append("\n")
  }
  private def fetchSingleResult(sql:String): Option[String]={
    withLoggingConnection(AutoCommit) { connection =>
      val stmt = connection.createStatement();
      With.autoClosingStatement(stmt) { s =>
        val resultSet = s.executeQuery(sql)
        With.autoClosingResultSet(resultSet) { rs =>
          if (rs.next()) Option(rs.getString(1)) else None
        }
      }
    }
  }
  protected val PRIMARY_KEYS_COLUMN_NAME = 4
  private def findPrimaryKeys(metaData:DatabaseMetaData , schemaPattern:String, tableName:String): Seq[String]={
    val resultSet = metaData.getPrimaryKeys(null,schemaPattern,tableName)
    With.autoClosingResultSet(resultSet){rs=>
      val buf = new ListBuffer[String]()
      while(rs.next()){
        buf += rs.getString(PRIMARY_KEYS_COLUMN_NAME)
      }
      buf.toSeq
    }
  }
  def dumpHead()(implicit sb:mutable.StringBuilder): Unit ={

    val dateStr = new SimpleDateFormat("YYYYMMddHHmmss").format(new Date())
    sb.append(s"""
import stark.migration._

class Migrate_${dateStr}_Init
  extends Migration {

  def up(): Unit = {
""".stripMargin)
  }
  def dumpMiddle()(implicit sb:mutable.StringBuilder): Unit = {
    sb.append("""
  }

  def down() {
    """.stripMargin)
  }
  def dumpFooter()(implicit sb:mutable.StringBuilder): Unit ={
    sb.append("""
      | }
      |}
    """.stripMargin)
  }
}
