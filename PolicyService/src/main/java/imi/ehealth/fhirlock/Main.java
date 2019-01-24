package imi.ehealth.fhirlock;

import spark.*;

import java.util.logging.Logger;

/**
 * Creates a spark entry point with routes
 */
public class Main {
    private static final Logger log = Logger.getLogger( Main.class.getName() );

    public static void main(String[] args) {
        //set port
        Spark.port(4567);

        //set route "/hello" (just for testing)
        Spark.get("/hello", (req, res) -> "Hello World");

        //set route /evaluate -> takes a post request with xacml request-xml as data
        Spark.post("/evaluate", handleEvaluate);

        //set route /patientAccessList -> takes a request with the username as data
        Spark.post("/patientAccessList",handlePatientAccess);
    }

    public static Route handleEvaluate = (request, response) -> {
        log.info("/evaluate request..");
        String requestXml = request.body();
        return PolicyEvaluator.getEvaluator().evaluate(requestXml);

    };

    public static Route handlePatientAccess = (request, response) -> {
        log.info("/patientAccessList request..");
        String userName = request.body();
        DbConfiguration dbConfig = new DbConfiguration();
        dbConfig.load();
        MongoQuery query = new MongoQuery(dbConfig);
        return String.join(";", query.getPatientAccessList(userName));

    };
}
