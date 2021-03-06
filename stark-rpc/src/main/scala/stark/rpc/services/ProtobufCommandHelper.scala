// Copyright 2014,2015,2016 the original author or authors. All rights reserved.
// site: http://www.ganshane.com
package stark.rpc.services

import com.google.protobuf.GeneratedMessage
import stark.rpc.protocol.CommandProto.{BaseCommand, CommandStatus}

/**
 * protocol command helper trait
 */
trait ProtobufCommandHelper {
  def wrap[T](extension: GeneratedMessage.GeneratedExtension[BaseCommand, T], value: T): BaseCommand = {
    BaseCommand.newBuilder().setExtension(extension, value).setTaskId(-1L).setStatus(CommandStatus.OK).build()
  }

  def wrap[T](taskId: Long, extension: GeneratedMessage.GeneratedExtension[BaseCommand, T], value: T): BaseCommand = {
    BaseCommand.newBuilder().setExtension(extension, value).setTaskId(taskId).setStatus(CommandStatus.OK).build()
  }
}
