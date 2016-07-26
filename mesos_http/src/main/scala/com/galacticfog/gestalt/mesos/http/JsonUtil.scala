package com.galacticfog.gestalt.lambda.impl.http

import org.apache.mesos.Protos
import play.api.libs.json.Json
import collection.JavaConverters._

case class OfferID( value : String )
case class FrameworkID( value : String ) {
  def toProtos : Protos.FrameworkID = {
    Protos.FrameworkID.newBuilder.setValue( value ).build
  }
}

case class AgentID( value : String ) {
  def toProtos : Protos.SlaveID = {
    Protos.SlaveID.newBuilder.setValue( value ).build
  }
}

case class Scalar( value : Double )
case class Range( begin : Long, end : Long )
case class RangeEnvelope( range : Seq[Range] )
case class Resource(
  name : String,
  role : String,
  `type` : String,
  //TODO : here we need conditional parsing based on the type and
  scalar : Option[Scalar] = None,
  ranges : Option[RangeEnvelope] = None
) {
  def toProtos : Protos.Resource = {

    val builder =  Protos.Resource.newBuilder

    builder.setName( name )
    builder.setRole( role )
    builder.setType( Protos.Value.Type.valueOf( `type` ) )

    `type` match {
      case "SCALAR" => {
        builder.setScalar( Protos.Value.Scalar.newBuilder
          .setValue( scalar.get.value )
        )
        builder.setType( Protos.Value.Type.SCALAR )
      }
      case "RANGES" => {
        val pRanges = ranges.get.range.map{ range =>
          Protos.Value.Range.newBuilder
            .setBegin( range.begin )
            .setEnd( range.end )
            .build
        }
        builder.setRanges( Protos.Value.Ranges.newBuilder
          .addAllRange( pRanges.toIterable.asJava )
        )
      }
    }
    builder.build
  }
}

object Resource {
  def make( res : Protos.Resource ) = {
    val r =new Resource(
      name = res.getName,
      role = res.getRole,
      `type` = res.getType.getValueDescriptor.getName )
    r.`type` match {
      case "SCALAR" => {
        r.copy( scalar = Some( new Scalar( res.getScalar.getValue ) ) )
      }
      case "RANGES" => {
        r.copy( ranges = Some( new RangeEnvelope(
          range = res.getRanges.getRangeList.asScala.map { ran =>
            new Range( ran.getBegin, ran.getEnd )
          }
        ) ) )
      }
    }

  }
}

case class Address( hostname : String, ip : String, port : Long ) {
  def toProtos : Protos.Address = {
    Protos.Address.newBuilder
      .setHostname( hostname )
      .setIp( ip )
      .setPort( port.toInt )
      .build
  }
}
case class URL( address : Address, path : String, scheme : String ) {
  def toProtos : Protos.URL = {
    Protos.URL.newBuilder
      .setAddress( address.toProtos )
      .setPath( path )
      .setScheme( scheme )
      .build
  }
}

case class Offer(
  id : OfferID,
  framework_id : FrameworkID,
  agent_id : AgentID,
  hostname : String,
  resources : Seq[Resource],
  url : URL
) {

  def toProtos : Protos.Offer = {

    val pResources = resources.map{ thing =>
      thing.toProtos
    }

    Protos.Offer.newBuilder
      .setId( Protos.OfferID.newBuilder.setValue( id.value ) )
      .setFrameworkId( Protos.FrameworkID.newBuilder.setValue( framework_id.value ) )
      .setSlaveId( Protos.SlaveID.newBuilder.setValue( agent_id.value ) )
      .setHostname( hostname )
      .addAllResources( pResources.toIterable.asJava )
      .setUrl( url.toProtos )
      .build
  }
}

case class OfferEnvelope(
  offers : List[Offer]
)

case class OfferEvent(
  `type` : String,
  offers : OfferEnvelope
)

case class Launch(
  task_infos : Seq[TaskInfo]
) {
  def toProtos : Protos.Offer.Operation.Launch = {
    Protos.Offer.Operation.Launch.newBuilder.addAllTaskInfos( task_infos.map( _.toProtos ).asJava ).build
  }
}

case class Reserve(
  resources : Seq[Resource]
) {
  def toProtos : Protos.Offer.Operation.Reserve = {
    Protos.Offer.Operation.Reserve.newBuilder.addAllResources( resources.map( _.toProtos ).asJava ).build
  }
}

