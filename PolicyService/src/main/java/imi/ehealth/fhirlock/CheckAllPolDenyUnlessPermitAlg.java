package imi.ehealth.fhirlock;

import org.wso2.balana.AbstractPolicy;
import org.wso2.balana.MatchResult;
import org.wso2.balana.ObligationResult;
import org.wso2.balana.combine.PolicyCombinerElement;
import org.wso2.balana.combine.PolicyCombiningAlgorithm;
import org.wso2.balana.ctx.AbstractResult;
import org.wso2.balana.ctx.EvaluationCtx;
import org.wso2.balana.ctx.ResultFactory;
import org.wso2.balana.xacml3.Advice;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom Policy Combination Algorithm
 * "Check all polices with deny-unless-permit algorithm"
 * Evaluates ALL policies and returns deny, unless at least one policy returns a permit
 */
public class CheckAllPolDenyUnlessPermitAlg extends PolicyCombiningAlgorithm {
    /**
     * The standard URN used to identify this algorithm
     */
    public static final String algId = "urn:oasis:names:tc:xacml:3.0:policy-combining-algorithm:" +
            "check-all-deny-unless-permit";

    // a URI form of the identifier
    private static URI identifierURI;
    // exception if the URI was invalid, which should never be a problem
    private static RuntimeException earlyException;

    static {
        try {
            identifierURI = new URI(algId);
        } catch (URISyntaxException se) {
            earlyException = new IllegalArgumentException();
            earlyException.initCause(se);
        }
    }

    /**
     * Standard constructor.
     */
    public CheckAllPolDenyUnlessPermitAlg() {
        super(identifierURI);

        if (earlyException != null){
            throw earlyException;
        }
    }

    @Override
    public AbstractResult combine(EvaluationCtx context, List parameters, List policyElements) {

        List<ObligationResult> denyObligations = new ArrayList<ObligationResult>();
        List<Advice> denyAdvices = new ArrayList<Advice>();
        AbstractResult permitResult = null;

        for (Object policyElement : policyElements) {
            AbstractPolicy policy = ((PolicyCombinerElement) (policyElement)).getPolicy();
            //no matching check: this is only used for the which-patients-allowed-request -> no matching required!
            //if we want the matching check here, policy targets must fullfill "Patient"
            AbstractResult result = policy.evaluate(context);
            int value = result.getDecision();
            if (value == AbstractResult.DECISION_PERMIT) {
                //important part: no stopping here, evaluate all other policies to get all advices
                if (permitResult == null) {
                    permitResult = result;
                } else {
                    permitResult.getObligations().addAll(result.getObligations());
                    permitResult.getAdvices().addAll(result.getAdvices());
                }
            } else if (value == AbstractResult.DECISION_DENY) {
                denyObligations.addAll(result.getObligations());
                denyAdvices.addAll(result.getAdvices());
            }

        }

        // if there is not any value of PERMIT. The return DENY
        if (permitResult != null) {
            return permitResult;
        }
        return ResultFactory.getFactory().getResult(AbstractResult.DECISION_DENY, denyObligations,
                denyAdvices, context);
    }
}
