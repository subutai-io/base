package io.subutai.core.environment.impl.workflow.creation.steps;


import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import io.subutai.common.command.CommandResult;
import io.subutai.common.command.CommandUtil;
import io.subutai.common.command.RequestBuilder;
import io.subutai.common.peer.EnvironmentContainerHost;
import io.subutai.common.peer.Host;
import io.subutai.common.settings.Common;
import io.subutai.common.tracker.TrackerOperation;
import io.subutai.common.util.CollectionUtil;
import io.subutai.core.environment.api.exception.EnvironmentManagerException;
import io.subutai.core.environment.impl.entity.EnvironmentContainerImpl;
import io.subutai.core.environment.impl.entity.EnvironmentImpl;


public class RegisterSshStep
{

    private final EnvironmentImpl environment;
    private final TrackerOperation trackerOperation;
    protected CommandUtil commandUtil = new CommandUtil();


    public RegisterSshStep( final EnvironmentImpl environment, final TrackerOperation trackerOperation )
    {
        this.environment = environment;
        this.trackerOperation = trackerOperation;
    }


    public void execute( Set<String> userKeys ) throws EnvironmentManagerException
    {
        Map<Integer, Set<EnvironmentContainerHost>> sshGroups = Maps.newHashMap();

        //group containers by ssh group
        for ( EnvironmentContainerHost containerHost : environment.getContainerHosts() )
        {
            int sshGroupId = ( ( EnvironmentContainerImpl ) containerHost ).getSshGroupId();
            Set<EnvironmentContainerHost> groupedContainers = sshGroups.get( sshGroupId );

            if ( groupedContainers == null )
            {
                groupedContainers = Sets.newHashSet();
                sshGroups.put( sshGroupId, groupedContainers );
            }

            groupedContainers.add( containerHost );
        }

        //configure ssh on each group
        for ( Map.Entry<Integer, Set<EnvironmentContainerHost>> sshGroup : sshGroups.entrySet() )
        {
            int sshGroupId = sshGroup.getKey();
            Set<EnvironmentContainerHost> groupedContainers = sshGroup.getValue();
            Set<Host> hosts = Sets.newHashSet();
            hosts.addAll( groupedContainers );

            if ( sshGroupId > 0 )
            {
                exchangeSshKeys( hosts, userKeys );
            }
            else if ( !CollectionUtil.isCollectionEmpty( userKeys ) )
            {
                appendSshKeys( hosts, userKeys );
            }
        }

        exchangeAllContainerKeys();
    }


    /**
     * Workaround for: https://github.com/optdyn/hub/issues/413.
     * We always need containers accessible to each other via SSH.
     */
    private void exchangeAllContainerKeys() throws EnvironmentManagerException
    {
        Set<Host> ch = Sets.newHashSet();
        ch.addAll( environment.getContainerHosts() );

        if ( ch.size() > 1 )
        {
            exchangeSshKeys( ch, Collections.EMPTY_SET );
        }
    }


    protected void exchangeSshKeys( Set<Host> hosts, Set<String> userKeys ) throws EnvironmentManagerException
    {
        Set<String> sshKeys = Sets.newHashSet();

        if ( !CollectionUtil.isCollectionEmpty( userKeys ) )
        {
            sshKeys.addAll( userKeys );
        }

        sshKeys.addAll( createSshKeys( hosts ) );

        addSshKeys( hosts, sshKeys );

        configureSsh( hosts );
    }


    protected void appendSshKeys( Set<Host> hosts, Set<String> sshKeys ) throws EnvironmentManagerException
    {
        addSshKeys( hosts, sshKeys );

        configureSsh( hosts );
    }


