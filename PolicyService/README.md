## PolicyService

**Policy Decision Point**  
This service serves as an external policy decision point and provides two functions on port 4567. One evaluates a full XACML-Request, the other is an additional function to get the list of all patients that a user may access (in general) without needing to evaluate the policies of all patients. (It's based on additional information stored in the Mongo Database.) This is used by the Authorization Layer in the HAPI-Server to extend search requests. 

It's a [Java Spark](http://sparkjava.com/) Microservice with the following entry points:  
1. _http://localhost:4567/hello_  
       Hello World (just for testing)  
2. _http://localhost:4567/evaluate_  
       accepts POST request with XACML/xml-Request as data  
       returns XACML/xml-Response   
3. _http://localhost:4567/patientAccessList_  
       accepts POST request with userId as String   
       returns semicolon separated list of patients for which (basic) access is allowed  
      
The service uses MongoDB as a Policy Storage. DB configuration can be set in ```resources/dbconfig.xml```.  
  * _path_ is the host path where the db server is located, e.h. localhost
  * _port_ is the database server port
  * _name_ specifies the database name 
  * _collection_ is the mongodb collection that contains the policies   
  
  At the moment no user credentials like _user_ and _password_ are used to connect to the database.

For basic testing purposes just configure the service for your database and start from IDE.                                                                                                      
