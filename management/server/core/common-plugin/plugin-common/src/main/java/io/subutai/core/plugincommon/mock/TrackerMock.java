package io.subutai.core.plugincommon.mock;


import java.util.Date;
import java.util.List;
import java.util.UUID;

import io.subutai.common.tracker.TrackerOperation;
import io.subutai.common.tracker.TrackerOperationView;
import io.subutai.core.tracker.api.Tracker;


public class TrackerMock implements Tracker
{
    @Override
    public TrackerOperationView getTrackerOperation( String source, UUID operationTrackId )
    {
        return null;
    }


    @Override
    public TrackerOperation createTrackerOperation( String source, String description )
    {
        return new TrackerOperationMock();
    }


    @Override
    public List<TrackerOperationView> getTrackerOperations( String source, Date fromDate, Date toDate, int limit )
    {
        return null;
    }


    @Override
    public List<String> getTrackerOperationSources()
    {
        return null;
    }


    @Override
    public void printOperationLog( String source, UUID operationTrackId, long maxOperationDurationMs )
    {

    }
}