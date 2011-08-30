package org.commonjava.couch.db;

import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.log4j.Logger;
import org.commonjava.couch.db.action.CouchDocumentAction;
import org.commonjava.couch.db.action.DeleteAction;
import org.commonjava.couch.db.action.StoreAction;
import org.commonjava.couch.db.handler.ResponseHandlerWithError;
import org.commonjava.couch.db.handler.SerializedGetHandler;
import org.commonjava.couch.db.model.ViewRequest;
import org.commonjava.couch.db.util.ToString;
import org.commonjava.couch.model.CouchApp;
import org.commonjava.couch.model.CouchDocument;
import org.commonjava.couch.model.CouchError;
import org.commonjava.couch.model.io.Serializer;

public class CouchManager
{

    // private static final String KEY = "key";
    //
    // private static final String INCLUDE_DOCS = "include_docs";

    private static final Logger LOGGER = Logger.getLogger( CouchManager.class );

    private static final String REV = "rev";

    private static final String VIEW_BASE = "_view";

    private static final String APP_BASE = "_design";

    private Serializer serializer;

    private HttpClient client;

    private final ExecutorService exec = Executors.newCachedThreadPool();

    public CouchManager( final Serializer serializer )
    {
        this.serializer = serializer;
    }

    CouchManager()
    {}

    public void store( final Collection<? extends CouchDocument> documents, final String dbUrl,
                       final boolean skipIfExists, final boolean allOrNothing )
        throws CouchDBException
    {
        Set<StoreAction> toStore = new HashSet<StoreAction>();
        for ( CouchDocument doc : documents )
        {
            if ( skipIfExists && exists( doc, dbUrl ) )
            {
                continue;
            }

            toStore.add( new StoreAction( doc, skipIfExists ) );
        }

        execute( toStore, dbUrl );

    }

    private void execute( final Set<? extends CouchDocumentAction> actions, final String dbUrl )
        throws CouchDBException
    {
        CountDownLatch latch = new CountDownLatch( actions.size() );
        for ( CouchDocumentAction action : actions )
        {
            action.prepareExecution( latch, dbUrl, this );
            exec.execute( action );
        }

        synchronized ( latch )
        {
            while ( latch.getCount() > 0 )
            {
                LOGGER.info( "Waiting for " + latch.getCount() + " actions to complete." );
                try
                {
                    latch.await( 2, TimeUnit.SECONDS );
                }
                catch ( InterruptedException e )
                {
                    break;
                }
            }
        }

        List<Throwable> errors = new ArrayList<Throwable>();
        for ( CouchDocumentAction action : actions )
        {
            if ( action.getError() != null )
            {
                errors.add( action.getError() );
            }
        }

        if ( !errors.isEmpty() )
        {
            throw new CouchDBException( "Failed to execute %d actions.", errors.size() ).withNestedErrors( errors );
        }
    }

    public void delete( final Collection<? extends CouchDocument> documents, final String dbUrl,
                        final boolean allOrNothing )
        throws CouchDBException
    {
        Set<DeleteAction> toDelete = new HashSet<DeleteAction>();
        for ( CouchDocument doc : documents )
        {
            if ( !hasRevision( doc, dbUrl ) )
            {
                continue;
            }

            toDelete.add( new DeleteAction( doc ) );
        }

        execute( toDelete, dbUrl );
    }

    public void modify( final Collection<? extends CouchDocumentAction> actions,
                        final String dbUrl, final boolean allOrNothing )
        throws CouchDBException
    {
        execute( new HashSet<CouchDocumentAction>( actions ), dbUrl );
    }

