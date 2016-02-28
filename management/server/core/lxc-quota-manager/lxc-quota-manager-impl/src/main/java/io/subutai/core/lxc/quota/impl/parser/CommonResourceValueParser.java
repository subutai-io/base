package io.subutai.core.lxc.quota.impl.parser;


import io.subutai.common.resource.ContainerResourceType;
import io.subutai.common.resource.ResourceValue;
import io.subutai.common.resource.ResourceValueParser;


/**
 * Common resource value parser
 */
public final class CommonResourceValueParser
{
    public static ResourceValueParser getInstance( ContainerResourceType type )
    {
        ResourceValueParser result = null;
        switch ( type )
        {
            case CPU:
                result = CpuResourceValueParser.getInstance();
                break;
            case RAM:
                result = RamResourceValueParser.getInstance();
                break;
            default:
                result = DiskValueResourceParser.getInstance();
                break;
        }
        return result;
    }


    public static ResourceValue parse( String resource, ContainerResourceType type )
    {
        ResourceValueParser parser = getInstance( type );
        if ( parser == null )
        {
            throw new IllegalArgumentException( "Resource value parser not registered for type: " + type );
        }
        return parser.parse( resource );
    }


    public static <T> T parse( String resource, ContainerResourceType type, Class<T> format )
    {
        ResourceValueParser parser = getInstance( type );
        if ( parser == null )
        {
            throw new IllegalArgumentException( "Resource value parser not registered for type: " + type );
        }
        ResourceValue resourceValue = parser.parse( resource );
        return ( T ) resourceValue;
    }
}
