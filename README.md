## FHIRLock

### Authorization for HAPI FHIR-Server

This project adds authorization functionality to the HAPI-FHIR-Server. It's based on the [JPA server example](https://github.com/jamesagnew/hapi-fhir/tree/master/hapi-fhir-jpaserver-example) provided by HAPI. The access control by authorization is added as an additional layer in form of an interceptor. It uses an external policy service built with [Java Spark](http://sparkjava.com/) and a  [MongoDB](https://www.mongodb.com/) as policy storage for evaluation.   
![alt text](service.png "Service")   
Currently only GET requests are included in the authorization functionality: The interceptor adds parameters to the search formed by the request (if possible) and, after receiving the result from the server's database, evaluates the resources in that response. It returns a 403 Forbidden if a user is trying to access a restricted resource. If the result of the GET is a Bundle, it may contain 0 resulting resources, although there might be more on the server matching the parameters of the original request. The interceptor also includes an authentication check.   

##### Project Structure
_hapi-fhir-jpaserver_ is the hapi server project, _PolicyService_ the matching policy service. _mongo_ contains a dump and a docker file for testing purposes (read further for instructions). To set up the system you basically need to follow these tree steps:
 1. Set up a mongo db server containing the policies 
 2. Configure the PolicyService for your mongo db and set it up 
 3. Configure the hapi server to your datebase (for the resources) and your PolicyService and set it up

