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

    public Set<Quad> getStatementWithAllContexts(Quad statement) {
//        Logger logger = LoggerFactory.getLogger(this.getClass());
        Set<Quad> statementWithAllContexts = new HashSet<Quad>();
        statementWithAllContexts.add(new Quad(statement.subject, statement.predicate, statement.object, statement.context, statement.status));
        try (StatementIdIterator ctxIter = repositoryConnection.getStatements(statement.subject, statement.predicate, statement.object, true, 0, ProofPlugin.excludeDeletedHiddenInferred)) {
            logger.debug(String.format("Contexts for %d %d %d", statement.subject, statement.predicate, statement.object));
            while (ctxIter.hasNext()) {
                statementWithAllContexts.add(new Quad(ctxIter.subj, ctxIter.pred, ctxIter.obj, ctxIter.context, ctxIter.status));
                logger.debug(String.valueOf(ctxIter.context));
                ctxIter.next();
            }
            logger.debug("All contexts for antecedent" + statementWithAllContexts);
        }
        return statementWithAllContexts;
    }
}