    public <V> V getView( final ViewRequest req, final String dbUrl, final Class<V> type )
        throws CouchDBException
    {
        String url;
        try
        {
            url =
                buildUrl( dbUrl, req.getRequestParameters(), APP_BASE, req.getApplication(),
                          VIEW_BASE, req.getView() );
        }
        catch ( MalformedURLException e )
        {
            throw new CouchDBException( "Invalid view URL for: %s. Reason: %s", e, req,
                                        e.getMessage() );
        }

        HttpGet request = new HttpGet( url );
        return executeHttpWithResponse( request,
                                        type,
                                        new ToString(
                                                      "Failed to retrieve contents for view request: %s",
                                                      req ) );
    }

    public void store( final CouchDocument doc, final String dbUrl, final boolean skipIfExists )
        throws CouchDBException
    {
        if ( skipIfExists && exists( doc, dbUrl ) )
        {
            return;
        }

        HttpPost request = new HttpPost( dbUrl );
        try
        {
            request.setHeader( "Referer", dbUrl );
            String src = serializer.toString( doc );
            request.setEntity( new StringEntity( src, "application/json", "UTF-8" ) );

            executeHttp( request, SC_CREATED, "Failed to store document" );
        }
        catch ( UnsupportedEncodingException e )
        {
            throw new CouchDBException( "Failed to store document: %s.\nReason: %s", e, doc,
                                        e.getMessage() );
        }
    }

    public void delete( final CouchDocument doc, final String dbUrl )
        throws CouchDBException
    {
        if ( !exists( doc, dbUrl ) )
        {
            return;
        }

        String url = buildDocUrl( dbUrl, doc, true );
        HttpDelete request = new HttpDelete( url );
        executeHttp( request, SC_OK, "Failed to delete document" );
    }

    public boolean viewExists( final String baseUrl, final String appName, final String viewName )
        throws CouchDBException
    {
        try
        {
            return exists( buildUrl( baseUrl, null, APP_BASE, appName, VIEW_BASE, viewName ) );
        }
        catch ( MalformedURLException e )
        {
            throw new CouchDBException( "Cannot format view URL for: %s in: %s. Reason: %s", e,
                                        viewName, appName, e.getMessage() );
        }
        catch ( CouchDBException e )
        {
            throw new CouchDBException( "Cannot verify existence of view: %s in: %s. Reason: %s",
                                        e, viewName, appName, e.getMessage() );
        }
    }

    public boolean appExists( final String baseUrl, final String appName )
        throws CouchDBException
    {
        try
        {
            return exists( buildUrl( baseUrl, null, APP_BASE, appName ) );
        }
        catch ( MalformedURLException e )
        {
            throw new CouchDBException( "Cannot format application URL: %s. Reason: %s", e,
                                        appName, e.getMessage() );
        }
        catch ( CouchDBException e )
        {
            throw new CouchDBException( "Cannot verify existence of application: %s. Reason: %s",
                                        e, appName, e.getMessage() );
        }
    }

    private boolean hasRevision( final CouchDocument doc, final String dbUrl )
        throws CouchDBException
    {
        String docUrl = buildDocUrl( dbUrl, doc, false );
        boolean exists = false;

        HttpHead request = new HttpHead( docUrl );
        HttpResponse response = executeHttp( request, "Failed to ping database URL" );

        StatusLine statusLine = response.getStatusLine();
        if ( statusLine.getStatusCode() == SC_OK )
        {
            exists = true;
        }
        else if ( statusLine.getStatusCode() != SC_NOT_FOUND )
        {
            HttpEntity entity = response.getEntity();
            CouchError error;

            try
            {
                error = serializer.toError( entity );
            }
            catch ( IOException e )
            {
                throw new CouchDBException(
                                            "Failed to ping database URL: %s.\nReason: %s\nError: Cannot read error status: %s",
                                            e, docUrl, statusLine, e.getMessage() );
            }

            throw new CouchDBException( "Failed to ping database URL: %s.\nReason: %s\nError: %s",
                                        docUrl, statusLine, error );
        }

        if ( exists )
        {
            Header etag = response.getFirstHeader( "Etag" );
            String rev = etag.getValue();
            if ( rev.startsWith( "\"" ) || rev.startsWith( "'" ) )
            {
                rev = rev.substring( 1 );
            }

            if ( rev.endsWith( "\"" ) || rev.endsWith( "'" ) )
            {
                rev = rev.substring( 0, rev.length() - 1 );
            }

            doc.setCouchDocRev( rev );
        }

        return exists;
    }

