package org.commonjava.couch.change;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import org.commonjava.couch.io.json.SerializationAdapter;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

public class CouchDocChangeDeserializer
    implements JsonDeserializer<CouchDocChange>, SerializationAdapter
{

    private static final String SEQ = "seq";

    private static final String ID = "id";

    private static final String CHANGES_ARRAY = "changes";

    private static final String REV = "rev";

    private static final String DELETED = "deleted";

    @Override
    public Type typeLiteral()
    {
        return CouchDocChange.class;
    }

    @Override
    public CouchDocChange deserialize( final JsonElement json, final Type typeOfT,
                                       final JsonDeserializationContext context )
        throws JsonParseException
    {
        JsonObject record = json.getAsJsonObject();
        int seq = record.get( SEQ ).getAsInt();
        String id = record.get( ID ).getAsString();

        JsonElement element = record.get( DELETED );
        boolean deleted = element == null ? false : element.getAsBoolean();

        JsonArray changesArray = record.getAsJsonArray( CHANGES_ARRAY );
        List<String> revs = new ArrayList<String>( changesArray.size() );
        for ( JsonElement revRecord : changesArray )
        {
            revs.add( revRecord.getAsJsonObject().get( REV ).getAsString() );
        }

        return new CouchDocChange( seq, id, revs, deleted );
    }

}
