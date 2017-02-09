package io.subutai.core.identity.api.model;


import java.util.Date;

import javax.security.auth.Subject;


/**
 *
 */
public interface Session
{
    String getId();

    void setId( String id );

    User getUser();

    void setUser( User user );

    int getStatus();

    void setStatus( int status );

    Date getStartDate();

    void setStartDate( Date startDate );

    Date getEndDate();

    void setEndDate( Date endDate );

    Subject getSubject();

    void setSubject( Subject subject );

    void setKurjunToken(String token);

    String getKurjunToken();
}
