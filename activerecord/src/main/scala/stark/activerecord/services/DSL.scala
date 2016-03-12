package stark.activerecord.services

import javax.persistence.criteria._

import stark.activerecord.services.DSL.QueryContext

import scala.collection.generic.CanBuildFrom
import scala.collection.{GenTraversableOnce, JavaConversions}
import scala.reflect.{ClassTag, classTag}
import scala.reflect.runtime.universe._

/**
 * ActiveRecord DSL
 *
 * @author <a href="mailto:jcai@ganshane.com">Jun Tsai</a>
 * @since 2016-03-09
 */
object DSL {
  private[activerecord] val dslContext = new scala.util.DynamicVariable[QueryContext](null)

  type DSLQuery={

  }

  case class QueryContext(builder:CriteriaBuilder,query:DSLQuery,root:Root[_]){
    var isMultiSelection = false
  }

  def select[T:ClassTag]: SelectStep[T,T]={
    lazy val clazz = classTag[T].runtimeClass.asInstanceOf[Class[T]]
    lazy val queryBuilder = ActiveRecord.entityManager.getCriteriaBuilder
    lazy val query  = queryBuilder.createQuery(clazz)
    lazy val root = query.from(clazz)
    implicit lazy val queryContext = QueryContext(queryBuilder,query,root)

    new SelectStep[T,T](clazz)
  }
  def select[T:ClassTag](fields:SelectionField*): SelectStep[T,Array[Any]]={
    lazy val clazz = classTag[T].runtimeClass.asInstanceOf[Class[T]]
    lazy val queryBuilder = ActiveRecord.entityManager.getCriteriaBuilder
    lazy val query  = queryBuilder.createQuery(clazz)
    lazy val root = query.from(clazz)
    implicit lazy val queryContext = QueryContext(queryBuilder,query,root)
    new SelectStep[T,Array[Any]](clazz).apply(fields:_*)
  }
  def delete[T:ClassTag]: DeleteStep[T]={
    lazy val clazz = classTag[T].runtimeClass.asInstanceOf[Class[T]]
    lazy val queryBuilder = ActiveRecord.entityManager.getCriteriaBuilder
    lazy val query  = queryBuilder.createCriteriaDelete(clazz)
    lazy val root = query.from(clazz)
    implicit lazy val queryContext = QueryContext(queryBuilder,query,root)

    new DeleteStep[T](clazz)
  }
  def update[T:ClassTag]:UpdateStep[T]={
    lazy val clazz = classTag[T].runtimeClass.asInstanceOf[Class[T]]
    lazy val queryBuilder = ActiveRecord.entityManager.getCriteriaBuilder
    lazy val query  = queryBuilder.createCriteriaUpdate(clazz)
    lazy val root = query.from(clazz)
    implicit lazy val queryContext = QueryContext(queryBuilder,query,root)

    new UpdateStep[T](clazz)
  }
  def column[T : TypeTag](name:String):Field[T]={
    new JPAField[T](name)
  }
}
class ConditionBuilder[R](implicit val context: QueryContext) extends ConditionsGetter {
  private var condition:Option[Predicate] = None
  def apply(fun: =>Condition):this.type={
    and(fun)
    this
  }
  def or(fun: =>Condition):this.type={
    DSL.dslContext.withValue(context){
      val currentCondition = fun
      condition =Some(condition.fold(currentCondition){p=>context.builder.or(Array[Predicate](p,currentCondition):_*)})
    }
    this
  }
  def and(fun: =>Condition):this.type={
    DSL.dslContext.withValue(context){
      val currentCondition = fun
      condition =Some(condition.fold(currentCondition){p=>context.builder.and(Array[Predicate](p,currentCondition):_*)})
    }
    this
  }

