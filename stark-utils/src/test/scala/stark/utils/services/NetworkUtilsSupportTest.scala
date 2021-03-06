// Copyright 2015,2016 the original author or authors. All rights reserved.
// site: http://www.ganshane.com
package stark.utils.services

import org.junit.Test

/**
 * @author <a href="mailto:jcai@ganshane.com">Jun Tsai</a>
 */
class NetworkUtilsSupportTest {
  @Test
  def test_ip: Unit = {
    val support = new NetworkUtilsSupport with LoggerSupport {}
    println(support.ip("10.1.7.*"))
    println(support.ip("127.*"))
  }

}