    protected Set<String> createSshKeys( Set<Host> hosts ) throws EnvironmentManagerException
    {
        Set<String> keys = Sets.newHashSet();

        Map<Host, CommandResult> results = commandUtil.executeParallelSilent( getCreateNReadSSHCommand(), hosts );

        Set<Host> succeededHosts = Sets.newHashSet();
        Set<Host> failedHosts = Sets.newHashSet( hosts );

        for ( Map.Entry<Host, CommandResult> resultEntry : results.entrySet() )
        {
            Host host = resultEntry.getKey();
            CommandResult result = resultEntry.getValue();
            if ( result.hasSucceeded() && !Strings.isNullOrEmpty( result.getStdOut() ) )
            {
                keys.add( result.getStdOut() );

                succeededHosts.add( host );
            }
        }

        failedHosts.removeAll( succeededHosts );

        for ( Host failedHost : failedHosts )
        {
            trackerOperation.addLog( String.format( "SSH key creation failed on host %s", failedHost.getHostname() ) );
        }

        if ( !failedHosts.isEmpty() )
        {
            throw new EnvironmentManagerException( "Failed to create SSH key on all hosts" );
        }

        return keys;
    }


    protected void addSshKeys( Set<Host> hosts, Set<String> keys ) throws EnvironmentManagerException
    {
        //send key in portions, since all can not fit into one command, it fails
        int i = 0;
        StringBuilder keysString = new StringBuilder();
        for ( String key : keys )
        {
            keysString.append( key );
            i++;
            //send next 5 keys
            if ( i % 5 == 0 || i == keys.size() )
            {
                Set<Host> succeededHosts = Sets.newHashSet();
                Set<Host> failedHosts = Sets.newHashSet( hosts );

                Map<Host, CommandResult> results =
                        commandUtil.executeParallelSilent( getAppendSshKeysCommand( keysString.toString() ), hosts );

                keysString.setLength( 0 );

                for ( Map.Entry<Host, CommandResult> resultEntry : results.entrySet() )
                {
                    CommandResult result = resultEntry.getValue();
                    Host host = resultEntry.getKey();

                    if ( result.hasSucceeded() )
                    {
                        succeededHosts.add( host );
                    }
                }

                failedHosts.removeAll( succeededHosts );

                for ( Host failedHost : failedHosts )
                {
                    trackerOperation
                            .addLog( String.format( "Failed to add ssh keys on host %s", failedHost.getHostname() ) );
                }

                if ( !failedHosts.isEmpty() )
                {
                    throw new EnvironmentManagerException( "Failed to add ssh keys on all hosts" );
                }
            }
        }
    }


    protected void configureSsh( Set<Host> hosts ) throws EnvironmentManagerException
    {
        Set<Host> succeededHosts = Sets.newHashSet();
        Set<Host> failedHosts = Sets.newHashSet( hosts );

        Map<Host, CommandResult> results = commandUtil.executeParallelSilent( getConfigSSHCommand(), hosts );

        for ( Map.Entry<Host, CommandResult> resultEntry : results.entrySet() )
        {
            CommandResult result = resultEntry.getValue();
            Host host = resultEntry.getKey();

            if ( result.hasSucceeded() )
            {
                succeededHosts.add( host );
            }
        }

        failedHosts.removeAll( succeededHosts );

        for ( Host failedHost : failedHosts )
        {
            trackerOperation.addLog( String.format( "Failed to configure ssh on host %s", failedHost.getHostname() ) );
        }

        if ( !failedHosts.isEmpty() )
        {
            throw new EnvironmentManagerException( "Failed to configure ssh on all hosts" );
        }
    }


    public RequestBuilder getCreateNReadSSHCommand()
    {
        return new RequestBuilder( String.format( "rm -rf %1$s && " +
                "mkdir -p %1$s && " +
                "chmod 700 %1$s && " +
                "ssh-keygen -t dsa -P '' -f %1$s/id_dsa -q && " + "cat %1$s/id_dsa.pub", Common.CONTAINER_SSH_FOLDER ) );
    }


    public RequestBuilder getAppendSshKeysCommand( String keys )
    {
        return new RequestBuilder( String.format( "mkdir -p %1$s && " +
                "chmod 700 %1$s && " +
                "echo '%3$s' >> %2$s && " +
                "chmod 644 %2$s", Common.CONTAINER_SSH_FOLDER, Common.CONTAINER_SSH_FILE, keys ) );
    }


    public RequestBuilder getConfigSSHCommand()
    {
        return new RequestBuilder( String.format( "echo 'Host *' > %1$s/config && " +
                "echo '    StrictHostKeyChecking no' >> %1$s/config && " +
                "chmod 644 %1$s/config", Common.CONTAINER_SSH_FOLDER ) );
    }
}
