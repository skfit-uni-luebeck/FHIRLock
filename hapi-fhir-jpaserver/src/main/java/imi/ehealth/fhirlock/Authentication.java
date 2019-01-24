package imi.ehealth.fhirlock;

import ca.uhn.fhir.jpa.dao.SearchParameterMap;
import imi.ehealth.fhirlock.crypto.KeyCreator;
import ca.uhn.fhir.jpa.rp.dstu3.PractitionerResourceProvider;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.TokenAndListParam;
import ca.uhn.fhir.rest.param.TokenOrListParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.hl7.fhir.dstu3.model.Identifier;
import org.hl7.fhir.dstu3.model.Practitioner;

import java.security.GeneralSecurityException;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Methods for Authentication issues
 */
public class Authentication {

    /**
     * get the "sub" claim value from a jwt token
     * @param token the jwt token
     * @return the userid
     */
    public static String getUserIdFromToken(String token) {
        try {
            DecodedJWT jwt = JWT.decode(token);
            return jwt.getClaim("sub").asString();
        }
        catch (JWTDecodeException jwt) {
            return null;
        }
    }

    /**
     * Check if a user who is a practitioner is the one who signed the token and is therefore authorized to access the server
     * @param token the jwt token (provided in the authentication header)
     * @param userId the extracted client user id
     * @param beans the resource servers provider beans
     * @return true, if the user is authenticated
     */
    public static Boolean checkAuthentication(String token, String userId, List<IResourceProvider> beans) {
        String publicKey = "";
        for (IResourceProvider ip : beans) {
            //find the bean for the resource type "Practitioner"
            if (ip.getResourceType().getSimpleName().equals("Practitioner")) {
                PractitionerResourceProvider prp = (PractitionerResourceProvider) ip;

                //create parameter map to search for the "userid" identifier
                SearchParameterMap paramMap = new SearchParameterMap();
                TokenAndListParam talParam = new TokenAndListParam();
                talParam.addValue(new TokenOrListParam().add(new TokenParam("http://www.germanhealth.de/med", userId)));
                paramMap.add("identifier", talParam);

                //..and search for matching resources
                IBundleProvider bundleProvider = prp.getDao().search(paramMap);
                if (bundleProvider.size() > 0) {
                    //we only need the first one (there shouldn't be more)
                    List resources = bundleProvider.getResources(0, 1);
                    Practitioner p = (Practitioner) resources.get(0);

                    //The resource needs another Identifier with the Doc's public key
                    Optional<Object> firstIdentifier = Arrays.stream(p.getIdentifier().toArray()).filter(o -> ((Identifier) o).getSystem().equals("http://www.germanhealth.de/pubkey")).findFirst();

                    if (firstIdentifier.isPresent()) {
                        //get the publicKey
                        Identifier i = (Identifier) firstIdentifier.get();;
                        publicKey = i.getValue();
                    }
                    else{ //no public key identifier found
                        throw new AuthenticationException("Practitioner doesn't have a public key in resource");
                    }

                }else{ //no practitioner with that userid found
                    throw new AuthenticationException("Practitioner with userId doesn't exist");
                }

            }
        }
        if(publicKey.length() > 0) {
            return Authentication.checkAuthN(token, publicKey);
        }else{
            return false;
        }
    }

    private static Boolean checkAuthN(String token, String key) {
        try {
            //Create key and algorithm..
            RSAPublicKey publicKey = (RSAPublicKey)KeyCreator.loadPublicKey(key);
            Algorithm algorithm = Algorithm.RSA256(publicKey, null);
            //..and verify the token
            JWTVerifier verifier = JWT.require(algorithm).build();
            DecodedJWT jwt = verifier.verify(token);
            return true;
        }
        catch (JWTVerificationException exception) {
            //no verification -> return false
            return false;
        }
        catch (GeneralSecurityException e) {
            e.printStackTrace();
            return false;
        }
    }
}
