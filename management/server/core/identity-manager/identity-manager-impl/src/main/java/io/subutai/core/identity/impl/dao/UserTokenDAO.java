package io.subutai.core.identity.impl.dao;


import java.util.Date;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import com.google.common.collect.Lists;

import io.subutai.common.dao.DaoManager;
import io.subutai.core.identity.api.model.UserToken;
import io.subutai.core.identity.impl.model.UserTokenEntity;


/**
 *
 */
public class UserTokenDAO
{
    private DaoManager daoManager = null;


    /* *************************************************
     *
     */
    public UserTokenDAO( final DaoManager daoManager )
    {
        this.daoManager = daoManager;
    }


    /* *************************************************
     *
     */
    public UserToken find( String token )
    {
        EntityManager em = daoManager.getEntityManagerFromFactory();

        UserToken result = null;
        try
        {
            daoManager.startTransaction( em );
            result = em.find( UserTokenEntity.class, token );
            daoManager.commitTransaction( em );
        }
        catch ( Exception e )
        {
            daoManager.rollBackTransaction( em );
        }
        finally
        {
            daoManager.closeEntityManager( em );
        }
        return result;
    }


    /* *************************************************
     *
     */
    public UserToken findValid( String token )
    {
        UserToken result = find( token );
        try
        {
            if ( result != null )
            {
                Date curDate = new Date( System.currentTimeMillis() );
                if ( !result.getValidDate().after( curDate ) )
                {
                    return null;
                }
            }
        }
        catch ( Exception e )
        {
        }

        return result;
    }


    /* *************************************************
     *
     */
    public void removeInvalid()
    {
        EntityManager em = daoManager.getEntityManagerFromFactory();
        try
        {
            daoManager.startTransaction( em );
            Query query = null;
            query = em.createQuery( "delete from UserTokenEntity ut where ut.type=1 and ut.validDate<:CurrentDate");
            query.setParameter( "CurrentDate", new Date( System.currentTimeMillis() ) );
            query.executeUpdate();
            daoManager.commitTransaction( em );
        }
        catch ( Exception e )
        {
            daoManager.rollBackTransaction( em );
        }
        finally
        {
            daoManager.closeEntityManager( em );
        }
    }


    /* *************************************************
     *
     */
    public List<UserToken> getAll()
    {
        EntityManager em = daoManager.getEntityManagerFromFactory();

        List<UserToken> result = Lists.newArrayList();
        Query query = null;
        try
        {
            query = em.createQuery( "select h from UserTokenEntity h", UserTokenEntity.class );
            result = ( List<UserToken> ) query.getResultList();
        }
        catch ( Exception e )
        {
        }
        finally
        {
            daoManager.closeEntityManager( em );
        }
        return result;
    }


    /* *************************************************
     *
     */
    public void persist( UserToken item )
    {
        EntityManager em = daoManager.getEntityManagerFromFactory();
        try
        {
            daoManager.startTransaction( em );
            em.persist( item );
            em.flush();
            daoManager.commitTransaction( em );
        }
        catch ( Exception e )
        {
            daoManager.rollBackTransaction( em );
        }
        finally
        {
            daoManager.closeEntityManager( em );
        }
    }


    /* *************************************************
     *
     */
    public void remove( final String id )
    {
        EntityManager em = daoManager.getEntityManagerFromFactory();
        try
        {
            daoManager.startTransaction( em );
            UserTokenEntity item = em.find( UserTokenEntity.class, id );
            em.remove( item );
            daoManager.commitTransaction( em );
        }
        catch ( Exception e )
        {
            daoManager.rollBackTransaction( em );
        }
        finally
        {
            daoManager.closeEntityManager( em );
        }
    }


    /* *************************************************
     *
     */
    public void update( final UserToken item)
    {
        EntityManager em = daoManager.getEntityManagerFromFactory();
        try
        {
            daoManager.startTransaction( em );
            em.merge( item );
            daoManager.commitTransaction( em );
        }
        catch ( Exception e )
        {
            daoManager.rollBackTransaction( em );
        }
        finally
        {
            daoManager.closeEntityManager( em );
        }
    }


    /* *************************************************
     *
     */
    public UserToken findByUserId( final long userId )
    {
        EntityManager em = daoManager.getEntityManagerFromFactory();
        UserToken tk = null;
        try
        {
            List<UserToken> result = null;
            Query qr = em.createQuery( "select h from UserTokenEntity h where h.user.id=:userId", UserToken.class );
            qr.setParameter( "userId", userId );
            result = qr.getResultList();

            if ( result != null )
            {
                tk = result.get( 0 );
            }
        }
        catch ( Exception e )
        {
        }
        finally
        {
            daoManager.closeEntityManager( em );
        }

        return tk;
    }


    /* *************************************************
     *
     */
    public UserToken findValidByUserId( final long userId )
    {
        EntityManager em = daoManager.getEntityManagerFromFactory();
        UserToken tk = null;
        try
        {
            List<UserToken> result = null;
            Query qr = em.createQuery(
                    "select h from UserTokenEntity h where h.user.id=:userId and h.validDate>=:validDate",
                    UserToken.class );
            qr.setParameter( "userId", userId );
            qr.setParameter( "validDate", new Date( System.currentTimeMillis() ) );
            result = qr.getResultList();

            if ( result != null )
            {
                tk = result.get( 0 );
            }
        }
        catch ( Exception e )
        {
        }
        finally
        {
            daoManager.closeEntityManager( em );
        }

        return tk;
    }
}
