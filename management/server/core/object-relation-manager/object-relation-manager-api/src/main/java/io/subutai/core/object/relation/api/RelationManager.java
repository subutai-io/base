package io.subutai.core.object.relation.api;


import java.util.List;

import io.subutai.common.security.relation.RelationLink;
import io.subutai.core.object.relation.api.model.Relation;
import io.subutai.core.object.relation.api.model.RelationInfo;
import io.subutai.core.object.relation.api.model.RelationInfoMeta;
import io.subutai.core.object.relation.api.model.RelationMeta;


public interface RelationManager
{
    String getContext();

    void processTrustMessage( String signedMessage, final String secretKeyId ) throws RelationVerificationException;

    RelationInfo createTrustRelationship( RelationInfoMeta relationInfoMeta );

    Relation buildTrustRelation( RelationInfo relationInfo, RelationMeta relationMeta );

    Relation getRelation( RelationMeta relationMeta );

    void saveRelation( Relation relation );

    RelationInfoManager getRelationInfoManager();

    RelationLink getRelationLink( RelationLink relationLink );

    List<Relation> getRelationsByObject( RelationLink objectRelationLink );

    List<Relation> getRelationsBySource( final RelationLink sourceRelationLink );

    List<Relation> getRelationsByTarget( final RelationLink targetRelationLink );

    void removeRelation( long relationId );
}
