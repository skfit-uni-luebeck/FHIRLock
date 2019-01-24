package imi.ehealth.fhirlock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.wso2.balana.*;
import org.wso2.balana.attr.BagAttribute;
import org.wso2.balana.attr.StringAttribute;
import org.wso2.balana.combine.PolicyCombiningAlgorithm;
import org.wso2.balana.combine.xacml3.DenyOverridesPolicyAlg;
import org.wso2.balana.cond.EvaluationResult;
import org.wso2.balana.ctx.EvaluationCtx;
import org.wso2.balana.ctx.Status;
import org.wso2.balana.finder.PolicyFinder;
import org.wso2.balana.finder.PolicyFinderModule;
import org.wso2.balana.finder.PolicyFinderResult;
import org.wso2.balana.utils.Constants.PolicyConstants;
import org.wso2.balana.utils.Utils;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Custom Policy Finder Module
 * Loads polices from a database and provides different behaviours for the two differenz request types
 */
public class MongoDbPolicyFinderModule extends PolicyFinderModule {
    private Map<URI, AbstractPolicy> policies;
    private PolicyFinder finder = null;

    private static final Logger myLog = LoggerFactory.getLogger(MongoDbPolicyFinderModule.class);

    private MongoQuery query;

    private final String attributeIdPatientResource = "urn:oasis:names:tc:xacml:1.0:resource:patient-id";
    private final String attributeIdResourceType = "urn:oasis:names:tc:xacml:1.0:resource:resource-type";

    private String patientId;
    private String resourceType;
    private String resourceId;

    public MongoDbPolicyFinderModule(){
        }

    @Override
    public void init(PolicyFinder finder) {
        this.finder = finder;
        DbConfiguration dbConfig = new DbConfiguration();
        dbConfig.load();
        //for Mongo
        this.query =  new MongoQuery(dbConfig);
    }

    @Override
    public PolicyFinderResult findPolicy(EvaluationCtx context) {
        policies = new HashMap<>();

        //try to get the request attributes
        patientId = getAttributeFromRequest(context, attributeIdPatientResource);
        resourceType = getAttributeFromRequest(context, attributeIdResourceType);
        resourceId = getAttributeFromRequest(context, PolicyConstants.RESOURCE_ID);

        ArrayList<AbstractPolicy> selectedPolicies = new ArrayList<AbstractPolicy>();

        PolicyCombiningAlgorithm combiningAlg;
        if(resourceId == null){
            //load all patients if no resourceId submitted
            combiningAlg = new CheckAllPolDenyUnlessPermitAlg();

            //load all base policies
            List<String> policies = query.loadPoliciesByType("base");
            for(String policy : policies){
                loadSinglePolicy(policy);
            }

            Set<Map.Entry<URI, AbstractPolicy>> entrySet = this.policies.entrySet();

            // iterate through all the policies we currently have loaded
            // here we want all of them (assuming they are all patient based): no target matching necessary
            for (Map.Entry<URI, AbstractPolicy> entry : entrySet) {
                selectedPolicies.add(entry.getValue());
            }

        }else{
            //TODO decide which one is the right here
            combiningAlg = new DenyOverridesPolicyAlg();

            //Mongo
            List<String> policies = query.loadPolicySetsByTarget(patientId);
            for(String policy : policies){
                loadSinglePolicy(policy);
            }

            Set<Map.Entry<URI, AbstractPolicy>> entrySet = this.policies.entrySet();

            // iterate through all the policies we currently have loaded
            for (Map.Entry<URI, AbstractPolicy> entry : entrySet) {
                AbstractPolicy policy = entry.getValue();
                MatchResult match = policy.match(context);
                int result = match.getResult();

                // if target matching was indeterminate, then return the error
                if (result == MatchResult.INDETERMINATE) {
                    return new PolicyFinderResult(match.getStatus());
                }

                // see if the target matched
                if (result == MatchResult.MATCH) {
                    if (combiningAlg == null && selectedPolicies.size() > 0) {
                        // we found a match before, so this is an error
                        ArrayList<String> code = new ArrayList<String>();
                        code.add(Status.STATUS_PROCESSING_ERROR);
                        Status status = new Status(code, "too many applicable top-level policies");
                        return new PolicyFinderResult(status);
                    }
                    // this is the first match we've found, so remember it
                    selectedPolicies.add(policy);
                }

            }
        }

        // no errors happened during the search, so now take the right
        // action based on how many policies we found
        switch (selectedPolicies.size()) {
            case 0:
//                if(log.isDebugEnabled()){
//                    log.debug("No matching XACML policy found");
//                }
                return new PolicyFinderResult();
            case 1:
                return new PolicyFinderResult((selectedPolicies.get(0)));
            default:
                return new PolicyFinderResult(new PolicySet(null, combiningAlg, null, selectedPolicies));
        }
    }

