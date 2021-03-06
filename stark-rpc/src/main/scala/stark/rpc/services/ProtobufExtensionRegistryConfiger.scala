// Copyright 2014,2015,2016 the original author or authors. All rights reserved.
// site: http://www.ganshane.com
package stark.rpc.services

import com.google.protobuf.ExtensionRegistry

/**
 * config registry
 */
trait ProtobufExtensionRegistryConfiger {
  def config(registry: ExtensionRegistry)
}
