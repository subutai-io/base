package io.subutai.core.security.impl.dao;


import javax.persistence.EntityManager;
import io.subutai.common.dao.DaoManager;
import io.subutai.core.security.api.model.SecretKeyStore;
import io.subutai.core.security.impl.model.SecretKeyStoreEntity;


/**
 * Implementation of SecretKeyStore DAO
 */
class SecretKeyStoreDAO
{
    private DaoManager daoManager = null;

    /******************************************
     *
     */
     SecretKeyStoreDAO( DaoManager daoManager )
    {
        this.daoManager = daoManager;
    }

    /******************************************
     *
     */
    
     void persist(SecretKeyStore secretKeyStore)
    {
        EntityManager em = daoManager.getEntityManagerFactory().createEntityManager();

        try
        {
            daoManager.startTransaction( em );
            em.persist( secretKeyStore );
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





    /******************************************
     *
     */
    void remove( String fingerprint )
    {
        EntityManager em = daoManager.getEntityManagerFactory().createEntityManager();

        try
        {
            daoManager.startTransaction( em );
            em.remove( fingerprint);
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


    /******************************************
     * Get Secret KeyId from DB
     */
     SecretKeyStore find( String fingerprint )
    {
        EntityManager em = daoManager.getEntityManagerFactory().createEntityManager();

        try
        {

            return em.find( SecretKeyStoreEntity.class, fingerprint );
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
}
