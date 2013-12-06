package org.safehaus.kiskis.mgmt.server.ui.modules.lxc;

import com.google.common.collect.EvictingQueue;
import com.google.common.collect.Queues;
import com.vaadin.ui.Component;
import com.vaadin.ui.CustomComponent;
import com.vaadin.ui.TabSheet;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.Runo;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.safehaus.kiskis.mgmt.server.ui.modules.lxc.forms.LxcCloneForm;
import org.safehaus.kiskis.mgmt.server.ui.modules.lxc.forms.LxcManageForm;
import org.safehaus.kiskis.mgmt.server.ui.services.Module;
import org.safehaus.kiskis.mgmt.server.ui.services.ModuleService;
import org.safehaus.kiskis.mgmt.shared.protocol.Response;
import org.safehaus.kiskis.mgmt.shared.protocol.api.CommandManagerInterface;
import org.safehaus.kiskis.mgmt.shared.protocol.api.ui.CommandListener;
import org.safehaus.kiskis.mgmt.shared.protocol.settings.Common;

import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LxcModule implements Module {


    private static final Logger LOG = Logger.getLogger(LxcModule.class.getName());
    private ModuleService service;
    private BundleContext context;
    public static final String MODULE_NAME = "LXC";
    private ModuleComponent component;

    public static class ModuleComponent extends CustomComponent implements CommandListener {

        private BundleContext context;
        private TabSheet commandsSheet;
        private LxcCloneForm cloneForm;
        private LxcManageForm manageForm;
        //messages queue
        private final EvictingQueue<Response> queue = EvictingQueue.create(Common.MAX_MODULE_MESSAGE_QUEUE_LENGTH);
        private final Queue<Response> messagesQueue = Queues.synchronizedQueue(queue);
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        //messages queue        

        public ModuleComponent(BundleContext context) {
            this.context = context;

            VerticalLayout verticalLayout = new VerticalLayout();
            verticalLayout.setSpacing(true);
            verticalLayout.setSizeFull();

            commandsSheet = new TabSheet();
            commandsSheet.setStyleName(Runo.TABSHEET_SMALL);
            commandsSheet.setSizeFull();

            cloneForm = new LxcCloneForm();
            commandsSheet.addTab(cloneForm, "Clone");
            manageForm = new LxcManageForm();
            commandsSheet.addTab(manageForm, "Manage");

            verticalLayout.addComponent(commandsSheet);

            setCompositionRoot(verticalLayout);

            try {
                getCommandManager().addListener(this);
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, "Error in addListener", ex);
            }

            addListener(new ComponentDetachListener() {
                @Override
                public void componentDetachedFromContainer(ComponentDetachEvent event) {
                    System.out.println("Lxc is detached");
                    executor.shutdown();
                }
            });

            //messages queue
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    while (!Thread.interrupted()) {
                        try {
                            processAllResponses();
                            Thread.sleep(500);
                        } catch (Exception ex) {
                            LOG.log(Level.SEVERE, "Error in queue executor", ex);
                        }
                    }
                }
            });
            //messages queue
        }

        @Override
        public void onCommand(Response response) {
            messagesQueue.add(response);
        }

        //messages queue
        private void processAllResponses() {
            if (!messagesQueue.isEmpty()) {
                Response[] responses = messagesQueue.toArray(new Response[messagesQueue.size()]);
                messagesQueue.clear();
                for (Response response : responses) {
                    cloneForm.outputResponse(response);
                    manageForm.outputResponse(response);
                }
            }
        }
        //messages queue

        @Override
        public synchronized String getName() {
            return MODULE_NAME;
        }

        private CommandManagerInterface getCommandManager() {
            ServiceReference reference = context
                    .getServiceReference(CommandManagerInterface.class.getName());
            return (CommandManagerInterface) context.getService(reference);
        }
    }

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @Override
    public Component createComponent() {
        component = new ModuleComponent(context);
        return component;
    }

    public void setModuleService(ModuleService service) {
        if (service != null) {
            System.out.println(MODULE_NAME + " registering with ModuleService");
            this.service = service;
            this.service.registerModule(this);
        }
    }

    public void unsetModuleService(ModuleService service) {
        if (service != null) {
            this.service.unregisterModule(this);

        }
        if (getCommandManager() != null) {
            getCommandManager().removeListener(component);
        }
        component.executor.shutdown();
    }

    public void setContext(BundleContext context) {
        this.context = context;
    }

    public static CommandManagerInterface getCommandManager() {
        // get bundle instance via the OSGi Framework Util class
        BundleContext ctx = FrameworkUtil.getBundle(LxcModule.class).getBundleContext();
        if (ctx != null) {
            ServiceReference serviceReference = ctx.getServiceReference(CommandManagerInterface.class.getName());
            if (serviceReference != null) {
                return CommandManagerInterface.class.cast(ctx.getService(serviceReference));
            }
        }

        return null;
    }
}