    public boolean exists( final CouchDocument doc, final String dbUrl )
        throws CouchDBException
    {
        String docUrl = buildDocUrl( dbUrl, doc, false );
        return exists( docUrl );
    }

    public boolean exists( final String url )
        throws CouchDBException
    {
        boolean exists = false;

        HttpHead request = new HttpHead( url );
        HttpResponse response = executeHttp( request, "Failed to ping database URL" );

        StatusLine statusLine = response.getStatusLine();
        if ( statusLine.getStatusCode() == SC_OK )
        {
            exists = true;
        }
        else if ( statusLine.getStatusCode() != SC_NOT_FOUND )
        {
            HttpEntity entity = response.getEntity();
            CouchError error;

            try
            {
                error = serializer.toError( entity );
            }
            catch ( IOException e )
            {
                throw new CouchDBException(
                                            "Failed to ping database URL: %s.\nReason: %s\nError: Cannot read error status: %s",
                                            e, url, statusLine, e.getMessage() );
            }

            throw new CouchDBException( "Failed to ping database URL: %s.\nReason: %s\nError: %s",
                                        url, statusLine, error );
        }

        return exists;
    }

    public void dropDatabase( final String url )
        throws CouchDBException
    {
        if ( !exists( url ) )
        {
            return;
        }

        HttpDelete request = new HttpDelete( url );
        executeHttp( request, SC_OK, "Failed to drop database" );
    }

    public void createDatabase( final String url )
        throws CouchDBException
    {
        HttpPut request = new HttpPut( url );
        executeHttp( request, SC_CREATED, "Failed to create database" );
    }

    public void installApplication( final CouchApp app, final String dbUrl )
        throws CouchDBException
    {
        String url = buildDocUrl( dbUrl, app, true );
        HttpPut request = new HttpPut( url );
        try
        {
            request.setHeader( "Referer", dbUrl );
            String appJson = serializer.toString( app );
            request.setEntity( new StringEntity( appJson, "application/json", "UTF-8" ) );

            executeHttp( request, SC_CREATED, "Failed to store application document" );
        }
        catch ( UnsupportedEncodingException e )
        {
            throw new CouchDBException( "Failed to store application document: %s.\nReason: %s", e,
                                        app, e.getMessage() );
        }
    }

    protected HttpResponse executeHttp( final HttpRequestBase request, final String failureMessage )
        throws CouchDBException
    {
        return executeHttp( request, null, failureMessage );
    }

    protected HttpResponse executeHttp( final HttpRequestBase request,
                                        final Integer expectedStatus, final Object failureMessage )
        throws CouchDBException
    {
        String url = request.getURI().toString();

        try
        {
            HttpResponse response = getClient().execute( request );
            StatusLine statusLine = response.getStatusLine();
            if ( expectedStatus != null && statusLine.getStatusCode() != expectedStatus )
            {
                HttpEntity entity = response.getEntity();
                CouchError error = serializer.toError( entity );
                throw new CouchDBException( "%s: %s.\nHTTP Response: %s\nError: %s",
                                            failureMessage, url, statusLine, error );
            }

            return response;
        }
        catch ( UnsupportedEncodingException e )
        {
            throw new CouchDBException( "%s: %s.\nReason: %s", e, failureMessage, url,
                                        e.getMessage() );
        }
        catch ( ClientProtocolException e )
        {
            throw new CouchDBException( "%s: %s.\nReason: %s", e, failureMessage, url,
                                        e.getMessage() );
        }
        catch ( IOException e )
        {
            throw new CouchDBException( "%s: %s.\nReason: %s", e, failureMessage, url,
                                        e.getMessage() );
        }
        finally
        {
            cleanup( request );
        }
    }

