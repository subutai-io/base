package io.subutai.core.object.relation.impl.dao;


import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import io.subutai.common.dao.DaoManager;
import io.subutai.common.security.relation.RelationLink;
import io.subutai.core.object.relation.api.model.Relation;
import io.subutai.core.object.relation.impl.model.RelationImpl;
import io.subutai.core.object.relation.impl.model.RelationLinkImpl;


/**
 * Created by talas on 12/8/15.
 */
public class RelationDataService
{
    private static final Logger logger = LoggerFactory.getLogger( RelationDataService.class );
    private DaoManager daoManager = null;

    //CRUD


    public RelationDataService( final DaoManager daoManager )
    {
        this.daoManager = daoManager;
    }


    public void save( Object relationLink )
    {
        EntityManager em = daoManager.getEntityManagerFactory().createEntityManager();

        try
        {
            daoManager.startTransaction( em );
            em.persist( relationLink );
            daoManager.commitTransaction( em );
        }
        catch ( Exception ex )
        {
            logger.error( "Error persisting object", ex );
            daoManager.rollBackTransaction( em );
        }
        finally
        {
            daoManager.closeEntityManager( em );
        }
    }


    public void update( Object relationLink )
    {
        EntityManager em = daoManager.getEntityManagerFactory().createEntityManager();

        try
        {
            daoManager.startTransaction( em );
            em.merge( relationLink );
            daoManager.commitTransaction( em );
        }
        catch ( Exception ex )
        {
            logger.error( "Error persisting object", ex );
            daoManager.rollBackTransaction( em );
        }
        finally
        {
            daoManager.closeEntityManager( em );
        }
    }


    public void remove( long trustRelationId )
    {
        EntityManager em = daoManager.getEntityManagerFactory().createEntityManager();

        try
        {
            daoManager.startTransaction( em );

            Query qr = em.createQuery( "DELETE FROM RelationImpl AS ss where ss.id=:id" );
            qr.setParameter( "id", trustRelationId );
            qr.executeUpdate();

            daoManager.commitTransaction( em );
        }
        catch ( Exception ex )
        {
            daoManager.rollBackTransaction( em );
        }
        finally
        {
            daoManager.closeEntityManager( em );
        }
    }


    public RelationImpl find( long trustRelationId )
    {
        EntityManager em = daoManager.getEntityManagerFactory().createEntityManager();

        try
        {
            return em.find( RelationImpl.class, trustRelationId );
        }
        catch ( Exception ex )
        {
            return null;
        }
        finally
        {
            daoManager.closeEntityManager( em );
        }
    }


    public List<Relation> findBySource( final RelationLinkImpl source )
    {
        EntityManager em = daoManager.getEntityManagerFactory().createEntityManager();
        List<Relation> result = Lists.newArrayList();
        try
        {
            Query qr = em.createQuery( "select ss from RelationImpl AS ss" + " where ss.source.linkId=:source" );
            qr.setParameter( "source", source.getLinkId() );
            result.addAll( qr.getResultList() );
        }
        catch ( Exception ex )
        {
            logger.warn( "Error querying for trust relation.", ex );
        }
        finally
        {
            daoManager.closeEntityManager( em );
        }
        return result;
    }


    public List<Relation> findByTarget( final RelationLinkImpl target )
    {
        EntityManager em = daoManager.getEntityManagerFactory().createEntityManager();
        List<Relation> result = Lists.newArrayList();
        try
        {
            Query qr = em.createQuery( "select ss from RelationImpl AS ss" + " where ss.target.linkId=:target" );
            qr.setParameter( "target", target.getLinkId() );
            result.addAll( qr.getResultList() );
        }
        catch ( Exception ex )
        {
            logger.warn( "Error querying for trust relation.", ex );
        }
        finally
        {
            daoManager.closeEntityManager( em );
        }
        return result;
    }


    public void findAllRelationships()
    {
    }


    public List<Relation> findByObject( final RelationLinkImpl object )
    {
        EntityManager em = daoManager.getEntityManagerFactory().createEntityManager();
        List<Relation> result = Lists.newArrayList();
        try
        {
            Query qr = em.createQuery( "select ss from RelationImpl AS ss" + " where ss.trustedObject.linkId=:trustedObject" );
            qr.setParameter( "trustedObject", object.getLinkId() );
            result.addAll( qr.getResultList() );
        }
        catch ( Exception ex )
        {
            logger.warn( "Error querying for trust relation.", ex );
        }
        finally
        {
            daoManager.closeEntityManager( em );
        }
        return result;
    }


