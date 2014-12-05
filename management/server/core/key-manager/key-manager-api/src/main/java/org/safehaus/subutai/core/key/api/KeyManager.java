package org.safehaus.subutai.core.key.api;


import java.util.Set;


/**
 * Provides means to work with PGP keys
 */
public interface KeyManager
{
    /**
     * Generates a PGP key
     *
     * @param realName - user name
     * @param email - user email
     *
     * @return - {@code KeyInfo}
     */
    public KeyInfo generateKey( String realName, String email ) throws KeyManagerException;

    /**
     * Exports PGP key as SSH key. Resulting key file name format will be %keyId%.pub
     *
     * @param keyId - id of pgp key
     * @param exportPath -  path to directory where exported ssh key will be placed
     */
    public void exportSshKey( String keyId, String exportPath ) throws KeyManagerException;


    /**
     * Sign file with specified key
     *
     * @param keyId - id of pgp key which is used to sign
     * @param filePath - full path to file to be signed
     */
    public void signFileWithKey( String keyId, String filePath ) throws KeyManagerException;

    /**
     * Sends key to HUB
     *
     * @param keyId - id of pgp key to be sent
     */
    public void sendKeyToHub( String keyId ) throws KeyManagerException;

    /**
     * Return key info
     *
     * @param keyId - id of pgp key whose info to return
     *
     * @return - {@code KeyInfo}
     */
    public KeyInfo getKey( String keyId ) throws KeyManagerException;


    /**
     * Returns info of all existing keys
     *
     * @return - set of {@code KeyInfo}
     */
    public Set<KeyInfo> getKeys() throws KeyManagerException;
}
