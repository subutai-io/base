package org.safehaus.subutai.plugin.solr.impl.handler.mock;


import org.safehaus.subutai.common.command.Command;
import org.safehaus.subutai.plugin.solr.impl.Commands;
import org.safehaus.subutai.product.common.test.unit.mock.CommandRunnerMock;
import org.safehaus.subutai.common.protocol.Agent;


public class CommandsMock extends Commands {

    private Command installCommand = null;
    private Command startCommand;
    private Command stopCommand;
    private Command statusCommand;


    public CommandsMock() {
        super( new CommandRunnerMock() );
    }


    @Override
    public Command getStartCommand( Agent agent ) {
        return startCommand;
    }


    @Override
    public Command getStopCommand( Agent agent ) {
        return stopCommand;
    }


    @Override
    public Command getStatusCommand( Agent agent ) {
        return statusCommand;
    }


    public CommandsMock setInstallCommand( Command command ) {
        this.installCommand = command;
        return this;
    }
}
