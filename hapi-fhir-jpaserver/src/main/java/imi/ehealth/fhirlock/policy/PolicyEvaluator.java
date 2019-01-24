package imi.ehealth.fhirlock.policy;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.wso2.balana.ParsingException;
import org.wso2.balana.ctx.AbstractResult;
import org.wso2.balana.ctx.AttributeAssignment;
import org.wso2.balana.ctx.ResponseCtx;
import org.wso2.balana.xacml3.Advice;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.wso2.balana.ctx.AbstractResult.DECISION_PERMIT;

/**
 * Evaluates xacmls request again a distant pdp
 */
public class PolicyEvaluator {
    private final String ATTRIBUTE_ID = "urn:ruleInformation:resourceid";

    /**
     * evaluate a xacml request and return the result in form of a @EvaluationResult
     * @param xacmlRequest the xacml request as string
     * @param path path of the pdp service
     * @return the result
     */
    public EvaluationResult evaluate(String xacmlRequest,String path) {
        //Send request to pdp
        String response = evaluateRequest(xacmlRequest, path);
        try {
            EvaluationResult er = new EvaluationResult();
            //load xml document
            Element xacmlResponse = this.getXacmlResponse(response);
            if (xacmlResponse != null) {
                ResponseCtx responseCtx = ResponseCtx.getInstance(xacmlResponse);

                //get the first result
                AbstractResult result = responseCtx.getResults().iterator().next();

                if (result.getDecision() == DECISION_PERMIT) {
                    er.setResult(EvaluationResultType.PERMIT);
                    //loop all advices
                    for (Advice advice : result.getAdvices()) {
                        for (AttributeAssignment att : advice.getAssignments()) {
                            switch (att.getAttributeId().toString()) {
                                case ATTRIBUTE_ID:
                                    //this is the important part: get all the allowed ids
                                    er.addResultId(att.getContent());
                                    break;

                            }
                        }
                    }
                } else {
                    //result is "deny"
                    er.setResult(EvaluationResultType.DENY);
                }
            } else {
                //no decision possible due to wrong formatted xml
                er.setResult(EvaluationResultType.NO_DECISION);
            }
            return er;
        } catch (ParsingException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Send request to server
     * @param xacmlRequest the request so send
     * @param path path of the pdp service
     * @return the xacml response
     */
    public String evaluateRequest(String xacmlRequest, String path) {
        String responseXml = null;
        CloseableHttpResponse response = null;
        try {
            //create http request to send to pdp server
            CloseableHttpClient httpclient = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(path);

            //xacml request as body content
            StringEntity entity = new StringEntity(xacmlRequest);
            httpPost.setEntity(entity);
            httpPost.setHeader("Content-type", "application/xml");

            response = httpclient.execute(httpPost);

            HttpEntity httpEntity = response.getEntity();
            responseXml = EntityUtils.toString(httpEntity);
            EntityUtils.consume(httpEntity);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            if (response != null) {
                try {
                    response.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return responseXml;
    }

    /**
     * load xml document from response xml
     * @param response the xacml response as xml string
     * @return the document root node
     */
    private Element getXacmlResponse(String response) {
        Document doc;
        ByteArrayInputStream inputStream = new ByteArrayInputStream(response.getBytes());
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        try {
            doc = dbf.newDocumentBuilder().parse(inputStream);
            return doc.getDocumentElement();
        }
        catch (Exception e) {
            System.err.println("DOM of request element can not be created from String");
            return null;
        }
        finally {
            try {
                inputStream.close();
            }
            catch (IOException e) {
                System.err.println("Error in closing input stream of XACML response");
            }
        }

    }
}