    protected <T> T executeHttpWithResponse( final HttpRequestBase request, final Class<T> type,
                                             final Object failureMessage )
        throws CouchDBException
    {
        return executeHttpWithResponse( request,
                                        new SerializedGetHandler<T>( getSerializer(), type ),
                                        failureMessage );
    }

    private Serializer getSerializer()
    {
        return serializer;
    }

    protected <T> T executeHttpWithResponse( final HttpRequestBase request,
                                             final ResponseHandlerWithError<T> handler,
                                             final Object failureMessage )
        throws CouchDBException
    {
        String url = request.getURI().toString();

        try
        {
            T result = getClient().execute( request, handler );
            if ( result == null && handler.getError() != null )
            {
                throw handler.getError();
            }

            return result;
        }
        catch ( UnsupportedEncodingException e )
        {
            throw new CouchDBException( "%s: %s.\nReason: %s", e, failureMessage, url,
                                        e.getMessage() );
        }
        catch ( ClientProtocolException e )
        {
            throw new CouchDBException( "%s: %s.\nReason: %s", e, failureMessage, url,
                                        e.getMessage() );
        }
        catch ( IOException e )
        {
            throw new CouchDBException( "%s: %s.\nReason: %s", e, failureMessage, url,
                                        e.getMessage() );
        }
        finally
        {
            cleanup( request );
        }
    }

    protected synchronized HttpClient getClient()
    {
        if ( client == null )
        {
            ThreadSafeClientConnManager ccm = new ThreadSafeClientConnManager();
            ccm.setMaxTotal( 20 );

            client = new DefaultHttpClient( ccm );
        }
        return client;
    }

    protected void cleanup( final HttpRequestBase request )
    {
        request.abort();
        getClient().getConnectionManager().closeExpiredConnections();
        getClient().getConnectionManager().closeIdleConnections( 2, TimeUnit.SECONDS );
    }

    protected String buildDocUrl( final String baseUrl, final CouchDocument doc,
                                  final boolean includeRevision )
        throws CouchDBException
    {
        try
        {
            String url;
            if ( includeRevision && doc.getCouchDocRev() != null )
            {
                Map<String, String> params = Collections.singletonMap( REV, doc.getCouchDocRev() );
                url = buildUrl( baseUrl, params, doc.getCouchDocId() );
            }
            else
            {
                url = buildUrl( baseUrl, null, doc.getCouchDocId() );
            }

            return url;
        }
        catch ( MalformedURLException e )
        {
            throw new CouchDBException(
                                        "Failed to format document URL for id: %s [revision=%s].\nReason: %s",
                                        e, doc.getCouchDocId(), doc.getCouchDocRev(),
                                        e.getMessage() );
        }
    }

    protected String buildUrl( final String baseUrl, final Map<String, String> params,
                               final String... parts )
        throws MalformedURLException
    {
        StringBuilder urlBuilder = new StringBuilder( baseUrl );
        for ( String part : parts )
        {
            if ( part.startsWith( "/" ) )
            {
                part = part.substring( 1 );
            }

            if ( urlBuilder.charAt( urlBuilder.length() - 1 ) != '/' )
            {
                urlBuilder.append( "/" );
            }

            urlBuilder.append( part );
        }

        if ( params != null && !params.isEmpty() )
        {
            urlBuilder.append( "?" );
            boolean first = true;
            for ( Map.Entry<String, String> param : params.entrySet() )
            {
                if ( first )
                {
                    first = false;
                }
                else
                {
                    urlBuilder.append( "&" );
                }

                urlBuilder.append( param.getKey() ).append( "=" ).append( param.getValue() );
            }
        }

        return new URL( urlBuilder.toString() ).toExternalForm();
    }

}
