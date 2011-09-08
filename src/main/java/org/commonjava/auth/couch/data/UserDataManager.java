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
package org.commonjava.auth.couch.data;

import static org.commonjava.auth.couch.util.IdUtils.namespaceId;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.commonjava.auth.couch.conf.UserManagerConfiguration;
import org.commonjava.auth.couch.data.UserViewRequest.View;
import org.commonjava.auth.couch.model.Permission;
import org.commonjava.auth.couch.model.Role;
import org.commonjava.auth.couch.model.User;
import org.commonjava.couch.db.CouchDBException;
import org.commonjava.couch.db.CouchManager;
import org.commonjava.couch.model.CouchDocRef;

public class UserDataManager
{

    @Inject
    private CouchManager couch;

    @Inject
    private UserManagerConfiguration config;

    @Inject
    private PasswordManager passwordManager;

    public UserDataManager()
    {}

    public UserDataManager( final UserManagerConfiguration config,
                            final PasswordManager passwordManager, final CouchManager couch )
    {
        this.config = config;
        this.passwordManager = passwordManager;
        this.couch = couch;
    }

    public void install()
        throws UserDataException
    {
        try
        {
            couch.initialize( config.getDatabaseUrl(), config.getLogicApplication(),
                              UserViewRequest.APPLICATION_RESOURCE );
        }
        catch ( CouchDBException e )
        {
            throw new UserDataException(
                                         "Failed to initialize user-management database: %s (application: %s). Reason: %s",
                                         e, config.getDatabaseUrl(),
                                         UserViewRequest.APPLICATION_RESOURCE, e.getMessage() );
        }
    }

    public void setupAdminInformation()
        throws UserDataException
    {
        Permission permission = new Permission( Permission.WILDCARD );
        Role role = new Role( Role.ADMIN, permission );

        User user = config.createInitialAdminUser( passwordManager );
        user.addRole( role );

        storePermission( permission );
        storeRole( role, true );
        storeUser( user, true );
    }

    public User getUser( final String username )
        throws UserDataException
    {
        try
        {
            return couch.getDocument( new CouchDocRef( namespaceId( User.NAMESPACE, username ) ),
                                      config.getDatabaseUrl(), User.class );
        }
        catch ( CouchDBException e )
        {
            throw new UserDataException( "Failed to retrieve user: %s. Reason: %s", e, username,
                                         e.getMessage() );
        }
    }

    public Permission getPermission( final String name )
        throws UserDataException
    {
        try
        {
            return couch.getDocument( new CouchDocRef( namespaceId( Permission.NAMESPACE, name ) ),
                                      config.getDatabaseUrl(), Permission.class );
        }
        catch ( CouchDBException e )
        {
            throw new UserDataException( "Failed to retrieve permission: %s. Reason: %s", e, name,
                                         e.getMessage() );
        }
    }

    public Role getRole( final String name )
        throws UserDataException
    {
        try
        {
            return couch.getDocument( new CouchDocRef( namespaceId( Role.NAMESPACE, name ) ),
                                      config.getDatabaseUrl(), Role.class );
        }
        catch ( CouchDBException e )
        {
            throw new UserDataException( "Failed to retrieve role: %s. Reason: %s", e, name,
                                         e.getMessage() );
        }
    }

    public Set<Role> getRoles( final User user )
        throws UserDataException
    {
        UserViewRequest req = new UserViewRequest( config, View.USER_ROLES );
        try
        {
            return new HashSet<Role>( couch.getViewListing( req, config.getDatabaseUrl(),
                                                            Role.class ) );
        }
        catch ( CouchDBException e )
        {
            throw new UserDataException( "Failed to get roles for user: %s. Reason: %s", e,
                                         user.getUsername(), e.getMessage() );
        }
    }

    public Set<Permission> getPermissions( final Role role )
        throws UserDataException
    {
        UserViewRequest req = new UserViewRequest( config, View.ROLE_PERMISSIONS );
        try
        {
            return new HashSet<Permission>( couch.getViewListing( req, config.getDatabaseUrl(),
                                                                  Permission.class ) );
        }
        catch ( CouchDBException e )
        {
            throw new UserDataException( "Failed to get permissions for role: %s. Reason: %s", e,
                                         role.getName(), e.getMessage() );
        }
    }

    public boolean storePermission( final Permission perm )
        throws UserDataException
    {
        try
        {
            return couch.store( perm, config.getDatabaseUrl(), true );
        }
        catch ( CouchDBException e )
        {
            throw new UserDataException( "Failed to store permission: %s. Reason: %s", e, perm,
                                         e.getMessage() );
        }
    }

