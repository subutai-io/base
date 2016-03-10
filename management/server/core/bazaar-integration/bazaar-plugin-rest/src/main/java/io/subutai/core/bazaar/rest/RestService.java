package io.subutai.core.bazaar.rest;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public interface RestService
{
	@GET
	@Path( "products" )
	@Produces( { MediaType.APPLICATION_JSON } )
	public Response listProducts ();

	@GET
	@Path( "installed" )
	@Produces( { MediaType.APPLICATION_JSON } )
	public Response listInstalled ();

	@POST
	@Path( "install" )
	@Produces( { MediaType.TEXT_PLAIN } )
	public Response installPlugin (@FormParam ("name") String name, @FormParam ("version") String version, @FormParam ("kar") String kar, @FormParam ("url") String url, @FormParam ("uid") String uid);

	@POST
	@Path( "uninstall" )
	@Produces( { MediaType.TEXT_PLAIN } )
	public Response uninstallPlugin (@FormParam ("id") Long id, @FormParam ("kar") String kar, @FormParam ("name") String name);
}