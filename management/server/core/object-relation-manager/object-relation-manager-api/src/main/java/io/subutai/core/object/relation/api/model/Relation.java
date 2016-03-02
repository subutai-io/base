package io.subutai.core.object.relation.api.model;


import java.io.Serializable;

import io.subutai.common.security.relation.RelationLink;


/**
 * Created by talas on 12/10/15.
 */
public interface Relation extends Serializable
{
    long getId();

    RelationLink getSource();

    RelationLink getTarget();

    RelationLink getTrustedObject();

    RelationInfo getRelationInfo();

    RelationStatus getRelationStatus();

    void setRelationStatus( final RelationStatus relationStatus );

    String getKeyId();
}
