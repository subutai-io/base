package org.safehaus.kiskis.mgmt.ui.accumulo.manager;

/**
 * Created by dilshat on 4/30/14.
 */
public interface CompleteEvent {

    public void onComplete(String status);
}
