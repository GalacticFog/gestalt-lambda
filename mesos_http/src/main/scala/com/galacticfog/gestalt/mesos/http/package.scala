package com.galacticfog.gestalt.lambda.impl

import org.apache.mesos.Protos
import play.api.libs.json.Json

package object http {

  implicit val offerIdParser = Json.format[OfferID]
  implicit val frameworkIdParser = Json.format[FrameworkID]
  implicit val agentIDParser = Json.format[AgentID]

  implicit val scalarParser = Json.format[Scalar]
  implicit val rangeParser = Json.format[Range]
  implicit val rangeEnvelopeParser = Json.format[RangeEnvelope]
  implicit val resourceParser = Json.format[Resource]

  implicit val addressParser = Json.format[Address]
  implicit val urlParser = Json.format[URL]

  implicit val offerParser = Json.format[Offer]
  implicit val caseParser = Json.format[OfferEnvelope]
  implicit val offerEventParser = Json.format[OfferEvent]

  implicit val subscribedParser = Json.format[Subscribed]
  implicit val subscribedEnvelopeParser = Json.format[SubscribedResponseEnvelope]

  implicit val envVarParser = Json.format[EnvironmentVariable]
  implicit val environmentParser = Json.format[Environment]

  implicit val commandUriParser = Json.format[CommandInfoUri]
  implicit val commandInfoParser = Json.format[CommandInfo]

  implicit val parameterParser = Json.format[Parameter]
  implicit val portMappingParser = Json.format[PortMapping]

  implicit val mesosAppcParser = Json.format[MesosAppC]
  implicit val dockerInfoParser = Json.format[DockerInfo]
  implicit val dockerParser = Json.format[Docker]
  implicit val imageDockerParser = Json.format[Image]
  implicit val mesosInfoParser = Json.format[MesosInfo]

  implicit val volumeParser = Json.format[Volume]
  implicit val ipAddressParser = Json.format[IPAddress]

  implicit val networkInfoParser = Json.format[NetworkInfo]
  implicit val containerInfoParser = Json.format[ContainerInfo]

  implicit val executorIdParser = Json.format[ExecutorID]
  implicit val executorInfoParser = Json.format[ExecutorInfo]

  implicit val taskIDParser = Json.format[TaskID]
  implicit val taskInfoParser = Json.format[TaskInfo]

  implicit val launchParser = Json.format[Launch]
  implicit val reserveParser = Json.format[Reserve]
  implicit val unreserveParser = Json.format[Unreserve]
  implicit val createParser = Json.format[Create]
  implicit val destroyParser = Json.format[Destroy]

  implicit val operationParser = Json.format[Operation]

  implicit val filterParser = Json.format[Filter]
  implicit val acceptParser = Json.format[Accept]
  implicit val acceptRequestParser = Json.format[AcceptRequest]

}