    public Relation findBySourceAndObject( final RelationLinkImpl source, final RelationLinkImpl object )
    {
        EntityManager em = daoManager.getEntityManagerFactory().createEntityManager();
        Relation result = null;
        try
        {
            Query qr = em.createQuery( "select ss from RelationImpl AS ss"
                    + " where ss.source.linkId=:source AND ss.trustedObject.linkId=:trustedObject" );
            qr.setParameter( "source", source.getLinkId() );
            qr.setParameter( "trustedObject", object.getLinkId() );
            List<Relation> list = qr.getResultList();

            if ( list.size() > 0 )
            {
                result = list.get( 0 );
            }
        }
        catch ( Exception ex )
        {
            logger.warn( "Error querying for trust relation.", ex );
        }
        finally
        {
            daoManager.closeEntityManager( em );
        }
        return result;
    }


    public Relation findBySourceTargetObject( final RelationLinkImpl source, final RelationLinkImpl target,
                                              final RelationLinkImpl object )
    {
        EntityManager em = daoManager.getEntityManagerFactory().createEntityManager();
        Relation result = null;
        try
        {
            Query qr = em.createQuery( "select ss from RelationImpl AS ss"
                    + " where ss.source.linkId=:source AND ss.target.linkId=:target AND ss.trustedObject"
                    + ".linkId=:trustedObject" );
            qr.setParameter( "source", source.getLinkId() );
            qr.setParameter( "target", target.getLinkId() );
            qr.setParameter( "trustedObject", object.getLinkId() );
            List<Relation> list = qr.getResultList();

            if ( list.size() > 0 )
            {
                result = list.get( 0 );
            }
        }
        catch ( Exception ex )
        {
            logger.warn( "Error querying for trust relation.", ex );
        }
        finally
        {
            daoManager.closeEntityManager( em );
        }
        return result;
    }


    public List<Relation> relationsBySourceAndObject( final RelationLinkImpl source, final RelationLinkImpl object )
    {
        EntityManager em = daoManager.getEntityManagerFactory().createEntityManager();
        List<Relation> result = Lists.newArrayList();
        try
        {
            Query qr = em.createQuery( "select ss from RelationImpl AS ss"
                    + " where ss.source.linkId=:source AND ss.trustedObject.linkId=:trustedObject" );
            qr.setParameter( "source", source.getLinkId() );
            qr.setParameter( "trustedObject", object.getLinkId() );
            result.addAll( qr.getResultList() );
        }
        catch ( Exception ex )
        {
            logger.warn( "Error querying for trust relation.", ex );
        }
        finally
        {
            daoManager.closeEntityManager( em );
        }
        return result;
    }


    public Relation relationByTargetAndObject( final RelationLinkImpl target, final RelationLinkImpl object )
    {
        EntityManager em = daoManager.getEntityManagerFactory().createEntityManager();
        Relation result = null;
        try
        {
            Query qr = em.createQuery( "select ss from RelationImpl AS ss"
                    + " where ss.target.linkId=:target AND ss.trustedObject.linkId=:trustedObject" );
            qr.setParameter( "target", target.getLinkId() );
            qr.setParameter( "trustedObject", object.getLinkId() );
            List<Relation> list = qr.getResultList();

            if ( list.size() > 0 )
            {
                result = list.get( 0 );
            }
        }
        catch ( Exception ex )
        {
            logger.warn( "Error querying for trust relation.", ex );
        }
        finally
        {
            daoManager.closeEntityManager( em );
        }
        return result;
    }


    public List<Relation> relationsByTargetAndObject( final RelationLinkImpl target, final RelationLinkImpl object )
    {
        EntityManager em = daoManager.getEntityManagerFactory().createEntityManager();
        List<Relation> result = Lists.newArrayList();
        try
        {
            Query qr = em.createQuery( "select ss from RelationImpl AS ss"
                    + " where ss.target.linkId=:target AND ss.trustedObject.linkId=:trustedObject" );
            qr.setParameter( "target", target.getLinkId() );
            qr.setParameter( "trustedObject", object.getLinkId() );
            result.addAll( qr.getResultList() );
        }
        catch ( Exception ex )
        {
            logger.warn( "Error querying for trust relation.", ex );
        }
        finally
        {
            daoManager.closeEntityManager( em );
        }
        return result;
    }


    public RelationLink findRelationLink( final RelationLink relationLink )
    {
        EntityManager em = daoManager.getEntityManagerFactory().createEntityManager();
        RelationLink result = null;
        try
        {
            Query qr = em.createQuery( "select ss from RelationLinkImpl AS ss"
                    + " where ss.uniqueIdentifier=:uniqueIdentifier AND ss.classPath=:classPath" );
            qr.setParameter( "uniqueIdentifier", relationLink.getUniqueIdentifier() );
            qr.setParameter( "classPath", relationLink.getClassPath() );
            List<RelationLink> list = qr.getResultList();

            if ( list.size() > 0 )
            {
                result = list.get( 0 );
            }
        }
        catch ( Exception ex )
        {
            logger.warn( "Error querying for trust item.", ex );
        }
        finally
        {
            daoManager.closeEntityManager( em );
        }
        return result;
    }
}
