package org.safehaus.kiskis.mgmt.impl.pig.handler;

import org.safehaus.kiskis.mgmt.api.commandrunner.AgentResult;
import org.safehaus.kiskis.mgmt.api.commandrunner.Command;
import org.safehaus.kiskis.mgmt.api.pig.Config;
import org.safehaus.kiskis.mgmt.shared.operation.ProductOperation;
import org.safehaus.kiskis.mgmt.impl.pig.Commands;
import org.safehaus.kiskis.mgmt.impl.pig.PigImpl;
import org.safehaus.kiskis.mgmt.shared.operation.AbstractOperationHandler;
import org.safehaus.kiskis.mgmt.shared.protocol.Agent;
import org.safehaus.kiskis.mgmt.shared.protocol.Util;

import java.util.UUID;

/**
 * Created by dilshat on 5/6/14.
 */
public class DestroyNodeOperationHandler extends AbstractOperationHandler<PigImpl> {
    private final String lxcHostname;
    private final ProductOperation po;

    public DestroyNodeOperationHandler(PigImpl manager, String clusterName, String lxcHostname) {
        super(manager, clusterName);
        this.lxcHostname = lxcHostname;
        po = manager.getTracker().createProductOperation(Config.PRODUCT_KEY,
                String.format("Destroying %s in %s", lxcHostname, clusterName));
    }

    @Override
    public UUID getTrackerId() {
        return po.getId();
    }

    @Override
    public void run() {
        Config config = manager.getCluster(clusterName);
        if (config == null) {
            po.addLogFailed(String.format("Cluster with name %s does not exist\nOperation aborted", clusterName));
            return;
        }

        Agent agent = manager.getAgentManager().getAgentByHostname(lxcHostname);
        if (agent == null) {
            po.addLogFailed(String.format("Agent with hostname %s is not connected\nOperation aborted", lxcHostname));
            return;
        }

        if (!config.getNodes().contains(agent)) {
            po.addLogFailed(String.format("Agent with hostname %s does not belong to cluster %s", lxcHostname, clusterName));
            return;
        }

        if (config.getNodes().size() == 1) {
            po.addLogFailed("This is the last node in the cluster. Please, destroy cluster instead\nOperation aborted");
            return;
        }
        po.addLog("Uninstalling Pig...");
        Command uninstallCommand = manager.getCommands().getUninstallCommand(Util.wrapAgentToSet(agent));
        manager.getCommandRunner().runCommand(uninstallCommand);

        if (uninstallCommand.hasCompleted()) {
            AgentResult result = uninstallCommand.getResults().get(agent.getUuid());
            if (result.getExitCode() != null && result.getExitCode() == 0) {
                if (result.getStdOut().contains("Package ksks-pig is not installed, so not removed")) {
                    po.addLog(String.format("Pig is not installed, so not removed on node %s",
                            agent.getHostname()));
                } else {
                    po.addLog(String.format("Pig is removed from node %s",
                            agent.getHostname()));
                }
            } else {
                po.addLog(String.format("Error %s on node %s", result.getStdErr(),
                        agent.getHostname()));
            }

            config.getNodes().remove(agent);
            po.addLog("Updating db...");

            if (manager.getDbManager().saveInfo(Config.PRODUCT_KEY, config.getClusterName(), config)) {
                po.addLogDone("Cluster info update in DB\nDone");
            } else {
                po.addLogFailed("Error while updating cluster info in DB. Check logs.\nFailed");
            }
        } else {
            po.addLogFailed("Uninstallation failed, command timed out");
        }
    }
}
