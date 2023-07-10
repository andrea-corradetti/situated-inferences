package situatedInference;

import com.ontotext.trree.AbstractRepositoryConnection;
import com.ontotext.trree.StatementIdIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proof.ProofPlugin;

import java.util.HashSet;
import java.util.Set;

public class ContextResolver {

    private final AbstractRepositoryConnection repositoryConnection;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public ContextResolver(AbstractRepositoryConnection repositoryConnection) {
        this.repositoryConnection = repositoryConnection;
    }

    public Set<Quad> getAntecedentWithAllContexts(Quad antecedent) {
//        Logger logger = LoggerFactory.getLogger(this.getClass());
        Set<Quad> antecedentsWithAllContexts = new HashSet<Quad>();
        antecedentsWithAllContexts.add(new Quad(antecedent.subject, antecedent.predicate, antecedent.object, antecedent.context, antecedent.status));
        try (StatementIdIterator ctxIter = repositoryConnection.getStatements(antecedent.subject, antecedent.predicate, antecedent.object, true, 0, ProofPlugin.excludeDeletedHiddenInferred)) {
            logger.debug(String.format("Contexts for %d %d %d", antecedent.subject, antecedent.predicate, antecedent.object));
            while (ctxIter.hasNext()) {
                antecedentsWithAllContexts.add(new Quad(ctxIter.subj, ctxIter.pred, ctxIter.obj, ctxIter.context, ctxIter.status));
                logger.debug(String.valueOf(ctxIter.context));
                ctxIter.next();
            }
            logger.debug("All contexts for antecedent" + antecedentsWithAllContexts);
        }
        return antecedentsWithAllContexts;
    }
}