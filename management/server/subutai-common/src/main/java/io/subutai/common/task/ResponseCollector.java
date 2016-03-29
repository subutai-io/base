package io.subutai.common.task;


import java.util.List;


/**
 * Response collector interface to collect task responses.
 */
public interface ResponseCollector<R extends TaskRequest, T extends TaskResponse>
{

    void onSuccess( R request, T response );

    void onFailure( R request, List<Throwable> exceptions );
}