case class Unreserve(
  resources : Seq[Resource]
) {
  def toProtos : Protos.Offer.Operation.Unreserve = {
    Protos.Offer.Operation.Unreserve.newBuilder.addAllResources( resources.map( _.toProtos ).asJava ).build
  }
}

case class Create(
  volumes : Seq[Resource]
) {
  def toProtos : Protos.Offer.Operation.Create = {
    Protos.Offer.Operation.Create.newBuilder.addAllVolumes( volumes.map( _.toProtos ).asJava ).build
  }
}

case class Destroy(
  volumes : Seq[Resource]
) {
  def toProtos : Protos.Offer.Operation.Destroy = {
    Protos.Offer.Operation.Destroy.newBuilder.addAllVolumes( volumes.map( _.toProtos ).asJava ).build
  }
}

case class Operation(
  `type` : String,
  launch : Option[Launch] = None,
  reserve : Option[Reserve] = None,
  unreserve : Option[Unreserve] = None,
  create : Option[Create] = None,
  destroy : Option[Destroy] = None
) {
  def toProtos: Protos.Offer.Operation = {
    val builder = Protos.Offer.Operation.newBuilder
    builder.setType( Protos.Offer.Operation.Type.valueOf( `type` ) )
    `type` match {
      case "ACCEPT" => {
        builder.setLaunch( launch.get.toProtos )
      }
      case "RESERVE" => {
        builder.setReserve( reserve.get.toProtos )
      }
      case "UNRESERVE" => {
        builder.setUnreserve( unreserve.get.toProtos )
      }
      case "CREATE" => {
        builder.setCreate( create.get.toProtos )
      }
      case "DESTROY" => {
        builder.setDestroy( destroy.get.toProtos )
      }
    }
    builder.build
  }
}
object Request{

  def launch( tasks : Seq[Protos.TaskInfo], offers : Seq[Protos.OfferID], frameworkID : String ): AcceptRequest = {

    val off = offers.map( thing => new OfferID( value = thing.getValue ) )
    val ops = tasks.map{ t=>
      val bareOp = new Operation( "LAUNCH" )
      val op = new TaskInfo(
          name = t.getName,
          task_id = new TaskID( t.getTaskId.getValue ),
          agent_id = new AgentID( t.getSlaveId.getValue ),
          resource = t.getResourcesList.asScala.map( res => Resource.make(res) )
      )
      val taskInfo = if( t.hasCommand )
      {
        val c = t.getCommand
        op.copy( command = Some( CommandInfo.make( c ) ))
      }
      else if( t.hasExecutor )
      {
        op.copy( executor = Some( ExecutorInfo.make( t.getExecutor ) ) )
      }
      else if( t.hasContainer )
      {
        op.copy( container = Some( ContainerInfo.make( t.getContainer ) ) )
      }
      else
      {
        throw new Exception( "This shouldn't be possible to have none of these defined" )
        op
      }

      bareOp.copy( launch = Some( new Launch( task_infos = Seq(taskInfo) ) ) )
    }

    val accept = new Accept( offer_ids = off, operations = ops )
    new AcceptRequest( framework_id = new FrameworkID( frameworkID ), accept = accept )
  }
}

case class Filter( refuse_seconds : Double = 5.0 )

case class Accept( offer_ids : Seq[OfferID], operations : Seq[Operation], filters : Option[String] = None ) //TODO : filters is what?
case class AcceptRequest( framework_id : FrameworkID, `type` : String = "ACCEPT", accept : Accept )

case class TaskID( value : String ) {
  def toProtos: Protos.TaskID = {
    Protos.TaskID.newBuilder.setValue( value ).build
  }
}

case class ExecutorID( value : String ){
  def toProtos: Protos.ExecutorID = {
    Protos.ExecutorID.newBuilder.setValue( value ).build
  }
}

case class EnvironmentVariable( name : String, value : String ) {
  def toProtos : Protos.Environment.Variable = {
    Protos.Environment.Variable.newBuilder
      .setName( name )
      .setValue( value )
      .build
  }
}
case class Environment( variables : Seq[EnvironmentVariable] ) {
  def toProtos : Protos.Environment = {
    Protos.Environment.newBuilder
      .addAllVariables( variables.map( _.toProtos ).asJava )
      .build
  }
}

