package imi.ehealth.fhirlock.policy;

import org.wso2.balana.utils.Constants.PolicyConstants;
import org.wso2.balana.utils.exception.PolicyBuilderException;
import org.wso2.balana.utils.policy.PolicyBuilder;
import org.wso2.balana.utils.policy.dto.AttributeElementDTO;
import org.wso2.balana.utils.policy.dto.AttributesElementDTO;
import org.wso2.balana.utils.policy.dto.RequestElementDTO;

import java.util.ArrayList;

/**
 *  Class for creating xacml requests
 */
public class RequestCreator {
    private String user;
    private String resourceType;
    private String resourceId;
    private String action;
    private String patientId;

    //additional attribute id for request
    private final String attributeIdPatientResource = "urn:oasis:names:tc:xacml:1.0:resource:patient-id";
    private final String attributeIdResourceType = "urn:oasis:names:tc:xacml:1.0:resource:resource-type";

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getUser() {
        return this.user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getResourceType() {
        return this.resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getAction() {
        return this.action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    /**
     * creates a request for/with the submitted parameters
     * @return the request as xml string
     */
    public String createRequest() {
        PolicyBuilder myPBuilder = PolicyBuilder.getInstance();
        RequestElementDTO reDTO = new RequestElementDTO();

        reDTO.setCombinedDecision(false);
        reDTO.setMultipleRequest(false);
        reDTO.setReturnPolicyIdList(false);

        ArrayList<AttributesElementDTO> attributesElementDTOs = new ArrayList<AttributesElementDTO>();
        //action
        AttributesElementDTO action = new AttributesElementDTO();
            action.setCategory(PolicyConstants.ACTION_CATEGORY_URI);
            AttributeElementDTO att = new AttributeElementDTO();
                att.setAttributeId(PolicyConstants.ACTION_ID);
                att.setIncludeInResult(false);
                att.setDataType(PolicyConstants.DataType.STRING);
                att.addAttributeValue(this.action);
            action.addAttributeElementDTO(att);
        //subject
        AttributesElementDTO subject = new AttributesElementDTO();
            subject.setCategory( PolicyConstants.SUBJECT_CATEGORY_URI);
            att = new AttributeElementDTO();
                att.setAttributeId(PolicyConstants.SUBJECT_ID_DEFAULT);
                att.setIncludeInResult(false);
                att.setDataType(PolicyConstants.DataType.STRING);
                att.addAttributeValue(this.user);
            subject.addAttributeElementDTO(att);
        //resourceType (resourceType-id + resourceType-type + pat-id)
        AttributesElementDTO resource = new AttributesElementDTO();
            resource.setCategory(PolicyConstants.RESOURCE_CATEGORY_URI);
                att = new AttributeElementDTO();
                att.setAttributeId(attributeIdResourceType);
                att.setIncludeInResult(false);
                att.setDataType(PolicyConstants.DataType.STRING);
                att.addAttributeValue(this.resourceType);
            resource.addAttributeElementDTO(att);
                if(this.patientId != null) {
                    att = new AttributeElementDTO();
                    att.setAttributeId(attributeIdPatientResource);
                    att.setIncludeInResult(false);
                    att.setDataType(PolicyConstants.DataType.STRING);
                    att.addAttributeValue(this.patientId);
                    resource.addAttributeElementDTO(att);
                }
                if(this.resourceId != null){
                    att = new AttributeElementDTO();
                    att.setAttributeId(PolicyConstants.RESOURCE_ID);
                    att.setIncludeInResult(false);
                    att.setDataType(PolicyConstants.DataType.STRING);
                    att.addAttributeValue(this.resourceId);
                    resource.addAttributeElementDTO(att);
                }
        attributesElementDTOs.add(action);
        attributesElementDTOs.add(subject);
        attributesElementDTOs.add(resource);
        reDTO.setAttributesElementDTOs(attributesElementDTOs);

        String myRequest = "";
        try {
            myRequest = myPBuilder.buildRequest(reDTO);
        }
        catch (PolicyBuilderException e) {
            e.printStackTrace();
        }
        return myRequest;
    }
}
