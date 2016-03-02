package io.subutai.common.security.objects;


/**
 * PGP Key trust level
 */
public enum KeyTrustLevel
{
    Never( 1, "Never Trust" ),
    Marginal( 2, "Marginal" ),
    Full( 3, "Full" ),
    Ultimate( 4, "Ultimate Trust" );

    private String name;
    private int id;

    KeyTrustLevel(  int id, String name)
    {
        this.id = id;
        this.name = name;
    }

    public String getName()
    {
        return name;
    }

    public int getId()
    {
        return id;
    }
}
