FROM jetty:9-jre8-alpine
#copy db files and set rights
USER root
ADD jpaserver_derby_files.tar /var/lib/jetty/webapps/db/
RUN chmod -R 777 /var/lib/jetty/webapps/db/jpaserver_derby_files/

#deploy *.war to jetty
USER jetty:jetty
ADD ./target/hapi-fhir-jpaserver-auth.war /var/lib/jetty/webapps/hapi-fhir-jpaserver-auth.war

#set port 8080
EXPOSE 8080