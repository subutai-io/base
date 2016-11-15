package io.subutai.core.systemmanager.impl.entity;


import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;


@Entity
@Table( name = "update_history" )
@Access( AccessType.FIELD )
public class UpdateEntity
{
    @Id
    @GeneratedValue
    private Long id;

    @Column
    private Long updateDate;

    @Column
    private String prevVersion;

    @Column
    private String currentVersion;

    @Column
    private String prevCommitId;

    @Column
    private String currentCommitId;


    public UpdateEntity()
    {
    }


    public UpdateEntity( final String prevVersion, final String prevCommitId )
    {
        this.prevVersion = prevVersion;
        this.prevCommitId = prevCommitId;
        this.updateDate = System.currentTimeMillis();
    }


    public Long getUpdateDate()
    {
        return updateDate;
    }


    public String getPrevVersion()
    {
        return prevVersion;
    }


    public String getPrevCommitId()
    {
        return prevCommitId;
    }


    public String getCurrentVersion()
    {
        return currentVersion;
    }


    public String getCurrentCommitId()
    {
        return currentCommitId;
    }


    public void setCurrentVersion( final String currentVersion )
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( currentVersion ) );

        this.currentVersion = currentVersion;
    }


    public void setCurrentCommitId( final String currentCommitId )
    {
        this.currentCommitId = currentCommitId;
    }
}