case class CommandInfoUri(
  value : String,
  executable : Option[Boolean],
  extract : Boolean = true,
  cache : Option[Boolean]
  //output_file : Option[Boolean]
) {
  def toProtos : Protos.CommandInfo.URI = {
    val builder = Protos.CommandInfo.URI.newBuilder
      .setValue( value )
    if( executable.isDefined )
      builder.setExecutable( executable.get )
    builder.setExtract( extract )
    if( cache.isDefined )
      builder.setCache( cache.get )
    builder.build
  }
}

case class CommandInfo(
  uris : Seq[CommandInfoUri],
  environment : Option[Environment],
  shell : Boolean = true,
  arguments : Seq[String],
  user : Option[String]
) {
  def toProtos : Protos.CommandInfo = {
    val builder = Protos.CommandInfo.newBuilder
    builder.addAllUris( uris.map( _.toProtos ).asJava )
    if( environment.isDefined )
      builder.setEnvironment( environment.get.toProtos )
    builder.setShell( shell )
    builder.addAllArguments( arguments.asJava )
    if( user.isDefined )
      builder.setUser( user.get )
    builder.build
  }
}
object CommandInfo {
  def make( c : Protos.CommandInfo ) = {

    val env : Option[Environment] = if( c.hasEnvironment ) {
      Some( new Environment( variables = c.getEnvironment.getVariablesList.asScala.map{ e => new EnvironmentVariable( e.getName, e.getValue ) } ) )
    }
    else None

    new CommandInfo(
      uris = c.getUrisList.asScala.map{ u => new CommandInfoUri(
        u.getValue,
        if (u.hasExecutable) Some(u.getExecutable) else None,
        if (u.hasExtract) u.getExtract else true,
        if (u.hasCache) Some(u.getCache) else None
      )
      },
      env,
      c.getShell,
      c.getArgumentsList.asScala,
      if (c.hasUser) Some(c.getUser) else None
    )
  }
}

case class Parameter( key : String, value : String ) {
  def toProtos : Protos.Parameter = {
    Protos.Parameter.newBuilder
      .setKey( key )
      .setValue( value )
      .build
  }
}

object Parameter {
  def make( p : Protos.Parameter ) = {
    new Parameter( p.getKey, p.getValue )
  }
}

case class PortMapping( host_port : Int, container_port : Int, protocol : Option[String] ) {
  def toProtos : Protos.ContainerInfo.DockerInfo.PortMapping = {
    val builder = Protos.ContainerInfo.DockerInfo.PortMapping.newBuilder
      .setHostPort( host_port )

    if( protocol.isDefined )
      builder.setProtocol( protocol.get )
    builder.build
  }
}

object PortMapping {
  def make( p : Protos.ContainerInfo.DockerInfo.PortMapping ) = {
    new PortMapping(
      p.getHostPort,
      p.getContainerPort,
      if( p.hasProtocol ) Some(p.getProtocol) else None
    )
  }
}

case class DockerInfo(
  image : String,
  network : String = "HOST",
  priveleged : Boolean = false,
  parameters : Seq[Parameter],
  port_mappings : Seq[PortMapping],
  force_pull_image : Option[Boolean],
  volume_driver : String
) {
  def toProtos : Protos.ContainerInfo.DockerInfo = {
    val builder = Protos.ContainerInfo.DockerInfo.newBuilder
      .setImage( image )
      .setNetwork( Protos.ContainerInfo.DockerInfo.Network.valueOf( network ) )
      .setPrivileged( priveleged )
      .addAllParameters( parameters.map( _.toProtos ).asJava )
      .addAllPortMappings( port_mappings.map( _.toProtos ).asJava )
    if( force_pull_image.isDefined )
      builder.setForcePullImage( force_pull_image.get )
    builder.setVolumeDriver( volume_driver )
    builder.build
  }
}

object DockerInfo {
  def make( d : Protos.ContainerInfo.DockerInfo ) = {
    new DockerInfo(
      d.getImage,
      d.getNetwork.getValueDescriptor.getName,
      d.getPrivileged,
      d.getParametersList.asScala.map( p => Parameter.make(p) ),
      d.getPortMappingsList.asScala.map( p => PortMapping.make(p) ),
      if (d.hasForcePullImage) Some(d.getForcePullImage) else None,
      d.getVolumeDriver
    )
  }
}

