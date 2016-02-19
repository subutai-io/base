package io.subutai.core.metric.cli;


import java.util.Collection;

import org.apache.karaf.shell.commands.Command;

import com.google.common.base.Preconditions;

import io.subutai.common.peer.AlertEvent;
import io.subutai.core.identity.rbac.cli.SubutaiShellCommandSupport;
import io.subutai.core.metric.api.Monitor;


/**
 * List of alerts command
 */
@Command( scope = "alert", name = "list", description = "Returns list of alerts" )
public class AlertListCommand extends SubutaiShellCommandSupport
{
    private final Monitor monitor;


    public AlertListCommand( final Monitor monitor )
    {
        Preconditions.checkNotNull( monitor, "Monitor is null" );

        this.monitor = monitor;
    }


    @Override
    protected Object doExecute() throws Exception
    {
        Collection<AlertEvent> alerts = monitor.getAlertEvents();
        System.out.println( String.format( "List of alerts. Found %d alert(s)", alerts.size() ) );
        for ( AlertEvent alert : alerts )
        {
            System.out.println( alert );
            for ( String log : alert.getLogs() )
            {
                System.out.println( log );
            }
        }

        return null;
    }
}
