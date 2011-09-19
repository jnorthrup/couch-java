/*******************************************************************************
 * Copyright (C) 2011  John Casey
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.commonjava.maven.mdd.mapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.maven.mae.depgraph.DepGraphNode;
import org.apache.maven.mae.depgraph.DependencyGraph;
import org.apache.maven.mae.depgraph.impl.DependencyGraphResolver;
import org.apache.maven.mae.graph.DirectionalEdge;
import org.apache.maven.mae.project.ProjectLoader;
import org.apache.maven.mae.project.ProjectToolsException;
import org.apache.maven.mae.project.key.FullProjectKey;
import org.apache.maven.mae.project.session.ProjectToolsSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.commonjava.maven.mdd.db.DatabaseException;
import org.commonjava.maven.mdd.db.DependencyDatabase;
import org.commonjava.maven.mdd.model.Artifact;
import org.commonjava.maven.mdd.model.DependencyRelationship;
import org.commonjava.maven.mdd.model.InvalidKeyException;

@Component( role = ProjectMapper.class )
public class ProjectMapper
{
    private static final Logger LOGGER = Logger.getLogger( ProjectMapper.class );;

    @Requirement
    private ProjectLoader projectLoader;

    @Requirement
    private DependencyGraphResolver dependencyGraphResolver;

    @Requirement
    private DependencyDatabase dependencyDatabase;

    public List<DependencyRelationship> mapProjectDirectDependencies( final FullProjectKey key,
                                                                      final MapperSession session )
        throws MapperException
    {
        ProjectToolsSession pts = session.getProjectToolsSession();

        MavenProject project;
        try
        {
            project = projectLoader.buildProjectInstance( key, pts );
        }
        catch ( ProjectToolsException e )
        {
            throw new MapperException( "Failed to load Maven project instance for: %s. Reason: %s",
                                       e, key, e.getMessage() );
        }

        try
        {
            return dependencyDatabase.storeDependencies( key, project.getDependencies(),
                                                         session.getDBSession() );
        }
        catch ( DatabaseException e )
        {
            throw new MapperException(
                                       "Failed to store dependency relationships from direct dependencies of: %s. Reason: %s",
                                       e, key, e.getMessage() );
        }
    }

    public List<DependencyRelationship> mapProjectDependencyGraph( final FullProjectKey key,
                                                                   final MapperSession session )
        throws MapperException
    {
        ProjectToolsSession pts = session.getProjectToolsSession();

        MavenProject project;
        try
        {
            project = projectLoader.buildProjectInstance( key, pts );
        }
        catch ( ProjectToolsException e )
        {
            throw new MapperException( "Failed to load Maven project instance for: %s. Reason: %s",
                                       e, key, e.getMessage() );
        }

        DependencyGraph depGraph =
            dependencyGraphResolver.accumulateGraph( Collections.singleton( project ),
                                                     pts.getRepositorySystemSession(), pts );

        List<DependencyRelationship> rels = new ArrayList<DependencyRelationship>();

        LinkedList<DepGraphNode> toProcess = new LinkedList<DepGraphNode>();
        toProcess.addAll( depGraph.getRoots() );

        while ( !toProcess.isEmpty() )
        {
            DepGraphNode parent = toProcess.removeFirst();

            Collection<DirectionalEdge<DepGraphNode>> childEdges =
                depGraph.getGraph().getManagedGraph().getOutEdges( parent );

            int count = 0;
            for ( DirectionalEdge<DepGraphNode> edge : childEdges )
            {
                DepGraphNode child = edge.getTo();
                if ( !parent.equals( child ) )
                {
                    try
                    {
                        LOGGER.info( "+ " + parent.getKey() + " -> " + child.getKey() );
                        rels.add( new DependencyRelationship( new Artifact( child.getKey() ),
                                                              new Artifact( parent.getKey() ),
                                                              count ) );
                        count++;
                    }
                    catch ( InvalidKeyException e )
                    {
                        throw new MapperException(
                                                   "Cannot create dependency relationship for dependency graph. Reason: %s",
                                                   e, e.getMessage() );
                    }

                    if ( !toProcess.contains( child ) )
                    {
                        toProcess.addLast( child );
                    }
                }
            }
        }

        try
        {
            dependencyDatabase.store( rels, session.getDBSession() );
        }
        catch ( DatabaseException e )
        {
            throw new MapperException(
                                       "Failed to store dependency relationships from dependency graph of: %s. Reason: %s",
                                       e, key, e.getMessage() );
        }

        return rels;
    }
}