package imi.ehealth.fhirlock;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Configuration for database (mongo or basex)
 */
public class DbConfiguration {
    private static final Logger log = Logger.getLogger( DbConfiguration.class.getName() );

    private String path;
    private int port;
    private String name;
    private String user;
    private String pass;
    private String collection;

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public String getPath() {
        return this.path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getPort() {
        return this.port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUser() {
        return this.user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPass() {
        return this.pass;
    }

    public void setPass(String pass) {
        this.pass = pass;
    }

    /**
     * load configuration from config file
     */
    public void load() {
        log.info("Start loading db configuration..");
        //Load DBConfig from file "dbconfig.xml" in resources
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("dbconfig.xml");
        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = null;
        try {
            docBuilder = docBuilderFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(in);
            doc.getDocumentElement().normalize();

            this.path = doc.getElementsByTagName("path").item(0).getTextContent();
            this.port = Integer.parseInt(doc.getElementsByTagName("port").item(0).getTextContent());
            this.name = doc.getElementsByTagName("name").item(0).getTextContent();
            this.user = doc.getElementsByTagName("user").item(0).getTextContent();
            this.pass = doc.getElementsByTagName("password").item(0).getTextContent();
            this.collection = doc.getElementsByTagName("collection").item(0).getTextContent();
        }
        catch (IOException | ParserConfigurationException | SAXException e) {
            e.printStackTrace();
        }
    }
}
