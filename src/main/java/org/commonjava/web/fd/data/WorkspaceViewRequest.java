/*******************************************************************************
 * Copyright (C) 2011  John Casey
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public
 * License along with this program.  If not, see 
 * <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.commonjava.web.fd.data;

import org.commonjava.couch.db.model.ViewRequest;
import org.commonjava.web.fd.config.FileDepotConfiguration;

public class WorkspaceViewRequest
    extends ViewRequest
{

    public static final String APPLICATION_RESOURCE = "workspace-logic";

    public enum View
    {
        WORKSPACES( "workspaces" );

        String name;

        private View( final String name )
        {
            this.name = name;
        }

        public String viewName()
        {
            return name;
        }
    }

    public WorkspaceViewRequest( final FileDepotConfiguration config, final View view )
    {
        super( config.getLogicApplication(), view.viewName() );
        setParameter( INCLUDE_DOCS, true );
    }

}
