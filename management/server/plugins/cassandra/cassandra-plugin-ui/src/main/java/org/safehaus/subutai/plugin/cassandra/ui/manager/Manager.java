/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.safehaus.subutai.plugin.cassandra.ui.manager;


import com.google.common.collect.Sets;
import com.vaadin.data.Item;
import com.vaadin.data.Property;
import com.vaadin.event.ItemClickEvent;
import com.vaadin.server.Sizeable;
import com.vaadin.server.ThemeResource;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.Component;
import com.vaadin.ui.Embedded;
import com.vaadin.ui.GridLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.Notification;
import com.vaadin.ui.Table;
import com.vaadin.ui.Window;
import org.safehaus.subutai.common.protocol.Agent;
import org.safehaus.subutai.plugin.cassandra.api.CassandraClusterConfig;
import org.safehaus.subutai.plugin.cassandra.ui.CassandraUI;
import org.safehaus.subutai.server.ui.component.ConfirmationDialog;
import org.safehaus.subutai.server.ui.component.ProgressWindow;
import org.safehaus.subutai.server.ui.component.TerminalWindow;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Manager {

    private final Table nodesTable;
    private GridLayout contentRoot;
    private ComboBox clusterCombo;
    private HorizontalLayout controlsContent;
    private CassandraClusterConfig config;
    private static final Pattern cassandraPattern = Pattern.compile( ".*(Cassandra.+?g).*" );
    private final Embedded progressIcon = new Embedded( "", new ThemeResource( "img/spinner.gif" ) );


    public Manager() {

        contentRoot = new GridLayout();
        contentRoot.setSpacing( true );
        contentRoot.setMargin( true );
        contentRoot.setSizeFull();
        contentRoot.setRows( 10 );
        contentRoot.setColumns( 1 );

        //tables go here
        nodesTable = createTableTemplate( "Cluster nodes" );

        controlsContent = new HorizontalLayout();
        controlsContent.setSpacing( true );
        controlsContent.setHeight( 100, Sizeable.Unit.PERCENTAGE );

        getClusterNameLabel();
        getClusterCombo();
        getRefreshClusterButton();
        getCheckAllButton();
        getStartAllButton();
        getStopAllButton();
        getDestroyClusterButton();

        controlsContent.addComponent( progressIcon );
        contentRoot.addComponent( controlsContent, 0, 0 );
        contentRoot.addComponent( nodesTable, 0, 1, 0, 9 );
    }


    /**
     * Creates table in which all nodes in the cluster are listed.
     * @param caption title of table
     * @return nodes table
     */
    private Table createTableTemplate( String caption ) {
        final Table table = new Table( caption );
        table.addContainerProperty( "Host", String.class, null );
        table.addContainerProperty( "IP", String.class, null );
        table.addContainerProperty( "Check", Button.class, null );
        table.addContainerProperty( "Seed", String.class, null );
        table.addContainerProperty( "Service Status", Label.class, null );
        table.setSizeFull();
        table.setPageLength( 10 );
        table.setSelectable( false );
        table.setImmediate( true );
        table.setColumnCollapsingAllowed( true );
        table.setColumnCollapsed( "Check", true );
        table.addItemClickListener( new ItemClickEvent.ItemClickListener() {
            @Override
            public void itemClick( ItemClickEvent event ) {
                if( event.isDoubleClick() ) {
                    String lxcHostname =
                            ( String ) table.getItem( event.getItemId() ).getItemProperty( "Host" ).getValue();
                    Agent lxcAgent = CassandraUI.getAgentManager().getAgentByHostname( lxcHostname );
                    if( lxcAgent != null ) {
                        TerminalWindow terminal =
                                new TerminalWindow( Sets.newHashSet( lxcAgent ), CassandraUI.getExecutor(),
                                        CassandraUI.getCommandRunner(), CassandraUI.getAgentManager() );
                        contentRoot.getUI().addWindow( terminal.getWindow() );
                    } else {
                        show( "Agent is not connected" );
                    }
                }
            }
        } );
        return table;
    }


    /**
     * Fill out the table in which all nodes in the cluster are listed.
     * @param table table to be filled
     * @param agents nodes
     */
    private void populateTable( final Table table, Set<Agent> agents ) {
        table.removeAllItems();
        for( final Agent agent : agents ) {
            final Label resultHolder = new Label();
            final Button checkButton = new Button( "Check" );
            checkButton.addStyleName( "default" );
            checkButton.setVisible( false );

            String isSeed = checkIfSeed( agent );

            final Object rowId = table.addItem( new Object[]{
                    agent.getHostname(), parseIPList( agent.getListIP().toString() ), checkButton, isSeed, resultHolder
            }, null );
            progressIcon.setVisible( false );
            checkButton.addClickListener( new Button.ClickListener() {
                @Override
                public void buttonClick( Button.ClickEvent event ) {
                    progressIcon.setVisible( true );
                    CassandraUI.getExecutor().execute(
                            new CheckTask( config.getClusterName(), agent.getHostname(),
                                    new CompleteEvent() {
                                        public void onComplete( String result ) {
                                            synchronized( progressIcon ) {
                                                resultHolder.setValue( parseServiceResult( result ) );
                                                progressIcon.setVisible( false );
                                            }
                                        }
                                    } ) );
                }
            } );
        }
    }

    /**
     * Shows notification with the given argument
     * @param notification notification which will shown.
     */
    private void show( String notification ) {
        Notification.show( notification );
    }


    /**
     * Creates "Select the cluster" label.
     */
    private void getClusterNameLabel() {
        Label clusterNameLabel = new Label( "Select the cluster" );
        controlsContent.addComponent( clusterNameLabel );
        controlsContent.setComponentAlignment( clusterNameLabel, Alignment.MIDDLE_CENTER );
    }

    /**
     * Creates combo box in which available clusters are listed.
     */
    private void getClusterCombo() {
        clusterCombo = new ComboBox();
        clusterCombo.setImmediate( true );
        clusterCombo.setTextInputAllowed( false );
        clusterCombo.setWidth( 200, Sizeable.Unit.PIXELS );
        clusterCombo.addValueChangeListener( new Property.ValueChangeListener() {
            @Override
            public void valueChange( Property.ValueChangeEvent event ) {
                config = ( CassandraClusterConfig ) event.getProperty().getValue();
                refreshUI();
                checkAll();
            }
        } );

        controlsContent.addComponent( clusterCombo );
        controlsContent.setComponentAlignment( clusterCombo, Alignment.MIDDLE_CENTER );
    }


    /**
     * Removes all items in the table, and populate it again with {@link #populateTable(com.vaadin.ui.Table, java.util.Set)}.
     */
    private void refreshUI() {
        if ( config != null ) {
            populateTable( nodesTable, config.getNodes() );
        }
        else {
            nodesTable.removeAllItems();
        }
    }

    /**
     * @param agent agent
     * @return Yes if give agent is among seeds, otherwise returns No
     */
    public String checkIfSeed( Agent agent ){
        if ( config.getSeedNodes().contains( agent ) ){
            return "Yes";
        }
        return "No";
    }

    /**
     * Parses output of 'service cassandra status' command
     * @param result
     * @return
     */
    public static String parseServiceResult( String result){
        StringBuilder parsedResult = new StringBuilder();
        Matcher tracersMatcher = cassandraPattern.matcher( result );
        if ( tracersMatcher.find() ) {
            parsedResult.append( tracersMatcher.group( 1 ) ).append( " " );
        }

        return parsedResult.toString();
    }


    /**
     * Creates "Refresh Cluster" button and adds click listener to this button.
     */
    private void getRefreshClusterButton() {
        Button refreshClustersBtn = new Button( "Refresh clusters" );
        refreshClustersBtn.addStyleName( "default" );
        refreshClustersBtn.addClickListener( new Button.ClickListener() {
            @Override
            public void buttonClick( Button.ClickEvent clickEvent ) {
                refreshClustersInfo();
            }
        } );

        controlsContent.addComponent( refreshClustersBtn );
        controlsContent.setComponentAlignment( refreshClustersBtn, Alignment.MIDDLE_CENTER );
    }


    /**
     * Refreshes combo box which lists available clusters in DB
     */
    public void refreshClustersInfo() {
        List<CassandraClusterConfig> info = CassandraUI.getCassandraManager().getClusters();
        CassandraClusterConfig clusterInfo = ( CassandraClusterConfig ) clusterCombo.getValue();
        clusterCombo.removeAllItems();
        if ( info != null && info.size() > 0 ) {
            for ( CassandraClusterConfig mongoInfo : info ) {
                clusterCombo.addItem( mongoInfo );
                clusterCombo.setItemCaption( mongoInfo, mongoInfo.getClusterName() );
            }
            if ( clusterInfo != null ) {
                for ( CassandraClusterConfig cassandraInfo : info ) {
                    if ( cassandraInfo.getClusterName().equals( clusterInfo.getClusterName() ) ) {
                        clusterCombo.setValue( cassandraInfo );
                        return;
                    }
                }
            }
            else {
                clusterCombo.setValue( info.iterator().next() );
            }
        }
    }

    /**
     * Creates "Check All" button and adds click listener to this button.
     */
    private void getCheckAllButton() {
        final Button checkAllBtn = new Button( "Check all" );
        checkAllBtn.addStyleName( "default" );
        checkAllBtn.addClickListener( new Button.ClickListener() {
            @Override
            public void buttonClick( Button.ClickEvent clickEvent ) {
                UUID trackID = CassandraUI.getCassandraManager().checkCluster( config.getClusterName() );
                ProgressWindow window = new ProgressWindow( CassandraUI.getExecutor(), CassandraUI.getTracker(), trackID,
                        CassandraClusterConfig.PRODUCT_KEY );
                window.getWindow().addCloseListener( new Window.CloseListener() {
                    @Override
                    public void windowClose( Window.CloseEvent closeEvent ) {
                        checkAll();
                    }
                } );
                contentRoot.getUI().addWindow( window.getWindow() );
            }
        } );

        controlsContent.addComponent( checkAllBtn );
        controlsContent.setComponentAlignment( checkAllBtn, Alignment.MIDDLE_CENTER );
    }


    /**
     * Created "Start All" button and adds click listener to this button.
     */
    private void getStartAllButton() {
        Button startAllBtn = new Button( "Start all" );
        startAllBtn.addStyleName( "default" );
        startAllBtn.addClickListener( new Button.ClickListener() {
            @Override
            public void buttonClick( Button.ClickEvent clickEvent ) {
                UUID trackID = CassandraUI.getCassandraManager().startCluster( config.getClusterName() );
                ProgressWindow window = new ProgressWindow( CassandraUI.getExecutor(), CassandraUI.getTracker(), trackID,
                        CassandraClusterConfig.PRODUCT_KEY );
                window.getWindow().addCloseListener( new Window.CloseListener() {
                    @Override
                    public void windowClose( Window.CloseEvent closeEvent ) {
                        checkAll();
                    }
                } );
                contentRoot.getUI().addWindow( window.getWindow() );
            }
        } );
        controlsContent.addComponent( startAllBtn );
        controlsContent.setComponentAlignment( startAllBtn, Alignment.MIDDLE_CENTER );
    }


    /**
     * Created "Stop All" button and adds click listener to this button.
     */
    private void getStopAllButton() {
        Button stopAllBtn = new Button( "Stop all" );
        stopAllBtn.addStyleName( "default" );
        stopAllBtn.addClickListener( new Button.ClickListener() {
            @Override
            public void buttonClick( Button.ClickEvent clickEvent ) {
                UUID trackID = CassandraUI.getCassandraManager().stopCluster( config.getClusterName() );
                ProgressWindow window = new ProgressWindow( CassandraUI.getExecutor(), CassandraUI.getTracker(), trackID,
                        CassandraClusterConfig.PRODUCT_KEY );
                window.getWindow().addCloseListener( new Window.CloseListener() {
                    @Override
                    public void windowClose( Window.CloseEvent closeEvent ) {
                        checkAll();
                    }
                } );
                contentRoot.getUI().addWindow( window.getWindow() );
            }
        } );

        controlsContent.addComponent( stopAllBtn );
        controlsContent.setComponentAlignment( stopAllBtn, Alignment.MIDDLE_CENTER );
    }

    /**
     * Parses supplied string argument to extract external IP.
     * @param ipList ex: [10.10.10.10, 127.0.0.1]
     * @return 10.10.10.10
     */
    public String parseIPList( String ipList ){
        return ipList.substring( ipList.indexOf( "[" ) + 1, ipList.indexOf( "," )  );
    }


    /**
     * Created "Destroy Cluster" button and adds click listener to this button.
     */
    private void getDestroyClusterButton() {
        Button destroyClusterBtn = new Button( "Destroy cluster" );
        destroyClusterBtn.addStyleName( "default" );
        destroyClusterBtn.addClickListener( new Button.ClickListener() {
            @Override
            public void buttonClick( Button.ClickEvent clickEvent ) {
                if ( config != null ) {
                    ConfirmationDialog alert = new ConfirmationDialog(
                            String.format( "Do you want to destroy the %s cluster?", config.getClusterName() ), "Yes",
                            "No" );
                    alert.getOk().addClickListener( new Button.ClickListener() {
                        @Override
                        public void buttonClick( Button.ClickEvent clickEvent ) {
                            UUID trackID =
                                    CassandraUI.getCassandraManager().uninstallCluster( config.getClusterName() );

                            ProgressWindow window =
                                    new ProgressWindow( CassandraUI.getExecutor(), CassandraUI.getTracker(), trackID,
                                            CassandraClusterConfig.PRODUCT_KEY );
                            window.getWindow().addCloseListener( new Window.CloseListener() {
                                @Override
                                public void windowClose( Window.CloseEvent closeEvent ) {
                                    refreshClustersInfo();
                                }
                            } );
                            contentRoot.getUI().addWindow( window.getWindow() );
                        }
                    } );

                    contentRoot.getUI().addWindow( alert.getAlert() );
                }
                else {
                    show( "Please, select cluster" );
                }
            }
        } );

        controlsContent.addComponent( destroyClusterBtn );
        controlsContent.setComponentAlignment( destroyClusterBtn, Alignment.MIDDLE_CENTER );
    }


    public Component getContent() {
        return contentRoot;
    }


    /**
     * Clicks all "Check" buttons on table in which on nodes are listed.
     * "Check" button is made hidden deliberately on this table.
     */
    public void checkAll(){
        for ( Object o : nodesTable.getItemIds() ) {
            int rowId = ( Integer ) o;
            Item row = nodesTable.getItem( rowId );
            Button checkBtn = ( Button ) ( row.getItemProperty( "Check" ).getValue() );
            checkBtn.addStyleName( "default" );
            checkBtn.click();
        }
    }
}
