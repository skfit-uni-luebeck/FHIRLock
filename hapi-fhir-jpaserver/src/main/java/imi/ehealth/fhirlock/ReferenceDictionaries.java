package imi.ehealth.fhirlock;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Dictionaries for mapping FHIR resource types and the matching patient parameter and methods for patient reference
 */
public class ReferenceDictionaries {

    public static final Map<String, String> PAT_REF_DICT = createMapRef();

    private static Map<String, String> createMapRef() {
        Map<String, String> refMap = new HashMap<>();
        refMap.put("Patient", "_id");
        refMap.put("Condition", "subject");
        refMap.put("Procedure" , "subject");
        refMap.put("Observation", "subject");
        return Collections.unmodifiableMap(refMap);
    }

    public static final Map<String, String> PAT_REF_METH_DICT = createMapMeth();

    private static Map<String, String> createMapMeth() {
        Map<String, String> refMap = new HashMap<>();
        refMap.put("Condition", "getSubject");
        refMap.put("Procedure" , "getSubject");
        refMap.put("Observation", "getSubject");
        return Collections.unmodifiableMap(refMap);
    }
}
