package stark.activerecord.services

import java.util.Date

import org.junit.{Assert, Test}
import stark.activerecord.services.QueryExpression.{Between, Gt, Like, NotNull}
import stark.activerecord.{BaseActiveRecordTestCase, ModelA, ModelB}

/**
 *
 * @author <a href="mailto:jcai@ganshane.com">Jun Tsai</a>
 * @since 2016-01-03
 */
class ActiveRecordTest extends BaseActiveRecordTestCase{
  @Test
  def test_find_by: Unit = {
    val modelA = new ModelA
    modelA.name = "asdf"
    modelA.save
    Array(1).sum
    var size = ModelA.find_by(name="asdf",id=Gt(0),name=NotNull,name=Like("a%"),id=Between(0,10000)).size
    Assert.assertEquals(1,size)
    size = ModelA.find_by.eq("name","asdf").gt("id",0).notNull("name").like("name","a%").between("id",0,10000).size
    Assert.assertEquals(1,size)

    ModelA.find_by(name="asdf").update(name="fdsa")
    size = ModelA.find_by(name="asdf",id=Gt(0)).size
    Assert.assertEquals(0,size)

    Assert.assertEquals(1,ModelA.all.size)

    Assert.assertEquals(0,ModelA.find_by(name="asdf").delete)
    Assert.assertEquals(1,ModelA.find_by(name="fdsa").delete)
  }
  @Test
  def test_update: Unit = {
    val modelA = new ModelA
    modelA.name = "asdf"
    modelA.save
    Assert.assertEquals(1,ModelA.all.size)

    val modeB = ModelA.find(modelA.id)
    modeB.name="fdsa"
    modeB.save
    Assert.assertEquals(1,ModelA.all.size)

    var modelB = new ModelB
    modelB.save
    Assert.assertEquals(1,ModelB.all.size)
    modelB = ModelB.find(modelB.id)
    modelB.name="fdsa"
    modelB.save
    Assert.assertEquals(1,ModelB.all.size)

  }

  @Test
  def test_save: Unit ={
    val modelA = new ModelA
    modelA.name = "asdf"
    modelA.save()

    val dsl = ModelA.find_by_name_and_id("adsf1",1).asc("name").desc("name")
    //Assert.assertEquals("name asc,name desc",dsl.orderBy.get)
    val dsl2 = ModelA.find_by_name_and_id("adsf1",1).order("name"->"asc","name"->"desc")
    //Assert.assertEquals(1,list.size)
    val size= ModelA.find_by_name_and_id("asdf",modelA.id).size
    Assert.assertEquals(1,size)
    Assert.assertEquals(0, ModelA.find_by_name("fdsa").size)

    modelA.delete()
    Assert.assertEquals(0, ModelA.find_by_name("asdf").size)
  }
  @Test
  def test_find: Unit ={
    val modelA = new ModelA
    modelA.name = "asdf"
    modelA.save()

    ModelA.find_by_name(modelA.name)
    ModelA.find_by_name(modelA.name)
    var modelA1 = ModelA.take
    Assert.assertEquals("asdf",modelA1.name)
    modelA1 = ModelA.all.take
    Assert.assertEquals("asdf",modelA1.name)

    val result = ModelA.find(modelA.id)
    Assert.assertEquals("asdf",result.name)

    Assert.assertTrue(ModelA.find_by(name="asdf").exists())
    Assert.assertFalse(ModelA.find_by(name="fdsa").exists())

    var size = ModelA.find_by(name="asdf").size
    Assert.assertEquals(1,size)

    size = ModelA.find_by_name("asdf").limit(10).size
    Assert.assertEquals(1,size)

    size = ModelA.find_by(name="asdf",id=modelA.id).size
    Assert.assertEquals(1,size)

    size = ModelA.where("name=?1","asdf")
      .offset(0).limit(10).asc("name").size
    Assert.assertEquals(1,size)

    ModelA.where("name=?1","asdf").update(name="asdf")
    ModelA.find_by(name="asdf").update(name="asdf")
    ModelA.where("name=?1","asdf").delete
  }
  @Test
  def test_lob: Unit ={
    val modelA = new ModelA()
    modelA.clob= Range(1,10000).mkString(",")
    modelA.blob= Range(1,10000).mkString(",").getBytes()
    modelA.save()
  }
  @Test
  def test_date: Unit ={
    val modelA = new ModelA()
    modelA.date = new Date()
    modelA.save()
  }
}