    private String getAttributeFromRequest(EvaluationCtx context, String attributeId) {
        try {
            EvaluationResult result = context.getAttribute(new URI(PolicyConstants.DataType.STRING), new URI(attributeId), null, new URI(PolicyConstants.RESOURCE_CATEGORY_URI));
            BagAttribute attributeValue = (BagAttribute)result.getAttributeValue();

            if(!attributeValue.isEmpty()){
                Iterator it = attributeValue.iterator();

               if(it.hasNext()) {
                    StringAttribute sAttribute = (StringAttribute) it.next();
                    return sAttribute.getValue();
                }
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * load a single policy and add to list
     * @param policy the policy xml
     */
    private void loadSinglePolicy(String policy) {

        Document doc = getPolicyXmlDocument(policy);
        if (doc != null) {
            loadPolicy(doc.getDocumentElement(), this.finder);
        }
    }

    private Document getPolicyXmlDocument(String policy) {
        try{
            DocumentBuilderFactory factory = Utils.getSecuredDocumentBuilderFactory();

            factory.setIgnoringComments(true);
            factory.setNamespaceAware(true);
            factory.setValidating(false);
            //Create XML-Document from result
            DocumentBuilder db = factory.newDocumentBuilder();
            InputSource is = new InputSource(new StringReader(policy));
            return db.parse(is);

        }
        catch (IOException | ParserConfigurationException | SAXException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public PolicyFinderResult findPolicy(URI idReference, int type, VersionConstraints constraints,
                                         PolicyMetaData parentMetaData) {

        AbstractPolicy policy = policies.get(idReference);
        if(policy != null){
            if (type == PolicyReference.POLICY_REFERENCE) {
                if (policy instanceof Policy){
                    return new PolicyFinderResult(policy);
                }
            } else {
                if (policy instanceof PolicySet){
                    return new PolicyFinderResult(policy);
                }
            }
        }else{
            //check whether we should load this one..
            Boolean load = false;
            String[] urnParts = idReference.toString().split(":");
            String policyResourceType = urnParts[4];
            String policyId = null;
            if(urnParts.length > 5){
                policyId = urnParts[5];
            }

            //matching by type, and if id exists, by id
            if(policyResourceType.equals(this.resourceType.toLowerCase())){
                if((policyId != null && policyId.equals(this.resourceId)) || policyId == null){
                    //this means the referenced policy is relevant for the request
                    load = true;
                }
            }
            if(load || policyResourceType.equals("base")){
                //try to load policy by name
                List<String> policies = query.loadPoliciesByName(idReference.toString());
                if(policies != null && policies.size() == 1){
                    loadSinglePolicy(policies.get(0));
                    return this.findPolicy(idReference,type,constraints,parentMetaData);
                }
            }else{
                //add dummy policy whose target matching nerver fits..
                AbstractPolicy dummyPolicy = null;
                try {
                    String dummyPolicyXml = "<Policy xmlns=\"urn:oasis:names:tc:xacml:3.0:core:schema:wd-17\" PolicyId=\"urn:policies:dummy\" RuleCombiningAlgId=\"urn:oasis:names:tc:xacml:3.0:rule-combining-algorithm:permit-unless-deny\" Version=\"1.0\">\n" +
                            "\t<Target>\n" +
                            "\t\t<AnyOf>\n" +
                            "\t\t\t<AllOf>\n" +
                            "\t\t\t\t<Match MatchId=\"urn:oasis:names:tc:xacml:1.0:function:string-equal\">\n" +
                            "\t\t\t\t\t<AttributeValue DataType=\"http://www.w3.org/2001/XMLSchema#string\"></AttributeValue>\n" +
                            "\t\t\t\t\t<AttributeDesignator AttributeId=\"urn:oasis:names:tc:xacml:1.0:resource:patient-id\" Category=\"urn:oasis:names:tc:xacml:3.0:attribute-category:resource\" DataType=\"http://www.w3.org/2001/XMLSchema#string\" MustBePresent=\"true\"/>\n" +
                            "\t\t\t\t</Match>\n" +
                            "\t\t\t</AllOf>\n" +
                            "\t\t</AnyOf>\n" +
                            "\t</Target>\n" +
                            "</Policy>";
                    Document policyXmlDocument = getPolicyXmlDocument(dummyPolicyXml);
                    if(policyXmlDocument != null){
                        dummyPolicy = Policy.getInstance(policyXmlDocument.getDocumentElement());
                    }
                } catch (ParsingException e) {
                    e.printStackTrace();
                }
                policies.put(idReference, dummyPolicy);
                return this.findPolicy(idReference,type,constraints,parentMetaData);
            }
            //error only if load but not loadable..
        }

        // if there was an error loading the policy, return the error
        ArrayList<String> code = new ArrayList<String>();
        code.add(Status.STATUS_PROCESSING_ERROR);
        Status status = new Status(code,
                "couldn't load referenced policy");
        return new PolicyFinderResult(status);
    }

    @Override
    public boolean isIdReferenceSupported() {
        return true;
    }

    @Override
    public boolean isRequestSupported() {
        return true;
    }

    /**
     * Private helper that tries to load the given xml policy, and
     * returns null if any error occurs.
     *
     * @param rootPolicyXml policy as xml
     * @param finder policy finder
     * @return  <code>AbstractPolicy</code>
     */
    private AbstractPolicy loadPolicy(Element rootPolicyXml, PolicyFinder finder) {
        AbstractPolicy policy = null;
        try {
            String name = DOMHelper.getLocalName(rootPolicyXml);
            if (name.equals("Policy")) {
                policy = Policy.getInstance(rootPolicyXml);
            } else if (name.equals("PolicySet")) {
                policy = PolicySet.getInstance(rootPolicyXml, finder);
            }
        }
        catch (Exception e) {
            // just only logs
            myLog.error("Fail to load policy : " + rootPolicyXml.getTagName() , e);
        }
        if (policy != null) {
            this.policies.put(policy.getId(), policy);
        }
        return policy;
    }
}