  override private[activerecord] def getConditions: Option[Predicate] = condition
}
class SelectStep[T,R](clazz:Class[T])(implicit val context: QueryContext) extends Fetch[R] with Limit with ConditionsGetter with OrderBy{
  private lazy val criteriaQuery = context.query.asInstanceOf[CriteriaQuery[T]]
  def where=new ConditionBuilder[T] with Limit with Fetch[T] with OrderBy
  def apply(f:SelectionField*):this.type={
    DSL.dslContext.withValue(context){
      if(f.nonEmpty) {
        val selection = context.builder.array(f.map(_.toSelection): _*)
        criteriaQuery.select(selection.asInstanceOf[Selection[T]])

        context.isMultiSelection = true
      }
    }
    this
  }
}
class UpdateStep[T](clazz:Class[T])(implicit val context: QueryContext) extends ExecuteStep[T]{
  private lazy val criteriaUpdate = context.query.asInstanceOf[CriteriaUpdate[T]]
  def set[F](field:Field[F],value:F):this.type={
    criteriaUpdate.set(field.fieldName,value)
    this
  }
}
class DeleteStep[T](clazz:Class[T])(implicit val context: QueryContext) extends ExecuteStep[T]{
}
abstract class ExecuteStep[T](implicit context:QueryContext) extends ConditionsGetter {
  def where=new ConditionBuilder[T] with Execute[T] with Limit
}
private[activerecord] trait OrderBy {
  val context:QueryContext
  def orderBy[T](field: Field[T]): this.type ={
    orderBy(field.asc)
    this
  }
  def orderBy[T](field: SortField[T]): this.type ={
    if(field.isAsc)
      context.query.asInstanceOf[CriteriaQuery[_]].orderBy(context.builder.asc(context.root.get(field.field.fieldName)))
    else
      context.query.asInstanceOf[CriteriaQuery[_]].orderBy(context.builder.desc(context.root.get(field.field.fieldName)))
    this
  }
}
private[activerecord] trait Limit{
  private[services] var limitNum:Int = 0
  private[services] var offsetNum:Int = 0
  def limit(limit:Int):this.type={
    this.limitNum = limit
    this
  }
  def offset(offset:Int):this.type={
    this.offsetNum = offset
    this
  }
}
private[activerecord]trait ConditionsGetter{
  private[activerecord] def getConditions:Option[Predicate]=None
}
trait Execute[A]{
  this:Limit with ConditionsGetter =>
  val context:QueryContext
  def execute: Int ={
    val entityManager = ActiveRecord.entityManager
    val query = context.query match{
      case q:CriteriaUpdate[A] =>
        val criteriaUpdate = context.query.asInstanceOf[CriteriaUpdate[A]]
        getConditions.foreach(criteriaUpdate.where)
        entityManager.createQuery(criteriaUpdate)
      case q:CriteriaDelete[A] =>
        val criteriaDelete = context.query.asInstanceOf[CriteriaDelete[A]]
        getConditions.foreach(criteriaDelete.where)
        entityManager.createQuery(criteriaDelete)

    }
    if(limitNum >0 )
      query.setMaxResults(limitNum)
    if(offsetNum > 0)
      query.setFirstResult(offsetNum)

    val entityService = ActiveRecord.entityService
    entityService.execute(query)
  }
}
trait Fetch[A] {
  this:Limit with ConditionsGetter =>
  val context:QueryContext
  private lazy val executeQuery:Stream[A]= fetchAsStream
  private def fetchAsStream: Stream[A]={
    val entityManager = ActiveRecord.entityManager
    val criteriaQuery = context.query.asInstanceOf[CriteriaQuery[A]]
    getConditions.foreach(criteriaQuery.where)
    val query = entityManager.createQuery(criteriaQuery)
    if(limitNum >0 )
      query.setMaxResults(limitNum)
    if(offsetNum > 0)
      query.setFirstResult(offsetNum)

    JavaConversions.asScalaBuffer(query.getResultList).toStream
  }

  @inline final def size = executeQuery.size
  @inline final def foreach[U](f: A => U) = executeQuery.foreach(f)
  @inline final def filter[U](f: A => Boolean) = executeQuery.filter(f)
  @inline final def head = executeQuery.head
  @inline final def headOption = executeQuery.headOption
  @inline final def tail = executeQuery.tail
  @inline final def map[B, That](f: A => B)(implicit bf: CanBuildFrom[Stream[A], B, That]): That =  executeQuery.map(f)
  @inline final def flatMap[B, That](f: A => GenTraversableOnce[B])(implicit bf: CanBuildFrom[Stream[A], B, That]): That = executeQuery.flatMap(f)
}
