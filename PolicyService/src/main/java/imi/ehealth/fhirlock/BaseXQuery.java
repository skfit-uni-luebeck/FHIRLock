package imi.ehealth.fhirlock;

import java.io.IOException;

/**
 * Querying BaseX database for policies
 */
public class BaseXQuery {
    private DbConfiguration config;

    /**
     * Constructor with Config object
     * @param config the config object
     */
    public BaseXQuery(DbConfiguration config) {
        this.config = config;
    }


    public String getPatientPolicySet(String patientId) {
        try {
            try (BaseXClient session = new BaseXClient(config.getPath(), config.getPort(), config.getUser(), config.getPass())){
                //XQuery-request to baseX-db
                //set xacml as basic namespace
                //request all "policy"s with policyId-attribute "policyId" in database "config.name"

                //declare namespace x="urn:oasis:names:tc:xacml:3.0:core:schema:wd-17";
                //let $results :=  collection("baseX")//x:Policy[matches(@PolicyId,"MoreComplexPolicy")]
                //let $results2 := collection("baseX")//x:PolicySet[matches(@PolicySetId,"mypolicyset")]
                //return <results>{$results}{$results2}</results>

                //Important: Setting namespace requires that policy xml include namespace..
                String myQuery =    "XQUERY declare namespace x=\"urn:oasis:names:tc:xacml:3.0:core:schema:wd-17\";" +
                                    " let $results := collection(\"" + config.getName() + "\")//x:PolicySet[matches(@PolicySetId,\"" + patientId + "\")]" +
                                    "return <results>{$results}</results>";
                return session.execute(myQuery);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getPolicyById(String policyId) {
        try {
            try (BaseXClient session = new BaseXClient(config.getPath(), config.getPort(), config.getUser(), config.getPass())){

                //Important: Setting namespace requires that policy xml include namespace..
                String myQuery =    "XQUERY declare namespace x=\"urn:oasis:names:tc:xacml:3.0:core:schema:wd-17\";" +
                        " let $results :=  collection(\"" + config.getName() + "\")//x:Policy[@PolicyId = \"" + policyId + "\"]" +
                        " return <results>{$results}{$results2}</results>";
                return session.execute(myQuery);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
