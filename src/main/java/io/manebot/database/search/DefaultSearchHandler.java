package io.manebot.database.search;

import io.manebot.database.Database;
import io.manebot.database.search.handler.SearchArgumentHandler;
import io.manebot.database.search.handler.SearchOrderHandler;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import javax.persistence.metamodel.Metamodel;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public class DefaultSearchHandler<T> implements SearchHandler<T> {
    private final Database database;
    private final Class<T> entityClass;
    private final Map<String, SearchArgumentHandler> argumentHandlers;
    private final Map<String, SearchArgumentHandler> commandHandlers;
    private final SearchArgumentHandler defaultCommandHandler;
    private final Collection<Consumer<Clause<T>>> always;
    private final Map<String, SearchOrderHandler> orderHandlers;

    private final SearchArgumentHandler stringHandler;
    private final Search.Order defaultOrder;

    public DefaultSearchHandler(Database database, Class<T> entityClass,
                                Map<String, SearchArgumentHandler> argumentHandlers,
                                Map<String, SearchArgumentHandler> commandHandlers,
                                SearchArgumentHandler defaultCommandHandler,
                                SearchArgumentHandler stringHandler,
                                Collection<Consumer<Clause<T>>> always,
                                Map<String, SearchOrderHandler> orderHandlers,
                                Search.Order defaultOrder) {
        this.database = database;
        this.entityClass = entityClass;
        this.argumentHandlers = argumentHandlers;
        this.commandHandlers = commandHandlers;
        this.defaultCommandHandler = defaultCommandHandler;
        this.stringHandler = stringHandler;
        this.always = always;

        this.orderHandlers = orderHandlers;
        this.defaultOrder = defaultOrder;
    }

    @Override
    public Class<T> getEntityClass() {
        return entityClass;
    }

    @Override
    public Database getDatabase() {
        return database;
    }

    @Override
    public SearchArgumentHandler getArgumentHandler(String s) {
        return argumentHandlers.get(s);
    }

    @Override
    public SearchArgumentHandler getCommandHandler(String s) {
        SearchArgumentHandler handler = commandHandlers.get(s);
        if (handler == null) return defaultCommandHandler;
        else return handler;
    }

    @Override
    public SearchArgumentHandler getStringHandler() {
        return stringHandler;
    }

    /**
     * Internally applies criteria filters for the given search.
     * @param criteriaBuilder CriteriaBuilder to use to construct new criteria builders
     * @param search Search to apply
     */
    private Predicate[] buildPredicates(Root root, CriteriaBuilder criteriaBuilder, Search search) {
        // Translate lexical analysis into criteria filters
        DefaultClause<T> clause = new DefaultClause<>(
                null,
                SearchOperator.UNSPECIFIED,
                this,
                criteriaBuilder,
                root,
                (complete) -> { /* Handled in method body */ }
        );

        for (SearchPredicate predicate : search.getLexicalClause().getActions())
            predicate.handle(clause);

        // Apply "always" filters
        always.forEach(clauseConsumer -> clauseConsumer.accept(clause));

        Collection<Predicate> predicateCollection = clause.getPredicates();
        Predicate[] predicates = new Predicate[predicateCollection.size()];
        predicateCollection.toArray(predicates);

        return predicates;
    }

    private List<T> searchIntl(EntityManager session,
                            CriteriaBuilder criteriaBuilder,
                            CriteriaQuery<T> selectQuery,
                            Root<T> root,
                            Predicate[] predicates,
                            Collection<Search.Order> orders,
                            int offset, int maxResults) {
        // SELECT clause
        selectQuery.select(root);

        // WHERE clause
        selectQuery.where(predicates);

        // ORDER BY clause
        List<Search.Order> searchOrders = new ArrayList<>(orders);
        if (searchOrders.size() <= 0 && defaultOrder != null) // default to the default order if none are given
            searchOrders.add(defaultOrder);

        List<Order> compiledOrders = new ArrayList<>();
        if (orders.size() > 0) {
            for (Search.Order order : searchOrders) {
                SearchOrderHandler handler = this.orderHandlers.get(order.getKey().toLowerCase());
                if (handler == null)
                    throw new IllegalArgumentException("Unknown sort order: " + order.getKey());

                compiledOrders.add(handler.handle(root, criteriaBuilder, order.getOrder()));
            }
        }

        if (compiledOrders.size() > 0) selectQuery.orderBy(compiledOrders);

        TypedQuery<T> typedQuery = session.createQuery(selectQuery);
        typedQuery.setFirstResult(offset); // page enumeration
        typedQuery.setMaxResults(maxResults); // LIMIT clause

        return typedQuery.getResultList();
    }

    @Override
    public SearchResult<T> search(Search search, int maxResults) throws IllegalArgumentException {
        if (search.getPage() <= 0) throw new IllegalArgumentException("Invalid page: " + search.getPage());
        else if (maxResults <= 0) throw new IllegalArgumentException("Invalid result size: " + maxResults);

        return database.execute(s -> {
            Metamodel metamodel = s.getMetamodel();
            CriteriaBuilder criteriaBuilder = s.getCriteriaBuilder();
            CriteriaQuery<T> selectQuery = criteriaBuilder.createQuery(getEntityClass());
            Root<T> root = selectQuery.from(metamodel.entity(getEntityClass()));
            Predicate[] predicates = buildPredicates(root, criteriaBuilder, search);

            CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
            countQuery.select(criteriaBuilder.count(countQuery.from(metamodel.entity(getEntityClass()))));
            s.createQuery(countQuery);
            countQuery.where(predicates);
            Long count = s.createQuery(countQuery).getSingleResult();

            if (count <= 0L) {
                return new DefaultSearchResult<>(
                        search,
                        this,
                        0,
                        maxResults,
                        search.getPage(),
                        Collections.emptyList()
                );
            } else {
                List<T> list = searchIntl(
                        s, criteriaBuilder,
                        selectQuery, root, predicates, search.getOrders(),
                        maxResults * (search.getPage()-1), maxResults
                );

                return new DefaultSearchResult<>(
                        search,
                        this,
                        count,
                        maxResults,
                        search.getPage(),
                        list
                );
            }
        });
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public SearchResult<T> random(Search search, int maxResults) throws SQLException, IllegalArgumentException {
        if (search.getPage() != 1) throw new IllegalArgumentException("Invalid page: " + search.getPage());
        else if (maxResults <= 0) throw new IllegalArgumentException("Invalid result size: " + maxResults);

        return database.execute(s -> {
            Metamodel metamodel = s.getMetamodel();
            CriteriaBuilder criteriaBuilder = s.getCriteriaBuilder();
            CriteriaQuery<T> selectQuery = criteriaBuilder.createQuery(getEntityClass());
            Root<T> root = selectQuery.from(metamodel.entity(getEntityClass()));
            Predicate[] predicates = buildPredicates(root, criteriaBuilder, search);

            CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
            countQuery.select(criteriaBuilder.count(countQuery.from(metamodel.entity(getEntityClass()))));
            s.createQuery(countQuery);
            countQuery.where(predicates);
            Long count = s.createQuery(countQuery).getSingleResult();

            if (count <= 0L) {
                return new DefaultSearchResult<>(
                        search,
                        this,
                        0,
                        maxResults,
                        1,
                        Collections.emptyList()
                );
            } else {
                // plan query
                //TODO: find a better performing version of random distribution generation
                List<Long> offsets = new ArrayList<>();
                if (maxResults < count) { // asking for less results than are in the total result set size
                    for (long i = 0; i < maxResults; i++) {
                        long n;
                        while (offsets.contains(n = ThreadLocalRandom.current().nextLong(count))) ;
                        offsets.add(n);
                    }
                } else { // asking for more or equal results than are in the result set size
                    for (long n = 0; n < count; n ++)
                        offsets.add(n);
                    Collections.shuffle(offsets);
                }

                List<T> result = new ArrayList<>(maxResults);

                for (Long offset : offsets) {
                    result.addAll(searchIntl(
                            s, criteriaBuilder,
                            selectQuery, root,
                            predicates, search.getOrders(),
                            (int)(long)offset, 1
                    ));
                }

                return new DefaultSearchResult<T>(
                        search,
                        this,
                        count,
                        maxResults,
                        1,
                        result
                );
            }
        });
    }

    private static class QueryExpression {
        private final SearchOperator operator;
        private final Expression<Boolean> expression;

        private QueryExpression(SearchOperator operator, Expression<Boolean> expression) {
            this.operator = operator;
            this.expression = expression;
        }

        public SearchOperator getOperator() {
            return operator;
        }

        public Expression<Boolean> getExpression() {
            return expression;
        }
    }

    private static class DefaultClause<T> implements Clause<T>, Consumer<DefaultClause> {
        private final Clause<T> parent;
        private final SearchOperator operator;
        private final DefaultSearchHandler<T> searchHandler;
        private final CriteriaBuilder criteriaBuilder;
        private final Root root;
        private final Consumer<DefaultClause> completedConsumer;

        private Predicate predicate;

        private DefaultClause(Clause<T> parent,
                              SearchOperator operator,
                              DefaultSearchHandler<T> searchHandler,
                              CriteriaBuilder criteriaBuilder,
                              Root root,
                              Consumer<DefaultClause> completedConsumer) {
            this.parent = parent;
            this.operator = operator;
            this.searchHandler = searchHandler;
            this.criteriaBuilder = criteriaBuilder;
            this.root = root;
            this.completedConsumer = completedConsumer;
        }

        @Override
        public SearchHandler<T> getSearchHandler() {
            return searchHandler;
        }

        @Override
        public Root getRoot() {
            return root;
        }

        @Override
        public CriteriaBuilder getCriteriaBuilder() {
            return criteriaBuilder;
        }

        @Override
        public void addPredicate(SearchOperator searchOperator, Predicate predicate) {
            if (this.predicate == null) {
                this.predicate = predicate;
            } else {
                switch (searchOperator) {
                    case MERGE:
                        this.predicate = getCriteriaBuilder().and(this.predicate, predicate);
                        break;
                    case INCLUDE:
                        this.predicate = getCriteriaBuilder().or(this.predicate, predicate);
                        break;
                    case EXCLUDE:
                        this.predicate = getCriteriaBuilder().and(this.predicate, predicate.not());
                        break;
                    default:
                        throw new IllegalArgumentException("Illegal compound predicate operator: " + searchOperator);
                }
            }
        }

        @Override
        public Clause<T> push(SearchOperator searchOperator) {
            return new DefaultClause<>(this, operator, searchHandler, criteriaBuilder, root, this);
        }

        @Override
        public Clause<T> pop() throws IllegalArgumentException {
            // Popping should construct and add a predicate if we have any in the buffer.
            completedConsumer.accept(this);
            return parent;
        }

        @Override
        public void accept(DefaultClause defaultClause) {
            // Accept a sub-clause
            addPredicate(defaultClause.operator, defaultClause.predicate);
        }

        /**
         * Gets a collection of all compiled predicates.
         * @return predicate collection.
         */
        public Collection<Predicate> getPredicates() {
            if (predicate == null) return Collections.emptyList();
            else return Collections.singleton(predicate);
        }
    }

    public static class Builder<T> implements SearchHandler.Builder<T> {
        private final Database database;
        private final Class<T> entityClass;
        private final Map<String, SearchArgumentHandler> argumentHandlers = new LinkedHashMap<>();
        private final Map<String, SearchArgumentHandler> commandHandlers = new LinkedHashMap<>();
        private SearchArgumentHandler defaultCommandHandler = null;
        private final Map<String, SearchOrderHandler> orderHandlers = new LinkedHashMap<>();
        private final Collection<Consumer<Clause<T>>> always = new LinkedList<>();

        private Search.Order defaultOrder;
        private SearchArgumentHandler stringHandler;

        public Builder(Database database, Class<T> entityClass) {
            this.database = database;
            this.entityClass = entityClass;
        }

        @Override
        public SearchHandler.Builder<T> argument(String s, SearchArgumentHandler searchArgumentHandler) {
            this.argumentHandlers.put(s, searchArgumentHandler);
            return this;
        }

        @Override
        public SearchHandler.Builder<T> command(String s, SearchArgumentHandler searchArgumentHandler) {
            this.commandHandlers.put(s, searchArgumentHandler);
            return this;
        }

        @Override
        public SearchHandler.Builder<T> command(SearchArgumentHandler handler) {
            this.defaultCommandHandler = handler;
            return this;
        }

        @Override
        public SearchHandler.Builder<T> string(SearchArgumentHandler searchArgumentHandler) {
            this.stringHandler = searchArgumentHandler;
            return this;
        }

        @Override
        public SearchHandler.Builder<T> always(Consumer<Clause<T>> consumer) {
            this.always.add(consumer);
            return this;
        }

        @Override
        public SearchHandler.Builder<T> sort(String key, SearchOrderHandler handler) {
            orderHandlers.put(key.toLowerCase(), handler);
            return this;
        }

        @Override
        public SearchHandler.Builder<T> defaultSort(String key, SortOrder order) {
            if (!orderHandlers.containsKey(key.toLowerCase()))
                throw new IllegalArgumentException("Unknown sort order: " + key);

            defaultOrder = new Search.DefaultOrder(key.toLowerCase(), order);
            return this;
        }

        @Override
        public SearchHandler<T> build() throws IllegalArgumentException {
            return new DefaultSearchHandler<>(
                    database,
                    entityClass,
                    argumentHandlers,
                    commandHandlers,
                    defaultCommandHandler,
                    stringHandler,
                    always,
                    orderHandlers,
                    defaultOrder
            );
        }
    }
}
