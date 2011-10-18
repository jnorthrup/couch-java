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
package org.commonjava.couch.util;

public final class IdUtils
{

    private IdUtils()
    {}

    public static String nonNamespaceId( final String namespace, final String namespaceId )
    {
        if ( !namespaceId.startsWith( namespace ) )
        {
            return namespaceId;
        }

        return namespaceId.substring( namespace.length() + 1 );
    }

    public static String namespaceId( final String namespace, final String... parts )
    {
        StringBuilder sb = new StringBuilder();
        sb.append( namespace );
        for ( String part : parts )
        {
            sb.append( ":" ).append( part );
        }

        return sb.toString();
    }

}
