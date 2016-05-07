/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.subutai.core.blueprint.auth;


import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;

import io.subutai.common.security.relation.RelationCredibility;


/**
 * Evaluates JEE security annotations
 *
 * @see PermitAll
 * @see DenyAll
 * @see RolesAllowed
 */
public class SecurityAnnotationParser
{

    /**
     * Get the effective annotation regarding method annotations override class annotations. DenyAll has highest
     * priority then RolesAllowed and in the end PermitAll. So the most restrictive annotation is preferred.
     *
     * @param m Method to check
     *
     * @return effective annotation (either DenyAll, PermitAll or RolesAllowed)
     */
    Annotation getEffectiveAnnotation( Class<?> beanClass, Method m )
    {
        Annotation classLevel = getAuthAnnotation( beanClass );
        try
        {
            Method beanMethod = beanClass.getMethod( m.getName(), m.getParameterTypes() );
            Annotation methodLevel = getAuthAnnotation( beanMethod );
            return ( methodLevel != null ) ? methodLevel : classLevel;
        }
        catch ( Exception e )
        {
            throw new IllegalStateException( e );
        }
    }


    private Annotation getAuthAnnotation( AnnotatedElement element )
    {
        Annotation ann = null;
        ann = element.getAnnotation( DenyAll.class );
        if ( ann == null )
        {
            ann = element.getAnnotation( RolesAllowed.class );
        }
        if ( ann == null )
        {
            ann = element.getAnnotation( PermitAll.class );
        }
        if ( ann == null )
        {
            ann = element.getAnnotation( RelationCredibility.class );
        }
        return ann;
    }


    /**
     * A class is secured if either the class or one of its methods is secured. An AnnotatedElement is secured if
     * @RolesAllowed or @DenyAll is present.
     */
    public boolean isSecured( Class<?> clazz )
    {
        if ( clazz == Object.class )
        {
            return false;
        }
        if ( isSecuredEl( clazz ) )
        {
            return true;
        }
        for ( Method m : clazz.getMethods() )
        {
            if ( isSecuredEl( m ) )
            {
                return true;
            }
        }
        return false;
    }


    private boolean isSecuredEl( AnnotatedElement element )
    {
        return element.isAnnotationPresent( RelationCredibility.class ) || element.isAnnotationPresent( RolesAllowed.class )
                || element.isAnnotationPresent( DenyAll.class );
    }
}
