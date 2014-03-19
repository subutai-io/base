/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.safehaus.kiskis.mgmt.server.ui.modules.mongo.manager;

import org.safehaus.kiskis.mgmt.server.ui.modules.mongo.common.NodeType;
import org.safehaus.kiskis.mgmt.server.ui.modules.mongo.manager.callback.CheckStatusCallback;
import org.safehaus.kiskis.mgmt.server.ui.modules.mongo.manager.callback.StopNodeCallback;
import org.safehaus.kiskis.mgmt.server.ui.modules.mongo.manager.callback.StartNodeCallback;
import com.vaadin.data.Item;
import com.vaadin.data.Property;
import com.vaadin.terminal.Sizeable;
import com.vaadin.terminal.ThemeResource;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.Component;
import com.vaadin.ui.Embedded;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.Table;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.safehaus.kiskis.mgmt.server.ui.ConfirmationDialogCallback;
import org.safehaus.kiskis.mgmt.server.ui.MgmtApplication;
import org.safehaus.kiskis.mgmt.server.ui.modules.mongo.common.Config;
import org.safehaus.kiskis.mgmt.server.ui.modules.mongo.common.Constants;
import org.safehaus.kiskis.mgmt.server.ui.modules.mongo.dao.MongoDAO;
import org.safehaus.kiskis.mgmt.server.ui.modules.mongo.common.Tasks;
import org.safehaus.kiskis.mgmt.shared.protocol.Agent;
import org.safehaus.kiskis.mgmt.api.taskrunner.Task;
import org.safehaus.kiskis.mgmt.shared.protocol.Util;
import org.safehaus.kiskis.mgmt.api.agentmanager.AgentManager;
import org.safehaus.kiskis.mgmt.api.taskrunner.Result;
import org.safehaus.kiskis.mgmt.api.taskrunner.TaskStatus;
import org.safehaus.kiskis.mgmt.server.ui.modules.mongo.MongoModule;

/**
 *
 * @author dilshat
 *
 */
public class Manager {

    private final VerticalLayout contentRoot;
    private final AgentManager agentManager;
    private final ComboBox clusterCombo;
    private final Table configServersTable;
    private final Table routersTable;
    private final Table dataNodesTable;
    private DestroyClusterWindow destroyWindow;
    private AddNodeWindow addNodeWindow;
    private Config config;

