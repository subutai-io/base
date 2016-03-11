package io.subutai.core.appender;


public class SubutaiErrorEvent
{
    final long timeStamp;
    final String loggerName;
    final String renderedMessage;
    final String stackTrace;


    public SubutaiErrorEvent( final long timeStamp, final String loggerName, final String renderedMessage,
                              final String stackTrace )
    {
        this.timeStamp = timeStamp;
        this.loggerName = loggerName;
        this.renderedMessage = renderedMessage;
        this.stackTrace = stackTrace;
    }


    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder( "SubutaiLogEvent{" );
        sb.append( "timeStamp=" ).append( timeStamp );
        sb.append( ", loggerName='" ).append( loggerName ).append( '\'' );
        sb.append( ", renderedMessage='" ).append( renderedMessage ).append( '\'' );
        sb.append( ", stackTrace='" ).append( stackTrace ).append( '\'' );
        sb.append( '}' );
        return sb.toString();
    }
}
