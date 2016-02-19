package io.subutai.core.environment.rest.ui;


import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.Multipart;


public interface RestService
{
    /** Templates **************************************************** */
    @GET
    @Path( "templates" )
    @Produces( { MediaType.APPLICATION_JSON } )
    Response listTemplates();


    /** Domain **************************************************** */

    @GET
    @Path( "domains" )
    @Produces( { MediaType.TEXT_PLAIN } )
    Response getDefaultDomainName();


    /** Environments **************************************************** */

    @GET
    @Produces( { MediaType.APPLICATION_JSON } )
    Response listEnvironments();

    @POST
    @Path( "build" )
    @Produces( { MediaType.TEXT_PLAIN } )
    Response buildAuto( @FormParam( "name" ) String name, @FormParam( "containers" ) String containersJson );


    @POST
    @Path( "build/advanced" )
    @Produces( { MediaType.TEXT_PLAIN } )
    Response buildAdvanced( @FormParam( "name" ) String name, @FormParam( "containers" ) String containersJson );


    @POST
    @Path( "{environmentId}/modify" )
    Response modifyEnvironment(@PathParam( "environmentId" ) String environmentId,
                               @FormParam( "topology" ) String topology,
                               @FormParam( "containers" ) String containers );

    @DELETE
    @Path( "{environmentId}" )
    Response destroyEnvironment( @PathParam( "environmentId" ) String environmentId );


    /** Environments SSH keys **************************************************** */

    @POST
    @Path( "keys" )
    Response addSshKey( @FormParam( "environmentId" ) String environmentId, @FormParam( "key" ) String key );


    @DELETE
    @Path( "{environmentId}/keys" )
    Response removeSshKey( @PathParam( "environmentId" ) String environmentId, @QueryParam( "key" ) String key );

    @GET
    @Path( "{environmentId}/keys" )
    @Produces( { MediaType.APPLICATION_JSON } )
    Response getEnvironmentSShKeys( @PathParam( "environmentId" ) String environmentId );


    /** Environment domains **************************************************** */

    @GET
    @Path( "{environmentId}/domain" )
    Response getEnvironmentDomain( @PathParam( "environmentId" ) String environmentId );


    @GET
    @Path( "/domains/strategies" )
    Response listDomainLoadBalanceStrategies();


    @POST
    @Consumes( MediaType.MULTIPART_FORM_DATA )
    @Path( "/domains" )
    Response addEnvironmentDomain( @Multipart( "environmentId" ) String environmentId,
                                   @Multipart( "hostName" ) String hostName,
                                   @Multipart( "strategy" ) String strategyJson,
                                   @Multipart( value = "file" ) Attachment attr );


    @DELETE
    @Path( "{environmentId}/domains" )
    Response removeEnvironmentDomain( @PathParam( "environmentId" ) String environmentId );


    @GET
    @Path( "{environmentId}/containers/{containerId}/domain" )
    Response isContainerDomain( @PathParam( "environmentId" ) String environmentId,
                                @PathParam( "containerId" ) String containerId );


    @PUT
    @Path( "{environmentId}/containers/{containerId}/domain" )
    Response setContainerDomain( @PathParam( "environmentId" ) String environmentId,
                                 @PathParam( "containerId" ) String containerId );


    /** Containers **************************************************** */

    @GET
    @Path( "containers/{containerId}" )
    @Produces( { MediaType.APPLICATION_JSON } )
    Response getContainerEnvironmentId( @PathParam( "containerId" ) String containerId );

    @DELETE
    @Path( "containers/{containerId}" )
    Response destroyContainer( @PathParam( "containerId" ) String containerId );

    @GET
    @Path( "containers/{containerId}/state" )
    @Produces( { MediaType.APPLICATION_JSON } )
    Response getContainerState( @PathParam( "containerId" ) String containerId );

    @POST
    @Path( "containers/{containerId}/start" )
    @Produces( { MediaType.APPLICATION_JSON } )
    Response startContainer( @PathParam( "containerId" ) String containerId );

