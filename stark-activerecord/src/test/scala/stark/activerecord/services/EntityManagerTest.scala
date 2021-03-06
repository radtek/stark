package stark.activerecord.services

import javax.persistence._
import javax.persistence.criteria.Predicate

import org.junit.{Assert, Test}
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionDefinition
import stark.activerecord.{ModelA, BaseActiveRecordTestCase}

/**
 *
 * @author <a href="mailto:jcai@ganshane.com">Jun Tsai</a>
 * @since 2016-01-03
 */
class EntityManagerTest extends BaseActiveRecordTestCase{
  @Test
  def test_save: Unit ={

    val transaction = getService[PlatformTransactionManager]
    val transactionDef = new DefaultTransactionDefinition()
    val status = transaction.getTransaction(transactionDef)
    val entityManager = getService[EntityManager]
    val modelA = new ModelA
    modelA.name = "xxx"
    entityManager.persist(modelA)
    transaction.commit(status)
    Assert.assertTrue(modelA.id>0)

    val builder = entityManager.getCriteriaBuilder
    val q = builder.createQuery(classOf[ModelA])
    val p = q.from(classOf[ModelA])

    val expr: Predicate = builder.equal(p.get("name"),"xxx")
    q.where(Array(expr):_*)

    val query = entityManager.createQuery(q);
    query.getResultList

    val results = entityManager.createQuery("from ModelA").getResultList
    Assert.assertEquals(1,results.size())
  }
}