    public Manager() {
        agentManager = MongoModule.getAgentManager();

        contentRoot = new VerticalLayout();
        contentRoot.setSpacing(true);
        contentRoot.setWidth(90, Sizeable.UNITS_PERCENTAGE);
        contentRoot.setHeight(100, Sizeable.UNITS_PERCENTAGE);

        VerticalLayout content = new VerticalLayout();
        content.setWidth(100, Sizeable.UNITS_PERCENTAGE);
        content.setHeight(100, Sizeable.UNITS_PERCENTAGE);

        contentRoot.addComponent(content);
        contentRoot.setComponentAlignment(content, Alignment.TOP_CENTER);
        contentRoot.setMargin(true);

        //tables go here
        configServersTable = createTableTemplate("Config Servers", 150);
        routersTable = createTableTemplate("Query Routers", 150);
        dataNodesTable = createTableTemplate("Data Nodes", 270);
        //tables go here

        Label clusterNameLabel = new Label("Select the cluster");
        content.addComponent(clusterNameLabel);

        HorizontalLayout topContent = new HorizontalLayout();
        topContent.setSpacing(true);

        clusterCombo = new ComboBox();
        clusterCombo.setMultiSelect(false);
        clusterCombo.setImmediate(true);
        clusterCombo.setTextInputAllowed(false);
        clusterCombo.setWidth(300, Sizeable.UNITS_PIXELS);
        clusterCombo.addListener(new Property.ValueChangeListener() {

            @Override
            public void valueChange(Property.ValueChangeEvent event) {
                config = (Config) event.getProperty().getValue();
                refreshUI();
            }
        });

        topContent.addComponent(clusterCombo);

        Button refreshClustersBtn = new Button("Refresh clusters");
        refreshClustersBtn.addListener(new Button.ClickListener() {

            @Override
            public void buttonClick(Button.ClickEvent event) {
                refreshClustersInfo();
            }
        });

        topContent.addComponent(refreshClustersBtn);

        Button checkAllBtn = new Button("Check all");
        checkAllBtn.addListener(new Button.ClickListener() {

            @Override
            public void buttonClick(Button.ClickEvent event) {
                checkNodesStatus(configServersTable);
                checkNodesStatus(routersTable);
                checkNodesStatus(dataNodesTable);
            }

        });

        topContent.addComponent(checkAllBtn);

        Button destroyClusterBtn = new Button("Destroy cluster");
        destroyClusterBtn.addListener(new Button.ClickListener() {

            @Override
            public void buttonClick(Button.ClickEvent event) {
                if (config != null) {
                    MgmtApplication.showConfirmationDialog(
                            "Cluster destruction confirmation",
                            String.format("Do you want to destroy the %s cluster?", config.getClusterName()),
                            "Yes", "No", new ConfirmationDialogCallback() {

                                @Override
                                public void response(boolean ok) {
                                    if (ok) {
                                        destroyWindow = new DestroyClusterWindow(config);
                                        MgmtApplication.addCustomWindow(destroyWindow);
                                        destroyWindow.addListener(new Window.CloseListener() {

                                            @Override
                                            public void windowClose(Window.CloseEvent e) {
                                                refreshClustersInfo();
                                            }
                                        });
                                        destroyWindow.startOperation();
                                    }
                                }
                            });
                } else {
                    show("Please, select cluster");
                }
            }

        });

        topContent.addComponent(destroyClusterBtn);

        Button addNodeBtn = new Button("Add New Node");

        addNodeBtn.addListener(new Button.ClickListener() {

            @Override
            public void buttonClick(Button.ClickEvent event) {
                if (config != null) {
                    addNodeWindow = new AddNodeWindow(
                            config, MongoModule.getTaskRunner());
                    MgmtApplication.addCustomWindow(addNodeWindow);
                    addNodeWindow.addListener(new Window.CloseListener() {

                        @Override
                        public void windowClose(Window.CloseEvent e) {
                            //refresh clusters and show the current one again
                            if (addNodeWindow.isSucceeded()) {
                                refreshClustersInfo();
                            }
                        }
                    });
                } else {
                    show("Please, select cluster");
                }
            }
        });

        topContent.addComponent(addNodeBtn);

        content.addComponent(topContent);

        HorizontalLayout midContent = new HorizontalLayout();
        midContent.setWidth(100, Sizeable.UNITS_PERCENTAGE);

        midContent.addComponent(configServersTable);

        midContent.addComponent(routersTable);

        content.addComponent(midContent);

        content.addComponent(dataNodesTable);

        refreshClustersInfo();
    }

    public Component getContent() {
        return contentRoot;
    }

    private void show(String notification) {
        contentRoot.getWindow().showNotification(notification);
    }

