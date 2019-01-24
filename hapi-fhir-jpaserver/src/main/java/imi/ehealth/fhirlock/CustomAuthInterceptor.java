package imi.ehealth.fhirlock;

import ca.uhn.fhir.context.FhirContext;
import imi.ehealth.fhirlock.policy.EvaluationResult;
import imi.ehealth.fhirlock.policy.EvaluationResultType;
import imi.ehealth.fhirlock.policy.PolicyEvaluator;
import imi.ehealth.fhirlock.policy.RequestCreator;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.ResponseDetails;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.interceptor.ServerOperationInterceptorAdapter;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Condition;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.Resource;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseParameters;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/** CustomAuthorizationInterceptor
 * for GET requests
 * check authN
 * adjust search parameters according to allowed resources
 * check each resulting resource if allowed by sending xacml request
 * Based on HAPI AuthorizationInterceptor with the following licence:

 * #%L
 * HAPI FHIR - Server Framework
 * %%
 * Copyright (C) 2014 - 2018 University Health Network
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

@SuppressWarnings("ConstantConditions")
public class CustomAuthInterceptor extends ServerOperationInterceptorAdapter {
    private static final Logger myLog = LoggerFactory.getLogger(CustomAuthInterceptor.class);

    private String policyServerUrl;

    public String getPolicyServerUrl() {
        return policyServerUrl;
    }

    public void setPolicyServerUrl(String policyServerUrl) {
        this.policyServerUrl = policyServerUrl;
    }

    private String currentUserId;
    private Boolean skipSingleCheck = false;
    private String originalUrl;

    private enum OperationExamineDirection {
        BOTH,
        IN,
        NONE,
        OUT,
    }

    @Override
    public boolean incomingRequestPostProcessed(RequestDetails theRequestDetails, HttpServletRequest theRequest, HttpServletResponse theResponse) throws AuthenticationException {
        //check authN first - might skip everything else
        checkAuthentication(theRequestDetails);

        //this method is called (once) BEFORE the search is actually performed
        skipSingleCheck = false;

        if (currentUserId != null) { //TODO this is mainly for debugging purpose -> to allow the browser to load everything
            //otherwise should checkAuthentication lead to an exception

            //Manipulate search by replacing or adding search parameters with allowed patient resource ids if it's a GET
            //this will reduce the loaded resources
            RequestTypeEnum action = theRequestDetails.getRequestType();
            if (action == RequestTypeEnum.GET) {
                //check if it's a search and not a read - no "/<number>" in path ("/234")
                final String regex = ".*/[0-9].*";
                String requestPath = theRequestDetails.getRequestPath();
                if (!requestPath.matches(regex)) {
                    //should be a search..

                    //get ALL patients the user is allowed to see
                    List<String> allowedIds = getAllowedPatientIds();

                    String requestedResource = theRequestDetails.getResourceName();
                    if (requestedResource.equals("Patient")) {
                        //in that case we don't have to do further single checks
                        skipSingleCheck = true;
                    }

                    //get possible existing search parameter values
                    String[] searchIds = theRequestDetails.getParameters().get(ReferenceDictionaries.PAT_REF_DICT.get(requestedResource));

                    //save original url to write back later (user should see "his" original search)
                    originalUrl = theRequestDetails.getCompleteUrl();

                    if (searchIds != null && searchIds.length > 0) {
                        //search with ids..
                        List<String> wantedIds = new ArrayList<>(Arrays.asList(searchIds[0].split(",")));
                        //..remove those that are not allowed
                        wantedIds.retainAll(allowedIds);

                        //..and save the left ones
                        theRequestDetails.addParameter(ReferenceDictionaries.PAT_REF_DICT.get(requestedResource), new String[]{String.join(",", wantedIds)});

                    } else {
                        //just add search parameter for patient refs
                        theRequestDetails.addParameter(ReferenceDictionaries.PAT_REF_DICT.get(requestedResource), new String[]{String.join(",", allowedIds)});
                    }
                }
            }
        }

        return super.incomingRequestPostProcessed(theRequestDetails, theRequest, theResponse);
    }

    @Override
    public void incomingRequestPreHandled(RestOperationTypeEnum theOperation, ActionRequestDetails theProcessedRequest) {
        IBaseResource inputResource = null;
        IIdType inputResourceId = null;

        switch (determineOperationDirection(theOperation, theProcessedRequest.getResource())) {
            case IN:
            case BOTH:
                inputResource = theProcessedRequest.getResource();
                inputResourceId = theProcessedRequest.getId();
                //only for request that want to put/save some data..
                RequestDetails requestDetails = theProcessedRequest.getRequestDetails();
                //TODO add method to check post requests
                break;
            case OUT:
                // inputResource = null;
                inputResourceId = theProcessedRequest.getId();
                break;
            case NONE:
        }
    }

    @Override
    public boolean outgoingResponse(RequestDetails theRequestDetails, IBaseResource theResponseObject) {
        //this one is called first; is marked as deprecated, so we won't use it
       return true;
    }

    @Override
    public boolean outgoingResponse(RequestDetails theRequestDetails, ResponseDetails theResponseDetails, HttpServletRequest theServletRequest, HttpServletResponse theServletResponse)
    {
        switch (determineOperationDirection(theRequestDetails.getRestOperationType(), null)) {
            case IN:
            case NONE:
                return true;
            case BOTH:
            case OUT:
                break;
        }

        //The output object
        IBaseResource theResponseObject = theResponseDetails.getResponseResource();

        if(currentUserId != null) {
                FhirContext fhirContext = theRequestDetails.getServer().getFhirContext();
                List<IBaseResource> resources = Collections.emptyList();

                switch (theRequestDetails.getRestOperationType()) {
                    case SEARCH_SYSTEM:
                    case SEARCH_TYPE:
                    case HISTORY_INSTANCE:
                    case HISTORY_SYSTEM:
                    case HISTORY_TYPE:
                    case TRANSACTION:
                    case GET_PAGE:
                    case EXTENDED_OPERATION_SERVER:
                    case EXTENDED_OPERATION_TYPE:
                    case EXTENDED_OPERATION_INSTANCE: {
                        if (theResponseObject != null) {
                            resources = toListOfResourcesAndExcludeContainer(theResponseObject, fhirContext);
                        }
                        break;
                    }
                    default: {
                        if (theResponseObject != null) {
                            resources = Collections.singletonList(theResponseObject);
                        }
                        break;
                    }
                }

                if (theResponseObject instanceof Bundle) { //use of Bundle because only STU3 atm
                    Bundle bundle = (Bundle) theResponseObject;

                    if (!skipSingleCheck) {
                        //check each resource and remove from bundle if not allowed
                        for (IBaseResource nextResponse : resources) {
                            if (checkResourceForbidden(theRequestDetails, nextResponse)) {
                                bundle.getEntry().removeIf(x -> x.getResource().getIdElement().getIdPart().equals(nextResponse.getIdElement().getIdPart()));
                            }
                        }

                        bundle.setTotal(bundle.getEntry().size());
                    }
                    //reset original url:
                    bundle.getLinkFirstRep().setUrl(originalUrl);

                    theResponseDetails.setResponseResource(theResponseObject);
                } else { //single resource
                    if (!skipSingleCheck) {
                        if (checkResourceForbidden(theRequestDetails, resources.get(0))) {
                            //there was a deny for that resource..
                            throw new ForbiddenOperationException("Access denied - not enought rights for this resource");
                        }
                    }
                }
        } //TODO for DEBUG only

        return true;
    }



    @Override
    public BaseServerResponseException preProcessOutgoingException(RequestDetails theRequestDetails, Throwable theException, HttpServletRequest theServletRequest) throws ServletException {
        //on exception reset local request parameters like userid
        resetCurrentParameters();
        return super.preProcessOutgoingException(theRequestDetails, theException, theServletRequest);
    }

    private void checkAuthentication(RequestDetails theRequestDetails) {
        //Authentication
        currentUserId = null;
        //get authorization header
        String bearer = theRequestDetails.getHeader("Authorization");

        if (bearer != null) {

            //we assume "Bearer <token>"
            String authHeader = bearer.split(" ")[1];

            currentUserId = Authentication.getUserIdFromToken(authHeader);
            if (currentUserId == null) {
                throw new AuthenticationException("No userid in token or invalid token");
            }

            //get all resource providers
            WebApplicationContext myAppCtx = ContextLoaderListener.getCurrentWebApplicationContext();
            List<IResourceProvider> beans = myAppCtx.getBean("myResourceProvidersDstu3", List.class);

            //check if token is valid and signed by requesting user
            if (!Authentication.checkAuthentication(authHeader, currentUserId, beans)) {
                throw new AuthenticationException("Authentication failed. Mismatch signature token and user key");
            }
        } else {
            //For production:
            //throw new AuthenticationException("No authentication header");
        }
    }

    /**
     * Create a request to get a list of all patients the user is basically allowed to see
     * @return the list of patient resource ids
     */
    private List<String> getAllowedPatientIds(){

        PolicyEvaluator pe = new PolicyEvaluator();

        String result = pe.evaluateRequest(currentUserId,policyServerUrl + "/patientAccessList");

        if (result != null && result.length() > 0) {
            return new ArrayList<>(Arrays.asList(result.split(";")));
        }
        else {
            //else return empty list
            return new ArrayList<>();
        }
    }

    private OperationExamineDirection determineOperationDirection(RestOperationTypeEnum theOperation, IBaseResource theRequestResource) {
        switch (theOperation) {
            case ADD_TAGS:
            case DELETE_TAGS:
            case GET_TAGS:
                // These are DSTU1 operations and not relevant
                return OperationExamineDirection.NONE;

            case EXTENDED_OPERATION_INSTANCE:
            case EXTENDED_OPERATION_SERVER:
            case EXTENDED_OPERATION_TYPE:
                return OperationExamineDirection.BOTH;

            case METADATA:
                // Security does not apply to these operations
                return OperationExamineDirection.IN;

            case DELETE:
                // Delete is a special case
                return OperationExamineDirection.NONE;

            case CREATE:
            case UPDATE:
            case PATCH:
                // if (theRequestResource != null) {
                // if (theRequestResource.getIdElement() != null) {
                // if (theRequestResource.getIdElement().hasIdPart() == false) {
                // return OperationExamineDirection.IN_UNCATEGORIZED;
                // }
                // }
                // }
                return OperationExamineDirection.IN;

            case META:
            case META_ADD:
            case META_DELETE:
                // meta operations do not apply yet
                return OperationExamineDirection.NONE;

            case GET_PAGE:
            case HISTORY_INSTANCE:
            case HISTORY_SYSTEM:
            case HISTORY_TYPE:
            case READ:
            case SEARCH_SYSTEM:
            case SEARCH_TYPE:
            case VREAD:
                return OperationExamineDirection.OUT;

            case TRANSACTION:
                return OperationExamineDirection.BOTH;

            case VALIDATE:
                // Nothing yet
                return OperationExamineDirection.NONE;

            default:
                // Should not happen
                throw new IllegalStateException("Unable to apply security to event of type " + theOperation);
        }

    }

    /**
     * check if a resource is forbidden (policy/rule evaluation)
     * @param theRequestDetails the request details
     * @param resource the resource to check
     * @return true, if access to resource is forbidden
     */
    private boolean checkResourceForbidden(RequestDetails theRequestDetails, IBaseResource resource) {

        RequestTypeEnum action = theRequestDetails.getRequestType();  //GET or POST

        if (resource != null) {
            String resourceName = resource.getClass().getSimpleName();
            String fullResourceName = resource.getClass().getName();
            String resourceId = resource.getIdElement().getIdPart();
            String patientId;

            if (!resourceName.equals("Patient")) {
                //get matching Patient ID
                try {
                    Class c = Class.forName(fullResourceName);

                    Method getSubject = c.getMethod(ReferenceDictionaries.PAT_REF_METH_DICT.get(resourceName));
                    Reference ref = (Reference) getSubject.invoke(resource);

                    patientId = ref.getReference().split("/")[1];

                } catch (ClassNotFoundException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                    e.printStackTrace();
                    throw new ResourceNotFoundException("Requested resource doesn't exist");
                }
            } else {
                patientId = resourceId;
            }

            //create xacml request..
            RequestCreator rc = new RequestCreator();
            rc.setAction(action.toString()); //TODO only GET atm
            rc.setResourceType(resourceName);
            rc.setResourceId(resourceId);
            rc.setUser(currentUserId);
            rc.setPatientId(patientId);

            String request = rc.createRequest();

            //.. and evaluate it
            PolicyEvaluator pe = new PolicyEvaluator();
            EvaluationResult result = pe.evaluate(request, policyServerUrl + "/evaluate");

            if (result != null) {
                return result.getResult() == EvaluationResultType.DENY;
            } else {
                //return true;
                throw new ForbiddenOperationException("Policy evaluation not possible - no access"); //TODO maybe find a better exception or no exception?
            }
        } //should never happen..
        return true;
    }

    private static List<IBaseResource> toListOfResourcesAndExcludeContainer(IBaseResource theResponseObject, FhirContext fhirContext) {
        if (theResponseObject == null) {
            return Collections.emptyList();
        }

        List<IBaseResource> retVal;

        boolean isContainer = false;
        if (theResponseObject instanceof IBaseBundle) {
            isContainer = true;
        } else if (theResponseObject instanceof IBaseParameters) {
            isContainer = true;
        }

        if (!isContainer) {
            return Collections.singletonList(theResponseObject);
        }

        retVal = fhirContext.newTerser().getAllPopulatedChildElementsOfType(theResponseObject, IBaseResource.class);

        // Exclude the container
        if (retVal.size() > 0 && retVal.get(0) == theResponseObject) {
            retVal = retVal.subList(1, retVal.size());
        }

        return retVal;
    }

    private void resetCurrentParameters() {
        currentUserId = null;
    }

    private Resource getResourceFromBody(RequestDetails theRequestDetails, Class<Condition> theResourceType) {
        try {
            IParser parser = theRequestDetails.getFhirContext().newXmlParser();
            byte[] bytes = theRequestDetails.loadRequestContents();
            String content = new String(bytes, "UTF-8");
            return parser.parseResource(theResourceType, content);
        }
        catch (UnsupportedEncodingException ex) {
            ex.printStackTrace();
            return null;
        }
    }
}