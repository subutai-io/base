package org.safehaus.kiskis.mgmt.impl.solr.handler;


import org.safehaus.kiskis.mgmt.api.solr.Config;
import org.safehaus.kiskis.mgmt.impl.solr.SolrImpl;
import org.safehaus.kiskis.mgmt.shared.operation.AbstractOperationHandler;
import org.safehaus.kiskis.mgmt.shared.protocol.Agent;


public class DestroyNodeOperationHandler extends AbstractOperationHandler<SolrImpl> {
    private final String lxcHostname;


    public DestroyNodeOperationHandler( SolrImpl manager, String clusterName, String lxcHostname ) {
        super( manager, clusterName );
        this.lxcHostname = lxcHostname;
        productOperation = manager.getTracker().createProductOperation( Config.PRODUCT_KEY,
                String.format( "Destroying %s in %s", lxcHostname, clusterName ) );
    }


    @Override
    public void run() {
        Config config = manager.getCluster( clusterName );

        if ( config == null ) {
            productOperation.addLogFailed(
                    String.format( "Cluster with name %s does not exist\nOperation aborted", clusterName ) );
            return;
        }

        Agent agent = manager.getAgentManager().getAgentByHostname( lxcHostname );

        if ( agent == null ) {
            productOperation.addLogFailed(
                    String.format( "Agent with hostname %s is not connected\nOperation aborted", lxcHostname ) );
            return;
        }

        if ( !config.getNodes().contains( agent ) ) {
            productOperation.addLogFailed(
                    String.format( "Agent with hostname %s does not belong to cluster %s", lxcHostname, clusterName ) );
            return;
        }

        if ( config.getNodes().size() == 1 ) {
            productOperation.addLogFailed(
                    "This is the last node in the cluster. Please, destroy cluster instead\nOperation aborted" );
            return;
        }

        // Destroy lxc
        productOperation.addLog( "Destroying lxc container..." );
        Agent physicalAgent = manager.getAgentManager().getAgentByHostname( agent.getParentHostName() );

        if ( physicalAgent == null ) {
            productOperation.addLog(
                    String.format( "Could not determine physical parent of %s. Use LXC module to cleanup, skipping...",
                            agent.getHostname() )
                                   );
        }
        else {
            if ( !manager.getLxcManager().destroyLxcOnHost( physicalAgent, agent.getHostname() ) ) {
                productOperation.addLog( "Could not destroy lxc container. Use LXC module to cleanup, skipping..." );
            }
            else {
                productOperation.addLog( "Lxc container destroyed successfully" );
            }
        }

        // Update db
        productOperation.addLog( "Updating db..." );
        config.getNodes().remove( agent );

        if ( !manager.getDbManager().saveInfo( Config.PRODUCT_KEY, config.getClusterName(), config ) ) {
            productOperation.addLogFailed(
                    String.format( "Error while updating cluster info [%s] in DB. Check logs\nFailed",
                            config.getClusterName() )
                                         );
        }
        else {
            productOperation.addLogDone( "Done" );
        }
    }
}
