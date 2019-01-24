package imi.ehealth.fhirlock;

import org.apache.commons.io.FileUtils;
import org.wso2.balana.*;
import org.wso2.balana.combine.CombiningAlgFactory;
import org.wso2.balana.combine.CombiningAlgorithm;
import org.wso2.balana.finder.PolicyFinderModule;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Inits the pdp and performs the xacml requests
 */
public class PolicyEvaluator {

    private PDP pdp;
    private static PolicyEvaluator instance;

    private static final Logger log = Logger.getLogger( PolicyEvaluator.class.getName() );


    private PolicyEvaluator() {
        log.info("Create PolicyEvaluator instance..");

        //for configuration by file
        //ClassLoader cl = this.getClass().getClassLoader();
        //InputStream is = cl.getResourceAsStream("pdpconfig.xml");

        try {
            //File file = File.createTempFile("config", ".xml");

            //FileUtils.copyInputStreamToFile(is, file);
            //ConfigurationStore store = new ConfigurationStore(file);
            //PDPConfig config = store.getDefaultPDPConfig();
            //System.setProperty(ConfigurationStore.PDP_CONFIG_PROPERTY, file.getAbsolutePath());
            //log.info("File: " + file.getAbsolutePath());

            Balana balana = Balana.getInstance();

            //manual configuration:
            //add MongoDbPolicyFinderModule as only finder module
            Set modules = balana.getPdpConfig().getPolicyFinder().getModules();
            modules.clear();
            MongoDbPolicyFinderModule mongoDbPolicyFinderModule = new MongoDbPolicyFinderModule();
            modules.add(mongoDbPolicyFinderModule);
            balana.getPdpConfig().getPolicyFinder().setModules(modules);

            pdp = new PDP(balana.getPdpConfig());

        } catch (Exception e) {
            log.info(e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * get the singleton instance of the evaluator and create, if null
     * @return the evaluator instance
     */
    public static PolicyEvaluator getEvaluator(){
        if (PolicyEvaluator.instance == null) {
            PolicyEvaluator.instance = new PolicyEvaluator();
        }
        return PolicyEvaluator.instance;
    }

    /**
     * evaluate a xacml request and return the response
     * @param xacmlRequest the xml xacml request
     * @return the xml xacml response
     */
    public String evaluate(String xacmlRequest) {
        log.info("Starting evaluation...");
        return pdp.evaluate(xacmlRequest);
    }
}