##### Authentication
The server expects an Authorization Header submitted with the request in form of a JWT. The name of the requesting user should be included as "sub". The token must be signed with the user's private key. The public key is saved on the fhir server in the matching resource. For testing, we assume that the requester is a practitioner and therefore search for a resource of that type, but in the future the group or type, the requesting party belongs to might be added to the token. Both public key and user name are saved as identifers with defined systems ("http://www.germanhealth.de/pubkey" and "http://www.germanhealth.de/med"). In a real world scenario in the German Healthcare System new identification cards like _eArztausweis_ which provide signing functions could be used to sign the authorization token.  
Only requests with a correct authorization token are accepted. (For testing: all requests that don't include an authorization header are accepted, this allows using the hapi fhir web page in a browser. )  

##### Authorization
After authentication follows authorization. For that purpose, each requested resource has to be checked for access rights. Patient consent information is transformed into policies which are saved in the policy store. The HAPI service has no direct access to that database, but can send XACML requests to a known policy service (PolicyService). For each resulting resource a request is send to the policy decision point and evaluated. If the result is _deny_, the resource will not be returned to the user (and may result in a 403.)   
The example request `http://server/hapi-jpa-server-auth/Condition/19952` is a simple "READ" operation to the Condition with the ID 19952 on the server by the user "doc1234". The following XACML request is created and sent to the policy service:
```xml
<Request xmlns="urn:oasis:names:tc:xacml:3.0:core:schema:wd-17" CombinedDecision="false" ReturnPolicyIdList="false">
	<Attributes Category="urn:oasis:names:tc:xacml:3.0:attribute-category:action">
		<Attribute AttributeId="urn:oasis:names:tc:xacml:1.0:action:action-id" IncludeInResult="false">
			<AttributeValue DataType="http://www.w3.org/2001/XMLSchema#string">GET</AttributeValue>
		</Attribute>
	</Attributes>
	<Attributes Category="urn:oasis:names:tc:xacml:1.0:subject-category:access-subject">
		<Attribute AttributeId="urn:oasis:names:tc:xacml:1.0:subject:subject-id" IncludeInResult="false">
			<AttributeValue DataType="http://www.w3.org/2001/XMLSchema#string">doc1234</AttributeValue>
		</Attribute>
	</Attributes>
	<Attributes Category="urn:oasis:names:tc:xacml:3.0:attribute-category:resource">
		<Attribute AttributeId="urn:oasis:names:tc:xacml:1.0:resource:resource-type" IncludeInResult="false">
			<AttributeValue DataType="http://www.w3.org/2001/XMLSchema#string">Condition</AttributeValue>
		</Attribute>
		<Attribute AttributeId="urn:oasis:names:tc:xacml:1.0:resource:patient-id" IncludeInResult="false">
			<AttributeValue DataType="http://www.w3.org/2001/XMLSchema#string">1</AttributeValue>
		</Attribute>
		<Attribute AttributeId="urn:oasis:names:tc:xacml:1.0:resource:resource-id" IncludeInResult="false">
			<AttributeValue DataType="http://www.w3.org/2001/XMLSchema#string">19952</AttributeValue>
		</Attribute>
	</Attributes>
</Request> 
```
The policy service evaluates the request against the policies in the known database and returns the evaluation result. For a deny, a 403 FORBIDDEN would be the answer because of the direct reading access. Otherwise, the resource would returned.   
Another example might be the list of all patients the user is allowed to see, in a FHIR REST request `http://server/hapi-jpa-server-auth/Patient`. In a typical database (e.g. in a hospital) there are a lot of patients. This would result in many evaluation requests (one for each patient). To limit the number of requests and the number of resources the server should read from its database, the search request will be extended with the ids of all that patients the user is (at least basically) allowed to access. The PolicyService provides a method to return that list. With that list the original request will be transformed to something like `http://server/hapi-jpa-server-auth/Patient?_id=1,2,3`. The result is a bundle of resources that is already limited to those patients the user is allowed to access. No further evaluation is needed. If the request is a search for other resource types, the number is resulting resources is limited and less evaluation requests have to be performed. (The structure of the policies is built according to this: Basic access to the patient resource is always included, but access to other resource types or actual resources might be restricted.)   

##### Test with Docker
We provide a Docker configuration and some sample data for simple and easy testing. Please follow the following steps for setup:
1. Get all the sources.
2. For HAPI, check the configuration in `FHIRServerConfig.java` in `responseSearchAuthInterceptor` and `dataSource`. The database and policy service url should be set up for Docker use as follows:  
PolicyServer: `http://policy:4567`, Database: `jdbc:derby:/var/lib/jetty/webapps/db/jpaserver_derby_files;create=true`
3. Build the HAPI JPA Server *.war file with maven: `mvn install` You should find that file in the `target` directory.
4. Check the database path for the PolicyService in `resources/dbconfig.xml`: The _path_ should be `mongodb`.
5. Start on Docker with `docker-compose up` in the FHIRLock directory.

This should result in a mongo database with a basic policy set, the policy service and a hapi server with some basic data all up and running. You can access the hapi server on `http://localhost:8080/hapi-fhir-jpaserver-auth`.   
To test some REST requests use the following JWT for the Authorization Header: 
```
Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJkb2MxMjM0IiwiaXNzIjoiYXV0aDAiLCJuYW1lIjoiRG9jIn0.pdPbSYvNG0E_ISnO-Q2KEOlczsoQ8AYb65Rs3X1E33557Y9jmDOTZXIio_-4wf2f4IahAEl5OMh_CozTONux2eK1sAUVH0x1TbA3NLvwMWfd9HgtkojFOPK4BlK3HdxnbPmvHRL8t3Vk5D7AgJDWooCFomeY16X0cZgdlfBDzys
```
Test the following requests:   
`http://localhost:8080/hapi-fhir-jpaserver-auth/baseDstu3/Patient` returns 3 of 4 patients currently saved on the server.   
`http://localhost:8080/hapi-fhir-jpaserver-auth/baseDstu3/Patient?_id=34952`returns an empty bundle (no access to that resource)  
`http://localhost:8080/hapi-fhir-jpaserver-auth/baseDstu3/Condition/24952` returns a 403 (no access to that resource)  
`http://localhost:8080/hapi-fhir-jpaserver-auth/baseDstu3/Condition/19952` returns the requested resource  

##### License


##### Help?
If you have any questions, want further information or want to give us any other input, just send an email to github@itcr.uni-luebeck.de