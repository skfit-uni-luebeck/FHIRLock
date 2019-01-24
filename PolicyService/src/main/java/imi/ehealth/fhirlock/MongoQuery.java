package imi.ehealth.fhirlock;

import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Projections.excludeId;
import static com.mongodb.client.model.Projections.fields;
import static com.mongodb.client.model.Projections.include;

/**
 * Class for accessing mongo db and running queries to fetch policies
 */
public class MongoQuery {

    private MongoCollection<Document> collection;
    private static final Logger log = Logger.getLogger( MongoQuery.class.getName() );


    /**
     * create new instance with a configuration
     * @param config the configuration
     */
    public MongoQuery(DbConfiguration config){

        MongoClient mongoClient = MongoClients.create("mongodb://" + config.getPath() + ":" + config.getPort());

        MongoDatabase database = mongoClient.getDatabase(config.getName());
        collection = database.getCollection(config.getCollection());
    }

    /**
     * load all policies with a defined "target" and return in a list
     * @param target the target
     * @return list with all matching polices
     */
    public List<String> loadPoliciesByTarget(String target){

        MongoCursor<Document> cursor = collection.find(eq("target", target)).iterator();
        return getPolicies(cursor);
    }

    /**
     * load policyset with a defined "target"
     * @param target the target = patientid
     * @return the polici(es) as a list
     */
    public List<String> loadPolicySetsByTarget(String target){
        MongoCursor<Document> cursor = collection.find(Filters.and(eq("type","set"),eq("target", target))).iterator();
        return getPolicies(cursor);
    }

    /**
     * load all policies with a defined "type" and return in a list
     * @param type the type
     * @return list with all matching polices
     */
    public List<String> loadPoliciesByType(String type){
        MongoCursor<Document> cursor = collection.find(eq("type", type)).iterator();
        return getPolicies(cursor);
    }

    /**
     * load all policies with a defined "name" and return in a list
     * @param name the name
     * @return list with all matching policies
     */
    public List<String> loadPoliciesByName(String name){
        MongoCursor<Document> cursor = collection.find(eq("name", name)).iterator();
        return getPolicies(cursor);
    }

    public List<String> getPatientAccessList(String userName){
        MongoCursor<Document> cursor =
                collection.find(Filters.and(eq("type", "base"),eq("accessList",userName ))).projection(fields(include("target"), excludeId())).iterator();
        List<String> patients = new ArrayList<>();
        if(cursor.hasNext()) {
            try {
                while (cursor.hasNext()) {
                    patients.add((String)cursor.next().get("target"));
                }
                return patients;
            } finally {
                cursor.close();
            }
        }else{
            cursor.close();
            return patients;
        }
    }


    /**
     * loop through collection results and add to list
     * @param cursor collection cursor
     * @return the list of policies
     */
    private List<String> getPolicies(MongoCursor<Document> cursor) {
        List<String> policies = new ArrayList<>();
        if(cursor.hasNext()) {
            try {
                while (cursor.hasNext()) {
                    policies.add((String)cursor.next().get("policy"));
                }
                return policies;
            } finally {
                cursor.close();
            }
        }else{
            cursor.close();
            return policies;
        }
    }
}
