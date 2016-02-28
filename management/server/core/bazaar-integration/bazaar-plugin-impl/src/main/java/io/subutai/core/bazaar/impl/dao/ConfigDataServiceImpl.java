package io.subutai.core.bazaar.impl.dao;


import com.google.common.collect.Lists;
import io.subutai.common.dao.DaoManager;
import io.subutai.core.bazaar.api.dao.ConfigDataService;
import io.subutai.core.bazaar.api.model.Plugin;
import io.subutai.core.bazaar.impl.model.PluginEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import java.util.List;

public class ConfigDataServiceImpl implements ConfigDataService
{

	private static final Logger LOG = LoggerFactory.getLogger( ConfigDataServiceImpl.class );
	private DaoManager daoManager;

	public ConfigDataServiceImpl (final DaoManager daoManager)
	{
		this.daoManager = daoManager;
	}


	@Override
	public void savePlugin (String name, String version, String kar, String url, String uid)
	{
		EntityManager em = daoManager.getEntityManagerFactory ().createEntityManager ();
		Plugin newPlugin = new PluginEntity ();
		newPlugin.setName (name);
		newPlugin.setVersion (version);
		newPlugin.setKar (kar);
		newPlugin.setUrl (url);
		newPlugin.setUid (uid);
		try
		{
			daoManager.startTransaction( em );
			em.merge( newPlugin );
			daoManager.commitTransaction( em );
		}
		catch ( Exception e )
		{
			daoManager.rollBackTransaction( em );
			LOG.error( "ConfigDataService savePlugin:" + e.toString() );
		}
		finally
		{
			daoManager.closeEntityManager( em );
		}
	}

	@Override
	public void deletePlugin (final Long id)
	{
		EntityManager em = daoManager.getEntityManagerFromFactory();

		try
		{
			daoManager.startTransaction( em );
			PluginEntity entity = em.find( PluginEntity.class, id );
			em.remove( entity );
			em.flush();
			daoManager.commitTransaction( em );
		}
		catch ( Exception ex )
		{
			daoManager.rollBackTransaction( em );
			LOG.error( "ConfigDataService deletePlugin:" + ex.toString() );
		}
		finally
		{
			daoManager.closeEntityManager( em );
		}
	}


	@Override
	public List<Plugin> getPlugins()
	{
		List<Plugin> result = Lists.newArrayList();

		EntityManager em = daoManager.getEntityManagerFromFactory();
		try
		{
			result = ( List<Plugin> ) em.createQuery( "select h from PluginEntity h" ).getResultList();
		}
		catch ( Exception e )
		{
			LOG.error( e.toString(), e );
		}
		finally
		{
			daoManager.closeEntityManager( em );
		}
		return result;
	}
}
