package imi.ehealth.fhirlock.policy;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of the evaluation of a xacml request response
 */
public class EvaluationResult {
    private EvaluationResultType result;

    public EvaluationResult() {
        this.resultIds = new ArrayList<>();
    }

    private List<String> resultIds;

    /**
     * get the determined ids of the request (which ids allowed?)
     * @return list of ids
     */
    public List<String> getResultIds() {
        return resultIds;
    }

    /**
     * add a result id to list
     * @param resultId the id to add
     */
    public void addResultId(String resultId) {
        resultIds.add(resultId);
    }

    /**
     * get the type of the result (permit or deny)
     * @return the result
     */
    public EvaluationResultType getResult() {
        return this.result;
    }

    /**
     * set the type of the result (permit or deny)
     * @param result the result
     */
    public void setResult(EvaluationResultType result) {
        this.result = result;
    }

}
