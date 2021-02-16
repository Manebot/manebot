package io.manebot.database.search;

import io.manebot.database.Database;
import io.manebot.database.DatabaseManager;
import io.manebot.database.HibernateManager;
import io.manebot.database.search.handler.SearchHandlerPropertyEquals;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Test;

import javax.persistence.*;
import java.sql.SQLException;
import java.util.Properties;

import static junit.framework.TestCase.*;

public class SearchTest {

    @Test
    public void testSearch_AlwaysField() throws SQLException {
        Properties properties = new Properties();
        properties.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        properties.put("hibernate.connection.url", "jdbc:h2:mem:AlwaysField;DB_CLOSE_DELAY=-1");
        properties.put("hibernate.show_sql", "true");
        properties.put("hibernate.format_sql", "true");

        DatabaseManager databaseManager = new HibernateManager(null, properties);
        Database testDatabase =
                databaseManager.defineDatabase("test", builder -> builder.registerEntity(TestTable.class));

        TestTable searchableRow = testDatabase.executeTransaction(em -> {
            TestTable row = new TestTable();
            row.setSearchable(true);
            row.setName("Searchable");
            em.persist(row);
            return row;
        });

        TestTable unsearchableRow = testDatabase.executeTransaction(em -> {
            TestTable row = new TestTable();
            row.setSearchable(false);
            row.setName("Not Searchable");
            em.persist(row);
            return row;
        });

        SearchHandler<TestTable> handler = testDatabase.createSearchHandler(TestTable.class)
                .always(clause -> clause.addPredicate(SearchOperator.MERGE, clause.getCriteriaBuilder().equal(
                        clause.getRoot().get("searchable"),
                        true
                ))).build();

        SearchResult<TestTable> result = handler.search(Search.parse(""), 2);

        assertTrue(result.getResults().contains(searchableRow));
        assertFalse(result.getResults().contains(unsearchableRow));
    }


    @Test
    public void testSearch_MultipleAlwaysField() throws SQLException {
        Properties properties = new Properties();
        properties.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        properties.put("hibernate.connection.url", "jdbc:h2:mem:MultipleAlwaysField;DB_CLOSE_DELAY=-1");
        properties.put("hibernate.show_sql", "true");
        properties.put("hibernate.format_sql", "true");

        DatabaseManager databaseManager = new HibernateManager(null, properties);
        Database testDatabase =
                databaseManager.defineDatabase("test", builder -> builder.registerEntity(TestTable.class));

        TestTable searchableRow = testDatabase.executeTransaction(em -> {
            TestTable row = new TestTable();
            row.setSearchable(true);
            row.setName("Searchable");
            em.persist(row);
            return row;
        });

        TestTable unsearchableRow = testDatabase.executeTransaction(em -> {
            TestTable row = new TestTable();
            row.setSearchable(false);
            row.setName("Not Searchable");
            em.persist(row);
            return row;
        });

        SearchHandler<TestTable> handler = testDatabase.createSearchHandler(TestTable.class)
                .argument("constant", new SearchHandlerPropertyEquals("constant2"))
                .always(clause -> {
                    clause.addPredicate(SearchOperator.MERGE, clause.getCriteriaBuilder().equal(
                            clause.getRoot().get("constant1"),
                            true
                    ));
                    clause.addPredicate(SearchOperator.MERGE, clause.getCriteriaBuilder().equal(
                            clause.getRoot().get("searchable"),
                            true
                    ));
                }).build();

        SearchResult<TestTable> result = handler.search(Search.parse("constant:Constant"), 2);

        assertTrue(result.getResults().contains(searchableRow));
        assertFalse(result.getResults().contains(unsearchableRow));
    }


    @Test(expected = junit.framework.ComparisonFailure.class)
    public void testSearch_DefaultSort() throws SQLException {
        Properties properties = new Properties();
        properties.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        properties.put("hibernate.connection.url", "jdbc:h2:mem:DefaultSort;DB_CLOSE_DELAY=-1");
        properties.put("hibernate.show_sql", "true");
        properties.put("hibernate.format_sql", "true");

        DatabaseManager databaseManager = new HibernateManager(null, properties);
        Database testDatabase =
                databaseManager.defineDatabase("test", builder -> builder.registerEntity(TestTable.class));

        TestTable row1 = testDatabase.executeTransaction(em -> {
            TestTable row = new TestTable();
            row.setSearchable(true);
            row.setName("A");
            em.persist(row);
            return row;
        });

        TestTable row2 = testDatabase.executeTransaction(em -> {
            TestTable row = new TestTable();
            row.setSearchable(true);
            row.setName("B");
            em.persist(row);
            return row;
        });

        for (SortOrder order : SortOrder.values()) {
            SearchHandler<TestTable> handler = testDatabase.createSearchHandler(TestTable.class)
                    .argument("constant", new SearchHandlerPropertyEquals("constant2"))
                    .sort("name", "name")
                    .defaultSort("name", order)
                    .build();

            SearchResult<TestTable> result = handler.search(Search.parse(""), 2);

            assertEquals(result.getResults().get(0).getName(), row1.getName());
            assertEquals(result.getResults().get(1).getName(), row2.getName());
        }
    }

    @javax.persistence.Entity
    @Table()
    public static class TestTable {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column()
        private int testId;

        @Column()
        private String name;

        @Column()
        private boolean constant1 = true;

        @Column()
        private String constant2 = "Constant";

        @Column()
        private boolean searchable;

        public TestTable() {

        }

        public int getTestId() {
            return testId;
        }

        public void setTestId(int testId) {
            this.testId = testId;
        }

        public boolean getSearchable() {
            return searchable;
        }

        public void setSearchable(boolean searchable) {
            this.searchable = searchable;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object obj) {
            return ((TestTable)obj).getTestId() == getTestId();
        }

        public boolean isConstant1() {
            return constant1;
        }

        public void setConstant1(boolean constant1) {
            this.constant1 = constant1;
        }

        public String getConstant2() {
            return constant2;
        }

        public void setConstant2(String constant2) {
            this.constant2 = constant2;
        }
    }

}