    private void populateTable(final Table table, Set<Agent> agents, final NodeType nodeType) {

        table.removeAllItems();

        for (final Agent agent : agents) {

            final Button checkBtn = new Button("Check");
            final Button startBtn = new Button("Start");
            final Button stopBtn = new Button("Stop");
            final Button destroyBtn = new Button("Destroy");
            final Embedded progressIcon = new Embedded("", new ThemeResource("../base/common/img/loading-indicator.gif"));
            stopBtn.setEnabled(false);
            startBtn.setEnabled(false);
            progressIcon.setVisible(false);

            final Object rowId = table.addItem(new Object[]{
                agent.getHostname(),
                checkBtn,
                startBtn,
                stopBtn,
                destroyBtn,
                progressIcon},
                    null);

            checkBtn.addListener(new Button.ClickListener() {

                @Override
                public void buttonClick(Button.ClickEvent event) {
                    Task checkStatusTask = Tasks.getCheckStatusTask(
                            new HashSet<Agent>(Arrays.asList(agent)),
                            nodeType, config);
                    MongoModule.getTaskRunner().executeTask(checkStatusTask, new CheckStatusCallback(MongoModule.getTaskRunner(), progressIcon, startBtn, stopBtn, destroyBtn));
                }
            });

            startBtn.addListener(new Button.ClickListener() {

                @Override
                public void buttonClick(Button.ClickEvent event) {
                    Task startNodeTask = null;
                    if (nodeType == NodeType.CONFIG_NODE) {
                        startNodeTask = Tasks.getStartConfigServersTask(
                                Util.wrapAgentToSet(agent), config);

                    } else if (nodeType == NodeType.DATA_NODE) {

                        startNodeTask = Tasks.getStartReplicaSetTask(
                                Util.wrapAgentToSet(agent), config);

                    } else if (nodeType == NodeType.ROUTER_NODE) {
                        startNodeTask = Tasks.getStartRoutersTask(
                                Util.wrapAgentToSet(agent),
                                config.getConfigServers(),
                                config);

                    }
                    if (startNodeTask != null) {
                        MongoModule.getTaskRunner().executeTask(startNodeTask,
                                new StartNodeCallback(MongoModule.getTaskRunner(), progressIcon, checkBtn, startBtn, stopBtn, destroyBtn));
                    }
                }
            });

            stopBtn.addListener(new Button.ClickListener() {

                @Override
                public void buttonClick(Button.ClickEvent event) {
                    Task stopNodeTask = Tasks.getStopMongoTask(
                            Util.wrapAgentToSet(agent));

                    MongoModule.getTaskRunner().executeTask(stopNodeTask,
                            new StopNodeCallback(progressIcon, checkBtn, startBtn, stopBtn, destroyBtn));
                }
            });

            destroyBtn.addListener(new Button.ClickListener() {

                @Override
                public void buttonClick(Button.ClickEvent event) {
                    progressIcon.setVisible(true);
                    checkBtn.setEnabled(false);
                    startBtn.setEnabled(false);
                    destroyBtn.setEnabled(false);
                    stopBtn.setEnabled(false);
                    MongoModule.getExecutor().execute(new Runnable() {

                        public void run() {

                            if (nodeType == NodeType.CONFIG_NODE) {
                                config.getConfigServers().remove(agent);
                                //restart routers
                                Task stopMongoTask = MongoModule.getTaskRunner().
                                        executeTask(Tasks.getStopMongoTask(config.getRouterServers()));
                                //don't check status of this task since this task always ends with execute_timeouted
                                if (stopMongoTask.isCompleted()) {
                                    Task startRoutersTask = MongoModule.getTaskRunner().
                                            executeTask(Tasks.getStartRoutersTask2(config.getRouterServers(),
                                                            config.getConfigServers(), config));
                                    //don't check status of this task since this task always ends with execute_timeouted
                                    if (startRoutersTask.isCompleted()) {
                                        //check number of started routers
                                        int numberOfRoutersRestarted = 0;
                                        for (Map.Entry<UUID, Result> res : startRoutersTask.getResults().entrySet()) {
                                            if (res.getValue().getStdOut().contains("child process started successfully, parent exiting")) {
                                                numberOfRoutersRestarted++;
                                            }
                                        }
                                        if (numberOfRoutersRestarted != config.getRouterServers().size()) {
                                            show("Not all routers restarted. Use Terminal module to restart them");
                                        }
                                        //check routers state
                                        checkNodesStatus(routersTable);
                                    } else {
                                        show("Could not restart routers. Use Terminal module to restart them");
                                    }
                                } else {
                                    show("Could not restart routers. Use Terminal module to restart them");
                                }

                            } else if (nodeType == NodeType.DATA_NODE) {
                                config.getDataNodes().remove(agent);
                                //unregister from primary
                                Task findPrimaryNodeTask = MongoModule.getTaskRunner().
                                        executeTask(Tasks.getFindPrimaryNodeTask(agent, config));

                                if (findPrimaryNodeTask.isCompleted()) {
                                    Pattern p = Pattern.compile("primary\" : \"(.*)\"");
                                    Matcher m = p.matcher(findPrimaryNodeTask.getResults().entrySet().iterator().next().getValue().getStdOut());
                                    Agent primaryNodeAgent = null;
                                    if (m.find()) {
                                        String primaryNodeHost = m.group(1);
                                        if (!Util.isStringEmpty(primaryNodeHost)) {
                                            String hostname = primaryNodeHost.split(":")[0].replace("." + config.getDomainName(), "");
                                            primaryNodeAgent = agentManager.getAgentByHostname(hostname);
                                        }
                                    }
                                    if (primaryNodeAgent != null) {
                                        if (primaryNodeAgent != agent) {
                                            Task unregisterSecondaryNodeFromPrimaryTask
                                                    = MongoModule.getTaskRunner().
                                                    executeTask(
                                                            Tasks.getUnregisterSecondaryFromPrimaryTask(
                                                                    primaryNodeAgent, agent, config));
                                            if (unregisterSecondaryNodeFromPrimaryTask.getTaskStatus() != TaskStatus.SUCCESS) {
                                                show("Could not unregister this node from replica set, skipping...");
                                            }
                                        }
                                    } else {
                                        show("Could not determine primary node for unregistering from replica set, skipping...");
                                    }
                                } else {
                                    show("Could not determine primary node for unregistering from replica set, skipping...");
                                }

                            } else if (nodeType == NodeType.ROUTER_NODE) {
                                config.getRouterServers().remove(agent);
                            }
                            //destroy lxc
                            Agent physicalAgent = MongoModule.getAgentManager().getAgentByHostname(agent.getParentHostName());
                            if (physicalAgent == null) {
                                show(
                                        String.format("Could not determine physical parent of %s. Use LXC module to cleanup",
                                                agent.getHostname()));
                            } else {
                                if (!MongoModule.getLxcManager().destroyLxcOnHost(physicalAgent, agent.getHostname())) {
                                    show("Could not destroy lxc container. Use LXC module to cleanup");
                                }
                            }
                            //update db
                            if (!MongoDAO.saveMongoClusterInfo(config)) {
                                show(String.format("Error while updating cluster info [%s] in DB. Check logs",
                                        config.getClusterName()));
                            }
                            Table table = nodeType == NodeType.CONFIG_NODE ? configServersTable
                                    : nodeType == NodeType.DATA_NODE ? dataNodesTable : routersTable;
                            table.removeItem(rowId);
                        }
                    });

                }
            });
        }
    }

