/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.subutai.blueprint.auth;


import java.net.URL;
import java.util.Set;

import org.osgi.service.blueprint.reflect.ComponentMetadata;
import org.osgi.service.blueprint.reflect.Metadata;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.apache.aries.blueprint.ComponentDefinitionRegistry;
import org.apache.aries.blueprint.NamespaceHandler;
import org.apache.aries.blueprint.ParserContext;
import org.apache.aries.blueprint.mutable.MutableBeanMetadata;
import org.apache.aries.blueprint.mutable.MutablePassThroughMetadata;


public class AuthorizationNsHandler implements NamespaceHandler
{

    private void parseElement( Element elt, ComponentMetadata cm, ParserContext pc )
    {
        ComponentDefinitionRegistry cdr = pc.getComponentDefinitionRegistry();

        if ( "enable".equals( elt.getLocalName() ) )
        {
            if ( !cdr.containsComponentDefinition( AuthorizationBeanProcessor.AUTH_PROCESSOR_BEAN_NAME ) )
            {
                cdr.registerComponentDefinition( authBeanProcessor( pc, cdr ) );
            }
        }
    }


    private MutableBeanMetadata authBeanProcessor( ParserContext pc, ComponentDefinitionRegistry cdr )
    {
        MutableBeanMetadata meta = pc.createMetadata( MutableBeanMetadata.class );
        meta.setId( AuthorizationBeanProcessor.AUTH_PROCESSOR_BEAN_NAME );
        meta.setRuntimeClass( AuthorizationBeanProcessor.class );
        meta.setProcessor( true );
        meta.addProperty( "cdr", passThrough( pc, cdr ) );
        return meta;
    }


    private MutablePassThroughMetadata passThrough( ParserContext pc, Object o )
    {
        MutablePassThroughMetadata meta = pc.createMetadata( MutablePassThroughMetadata.class );
        meta.setObject( o );
        return meta;
    }


    public ComponentMetadata decorate( Node node, ComponentMetadata cm, ParserContext pc )
    {
        if ( node instanceof Element )
        {
            parseElement( ( Element ) node, cm, pc );
        }
        return cm;
    }


    public Metadata parse( Element elt, ParserContext pc )
    {
        parseElement( elt, pc.getEnclosingComponent(), pc );
        return null;
    }


    public URL getSchemaLocation( String namespace )
    {
        return this.getClass().getResource( "/authz10.xsd" );
    }


    @SuppressWarnings( "rawtypes" )
    public Set<Class> getManagedClasses()
    {
        return null;
    }
}