case class MesosAppC(
  name : String,
  id : Option[String]
) {
  def toProtos : Protos.Image.Appc = {
    val builder = Protos.Image.Appc.newBuilder.setName( name )
    if( id.isDefined )
      builder.setId( id.get )
    builder.build
  }
}

object MesosAppC {
  def make( a : Protos.Image.Appc ) = {
    new MesosAppC( a.getName,
      if( a.hasId ) Some(a.getId)  else None
    )
  }
}

case class Docker(
  name : String
) {
  def toProtos : Protos.Image.Docker = {
    Protos.Image.Docker.newBuilder
      .setName( name )
      .build
  }
}

object Docker {
  def make( d : Protos.Image.Docker ) = {
    new Docker( d.getName )
  }
}

case class Image(
  `type` : String,
  appc : Option[MesosAppC],
  docker : Option[Docker]
) {
  def toProtos : Protos.Image = {
    val builder = Protos.Image.newBuilder
      .setType( Protos.Image.Type.valueOf( `type` ) )
    if( appc.isDefined )
      builder.setAppc( appc.get.toProtos )
    if( docker.isDefined )
      builder.setDocker( docker.get.toProtos )
    builder.build
  }
}

object Image {
  def make( i : Protos.Image ) = {

    val DOCKER = Protos.Image.Type.valueOf( "DOCKER" )
    val APPC = Protos.Image.Type.valueOf( "APPC" )
    val t = i.getType match {
      case DOCKER => "DOCKER"
      case APPC => "APPC"
    }

    new Image(
      t,
      if (i.hasAppc) Some(MesosAppC.make(i.getAppc)) else None,
      if (i.hasDocker) Some(Docker.make(i.getDocker)) else None
    )
  }
}


case class MesosInfo(
  image : Option[Image]
) {
  def toProtos : Protos.ContainerInfo.MesosInfo = {
    val builder = Protos.ContainerInfo.MesosInfo.newBuilder
    if( image.isDefined )
      builder.setImage( image.get.toProtos )
    builder.build
  }
}

object MesosInfo {
  def make( m : Protos.ContainerInfo.MesosInfo ) = {
    new MesosInfo(
      if( m.hasImage ) Some(Image.make(m.getImage)) else None
    )
  }
}

case class Volume(
  mode : String,
  container_path : String,
  host_path : Option[String],
  image : Option[Image]
) {
  def toProtos : Protos.Volume = {
    val builder = Protos.Volume.newBuilder
      .setMode( Protos.Volume.Mode.valueOf( mode ) )
      .setContainerPath( container_path )
    if( host_path.isDefined )
      builder.setHostPath( host_path.get )
    if( image.isDefined )
      builder.setImage( image.get.toProtos )
    builder.build
  }
}

object Volume {
  def make( v : Protos.Volume ) = {
    new Volume(
      v.getMode.getValueDescriptor.getName,
      v.getContainerPath,
      if (v.hasHostPath) Some(v.getHostPath) else None,
      if (v.hasImage) Some(Image.make(v.getImage)) else None
    )
  }
}

case class IPAddress(
  protocol : Option[String],
  ip_address : Option[String]
) {
  def toProtos : Protos.NetworkInfo.IPAddress = {
    val builder = Protos.NetworkInfo.IPAddress.newBuilder
    if( protocol.isDefined )
      builder.setProtocol( Protos.NetworkInfo.Protocol.valueOf( protocol.get ) )
    if( protocol.isDefined )
      builder.setIpAddress( ip_address.get )
    builder.build
  }
}

object IPAddress {
  def make( ip : Protos.NetworkInfo.IPAddress ) = {
    new IPAddress(
      if ( ip.hasProtocol ) Some(ip.getProtocol.getValueDescriptor.getName) else None,
      if ( ip.hasIpAddress ) Some(ip.getIpAddress) else None
    )
  }
}

case class NetworkInfo(
  ip_addresses : Seq[IPAddress],
  groups : Seq[String]
) {
  def toProtos : Protos.NetworkInfo = {
    val builder = Protos.NetworkInfo.newBuilder
    ip_addresses.map { thing =>
      val ipBuilder = Protos.NetworkInfo.IPAddress.newBuilder
      if ( thing.ip_address.isDefined )
        ipBuilder.setIpAddress( thing.ip_address.get )
      if ( thing.protocol.isDefined ) {
        ipBuilder.setProtocol( Protos.NetworkInfo.Protocol.valueOf( thing.protocol.get ) )
      }
      ipBuilder.build
    }
    builder.build
  }
}

