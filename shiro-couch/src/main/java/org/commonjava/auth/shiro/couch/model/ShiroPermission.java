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
package org.commonjava.auth.shiro.couch.model;

import org.commonjava.auth.couch.model.Permission;

public class ShiroPermission
    implements org.apache.shiro.authz.Permission
{

    // private final Logger logger = new Logger( getClass() );

    private final Permission permission;

    public ShiroPermission( final Permission permission )
    {
        this.permission = permission;
    }

    @Override
    public boolean implies( final org.apache.shiro.authz.Permission p )
    {
        // logger.info( "Checking whether permission: '%s' implies permission: '%s'",
        // this.permission.getName(), ( (ShiroPermission) p ).permission.getName() );

        String name = permission.getName();
        if ( name.equals( Permission.WILDCARD ) )
        {
            // logger.info( "YES(1)" );
            return true;
        }

        if ( name.endsWith( Permission.WILDCARD ) && ( p instanceof ShiroPermission ) )
        {
            ShiroPermission perm = (ShiroPermission) p;
            String prefix = name.substring( 0, name.length() - Permission.WILDCARD.length() );

            String permName = perm.permission.getName();
            boolean result = permName.length() > prefix.length() && permName.startsWith( prefix );
            // logger.info( result ? "YES(2)" : "NO(2)" );

            return result;
        }

        // logger.info( "NO(3)" );
        return false;
    }

}