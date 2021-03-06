// Copyright 2012,2013,2015,2016 the original author or authors. All rights reserved.
// site: http://www.ganshane.com
package stark.utils.services

import stark.utils.StarkUtilsConstants


/**
 * http操作的客户端接口.
 * 用来提供http方式访问API接口
  *
  * @author jcai
 */
trait HttpRestClient {
    /**
     * 通过get方式获取远端API内容
      *
      * @param url 访问的URL
     * @param params 访问参数
     * @param headers http的头信息
     * @param encoding 请求和返回所使用的编码
     * @return API返回的字符串
     */
    def get(url:String,params:Option[Map[String,String]]=None,headers:Option[Map[String,String]]=None,
            encoding:String=StarkUtilsConstants.UTF8_ENCODING):String

    /**
     * 通过post方式获取远端API内容
      *
      * @param url 远端URL地址
     * @param params 访问参数
     * @param headers http请求使用的头
     * @param encoding 请求和返回所使用的编码
     * @return API返回的字符串
     */
    def post(url:String,params:Option[Map[String,String]]=None,headers:Option[Map[String,String]]=None,
             encoding:String=StarkUtilsConstants.UTF8_ENCODING):String
}