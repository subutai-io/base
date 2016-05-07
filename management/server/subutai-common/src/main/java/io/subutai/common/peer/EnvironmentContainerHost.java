package io.subutai.common.peer;


import java.util.Set;


public interface EnvironmentContainerHost extends ContainerHost
{
    EnvironmentContainerHost addTag( String tag );

    EnvironmentContainerHost removeTag( String tag );

    Set<String> getTags();

    EnvironmentContainerHost setHostname( String newHostname ) throws PeerException;
}
