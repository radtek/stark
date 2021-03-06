// Copyright 2014,2015,2016 the original author or authors. All rights reserved.
// site: http://www.ganshane.com
package stark.rpc.services

import stark.utils.services.ErrorCode

/**
 * monad rpc error code
 */
object MonadRpcErrorCode {

  case object OVER_BIND_COUNT extends ErrorCode(5001)

  case object SERVER_NOT_FOUND extends ErrorCode(5002)

}
