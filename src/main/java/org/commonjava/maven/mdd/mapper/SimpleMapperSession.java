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

import org.apache.maven.mae.project.session.ProjectToolsSession;
import org.apache.maven.mae.project.session.SimpleProjectToolsSession;
import org.commonjava.maven.mdd.db.session.DependencyDBSession;
import org.commonjava.maven.mdd.db.session.SimpleDependencyDBSession;

public class SimpleMapperSession
    implements MapperSession
{

    private final ProjectToolsSession projectToolsSession;

    private final DependencyDBSession dbSession;

    public SimpleMapperSession( final DependencyDBSession dbSession,
                                final ProjectToolsSession projectToolsSession )
    {
        this.dbSession = dbSession;
        this.projectToolsSession = projectToolsSession;
    }

    public SimpleMapperSession( final String dbBaseUrl )
    {
        this.dbSession = new SimpleDependencyDBSession( dbBaseUrl );
        this.projectToolsSession = new SimpleProjectToolsSession();
    }

    @Override
    public ProjectToolsSession getProjectToolsSession()
    {
        return projectToolsSession;
    }

    @Override
    public DependencyDBSession getDBSession()
    {
        return dbSession;
    }

}