    private void refreshUI() {
        if (config != null) {
            populateTable(configServersTable, config.getConfigServers(), NodeType.CONFIG_NODE);
            populateTable(routersTable, config.getRouterServers(), NodeType.ROUTER_NODE);
            populateTable(dataNodesTable, config.getDataNodes(), NodeType.DATA_NODE);
        } else {
            configServersTable.removeAllItems();
            routersTable.removeAllItems();
            dataNodesTable.removeAllItems();
        }
    }

    private void refreshClustersInfo() {
        List<Config> mongoClusterInfos = MongoDAO.getMongoClustersInfo();
        Config clusterInfo = (Config) clusterCombo.getValue();
        clusterCombo.removeAllItems();
        if (mongoClusterInfos != null && mongoClusterInfos.size() > 0) {
            for (Config mongoClusterInfo : mongoClusterInfos) {
                clusterCombo.addItem(mongoClusterInfo);
                clusterCombo.setItemCaption(mongoClusterInfo,
                        String.format("Name: %s RS: %s", mongoClusterInfo.getClusterName(), mongoClusterInfo.getReplicaSetName()));
            }
            if (clusterInfo != null) {
                for (Config mongoClusterInfo : mongoClusterInfos) {
                    if (mongoClusterInfo.getClusterName().equals(clusterInfo.getClusterName())) {
                        clusterCombo.setValue(mongoClusterInfo);
                        return;
                    }
                }
            } else {
                clusterCombo.setValue(mongoClusterInfos.iterator().next());
            }
        }
    }

    public static void checkNodesStatus(Table table) {
        for (Iterator it = table.getItemIds().iterator(); it.hasNext();) {
            int rowId = (Integer) it.next();
            Item row = table.getItem(rowId);
            Button checkBtn = (Button) (row.getItemProperty(Constants.TABLE_CHECK_PROPERTY).getValue());
            checkBtn.click();
        }
    }

    private Table createTableTemplate(String caption, int size) {
        Table table = new Table(caption);
        table.addContainerProperty("Host", String.class, null);
        table.addContainerProperty(Constants.TABLE_CHECK_PROPERTY, Button.class, null);
        table.addContainerProperty("Start", Button.class, null);
        table.addContainerProperty("Stop", Button.class, null);
        table.addContainerProperty("Destroy", Button.class, null);
        table.addContainerProperty("Status", Embedded.class, null);
        table.setWidth(100, Sizeable.UNITS_PERCENTAGE);
        table.setHeight(size, Sizeable.UNITS_PIXELS);
        table.setPageLength(10);
        table.setSelectable(false);
        table.setImmediate(true);
        return table;
    }

}