object NetworkInfo {
  def make( n : Protos.NetworkInfo ) = {
    new NetworkInfo(
      n.getIpAddressesList.asScala.map( ip => IPAddress.make(ip) ),
      n.getGroupsList.asScala
    )
  }
}

case class ContainerInfo(
  `type` : String,
  volumes : Seq[Volume],
  hostname : Option[String],
  docker : Option[DockerInfo],
  mesos : Option[MesosInfo],
  network_infos : Seq[NetworkInfo]
) {
  def toProtos : Protos.ContainerInfo = {
    val builder = Protos.ContainerInfo.newBuilder
      .setType( Protos.ContainerInfo.Type.valueOf( `type` ) )
      .addAllVolumes( volumes.map( _.toProtos ).asJava )
      .addAllNetworkInfos( network_infos.map( _.toProtos ).asJava )
    if( hostname.isDefined )
      builder.setHostname( hostname.get )
    if( docker.isDefined )
      builder.setDocker( docker.get.toProtos )
    if( mesos.isDefined )
      builder.setMesos( mesos.get.toProtos )
    builder.build
  }
}

object ContainerInfo {
  def make( c : Protos.ContainerInfo ) = {


    val DOCKER = Protos.ContainerInfo.Type.valueOf("DOCKER")
    val MESOS = Protos.ContainerInfo.Type.valueOf("MESOS")

    new ContainerInfo(
      `type` = c.getType match {
        case DOCKER => "DOCKER"
        case MESOS => "MESOS"
      },
      c.getVolumesList.asScala.map( v => Volume.make(v) ),
      if ( c.hasHostname ) Some(c.getHostname) else None,
      if ( c.hasDocker ) Some(DockerInfo.make(c.getDocker)) else None,
      if ( c.hasMesos ) Some(MesosInfo.make(c.getMesos)) else None,
      c.getNetworkInfosList.asScala.map{ n => NetworkInfo.make(n) }
    )
  }
}

case class ExecutorInfo(
  executor_id : ExecutorID,
  framework_id : FrameworkID,
  command : CommandInfo,
  container : Option[ContainerInfo],
  resources : Seq[Resource]
) {
  def toProtos : Protos.ExecutorInfo = {
    val builder = Protos.ExecutorInfo.newBuilder
      .setExecutorId( executor_id.toProtos )
      .setFrameworkId( framework_id.toProtos )
      .setCommand( command.toProtos )
      .addAllResources( resources.map( _.toProtos ).asJava )
    if( container.isDefined )
      builder.setContainer( container.get.toProtos )
    builder.build
  }
}

object ExecutorInfo{
  def make( e : Protos.ExecutorInfo ) = {

    new ExecutorInfo(
      new ExecutorID( e.getExecutorId.getValue ),
      new FrameworkID( e.getFrameworkId.getValue ),
      CommandInfo.make( e.getCommand ),
      if (e.hasContainer) Some(ContainerInfo.make(e.getContainer)) else None,
      e.getResourcesList.asScala.map( r => Resource.make(r) )
    )
  }
}

case class TaskInfo(
  name : String,
  task_id : TaskID,
  agent_id : AgentID,
  resource : Seq[Resource],
  executor : Option[ExecutorInfo] = None,
  command : Option[CommandInfo] = None,
  container : Option[ContainerInfo] = None
  //health_check : Option[HealthCheck],
  //kill_policy : Option[KillPolicy],
  //labels : Option[Labels],
  //discover : Option[DiscoveryInfo]
) {
  def toProtos : Protos.TaskInfo = {
    val builder = Protos.TaskInfo.newBuilder
      .setName( name )
      .setTaskId( task_id.toProtos )
      .setSlaveId( agent_id.toProtos )
      .addAllResources( resource.map( _.toProtos ).asJava )
    if( executor.isDefined )
      builder.setExecutor( executor.get.toProtos )
    if( command.isDefined )
      builder.setCommand( command.get.toProtos )
    if( container.isDefined )
      builder.setContainer( container.get.toProtos )
    builder.build
  }
}