    public boolean storeRole( final Role role )
        throws UserDataException
    {
        return storeRole( role, false );
    }

    public boolean storeRole( final Role role, final boolean skipIfExists )
        throws UserDataException
    {
        try
        {
            return couch.store( role, config.getDatabaseUrl(), skipIfExists );
        }
        catch ( CouchDBException e )
        {
            throw new UserDataException( "Failed to store role: %s. Reason: %s", e, role,
                                         e.getMessage() );
        }
    }

    public boolean storeUser( final User user )
        throws UserDataException
    {
        return storeUser( user, false );
    }

    public boolean storeUser( final User user, final boolean skipIfExists )
        throws UserDataException
    {
        try
        {
            return couch.store( user, config.getDatabaseUrl(), true );
        }
        catch ( CouchDBException e )
        {
            throw new UserDataException( "Failed to store user: %s. Reason: %s", e, user,
                                         e.getMessage() );
        }
    }

    protected final CouchManager getCouch()
    {
        return couch;
    }

    public Map<String, Permission> createPermissions( final String namespace, final String name,
                                                      final String... verbs )
        throws UserDataException
    {
        Map<String, Permission> result = new HashMap<String, Permission>();
        for ( String verb : verbs )
        {
            Permission perm = new Permission( namespace, name, verb );
            if ( !storePermission( perm ) )
            {
                perm = getPermission( perm.getName() );
            }

            result.put( verb, perm );
        }

        return result;
    }

    public Role createRole( final String name, final Collection<Permission> permissions )
        throws UserDataException
    {
        Role role = new Role( name, permissions );
        if ( !storeRole( role, true ) )
        {
            role = getRole( name );
        }

        return role;
    }

    public Role createRole( final String name, final Permission... permissions )
        throws UserDataException
    {
        Role role = new Role( name, permissions );
        if ( !storeRole( role, true ) )
        {
            role = getRole( name );
        }

        return role;
    }

    public Set<User> getAllUsers()
        throws UserDataException
    {
        try
        {
            List<User> users =
                couch.getViewListing( new UserViewRequest( config, View.ALL_USERS ),
                                      config.getDatabaseUrl(), User.class );

            return new HashSet<User>( users );
        }
        catch ( CouchDBException e )
        {
            throw new UserDataException( "Failed to retrieve full listing of users: %s", e,
                                         e.getMessage() );
        }
    }

    public void deleteUser( final String name )
        throws UserDataException
    {
        try
        {
            couch.delete( new CouchDocRef( namespaceId( User.NAMESPACE, name ) ),
                          config.getDatabaseUrl() );
        }
        catch ( CouchDBException e )
        {
            throw new UserDataException( "Failed to delete user: %s. Reason: %s", e, name,
                                         e.getMessage() );
        }
    }

    public Set<Role> getAllRoles()
        throws UserDataException
    {
        try
        {
            List<Role> roles =
                couch.getViewListing( new UserViewRequest( config, View.ALL_ROLES ),
                                      config.getDatabaseUrl(), Role.class );

            return new HashSet<Role>( roles );
        }
        catch ( CouchDBException e )
        {
            throw new UserDataException( "Failed to retrieve full listing of roles: %s", e,
                                         e.getMessage() );
        }
    }

    public void deleteRole( final String name )
        throws UserDataException
    {
        try
        {
            couch.delete( new CouchDocRef( namespaceId( Role.NAMESPACE, name ) ),
                          config.getDatabaseUrl() );
        }
        catch ( CouchDBException e )
        {
            throw new UserDataException( "Failed to delete role: %s. Reason: %s", e, name,
                                         e.getMessage() );
        }
    }

    public Set<Permission> getAllPermissions()
        throws UserDataException
    {
        try
        {
            List<Permission> permissions =
                couch.getViewListing( new UserViewRequest( config, View.ALL_PERMISSIONS ),
                                      config.getDatabaseUrl(), Permission.class );

            return new HashSet<Permission>( permissions );
        }
        catch ( CouchDBException e )
        {
            throw new UserDataException( "Failed to retrieve full listing of permission: %s", e,
                                         e.getMessage() );
        }
    }

    public void deletePermission( final String name )
        throws UserDataException
    {
        try
        {
            couch.delete( new CouchDocRef( namespaceId( Permission.NAMESPACE, name ) ),
                          config.getDatabaseUrl() );
        }
        catch ( CouchDBException e )
        {
            throw new UserDataException( "Failed to delete permission: %s. Reason: %s", e, name,
                                         e.getMessage() );
        }
    }

}