    @POST
    @Path( "containers/{containerId}/stop" )
    @Produces( { MediaType.APPLICATION_JSON } )
    Response stopContainer( @PathParam( "containerId" ) String containerId );


    /** Container types **************************************************** */

    @GET
    @Path( "containers/types" )
    @Produces( { MediaType.APPLICATION_JSON } )
    Response listContainerTypes();


    /** Container quota **************************************************** */

    @GET
    @Path( "containers/{containerId}/quota" )
    @Produces( { MediaType.APPLICATION_JSON } )
    Response getContainerQuota( @PathParam( "containerId" ) String containerId );

    @POST
    @Path( "containers/{containerId}/quota" )
    @Produces( { MediaType.APPLICATION_JSON } )
    Response setContainerQuota( @PathParam( "containerId" ) String containerId, @FormParam( "cpu" ) int cpu,
                                @FormParam( "ram" ) int ram, @FormParam( "disk_home" ) Double diskHome,
                                @FormParam( "disk_var" ) Double diskVar, @FormParam( "disk_root" ) Double diskRoot,
                                @FormParam( "disk_opt" ) Double diskOpt );

    @GET
    @Path( "containers/{containerId}/quota/ram" )
    @Produces( { MediaType.APPLICATION_JSON } )
    Response getRamQuota( @PathParam( "containerId" ) String containerId );

    @POST
    @Path( "containers/{containerId}/quota/ram" )
    Response setRamQuota( @PathParam( "containerId" ) String containerId, @FormParam( "ram" ) int ram );

    @GET
    @Path( "containers/{containerId}/quota/cpu" )
    @Produces( { MediaType.APPLICATION_JSON } )
    Response getCpuQuota( @PathParam( "containerId" ) String containerId );

    @POST
    @Path( "containers/{containerId}/quota/cpu" )
    Response setCpuQuota( @PathParam( "containerId" ) String containerId, @FormParam( "cpu" ) int cpu );

    @GET
    @Path( "containers/{containerId}/quota/disk/{diskPartition}" )
    @Produces( { MediaType.APPLICATION_JSON } )
    Response getDiskQuota( @PathParam( "containerId" ) String containerId,
                           @PathParam( "diskPartition" ) String diskPartition );

    @POST
    @Path( "containers/{containerId}/quota/disk" )
    Response setDiskQuota( @PathParam( "containerId" ) String containerId, @FormParam( "diskQuota" ) String diskQuota );


    /** Peers strategy **************************************************** */

    @GET
    @Path( "strategies" )
    @Produces( { MediaType.APPLICATION_JSON } )
    Response listPlacementStrategies();


    /** Peers **************************************************** */

    @GET
    @Path( "peers" )
    @Produces( { MediaType.APPLICATION_JSON } )
    Response getPeers();


    /** Tags **************************************************** */

    @POST
    @Path( "{environmentId}/containers/{containerId}/tags" )
    Response addTags( @PathParam( "environmentId" ) String environmentId,
                      @PathParam( "containerId" ) String containerId, @FormParam( "tags" ) String tags );

    @DELETE
    @Path( "{environmentId}/containers/{containerId}/tags/{tag}" )
    Response removeTag( @PathParam( "environmentId" ) String environmentId,
                        @PathParam( "containerId" ) String containerId, @PathParam( "tag" ) String tag );

    @GET
    @Path( "{environmentId}/containers/{containerId}/ssh" )
    @Produces( { MediaType.TEXT_PLAIN } )
    Response setupContainerSsh( @PathParam( "environmentId" ) String environmentId,
                                @PathParam( "containerId" ) String containerId );


    /** Share **************************************************** */

    @GET
    @Path( "shared/users/{objectId}" )
    @Produces( { MediaType.APPLICATION_JSON } )
    Response getSharedUsers( @PathParam( "objectId" ) String objectId );


    @POST
    @Path( "share" )
    Response shareEnvironment( @FormParam( "users" ) String users, @FormParam( "environmentId" ) String environmentId );
}
