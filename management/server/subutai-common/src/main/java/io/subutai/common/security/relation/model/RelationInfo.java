package io.subutai.common.security.relation.model;


import java.util.Map;


public interface RelationInfo
{
    long getId();

    int getOwnershipLevel();

    boolean isReadPermission();

    boolean isWritePermission();

    boolean isUpdatePermission();

    boolean isDeletePermission();

    Map<String, String> getRelationTraits();
}
